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
            silentMode = true,
            importantVibration = "rapid",
            normalVibration = Vibrations.NONE,
            appTiers = mapOf("com.slack" to Tier.IMPORTANT, "com.game" to Tier.MUTED),
            vips = listOf(
                Vip(
                    id = "a1", name = "Mom", phones = listOf("+1 555"), lookupKey = "lk", aliases = listOf("Mum"),
                    sound = "app:bell", vibration = "long", addedAt = 99,
                ),
            ),
        )
        assertEquals(c, ConfigJson.decode(ConfigJson.encode(c)))
    }

    @Test fun rulesFromBeforeVibrationsGetDefaultPatterns() {
        val c = ConfigJson.decode("""{"importantSound":"app:rise","vips":[{"id":"m","name":"Mom","sound":"app:bell"}]}""")
        assertEquals(false, c.silentMode)
        assertEquals(Vibrations.DEFAULT_IMPORTANT, c.importantVibration)
        assertEquals(Vibrations.DEFAULT_NORMAL, c.normalVibration)
        assertEquals(Vibrations.DEFAULT_VIP, c.vips.single().vibration)
    }

    @Test fun eachPriorityFeelsDifferentByDefault() {
        val c = Config()
        val defaults = listOf(c.vibrationFor(Tier.IMPORTANT), c.vibrationFor(Tier.NORMAL), Vibrations.DEFAULT_VIP)
        assertEquals(defaults.size, defaults.toSet().size)
        defaults.forEach { assertTrue("$it has a pattern", Vibrations.pattern(it) != null) }
        assertEquals(Vibrations.NONE, c.vibrationFor(Tier.MUTED))
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
