package app.tiertone.core

import org.json.JSONArray
import org.json.JSONObject

/** Plain JSON so the rules file stays human-readable and needs no extra libraries. */
object ConfigJson {
    private const val VERSION = 1

    fun encode(c: Config): String = JSONObject().apply {
        put("v", VERSION)
        put("enabled", c.enabled)
        put("pausedUntil", c.pausedUntil)
        put("onboarded", c.onboarded)
        put("sortUnlisted", c.sortUnlisted)
        put("vipBypassDnd", c.vipBypassDnd)
        put("importantSound", c.importantSound)
        put("normalSound", c.normalSound)
        put("apps", JSONObject().apply { c.appTiers.forEach { (pkg, tier) -> put(pkg, tier.key) } })
        put("vips", JSONArray().apply {
            c.vips.forEach { v ->
                put(JSONObject().apply {
                    put("id", v.id)
                    put("name", v.name)
                    put("phones", JSONArray(v.phones))
                    if (v.lookupKey != null) put("lookupKey", v.lookupKey)
                    put("aliases", JSONArray(v.aliases))
                    put("sound", v.sound)
                    put("addedAt", v.addedAt)
                })
            }
        })
    }.toString()

    fun decode(text: String): Config {
        val o = JSONObject(text)
        val d = Config()
        val apps = o.optJSONObject("apps")
        val tiers = buildMap {
            apps?.keys()?.forEach { pkg -> Tier.of(apps.optString(pkg))?.let { put(pkg, it) } }
        }
        val vipArray = o.optJSONArray("vips") ?: JSONArray()
        val vips = (0 until vipArray.length()).mapNotNull { i ->
            val v = vipArray.optJSONObject(i) ?: return@mapNotNull null
            val id = v.optString("id").ifEmpty { return@mapNotNull null }
            Vip(
                id = id,
                name = v.optString("name"),
                phones = v.optJSONArray("phones").strings(),
                // The React UI may send null; org.json would turn that into the string "null".
                lookupKey = if (v.isNull("lookupKey")) null else v.optString("lookupKey").ifEmpty { null },
                aliases = v.optJSONArray("aliases").strings(),
                sound = v.optString("sound", Sounds.DEFAULT_VIP),
                addedAt = v.optLong("addedAt"),
            )
        }
        return Config(
            enabled = o.optBoolean("enabled", d.enabled),
            pausedUntil = o.optLong("pausedUntil", d.pausedUntil),
            onboarded = o.optBoolean("onboarded", d.onboarded),
            sortUnlisted = o.optBoolean("sortUnlisted", d.sortUnlisted),
            vipBypassDnd = o.optBoolean("vipBypassDnd", d.vipBypassDnd),
            importantSound = o.optString("importantSound", d.importantSound),
            normalSound = o.optString("normalSound", d.normalSound),
            appTiers = tiers,
            vips = vips,
        )
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotEmpty() }
}
