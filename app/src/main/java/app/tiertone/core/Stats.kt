package app.tiertone.core

import org.json.JSONArray
import org.json.JSONObject

/** One sorted notification. Holds no message text: only when, which app, and where it went. */
data class Entry(val time: Long, val pkg: String, val lane: Lane, val vip: String? = null)

/**
 * On-device activity: per-day counts per lane, the latest few entries, and which apps have
 * notified recently (so the Apps screen can list them first). Written only by the listener.
 * Nothing here ever leaves the phone.
 */
class Stats(
    val days: MutableMap<Long, IntArray> = HashMap(),
    val recent: ArrayDeque<Entry> = ArrayDeque(),
    val seen: MutableMap<String, Long> = HashMap(),
) {
    fun record(entry: Entry, epochDay: Long) {
        days.getOrPut(epochDay) { IntArray(Lane.entries.size) }[entry.lane.ordinal]++
        recent.addFirst(entry)
        while (recent.size > MAX_RECENT) recent.removeLast()
        seen[entry.pkg] = entry.time
        if (seen.size > MAX_SEEN) seen.entries.minByOrNull { it.value }?.let { seen.remove(it.key) }
        // Keep two weeks of day buckets.
        days.keys.filter { it <= epochDay - KEEP_DAYS }.forEach { days.remove(it) }
    }

    /** Totals per lane over the [n] days ending at [today]. Index with [Lane.ordinal]. */
    fun lastDays(today: Long, n: Int = 7): IntArray {
        val out = IntArray(Lane.entries.size)
        for (d in (today - n + 1)..today) days[d]?.forEachIndexed { i, c -> out[i] += c }
        return out
    }

    fun encode(): String = JSONObject().apply {
        put("days", JSONObject().apply { days.forEach { (d, counts) -> put(d.toString(), JSONArray(counts.toList())) } })
        put("recent", JSONArray().apply {
            recent.forEach { e ->
                put(JSONObject().apply {
                    put("t", e.time)
                    put("p", e.pkg)
                    put("l", e.lane.key)
                    if (e.vip != null) put("v", e.vip)
                })
            }
        })
        put("seen", JSONObject().apply { seen.forEach { (p, t) -> put(p, t) } })
    }.toString()

    companion object {
        const val MAX_RECENT = 40
        const val MAX_SEEN = 150
        const val KEEP_DAYS = 14

        fun decode(text: String): Stats {
            val o = JSONObject(text)
            val stats = Stats()
            o.optJSONObject("days")?.let { days ->
                days.keys().forEach { k ->
                    val arr = days.optJSONArray(k) ?: return@forEach
                    val counts = IntArray(Lane.entries.size)
                    for (i in 0 until minOf(arr.length(), counts.size)) counts[i] = arr.optInt(i)
                    k.toLongOrNull()?.let { stats.days[it] = counts }
                }
            }
            o.optJSONArray("recent")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val lane = Lane.of(e.optString("l")) ?: continue
                    stats.recent.addLast(Entry(e.optLong("t"), e.optString("p"), lane, e.optString("v").ifEmpty { null }))
                }
            }
            o.optJSONObject("seen")?.let { seen -> seen.keys().forEach { stats.seen[it] = seen.optLong(it) } }
            return stats
        }
    }
}
