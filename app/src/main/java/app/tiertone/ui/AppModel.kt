package app.tiertone.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import app.tiertone.CrashLog
import app.tiertone.core.Channels
import app.tiertone.core.Config
import app.tiertone.core.ConfigStore
import app.tiertone.core.Stats
import app.tiertone.core.StatsStore
import app.tiertone.core.Tier
import app.tiertone.core.Vip
import app.tiertone.service.SorterService
import java.util.UUID

/** What Android currently allows TierTone to do. */
data class AccessState(val listener: Boolean, val post: Boolean, val dnd: Boolean) {
    companion object {
        fun check(context: Context) = AccessState(
            listener = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
            post = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            dnd = context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted,
        )
    }
}

data class PickedContact(val name: String, val number: String?, val lookupKey: String?)

/** UI state. Every change is written to the rules file at once; the listener picks it up. */
class AppModel(private val app: Context) {
    private val store = ConfigStore(app)
    private val statsStore = StatsStore(app)

    var config by mutableStateOf(store.load())
        private set
    var stats by mutableStateOf(Stats())
        private set
    var access by mutableStateOf(AccessState.check(app))
        private set
    var crashReport by mutableStateOf<String?>(null)
        private set
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set
    private var configStamp = store.stamp()

    fun refresh() {
        configStamp = store.stamp()
        config = store.load()
        access = AccessState.check(app)
        crashReport = CrashLog.read(app)
        tick()
        if (config.onboarded) syncChannels(config)
    }

    /** Called every few seconds while the app is on screen. */
    fun tick() {
        now = System.currentTimeMillis()
        stats = statsStore.load()
        // Pick up a rules file changed outside this screen (e.g. restored, or edited by a test harness).
        if (store.stamp() != configStamp) {
            configStamp = store.stamp()
            config = store.load()
        }
    }

    fun update(change: (Config) -> Config) {
        val before = config
        val after = change(before)
        if (after == before) return
        config = after
        store.save(after)
        configStamp = store.stamp()
        syncChannels(after)
        if (after.enabled && !before.enabled) {
            // The listener let go when TierTone was turned off; ask Android to bind it again.
            runCatching { NotificationListenerService.requestRebind(ComponentName(app, SorterService::class.java)) }
        }
    }

    fun setTier(pkg: String, tier: Tier?) = update { c ->
        c.copy(appTiers = if (tier == null) c.appTiers - pkg else c.appTiers + (pkg to tier))
    }

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on, pausedUntil = 0) }

    fun pauseFor(minutes: Int) = update { it.copy(pausedUntil = System.currentTimeMillis() + minutes * 60_000L) }

    fun resume() = update { it.copy(pausedUntil = 0) }

    /** Adds a VIP, or adds the number to an existing VIP picked from the same contact. */
    fun addVip(picked: PickedContact): Vip {
        val existing = config.vips.firstOrNull { picked.lookupKey != null && it.lookupKey == picked.lookupKey }
        if (existing != null) {
            val merged = existing.copy(phones = (existing.phones + listOfNotNull(picked.number)).distinct())
            updateVip(merged)
            return merged
        }
        val vip = Vip(
            id = UUID.randomUUID().toString().take(8),
            name = picked.name,
            phones = listOfNotNull(picked.number),
            lookupKey = picked.lookupKey,
            addedAt = System.currentTimeMillis(),
        )
        update { it.copy(vips = it.vips + vip) }
        return vip
    }

    fun updateVip(vip: Vip) = update { c -> c.copy(vips = c.vips.map { if (it.id == vip.id) vip else it }) }

    fun removeVip(id: String) = update { c -> c.copy(vips = c.vips.filterNot { it.id == id }) }

    fun resetRules() = update { it.reset() }

    fun clearCrashReport() {
        CrashLog.clear(app)
        crashReport = null
    }

    private fun syncChannels(c: Config) {
        runCatching { Channels.sync(app, c) }.onFailure { Log.w("TierTone", "Channel sync failed", it) }
    }
}

/** Reads the single phone number the user picked. The picker grants access to that row only. */
fun readPickedContact(context: Context, uri: Uri): PickedContact? = runCatching {
    val columns = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
    )
    context.contentResolver.query(uri, columns, null, null, null)?.use { c ->
        if (!c.moveToFirst()) return@use null
        val name = c.getString(0)?.takeIf { it.isNotBlank() } ?: return@use null
        PickedContact(name, c.getString(1), c.getString(2))
    }
}.onFailure { Log.w("TierTone", "Could not read picked contact", it) }.getOrNull()

/** System contact picker for phone numbers: no READ_CONTACTS permission needed. */
class PickPhoneContact : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

/** Opens the Android settings screens TierTone depends on, with fallbacks for OEM variations. */
object SystemScreens {
    fun notificationAccess(context: Context) {
        val component = ComponentName(context, SorterService::class.java).flattenToString()
        open(
            context,
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
                .takeIf { android.os.Build.VERSION.SDK_INT >= 30 },
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        )
    }

    fun appDetails(context: Context) =
        open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

    fun appNotifications(context: Context) = open(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

    fun dndAccess(context: Context) = open(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    fun battery(context: Context) = open(
        context,
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

    private fun open(context: Context, vararg intents: Intent?) {
        for (intent in intents.filterNotNull()) {
            try {
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next, more generic screen.
            } catch (_: SecurityException) {
            }
        }
    }
}
