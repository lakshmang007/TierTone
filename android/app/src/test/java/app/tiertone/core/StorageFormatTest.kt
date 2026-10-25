package app.tiertone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageFormatTest {

    @Test fun configRoundTrips() {
        val c = Config(
            enabled = false,
            pausedUntil = 123,
            onboarded = true,
            sortUnlisted = true,
            vipBypassDnd = true,
            importantSound = "app:chime",
            normalSound = "content://media/internal/audio/media/12",
            appTiers = mapOf("com.slack" to Tier.IMPORTANT, "com.game" to Tier.MUTED),
            vips = listOf(Vip("a1", "Mom", listOf("+1 555"), "lk", listOf("Mum"), "app:bell", 99)),
        )
        assertEquals(c, ConfigJson.decode(ConfigJson.encode(c)))
    }

    @Test fun missingFieldsFallBackToDefaults() {
        assertEquals(Config(), ConfigJson.decode("{}"))
        val c = ConfigJson.decode("""{"apps":{"x":"bogus","y":"muted"},"vips":[{"name":"no id"}]}""")
        assertEquals(mapOf("y" to Tier.MUTED), c.appTiers)
        assertTrue(c.vips.isEmpty())
    }

    @Test fun resetKeepsOnboarding() {
        val c = Config(onboarded = true, appTiers = mapOf("a" to Tier.MUTED), vipBypassDnd = true)
        assertEquals(Config(onboarded = true), c.reset())
    }

    @Test fun statsCountAndRoundTrip() {
        val s = Stats()
        s.record(Entry(1, "com.a", Lane.VIP, "Mom"), epochDay = 100)
        s.record(Entry(2, "com.b", Lane.MUTED), epochDay = 100)
        s.record(Entry(3, "com.b", Lane.MUTED), epochDay = 95)
        s.record(Entry(4, "com.c", Lane.IMPORTANT), epochDay = 80) // outside the 7-day window

        val week = s.lastDays(today = 100)
        assertEquals(1, week[Lane.VIP.ordinal])
        assertEquals(2, week[Lane.MUTED.ordinal])
        assertEquals(0, week[Lane.IMPORTANT.ordinal])

        val back = Stats.decode(s.encode())
        assertEquals(s.recent.toList(), back.recent.toList())
        assertEquals(s.seen, back.seen)
        assertEquals(week.toList(), back.lastDays(today = 100).toList())
    }

    @Test fun statsStayBounded() {
        val s = Stats()
        repeat(500) { i -> s.record(Entry(i.toLong(), "pkg$i", Lane.NORMAL), epochDay = 100L + i / 50) }
        assertEquals(Stats.MAX_RECENT, s.recent.size)
        assertEquals(Stats.MAX_SEEN, s.seen.size)
        assertTrue(s.days.size <= Stats.KEEP_DAYS)
        assertEquals("pkg499", s.recent.first().pkg)
    }
}
