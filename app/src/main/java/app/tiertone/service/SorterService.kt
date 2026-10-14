package app.tiertone.service

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.tiertone.BuildConfig
import app.tiertone.core.Channels
import app.tiertone.core.Config
import app.tiertone.core.ConfigStore
import app.tiertone.core.Entry
import app.tiertone.core.Lane
import app.tiertone.core.Stats
import app.tiertone.core.StatsStore
import app.tiertone.engine.Decision
import app.tiertone.engine.NotificationFacts
import app.tiertone.engine.RuleEngine
import app.tiertone.engine.Why
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * The always-on part of TierTone. Runs in the app's main process, which never loads the UI, so
 * it stays small in memory. For each incoming notification it decides a tier, and for the ones
 * TierTone handles it posts a copy on that tier's channel (so the sound really changes) and
 * cancels the original.
 */
class SorterService : NotificationListenerService() {

    private lateinit var store: ConfigStore
    private lateinit var statsStore: StatsStore
    private lateinit var reposter: Reposter
    private lateinit var nm: NotificationManager

    private var config = Config()
    private var configStamp = Long.MIN_VALUE
    private val ensuredChannels = HashSet<String>()

    /** TierTone's own visible notifications: tag -> group key. */
    private val ours = HashMap<String, String?>()
    private val sourceGroups = GroupIndex()

    private val main = Handler(Looper.getMainLooper())
    private val io by lazy { Executors.newSingleThreadExecutor() }
    private var stats: Stats? = null
    private var statsSaveQueued = false
    private var receiverRegistered = false

    /** Debug builds also treat adb's shell and the bundled test sender as chat apps, for testing VIPs. */
    private val chatApps =
        if (BuildConfig.DEBUG) RuleEngine.CHAT_APPS + "com.android.shell" + "app.tiertone.sender" else RuleEngine.CHAT_APPS

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reloadConfig(force = true)
            if (!config.enabled) requestUnbind()
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = ConfigStore(this)
        statsStore = StatsStore(this)
        reposter = Reposter(this)
        nm = getSystemService(NotificationManager::class.java)
    }

    override fun onListenerConnected() {
        reloadConfig(force = true)
        if (!config.enabled) {
            // Turned off in the app: let go entirely so this process can exit.
            requestUnbind()
            return
        }
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this, configReceiver, IntentFilter(ConfigStore.ACTION_CONFIG_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        seedState()
    }

    override fun onListenerDisconnected() {
        if (receiverRegistered) {
            unregisterReceiver(configReceiver)
            receiverRegistered = false
        }
        flushStats()
        // The system sometimes drops listeners (low memory, OEM task killers). Ask to come back,
        // unless the user turned TierTone off or revoked access.
        if (config.enabled && NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            requestRebind(ComponentName(this, SorterService::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        try {
            handlePosted(sbn, rankingMap)
        } catch (t: Throwable) {
            // Never let one odd notification take the listener down; the original stays untouched.
            Log.e(TAG, "Could not sort a notification from ${sbn.packageName}", t)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        if (sbn.packageName == packageName) {
            val tag = sbn.tag ?: return
            val group = ours.remove(tag)
            if (group != null && tag.startsWith(Reposter.CHILD_PREFIX)) cancelSummaryIfEmpty(group)
        } else {
            sourceGroups.remove(sbn)
        }
    }

    private fun handlePosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (sbn.packageName == packageName) {
            sbn.tag?.let { ours[it] = sbn.notification.group }
            return
        }
        sourceGroups.add(sbn)
        reloadConfig(force = false)

        val now = System.currentTimeMillis()
        val ranking = rankingMap?.let { map -> Ranking().takeIf { map.getRanking(sbn.key, it) } }
        val content = Extract.content(sbn.notification)
        val tag = Reposter.childTag(sbn.key)
        val facts = Extract.facts(this, sbn, content, ranking, hasCopy = tag in ours)
        val decision = RuleEngine.decide(facts, config, now, packageName, chatApps)
        if (BuildConfig.DEBUG) logDecision(sbn, facts, decision)

        when (decision) {
            is Decision.Leave -> {
                if (decision.why == Why.SUMMARY && sourceGroups.wasTaken(sbn.groupKey)) {
                    // Children usually arrive right after their summary; check once they have.
                    val group = sbn.groupKey
                    main.postDelayed({ cancelOrphanSourceSummary(group) }, ORPHAN_CHECK_DELAY_MS)
                }
                if ((decision.why == Why.UNLISTED || decision.why == Why.SYSTEM_APP) && !facts.onlyAlertOnce) {
                    record(sbn.packageName, Lane.UNTOUCHED, null, now)
                }
            }
            is Decision.Drop -> {
                cancelNotification(sbn.key)
                afterOriginalCancelled(sbn)
            }
            is Decision.Repost -> repost(sbn, content, decision, tag, now)
        }
    }

    private fun repost(sbn: StatusBarNotification, content: Content, d: Decision.Repost, tag: String, now: Long) {
        // Android shows at most 50 notifications per app; past that, leave originals alone.
        if (tag !in ours && ours.size >= MAX_OWN_NOTIFICATIONS) return
        // Without permission to post, a copy would vanish: keep the original instead.
        if (!nm.areNotificationsEnabled()) return

        val channel = Channels.channelFor(this, config, d.tier, d.vip)
        if (channel.id !in ensuredChannels) {
            if (ensuredChannels.isEmpty()) Channels.ensureGroups(nm)
            nm.createNotificationChannel(channel)
            ensuredChannels += channel.id
        }
        // The user blocked this tier's channel in system settings: respect it by not interfering.
        if (nm.getNotificationChannel(channel.id)?.importance == NotificationManager.IMPORTANCE_NONE) return

        val lane = d.lane
        val groupKey = "${sbn.packageName}|${lane.key}"
        val previous = if (tag in ours && content.messages.isNotEmpty()) previousMessages(tag) else null
        val posted = post(tag, Reposter.CHILD_ID) {
            reposter.build(sbn, content, lane, channel.id, groupKey, tag, d.quiet, previous)
        } || post(tag, Reposter.CHILD_ID) {
            reposter.buildPlain(sbn, content, lane, channel.id, groupKey, d.quiet)
        }
        if (!posted) return

        val oldGroup = ours.put(tag, groupKey)
        if (oldGroup != null && oldGroup != groupKey) cancelSummaryIfEmpty(oldGroup)
        val summaryTag = Reposter.summaryTag(groupKey)
        if (summaryTag !in ours && post(summaryTag, Reposter.SUMMARY_ID) { reposter.summary(sbn, lane, channel.id, groupKey) }) {
            ours[summaryTag] = groupKey
        }

        cancelNotification(sbn.key)
        afterOriginalCancelled(sbn)
        if (!d.quiet) record(sbn.packageName, lane, d.vip?.name, now)
    }

    private inline fun post(tag: String, id: Int, build: () -> Notification): Boolean = try {
        nm.notify(tag, id, build())
        true
    } catch (e: Exception) {
        Log.w(TAG, "Android refused TierTone's copy", e)
        false
    }

    /** The messages TierTone's current copy shows, read back from the system (nothing is kept in memory). */
    private fun previousMessages(tag: String): List<Message>? = runCatching {
        nm.activeNotifications.firstOrNull { it.tag == tag && it.id == Reposter.CHILD_ID }
            ?.let { Extract.content(it.notification).messages }
    }.getOrNull()

    private fun afterOriginalCancelled(sbn: StatusBarNotification) {
        sourceGroups.remove(sbn)
        if (sbn.notification.group != null) {
            sourceGroups.markTaken(sbn.groupKey)
            cancelOrphanSourceSummary(sbn.groupKey)
        }
    }

    /** A source app's group summary with no children left would linger as an empty duplicate. */
    private fun cancelOrphanSourceSummary(groupKey: String) {
        sourceGroups.orphanSummary(groupKey)?.let { key ->
            runCatching { cancelNotification(key) }
            sourceGroups.forgetSummary(groupKey)
        }
    }

    private fun cancelSummaryIfEmpty(groupKey: String) {
        val hasChildren = ours.any { (tag, group) -> group == groupKey && tag.startsWith(Reposter.CHILD_PREFIX) }
        if (!hasChildren) nm.cancel(Reposter.summaryTag(groupKey), Reposter.SUMMARY_ID)
    }

    private fun seedState() {
        ours.clear()
        sourceGroups.clear()
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        for (sbn in active) {
            if (sbn.packageName == packageName) {
                sbn.tag?.let { ours[it] = sbn.notification.group }
            } else {
                sourceGroups.add(sbn)
            }
        }
    }

    private fun reloadConfig(force: Boolean) {
        val stamp = store.stamp()
        if (!force && stamp == configStamp) return
        configStamp = stamp
        config = store.load()
        ensuredChannels.clear()
    }

    private fun record(pkg: String, lane: Lane, vip: String?, now: Long) {
        val s = stats ?: statsStore.load().also { stats = it }
        s.record(Entry(now, pkg, lane, vip), LocalDate.now().toEpochDay())
        if (!statsSaveQueued) {
            statsSaveQueued = true
            main.postDelayed({
                statsSaveQueued = false
                flushStats()
            }, STATS_SAVE_DELAY_MS)
        }
    }

    private fun flushStats() {
        val json = stats?.encode() ?: return
        io.execute { runCatching { statsStore.save(json) }.onFailure { Log.w(TAG, "Could not save activity", it) } }
    }

    /** Build step 1: see what notifications look like across apps. Debug builds only, never message text. */
    private fun logDecision(sbn: StatusBarNotification, f: NotificationFacts, d: Decision) {
        val outcome = when (d) {
            is Decision.Leave -> "leave(${d.why}) tier=${d.tier}"
            is Decision.Drop -> "drop(own reply)"
            is Decision.Repost -> "repost lane=${d.lane}${d.vip?.let { " vip=${it.name}" } ?: ""}${if (d.quiet) " quiet" else ""}"
        }
        Log.d(
            TAG,
            "pkg=${sbn.packageName} app=${reposter.appLabel(sbn)} sender=${f.sender} category=${f.category} " +
                "group=${f.groupConversation} silent=${f.originalSilent} summary=${f.groupSummary} -> $outcome",
        )
    }

    companion object {
        private const val TAG = "TierTone"
        private const val MAX_OWN_NOTIFICATIONS = 45
        private const val ORPHAN_CHECK_DELAY_MS = 600L
        private const val STATS_SAVE_DELAY_MS = 2_000L
    }
}

/** Tracks which source notifications belong to which group, to spot summaries left with no children. */
class GroupIndex {
    private val children = HashMap<String, HashSet<String>>()
    private val summaries = HashMap<String, String>()
    private val taken = HashSet<String>()

    fun add(sbn: StatusBarNotification) {
        if (sbn.notification.group == null) return
        if (sbn.isSummary()) summaries[sbn.groupKey] = sbn.key else children.getOrPut(sbn.groupKey) { HashSet() } += sbn.key
    }

    fun remove(sbn: StatusBarNotification) {
        if (sbn.notification.group == null) return
        if (sbn.isSummary()) {
            if (summaries[sbn.groupKey] == sbn.key) summaries.remove(sbn.groupKey)
        } else {
            children[sbn.groupKey]?.let { set ->
                set -= sbn.key
                if (set.isEmpty()) children.remove(sbn.groupKey)
            }
        }
    }

    /** TierTone has taken over at least one child of this group. */
    fun markTaken(groupKey: String) {
        if (taken.size > 200) taken.clear()
        taken += groupKey
    }

    fun wasTaken(groupKey: String) = groupKey in taken

    /** Only summaries of groups TierTone emptied count, so a summary-only notification is never removed. */
    fun orphanSummary(groupKey: String): String? =
        summaries[groupKey]?.takeIf { groupKey in taken && children[groupKey].isNullOrEmpty() }

    fun forgetSummary(groupKey: String) {
        summaries.remove(groupKey)
    }

    fun clear() {
        children.clear()
        summaries.clear()
    }

    private fun StatusBarNotification.isSummary() = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
}
