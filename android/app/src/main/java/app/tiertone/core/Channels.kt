package app.tiertone.core

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings

/** Turns a sound token from [Config] into a URI the system can play. */
object SoundUris {
    fun of(context: Context, token: String): Uri? = when {
        token == Sounds.SILENT -> null
        token == Sounds.DEFAULT -> Settings.System.DEFAULT_NOTIFICATION_URI
        token.startsWith(Sounds.APP_PREFIX) ->
            Uri.parse("android.resource://${context.packageName}/raw/${token.removePrefix(Sounds.APP_PREFIX)}")
        else -> Uri.parse(token)
    }
}

/**
 * Notification channels, one per tier and one per VIP. A channel's sound can't change after it is
 * created, so each channel ID includes a hash of its sound (and DND setting). Changing a sound
 * creates a new channel, and the old one is deleted once nothing is showing on it.
 *
 * IDs are pure functions of [Config], so the UI and listener processes agree without talking.
 */
object Channels {
    private const val GROUP_TIERS = "tiers"
    private const val GROUP_VIPS = "vips"

    fun tierId(config: Config, tier: Tier): String = when (tier) {
        Tier.IMPORTANT -> "important_" + hash(config.importantSound)
        Tier.NORMAL -> "normal_" + hash(config.normalSound)
        Tier.MUTED -> "muted"
    }

    fun vipId(config: Config, vip: Vip): String = "vip_${vip.id}_" + hash("${vip.sound}|${config.vipBypassDnd}")

    fun tierChannel(context: Context, config: Config, tier: Tier): NotificationChannel {
        val importance = when (tier) {
            Tier.IMPORTANT -> NotificationManager.IMPORTANCE_HIGH
            Tier.NORMAL -> NotificationManager.IMPORTANCE_DEFAULT
            Tier.MUTED -> NotificationManager.IMPORTANCE_LOW
        }
        return NotificationChannel(tierId(config, tier), tier.label, importance).apply {
            group = GROUP_TIERS
            description = when (tier) {
                Tier.IMPORTANT -> "Apps you marked Important"
                Tier.NORMAL -> "Apps you marked Normal"
                Tier.MUTED -> "Apps you muted. Shown quietly, never make a sound."
            }
            setSound(SoundUris.of(context, config.soundFor(tier)), notificationAudio())
            enableVibration(tier != Tier.MUTED)
            if (tier == Tier.IMPORTANT) vibrationPattern = longArrayOf(0, 180, 120, 180)
        }
    }

    fun vipChannel(context: Context, config: Config, vip: Vip): NotificationChannel =
        NotificationChannel(vipId(config, vip), "VIP: ${vip.name}", NotificationManager.IMPORTANCE_HIGH).apply {
            group = GROUP_VIPS
            description = "Chats from ${vip.name}"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250, 150, 250)
            if (config.vipBypassDnd) {
                // Needs Do Not Disturb access, which Android grants along with notification access.
                setBypassDnd(true)
                // Alarm usage plays even when the ringer is on Silent or Vibrate.
                setSound(SoundUris.of(context, vip.sound), alarmAudio())
            } else {
                setSound(SoundUris.of(context, vip.sound), notificationAudio())
            }
        }

    fun channelFor(context: Context, config: Config, tier: Tier, vip: Vip?): NotificationChannel =
        if (vip != null) vipChannel(context, config, vip) else tierChannel(context, config, tier)

    /** Channels belong to these groups, so they must exist first (the listener may run before the UI ever has). */
    fun ensureGroups(nm: NotificationManager) {
        nm.createNotificationChannelGroups(
            listOf(
                NotificationChannelGroup(GROUP_TIERS, "Tiers"),
                NotificationChannelGroup(GROUP_VIPS, "VIP contacts"),
            ),
        )
    }

    /** Create every channel the config needs and delete stale ones that have nothing showing. */
    fun sync(context: Context, config: Config) {
        val nm = context.getSystemService(NotificationManager::class.java)
        ensureGroups(nm)
        val wanted = Tier.entries.map { tierChannel(context, config, it) } +
            config.vips.map { vipChannel(context, config, it) }
        nm.createNotificationChannels(wanted)

        val keep = wanted.map { it.id }.toSet()
        val inUse = runCatching { nm.activeNotifications.map { it.notification.channelId }.toSet() }.getOrDefault(emptySet())
        nm.notificationChannels
            .filter { it.id !in keep && it.id !in inUse && it.id != NotificationChannel.DEFAULT_CHANNEL_ID }
            .forEach { nm.deleteNotificationChannel(it.id) }
    }

    private fun notificationAudio(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun alarmAudio(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun hash(s: String): String = Integer.toHexString(s.hashCode())
}
