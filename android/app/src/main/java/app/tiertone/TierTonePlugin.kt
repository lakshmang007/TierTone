package app.tiertone

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Base64
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import app.tiertone.core.Channels
import app.tiertone.core.ConfigJson
import app.tiertone.core.ConfigStore
import app.tiertone.core.Lane
import app.tiertone.core.SoundUris
import app.tiertone.core.Sounds
import app.tiertone.core.StatsStore
import app.tiertone.service.SorterService
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * The bridge between the React UI and TierTone's native engine. The UI reads state and writes the
 * rules file through here; the listener service (another process) picks the changes up.
 * Capacitor runs these methods on a background thread, so file and package lookups are fine.
 */
@CapacitorPlugin(
    name = "TierTone",
    permissions = [Permission(alias = "notifications", strings = [Manifest.permission.POST_NOTIFICATIONS])],
)
class TierTonePlugin : Plugin() {
    private val store by lazy { ConfigStore(context) }
    private val statsStore by lazy { StatsStore(context) }
    private val labels = HashMap<String, String>()
    private var preview: Ringtone? = null

    override fun load() {
        // Make sure every channel exists (and stale ones are gone) each time the UI opens.
        val config = store.load()
        if (config.onboarded) syncChannels(config)
    }

    @PluginMethod
    fun getState(call: PluginCall) {
        val config = store.load()
        val stats = statsStore.load()
        val counts = stats.lastDays(LocalDate.now().toEpochDay())
        val nm = context.getSystemService(NotificationManager::class.java)

        val week = JSObject()
        Lane.entries.forEach { week.put(it.key, counts[it.ordinal]) }
        val recent = JSArray()
        stats.recent.take(20).forEach { e ->
            recent.put(
                JSObject()
                    .put("time", e.time)
                    .put("pkg", e.pkg)
                    .put("label", label(e.pkg))
                    .put("lane", e.lane.key)
                    .apply { if (e.vip != null) put("vip", e.vip) },
            )
        }
        val seen = JSObject()
        stats.seen.forEach { (pkg, time) -> seen.put(pkg, time) }

        call.resolve(
            JSObject()
                .put("config", JSObject(ConfigJson.encode(config)))
                .put(
                    "access",
                    JSObject()
                        .put("listener", NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
                        .put("post", NotificationManagerCompat.from(context).areNotificationsEnabled())
                        .put("dnd", nm.isNotificationPolicyAccessGranted),
                )
                .put("week", week)
                .put("recent", recent)
                .put("seen", seen)
                .put("crashReport", CrashLog.read(context))
                .put("version", BuildConfig.VERSION_NAME)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("now", System.currentTimeMillis()),
        )
    }

    /** Saves the whole rules object. The JSON shape is exactly the rules file's. */
    @PluginMethod
    fun setConfig(call: PluginCall) {
        val json = call.getObject("config") ?: return call.reject("Missing config")
        val next = try {
            ConfigJson.decode(json.toString())
        } catch (e: Exception) {
            return call.reject("Invalid config", e)
        }
        val before = store.load()
        store.save(next)
        syncChannels(next)
        if (next.enabled && !before.enabled) {
            // The listener let go when TierTone was turned off; ask Android to bind it again.
            runCatching { NotificationListenerService.requestRebind(ComponentName(context, SorterService::class.java)) }
        }
        call.resolve()
    }

    /** Launchable apps plus any extra packages (configured or seen notifying), sorted by name. */
    @PluginMethod
    fun listApps(call: PluginCall) {
        val pm = context.packageManager
        val packages = LinkedHashSet<String>()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(launcher, 0).forEach { packages += it.activityInfo.packageName }
        call.getArray("extra")?.toList<String>()?.let(packages::addAll)
        packages -= context.packageName
        val apps = JSArray()
        packages.map { it to label(it) }.sortedBy { it.second.lowercase() }.forEach { (pkg, name) ->
            apps.put(JSObject().put("pkg", pkg).put("label", name))
        }
        call.resolve(JSObject().put("apps", apps))
    }

    /** One app icon as a PNG data URL. The UI asks only for icons that scroll into view. */
    @PluginMethod
    fun appIcon(call: PluginCall) {
        val pkg = call.getString("pkg") ?: return call.reject("Missing pkg")
        val size = call.getInt("size", 96)!!
        try {
            val drawable = context.packageManager.getApplicationIcon(pkg)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bitmap))
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            call.resolve(JSObject().put("dataUrl", "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)))
        } catch (_: PackageManager.NameNotFoundException) {
            call.resolve(JSObject())
        }
    }

    @PluginMethod
    fun openSettings(call: PluginCall) {
        val pkg = context.packageName
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
        val intents = when (call.getString("screen")) {
            "notificationAccess" -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 30) {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(context, SorterService::class.java).flattenToString(),
                    )
                } else {
                    null
                },
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            )
            "appNotifications" -> listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg), details)
            "dndAccess" -> listOf(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            "battery" -> listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), details)
            else -> listOf(details)
        }
        for (intent in intents) {
            try {
                activity.startActivity(intent)
                return call.resolve()
            } catch (_: ActivityNotFoundException) {
                // Some phones lack the specific screen; try the next, more general one.
            } catch (_: SecurityException) {
            }
        }
        call.reject("No settings screen available")
    }

    /** Android 13+ runtime permission to post notifications. Older versions don't need it. */
    @PluginMethod
    fun requestNotifications(call: PluginCall) {
        if (Build.VERSION.SDK_INT < 33 || getPermissionState("notifications") == PermissionState.GRANTED) {
            return resolvePost(call)
        }
        requestPermissionForAlias("notifications", call, "onNotificationsPermission")
    }

    @PermissionCallback
    private fun onNotificationsPermission(call: PluginCall) = resolvePost(call)

    private fun resolvePost(call: PluginCall) =
        call.resolve(JSObject().put("granted", NotificationManagerCompat.from(context).areNotificationsEnabled()))

    /** The system contact picker for one phone number: no READ_CONTACTS needed. */
    @PluginMethod
    fun pickContact(call: PluginCall) {
        try {
            startActivityForResult(call, Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), "onContactPicked")
        } catch (_: ActivityNotFoundException) {
            call.reject("No contacts app found")
        }
    }

    @ActivityCallback
    private fun onContactPicked(call: PluginCall?, result: ActivityResult) {
        call ?: return
        val uri = result.data?.data
        if (result.resultCode != android.app.Activity.RESULT_OK || uri == null) {
            return call.resolve(JSObject().put("cancelled", true))
        }
        val columns = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
        )
        val picked = runCatching {
            context.contentResolver.query(uri, columns, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val name = c.getString(0)?.takeIf { it.isNotBlank() } ?: return@use null
                JSObject().put("name", name).put("number", c.getString(1)).put("lookupKey", c.getString(2))
            }
        }.onFailure { Log.w(TAG, "Could not read picked contact", it) }.getOrNull()
        if (picked == null) call.reject("Couldn't read that contact") else call.resolve(picked)
    }

    /** Android's own notification-sound picker. Resolves a sound token, or cancelled. */
    @PluginMethod
    fun pickSystemSound(call: PluginCall) {
        val current = call.getString("current") ?: Sounds.DEFAULT
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, SoundUris.of(context, current))
        startActivityForResult(call, intent, "onSoundPicked")
    }

    @ActivityCallback
    private fun onSoundPicked(call: PluginCall?, result: ActivityResult) {
        call ?: return
        val data = result.data
        val uri = if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            null
        }
        if (uri == null) return call.resolve(JSObject().put("cancelled", true))
        val token = if (uri == Settings.System.DEFAULT_NOTIFICATION_URI) Sounds.DEFAULT else uri.toString()
        call.resolve(JSObject().put("token", token).put("label", soundLabel(token)))
    }

    /** Names for sound tokens; custom ones need a media-store lookup. */
    @PluginMethod
    fun soundLabels(call: PluginCall) {
        val out = JSObject()
        call.getArray("tokens")?.toList<String>()?.forEach { out.put(it, soundLabel(it)) }
        call.resolve(out)
    }

    @PluginMethod
    fun previewSound(call: PluginCall) {
        stopPreviewInternal()
        val uri = SoundUris.of(context, call.getString("token") ?: Sounds.DEFAULT)
        if (uri != null) {
            preview = RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        }
        call.resolve()
    }

    @PluginMethod
    fun stopPreview(call: PluginCall) {
        stopPreviewInternal()
        call.resolve()
    }

    @PluginMethod
    fun shareText(call: PluginCall) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, call.getString("text") ?: "")
        activity.startActivity(Intent.createChooser(send, call.getString("title") ?: "Share"))
        call.resolve()
    }

    @PluginMethod
    fun clearCrashReport(call: PluginCall) {
        CrashLog.clear(context)
        call.resolve()
    }

    override fun handleOnDestroy() {
        stopPreviewInternal()
    }

    private fun stopPreviewInternal() {
        preview?.stop()
        preview = null
    }

    private fun soundLabel(token: String): String = Sounds.builtInLabel(token) ?: when (token) {
        Sounds.DEFAULT -> "Phone default"
        Sounds.SILENT -> "Silent"
        else -> runCatching { RingtoneManager.getRingtone(context, Uri.parse(token))?.getTitle(context) }.getOrNull() ?: "Custom sound"
    }

    private fun label(pkg: String): String = labels.getOrPut(pkg) {
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    private fun syncChannels(config: app.tiertone.core.Config) {
        runCatching { Channels.sync(context, config) }.onFailure { Log.w(TAG, "Channel sync failed", it) }
    }

    companion object {
        private const val TAG = "TierTone"
    }
}
