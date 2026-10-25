package app.tiertone.core

/** The three priority tiers. Every incoming notification is assigned one. */
enum class Tier(val key: String, val label: String) {
    IMPORTANT("important", "Important"),
    NORMAL("normal", "Normal"),
    MUTED("muted", "Muted");

    companion object {
        fun of(key: String?): Tier? = entries.firstOrNull { it.key == key }
    }
}

/** Where a notification ended up; used for the on-device activity log and weekly counts. */
enum class Lane(val key: String, val label: String) {
    VIP("vip", "VIP"),
    IMPORTANT("important", "Important"),
    NORMAL("normal", "Normal"),
    MUTED("muted", "Muted"),
    UNTOUCHED("untouched", "Untouched");

    companion object {
        fun of(key: String?): Lane? = entries.firstOrNull { it.key == key }
        fun of(tier: Tier): Lane = when (tier) {
            Tier.IMPORTANT -> IMPORTANT
            Tier.NORMAL -> NORMAL
            Tier.MUTED -> MUTED
        }
    }
}

/**
 * A contact the user picked from their phone. Matching uses the phone number when the chat app
 * exposes one, otherwise the display name or one of the [aliases].
 */
data class Vip(
    val id: String,
    val name: String,
    val phones: List<String> = emptyList(),
    val lookupKey: String? = null,
    val aliases: List<String> = emptyList(),
    val sound: String = Sounds.DEFAULT_VIP,
    val addedAt: Long = 0,
)

/**
 * Everything the user configured. Written only by the UI process and read by the listener,
 * so the listener never has to keep the UI in memory.
 */
data class Config(
    val enabled: Boolean = true,
    val pausedUntil: Long = 0,
    val onboarded: Boolean = false,
    /** Also re-post apps the user never listed, on the Normal channel. Off: they keep their own sound. */
    val sortUnlisted: Boolean = false,
    /** VIP channels ring through Silent and Do Not Disturb (at alarm volume). */
    val vipBypassDnd: Boolean = false,
    val importantSound: String = Sounds.DEFAULT_IMPORTANT,
    val normalSound: String = Sounds.DEFAULT_NORMAL,
    val appTiers: Map<String, Tier> = emptyMap(),
    val vips: List<Vip> = emptyList(),
) {
    fun isActive(now: Long): Boolean = enabled && now >= pausedUntil

    fun isPaused(now: Long): Boolean = enabled && now < pausedUntil

    fun soundFor(tier: Tier): String = when (tier) {
        Tier.IMPORTANT -> importantSound
        Tier.NORMAL -> normalSound
        Tier.MUTED -> Sounds.SILENT
    }

    /** "Reset rules": forget every app tier, VIP and sound choice, but keep onboarding done. */
    fun reset(): Config = Config(onboarded = onboarded)
}

/** Sound tokens stored in [Config]. Resolved to real URIs on the device by `SoundUris`. */
object Sounds {
    const val DEFAULT = "default"
    const val SILENT = "silent"
    const val APP_PREFIX = "app:"

    /** Short tones bundled in res/raw, as (resource name, label). */
    val BUILT_IN = listOf(
        "rise" to "Rise",
        "bell" to "Bell",
        "chime" to "Chime",
        "ping" to "Ping",
        "pop" to "Pop",
        "pulse" to "Pulse",
    )

    const val DEFAULT_IMPORTANT = "app:rise"
    const val DEFAULT_NORMAL = DEFAULT
    const val DEFAULT_VIP = "app:bell"

    fun builtInLabel(token: String): String? =
        if (token.startsWith(APP_PREFIX)) BUILT_IN.firstOrNull { APP_PREFIX + it.first == token }?.second else null
}
