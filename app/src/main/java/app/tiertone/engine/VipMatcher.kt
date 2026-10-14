package app.tiertone.engine

import app.tiertone.core.Vip
import java.text.Normalizer
import java.util.Locale

/**
 * Decides whether a chat notification came from a VIP. Pure Kotlin so it can be unit tested.
 *
 * Signals, strongest first:
 *  1. A `tel:` or contacts-lookup URI the chat app attached to the sender (exact contact).
 *  2. The sender's display name, which WhatsApp takes from the phone's contacts.
 *  3. A sender shown as a raw phone number (someone not saved in the chat app).
 */
object VipMatcher {

    fun match(vips: List<Vip>, sender: String?, personUris: List<String>): Vip? {
        if (vips.isEmpty()) return null

        for (uri in personUris) {
            when {
                uri.startsWith("tel:", ignoreCase = true) -> {
                    val number = uri.substring(4)
                    vips.firstOrNull { v -> v.phones.any { samePhone(it, number) } }?.let { return it }
                }
                uri.contains("/lookup/") -> {
                    val key = lookupKeyOf(uri) ?: continue
                    vips.firstOrNull { it.lookupKey != null && it.lookupKey == key }?.let { return it }
                }
            }
        }

        if (sender.isNullOrBlank()) return null
        val wanted = nameKey(sender)
        if (wanted.isNotEmpty()) {
            vips.firstOrNull { v -> (listOf(v.name) + v.aliases).any { nameKey(it) == wanted } }?.let { return it }
        }
        if (looksLikePhone(sender)) {
            vips.firstOrNull { v -> v.phones.any { samePhone(it, sender) } }?.let { return it }
        }
        return null
    }

    /**
     * Case-, accent-width- and emoji-insensitive form of a name: "  Mum ❤️ " and "mum" compare equal.
     * Names made only of symbols fall back to their trimmed text so they can still match exactly.
     */
    fun nameKey(name: String): String {
        val folded = Normalizer.normalize(name, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val kept = buildString {
            for (ch in folded) {
                if (ch.isLetterOrDigit()) append(ch) else if (ch.isWhitespace()) append(' ')
            }
        }.trim().replace(Regex("\\s+"), " ")
        return kept.ifEmpty { name.trim() }
    }

    fun digits(s: String): String = s.filter { it in '0'..'9' }

    /**
     * Two numbers are the same if their last 10 digits agree (so "+91 98765 43210",
     * "098765 43210" and "9876543210" all match). Shorter numbers must match exactly.
     */
    fun samePhone(a: String, b: String): Boolean {
        val da = digits(a)
        val db = digits(b)
        if (da.length < 5 || db.length < 5) return false
        return if (da.length >= 10 && db.length >= 10) da.takeLast(10) == db.takeLast(10) else da == db
    }

    fun looksLikePhone(s: String): Boolean {
        val t = s.trim()
        return t.isNotEmpty() && t.all { it.isDigit() || it in "+-() ." } && digits(t).length >= 7
    }

    private fun lookupKeyOf(uri: String): String? =
        uri.substringAfter("/lookup/", "").substringBefore('/').ifEmpty { null }
}

/** Cleans chat-notification titles into a sender name. */
object Titles {
    private val COUNT_SUFFIX = Regex("""\s*\(\d+\s+[^)]*\)\s*$""")

    /** "Mom (3 messages)" -> "Mom"; "Mom @ Family" -> "Mom". */
    fun senderFromTitle(title: String?): String? {
        if (title.isNullOrBlank()) return null
        var t = title.replace(COUNT_SUFFIX, "")
        val at = t.indexOf(" @ ")
        if (at > 0) t = t.substring(0, at)
        return t.trim().ifEmpty { null }
    }
}
