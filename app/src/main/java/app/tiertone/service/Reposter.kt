package app.tiertone.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.LruCache
import app.tiertone.R
import app.tiertone.core.Lane
import kotlin.math.roundToInt

/**
 * Rebuilds a notification under one of TierTone's channels. Only safe parts are copied: text,
 * conversation messages, bitmap icons, the tap action and the action buttons. Anything that points
 * into the source app's private storage (content URIs, shortcuts, custom layouts) is left out,
 * because Android would refuse it or crash rendering it.
 */
class Reposter(private val context: Context) {
    private val pm = context.packageManager
    private val density = context.resources.displayMetrics.density
    private val smallIcons = LruCache<String, Icon>(24)
    private val labels = HashMap<String, CharSequence>()

    fun appLabel(sbn: StatusBarNotification): CharSequence = labels.getOrPut(sbn.packageName) {
        Extract.appInfo(context, sbn.packageName, sbn.notification.extras)?.let { pm.getApplicationLabel(it) } ?: sbn.packageName
    }

    fun build(
        sbn: StatusBarNotification,
        c: Content,
        lane: Lane,
        channelId: String,
        groupKey: String,
        tag: String,
        quiet: Boolean,
        previous: List<Message>?,
    ): Notification {
        val n = sbn.notification
        val b = base(sbn, c, lane, channelId, groupKey, quiet)
        largeIcon(n)?.let(b::setLargeIcon)

        val messages = merge(previous, c.messages)
        when {
            messages.isNotEmpty() -> b.setStyle(messagingStyle(c, messages))
            c.lines.isNotEmpty() -> b.setStyle(
                Notification.InboxStyle().also { s ->
                    c.lines.take(7).forEach(s::addLine)
                    c.summaryText?.let(s::setSummaryText)
                    c.bigTitle?.let(s::setBigContentTitle)
                },
            )
            !c.bigText.isNullOrBlank() -> b.setStyle(
                Notification.BigTextStyle().bigText(c.bigText).also { s -> c.bigTitle?.let(s::setBigContentTitle) },
            )
        }

        n.actions?.forEachIndexed { i, a -> relayed(a, tag, i)?.let(b::addAction) }
        return b.build()
    }

    /** Last resort if Android rejects the full copy: title, text and the tap action only. */
    fun buildPlain(sbn: StatusBarNotification, c: Content, lane: Lane, channelId: String, groupKey: String, quiet: Boolean): Notification =
        base(sbn, c, lane, channelId, groupKey, quiet, useSourceIcon = false).build()

    fun summary(sbn: StatusBarNotification, lane: Lane, channelId: String, groupKey: String): Notification {
        val label = appLabel(sbn)
        return Notification.Builder(context, channelId)
            .setSmallIcon(smallIcon(sbn) ?: Icon.createWithResource(context, laneIcon(lane)))
            .setContentTitle(label)
            .setContentText(lane.label)
            .setSubText(label)
            .setColor(sbn.notification.color)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .apply { launchIntent(sbn.packageName)?.let(::setContentIntent) }
            .build()
    }

    private fun base(
        sbn: StatusBarNotification,
        c: Content,
        lane: Lane,
        channelId: String,
        groupKey: String,
        quiet: Boolean,
        useSourceIcon: Boolean = true,
    ): Notification.Builder {
        val n = sbn.notification
        val label = appLabel(sbn)
        val icon = (if (useSourceIcon) smallIcon(sbn) else null) ?: Icon.createWithResource(context, laneIcon(lane))
        return Notification.Builder(context, channelId).apply {
            setSmallIcon(icon)
            setContentTitle(c.title ?: label)
            c.text?.let(::setContentText)
            // The header reads "TierTone • WhatsApp", so the source app is always visible.
            setSubText(c.subText ?: label)
            setWhen(if (n.`when` > 0) n.`when` else System.currentTimeMillis())
            setShowWhen(true)
            n.category?.let(::setCategory)
            setColor(n.color)
            setVisibility(n.visibility)
            setPublicVersion(publicVersion(label, icon, channelId))
            setGroup(groupKey)
            setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            setOnlyAlertOnce(quiet)
            setAutoCancel(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
            if (n.timeoutAfter > 0) setTimeoutAfter(n.timeoutAfter)
            (n.contentIntent ?: launchIntent(sbn.packageName))?.let(::setContentIntent)
            addExtras(Bundle().apply { putString(EXTRA_SOURCE_PACKAGE, sbn.packageName) })
        }
    }

    /** What the lock screen shows when content is hidden. */
    private fun publicVersion(label: CharSequence, icon: Icon, channelId: String): Notification =
        Notification.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(label)
            .setContentText("New notification")
            .build()

    private fun messagingStyle(c: Content, messages: List<Message>): Notification.Style {
        val selfName = c.selfName?.takeIf { it.isNotBlank() } ?: "You"
        if (Build.VERSION.SDK_INT < 28) {
            @Suppress("DEPRECATION")
            return Notification.MessagingStyle(selfName).also { s ->
                s.conversationTitle = c.conversationTitle
                messages.forEach { m -> s.addMessage(m.text ?: "", m.time, if (m.fromSelf) null else m.senderName) }
            }
        }
        val style = Notification.MessagingStyle(Person.Builder().setName(selfName).build())
        style.conversationTitle = c.conversationTitle
        style.isGroupConversation = c.groupConversation
        for (m in messages) {
            val sender = if (m.fromSelf) {
                null
            } else {
                // No URI: Android checks URIs against the poster's permissions, and contact URIs belong to the chat app.
                Person.Builder()
                    .setName(m.senderName ?: "?")
                    .setKey(m.senderKey)
                    .setImportant(m.senderImportant)
                    .setBot(m.senderBot)
                    .setIcon(m.senderIcon)
                    .build()
            }
            style.addMessage(Notification.MessagingStyle.Message(m.text ?: "", m.time, sender))
        }
        return style
    }

    /**
     * Action buttons go through [ActionRelay], which fires the app's own action and then removes
     * TierTone's copy (the app can't, because its original is already gone).
     */
    private fun relayed(a: Notification.Action, tag: String, index: Int): Notification.Action? {
        val target = a.actionIntent ?: return null
        val inputs = a.remoteInputs
        val intent = Intent(context, ActionRelay::class.java)
            .setData(Uri.parse("tiertone://action/${Uri.encode(tag)}/$index"))
            .putExtra(ActionRelay.EXTRA_TARGET, target)
            .putExtra(ActionRelay.EXTRA_TAG, tag)
        if (!inputs.isNullOrEmpty()) intent.putExtra(ActionRelay.EXTRA_INPUTS, inputs)
        // Reply actions must be mutable so the system can attach the typed text.
        val mutability = when {
            inputs.isNullOrEmpty() -> PendingIntent.FLAG_IMMUTABLE
            Build.VERSION.SDK_INT >= 31 -> PendingIntent.FLAG_MUTABLE
            else -> 0
        }
        val relay = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutability)

        val b = Notification.Action.Builder(null as Icon?, a.title, relay)
        inputs?.forEach(b::addRemoteInput)
        b.addExtras(a.extras)
        b.setAllowGeneratedReplies(a.allowGeneratedReplies)
        if (Build.VERSION.SDK_INT >= 28) b.setSemanticAction(a.semanticAction)
        if (Build.VERSION.SDK_INT >= 29) b.setContextual(a.isContextual)
        if (Build.VERSION.SDK_INT >= 31) b.setAuthenticationRequired(a.isAuthenticationRequired)
        return b.build()
    }

    /**
     * The source app's status-bar icon, drawn into a bitmap so the system never has to load another
     * app's resources on TierTone's behalf (a failure there would crash TierTone).
     */
    private fun smallIcon(sbn: StatusBarNotification): Icon? {
        val n = sbn.notification
        val icon = n.smallIcon ?: return null
        if (Extract.isBitmap(icon)) return icon
        val key = if (Build.VERSION.SDK_INT >= 28 && icon.type == Icon.TYPE_RESOURCE) {
            "${icon.resPackage}:${icon.resId}"
        } else {
            @Suppress("DEPRECATION")
            "${sbn.packageName}:${n.icon}"
        }
        smallIcons.get(key)?.let { return it }
        val drawable = runCatching {
            val info = Extract.appInfo(context, sbn.packageName, n.extras)
            if (Build.VERSION.SDK_INT >= 28 && icon.type == Icon.TYPE_RESOURCE && info != null && icon.resPackage == sbn.packageName) {
                pm.getResourcesForApplication(info).getDrawable(icon.resId, null)
            } else {
                icon.loadDrawable(context)
            }
        }.getOrNull() ?: return null
        val made = Icon.createWithBitmap(drawable.toBitmap((24 * density).roundToInt()))
        smallIcons.put(key, made)
        return made
    }

    private fun largeIcon(n: Notification): Icon? {
        val icon = n.getLargeIcon() ?: return null
        if (Extract.isBitmap(icon)) return icon
        val drawable = runCatching { icon.loadDrawable(context) }.getOrNull() ?: return null
        return Icon.createWithBitmap(drawable.toBitmap((64 * density).roundToInt()))
    }

    private fun launchIntent(pkg: String): PendingIntent? {
        val launch = pm.getLaunchIntentForPackage(pkg) ?: return null
        return PendingIntent.getActivity(context, pkg.hashCode(), launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, size, size)
        draw(Canvas(bmp))
        return bmp
    }

    companion object {
        const val CHILD_ID = 1
        const val SUMMARY_ID = 2
        const val CHILD_PREFIX = "r|"
        const val SUMMARY_PREFIX = "s|"
        const val EXTRA_SOURCE_PACKAGE = "app.tiertone.source"
        private const val MAX_MESSAGES = 10

        fun childTag(sourceKey: String) = CHILD_PREFIX + sourceKey
        fun summaryTag(groupKey: String) = SUMMARY_PREFIX + groupKey

        fun laneIcon(lane: Lane): Int = when (lane) {
            Lane.VIP -> R.drawable.ic_stat_vip
            Lane.IMPORTANT -> R.drawable.ic_stat_important
            Lane.MUTED -> R.drawable.ic_stat_muted
            Lane.NORMAL, Lane.UNTOUCHED -> R.drawable.ic_stat_normal
        }

        /**
         * Chat apps often send only the newest messages once a notification was dismissed (and
         * cancelling the original counts as dismissing). Keep the conversation readable by merging
         * with what TierTone's copy already shows.
         */
        fun merge(previous: List<Message>?, next: List<Message>): List<Message> {
            if (previous.isNullOrEmpty()) return next.takeLast(MAX_MESSAGES)
            val seen = HashSet<String>()
            return (previous + next)
                .filter { m -> seen.add(if (m.fromSelf) "self|${m.text}" else "${m.time}|${m.senderName}|${m.text}") }
                .sortedBy { it.time }
                .takeLast(MAX_MESSAGES)
        }
    }
}
