package app.tiertone.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Person
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.Process
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import androidx.core.os.BundleCompat
import app.tiertone.engine.NotificationFacts
import app.tiertone.engine.Titles

/** One message of a MessagingStyle notification. Lives only while a notification is being sorted. */
class Message(
    val text: CharSequence?,
    val time: Long,
    val senderName: CharSequence?,
    val senderKey: String?,
    /** A `tel:` or contacts URI the app attached to the sender, if any. */
    val senderUri: String?,
    /** Only bitmap icons are kept: other kinds point into the source app and can't be re-posted. */
    val senderIcon: Icon?,
    val senderImportant: Boolean,
    val senderBot: Boolean,
    val fromSelf: Boolean,
)

/** The readable parts of a notification, enough to rebuild it under TierTone's channel. */
class Content(
    val title: CharSequence?,
    val text: CharSequence?,
    val bigTitle: CharSequence?,
    val bigText: CharSequence?,
    val subText: CharSequence?,
    val summaryText: CharSequence?,
    val lines: List<CharSequence>,
    val messages: List<Message>,
    val conversationTitle: CharSequence?,
    val groupConversation: Boolean,
    val selfName: CharSequence?,
    val personUris: List<String>,
    val template: String?,
) {
    val hasContent: Boolean
        get() = !title.isNullOrBlank() || !text.isNullOrBlank() || messages.isNotEmpty() || lines.isNotEmpty()
}

object Extract {

    fun content(n: Notification): Content {
        val e = n.extras ?: Bundle.EMPTY
        var selfName: CharSequence? = null
        var selfKey: String? = null
        if (Build.VERSION.SDK_INT >= 28) {
            BundleCompat.getParcelable(e, Notification.EXTRA_MESSAGING_PERSON, Person::class.java)?.let { p ->
                selfName = p.name
                selfKey = p.key
            }
        }
        if (selfName == null) selfName = legacySelfName(e)
        val messages = messages(e, selfName, selfKey)

        val uris = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 28) {
            BundleCompat.getParcelableArrayList(e, Notification.EXTRA_PEOPLE_LIST, Person::class.java)
                ?.forEach { p -> p.uri?.let(uris::add) }
        }
        @Suppress("DEPRECATION")
        e.getStringArray(Notification.EXTRA_PEOPLE)?.let { uris.addAll(it) }

        return Content(
            title = e.getCharSequence(Notification.EXTRA_TITLE),
            text = e.getCharSequence(Notification.EXTRA_TEXT),
            bigTitle = e.getCharSequence(Notification.EXTRA_TITLE_BIG),
            bigText = e.getCharSequence(Notification.EXTRA_BIG_TEXT),
            subText = e.getCharSequence(Notification.EXTRA_SUB_TEXT),
            summaryText = e.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
            lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.filterNotNull() ?: emptyList(),
            messages = messages,
            conversationTitle = e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE),
            // Same key on every version; the constant itself is API 28+.
            groupConversation = e.getBoolean("android.isGroupConversation"),
            selfName = selfName,
            personUris = uris,
            template = e.getString(Notification.EXTRA_TEMPLATE),
        )
    }

    @Suppress("DEPRECATION")
    private fun legacySelfName(e: Bundle): CharSequence? = e.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)

    /** MessagingStyle messages, oldest first, read straight from the bundle format (stable since API 24). */
    private fun messages(e: Bundle, selfName: CharSequence?, selfKey: String?): List<Message> {
        val raw: Array<Parcelable> =
            BundleCompat.getParcelableArray(e, Notification.EXTRA_MESSAGES, Parcelable::class.java) ?: return emptyList()
        return raw.mapNotNull { p ->
            val b = p as? Bundle ?: return@mapNotNull null
            if (!b.containsKey("text")) return@mapNotNull null
            var name: CharSequence? = null
            var key: String? = null
            var uri: String? = null
            var icon: Icon? = null
            var important = false
            var bot = false
            if (Build.VERSION.SDK_INT >= 28) {
                BundleCompat.getParcelable(b, "sender_person", Person::class.java)?.let { person ->
                    name = person.name
                    key = person.key
                    uri = person.uri
                    icon = person.icon?.takeIf(::isBitmap)
                    important = person.isImportant
                    bot = person.isBot
                }
            }
            if (name == null) name = b.getCharSequence("sender")
            val fromSelf = name == null ||
                (selfKey != null && key == selfKey) ||
                (selfKey == null && selfName != null && name.toString() == selfName.toString())
            Message(
                text = b.getCharSequence("text"),
                time = b.getLong("time"),
                senderName = name,
                senderKey = key,
                senderUri = uri,
                senderIcon = icon,
                senderImportant = important,
                senderBot = bot,
                fromSelf = fromSelf,
            )
        }
    }

    fun facts(context: Context, sbn: StatusBarNotification, c: Content, ranking: Ranking?, hasCopy: Boolean): NotificationFacts {
        val n = sbn.notification
        val flags = n.flags
        val e = n.extras ?: Bundle.EMPTY
        val newest = c.messages.lastOrNull()
        val sender = when {
            newest != null -> if (newest.fromSelf) null else newest.senderName?.toString()
            else -> Titles.senderFromTitle(c.title?.toString())
        }
        val uris = ArrayList<String>()
        newest?.senderUri?.takeIf { !newest.fromSelf }?.let(uris::add)
        // In a group the people list names every participant, so only the sender's own URI counts there.
        if (!c.groupConversation) uris.addAll(c.personUris)
        val importance = ranking?.importance ?: NotificationManager.IMPORTANCE_UNSPECIFIED
        return NotificationFacts(
            pkg = sbn.packageName,
            category = n.category,
            ongoing = flags and Notification.FLAG_ONGOING_EVENT != 0,
            foregroundService = flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            noClear = flags and Notification.FLAG_NO_CLEAR != 0,
            fullScreen = n.fullScreenIntent != null,
            groupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
            bubble = flags and FLAG_BUBBLE != 0,
            media = e.containsKey(Notification.EXTRA_MEDIA_SESSION) || c.template?.contains("MediaStyle") == true,
            progress = e.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 || e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE),
            otherUser = sbn.user != Process.myUserHandle(),
            hasContent = c.hasContent,
            systemApp = isSystemApp(context, sbn.packageName, e),
            originalSilent = importance in NotificationManager.IMPORTANCE_NONE until NotificationManager.IMPORTANCE_DEFAULT,
            onlyAlertOnce = flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
            hasCopy = hasCopy,
            sender = sender,
            personUris = uris,
            groupConversation = c.groupConversation,
            fromSelf = newest?.fromSelf == true,
        )
    }

    fun appInfo(context: Context, pkg: String, extras: Bundle?): ApplicationInfo? =
        extras?.let { BundleCompat.getParcelable(it, EXTRA_APP_INFO, ApplicationInfo::class.java) }
            ?: try {
                context.packageManager.getApplicationInfo(pkg, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }

    /** Unknown packages count as system apps, so "sort unlisted apps" stays conservative. */
    private fun isSystemApp(context: Context, pkg: String, extras: Bundle): Boolean {
        val info = appInfo(context, pkg, extras) ?: return true
        return info.flags and ApplicationInfo.FLAG_SYSTEM != 0 && info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
    }

    fun isBitmap(icon: Icon): Boolean =
        Build.VERSION.SDK_INT >= 28 && (icon.type == Icon.TYPE_BITMAP || icon.type == Icon.TYPE_ADAPTIVE_BITMAP)

    /** Notification.EXTRA_BUILDER_APPLICATION_INFO is hidden, but the key has been stable since API 24. */
    private const val EXTRA_APP_INFO = "android.appInfo"

    /** Notification.FLAG_BUBBLE (API 29); checking the bit is harmless on older versions. */
    private const val FLAG_BUBBLE = 0x00001000
}
