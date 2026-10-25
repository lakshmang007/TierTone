package app.tiertone.engine

import app.tiertone.core.Config
import app.tiertone.core.Lane
import app.tiertone.core.Tier
import app.tiertone.core.Vip

/**
 * What the listener learned about one incoming notification. Built from a StatusBarNotification
 * on the device and by hand in tests. Never contains message text.
 */
data class NotificationFacts(
    val pkg: String,
    val category: String? = null,
    val ongoing: Boolean = false,
    val foregroundService: Boolean = false,
    val noClear: Boolean = false,
    val fullScreen: Boolean = false,
    val groupSummary: Boolean = false,
    val bubble: Boolean = false,
    val media: Boolean = false,
    val progress: Boolean = false,
    val otherUser: Boolean = false,
    val hasContent: Boolean = true,
    val systemApp: Boolean = false,
    /** The app (or the user, inside the app) posted this one silently. */
    val originalSilent: Boolean = false,
    /** The app marked this as an update that should not alert again. */
    val onlyAlertOnce: Boolean = false,
    /** TierTone is already showing its copy of this notification. */
    val hasCopy: Boolean = false,
    /** Sender of the newest message, or the chat title for one-to-one chats. */
    val sender: String? = null,
    val personUris: List<String> = emptyList(),
    val groupConversation: Boolean = false,
    /** The newest message was written by the user (e.g. an inline reply being echoed back). */
    val fromSelf: Boolean = false,
)

enum class Why { SELF, OFF, PROTECTED, SUMMARY, UNLISTED, SYSTEM_APP }

sealed interface Decision {
    /** The tier the notification was assigned, whatever happens to it. */
    val tier: Tier

    /** Leave the original alone: it keeps its own sound. */
    data class Leave(override val tier: Tier, val why: Why) : Decision

    /** Cancel the original and post a copy on the channel for [tier], or [vip]'s channel. */
    data class Repost(override val tier: Tier, val vip: Vip?, val quiet: Boolean) : Decision {
        val lane: Lane get() = if (vip != null) Lane.VIP else Lane.of(tier)
    }

    /** Cancel the original without a copy: the user's own reply echoed back after they dismissed ours. */
    data class Drop(override val tier: Tier) : Decision
}

object RuleEngine {

    /** VIP matching starts with these chat apps. */
    val CHAT_APPS = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
    )

    /** Never touched even if the user lists the app: calls, alarms, navigation, timers and the like. */
    private val PROTECTED_CATEGORIES = setOf(
        "call", "alarm", "navigation", "transport", "progress", "service", "sys",
        "stopwatch", "location_sharing", "workout", "car_emergency", "car_warning",
    )

    /** Core OS packages are never taken over by "sort unlisted apps". */
    private val SYSTEM_PACKAGES = setOf(
        "android", "com.android.systemui", "com.android.phone", "com.android.server.telecom",
        "com.android.shell", "com.android.providers.downloads", "com.google.android.gms",
    )

    fun decide(
        f: NotificationFacts,
        config: Config,
        now: Long,
        selfPackage: String,
        chatApps: Set<String> = CHAT_APPS,
    ): Decision {
        val listed = config.appTiers[f.pkg]
        val tier = listed ?: Tier.NORMAL

        if (f.pkg == selfPackage) return Decision.Leave(tier, Why.SELF)
        if (!config.isActive(now)) return Decision.Leave(tier, Why.OFF)
        if (isProtected(f)) return Decision.Leave(tier, Why.PROTECTED)
        if (f.groupSummary) return Decision.Leave(tier, Why.SUMMARY)

        val vip = if (f.pkg in chatApps) VipMatcher.match(config.vips, f.sender, f.personUris) else null
        val repost: Decision.Repost = when {
            // A VIP overrides the app's tier, but a group the user muted inside the chat app stays muted.
            vip != null && !(f.originalSilent && f.groupConversation) ->
                Decision.Repost(tier, vip, quiet = isQuiet(f))
            listed == null && !config.sortUnlisted -> return Decision.Leave(tier, Why.UNLISTED)
            listed == null && (f.systemApp || f.pkg in SYSTEM_PACKAGES) -> return Decision.Leave(tier, Why.SYSTEM_APP)
            // Respect notifications the app itself posted silently (e.g. a low-priority channel).
            f.originalSilent -> Decision.Repost(Tier.MUTED, null, quiet = isQuiet(f))
            else -> Decision.Repost(tier, null, quiet = isQuiet(f))
        }
        if (f.fromSelf && !f.hasCopy) return Decision.Drop(repost.tier)
        return repost
    }

    fun isProtected(f: NotificationFacts): Boolean =
        f.ongoing || f.foregroundService || f.noClear || f.fullScreen || f.bubble || f.media ||
            f.progress || f.otherUser || !f.hasContent || f.category in PROTECTED_CATEGORIES

    /** Update an existing copy without playing a sound again. */
    private fun isQuiet(f: NotificationFacts): Boolean = f.fromSelf || (f.onlyAlertOnce && f.hasCopy)
}
