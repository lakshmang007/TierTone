package app.tiertone.engine

import app.tiertone.core.Config
import app.tiertone.core.Lane
import app.tiertone.core.Tier
import app.tiertone.core.Vip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {
    private val self = "app.tiertone"
    private val now = 1_000_000L
    private val mom = Vip(id = "m1", name = "Mom", phones = listOf("+91 98765 43210"))
    private val base = Config(
        onboarded = true,
        appTiers = mapOf("com.slack" to Tier.IMPORTANT, "com.game" to Tier.MUTED, "com.whatsapp" to Tier.MUTED),
        vips = listOf(mom),
    )

    private fun decide(f: NotificationFacts, c: Config = base) = RuleEngine.decide(f, c, now, self)

    @Test fun listedAppGetsItsTier() {
        val d = decide(NotificationFacts(pkg = "com.slack")) as Decision.Repost
        assertEquals(Tier.IMPORTANT, d.tier)
        assertEquals(Lane.IMPORTANT, d.lane)
        assertNull(d.vip)
        assertFalse(d.quiet)
    }

    @Test fun mutedAppIsRepostedSilently() {
        val d = decide(NotificationFacts(pkg = "com.game")) as Decision.Repost
        assertEquals(Lane.MUTED, d.lane)
    }

    @Test fun unlistedAppIsNormalButUntouchedByDefault() {
        val d = decide(NotificationFacts(pkg = "com.news"))
        assertEquals(Decision.Leave(Tier.NORMAL, Why.UNLISTED), d)
    }

    @Test fun unlistedAppIsRepostedWhenSortUnlistedIsOn() {
        val d = decide(NotificationFacts(pkg = "com.news"), base.copy(sortUnlisted = true)) as Decision.Repost
        assertEquals(Lane.NORMAL, d.lane)
    }

    @Test fun sortUnlistedNeverTakesSystemApps() {
        val c = base.copy(sortUnlisted = true)
        assertEquals(Decision.Leave(Tier.NORMAL, Why.SYSTEM_APP), decide(NotificationFacts(pkg = "com.oem.thing", systemApp = true), c))
        assertEquals(Decision.Leave(Tier.NORMAL, Why.SYSTEM_APP), decide(NotificationFacts(pkg = "android"), c))
    }

    @Test fun vipOverridesMutedAppTier() {
        val d = decide(NotificationFacts(pkg = "com.whatsapp", sender = "Mom")) as Decision.Repost
        assertEquals(Lane.VIP, d.lane)
        assertEquals(mom, d.vip)
    }

    @Test fun vipWorksEvenWhenChatAppIsUnlisted() {
        val c = base.copy(appTiers = emptyMap())
        val d = decide(NotificationFacts(pkg = "org.telegram.messenger", sender = "mom"), c) as Decision.Repost
        assertEquals(Lane.VIP, d.lane)
    }

    @Test fun vipMatchingIsLimitedToChatApps() {
        val d = decide(NotificationFacts(pkg = "com.slack", sender = "Mom")) as Decision.Repost
        assertEquals(Lane.IMPORTANT, d.lane)
    }

    @Test fun nonVipChatFollowsAppTier() {
        val d = decide(NotificationFacts(pkg = "com.whatsapp", sender = "Bob")) as Decision.Repost
        assertEquals(Lane.MUTED, d.lane)
    }

    @Test fun groupMutedInsideTheChatAppStaysMutedEvenForVip() {
        val f = NotificationFacts(pkg = "com.whatsapp", sender = "Mom", groupConversation = true, originalSilent = true)
        val d = decide(f) as Decision.Repost
        assertEquals(Lane.MUTED, d.lane)
        assertNull(d.vip)
    }

    @Test fun silentOneToOneFromVipStillRings() {
        val f = NotificationFacts(pkg = "com.whatsapp", sender = "Mom", originalSilent = true)
        assertEquals(Lane.VIP, (decide(f) as Decision.Repost).lane)
    }

    @Test fun silentOriginalFromImportantAppIsMuted() {
        val d = decide(NotificationFacts(pkg = "com.slack", originalSilent = true)) as Decision.Repost
        assertEquals(Lane.MUTED, d.lane)
    }

    @Test fun protectedNotificationsAreNeverTouched() {
        listOf(
            NotificationFacts(pkg = "com.slack", ongoing = true),
            NotificationFacts(pkg = "com.slack", foregroundService = true),
            NotificationFacts(pkg = "com.slack", fullScreen = true),
            NotificationFacts(pkg = "com.slack", category = "call"),
            NotificationFacts(pkg = "com.slack", category = "alarm"),
            NotificationFacts(pkg = "com.slack", media = true),
            NotificationFacts(pkg = "com.slack", progress = true),
            NotificationFacts(pkg = "com.slack", bubble = true),
            NotificationFacts(pkg = "com.slack", otherUser = true),
            NotificationFacts(pkg = "com.slack", hasContent = false),
        ).forEach { f ->
            assertEquals(f.toString(), Decision.Leave(Tier.IMPORTANT, Why.PROTECTED), decide(f))
        }
    }

    @Test fun summariesAreLeftForTheListenerToTidy() {
        assertEquals(Decision.Leave(Tier.IMPORTANT, Why.SUMMARY), decide(NotificationFacts(pkg = "com.slack", groupSummary = true)))
    }

    @Test fun ownNotificationsAreIgnored() {
        assertEquals(Why.SELF, (decide(NotificationFacts(pkg = self)) as Decision.Leave).why)
    }

    @Test fun offAndPausedLeaveEverythingAlone() {
        assertEquals(Why.OFF, (decide(NotificationFacts(pkg = "com.slack"), base.copy(enabled = false)) as Decision.Leave).why)
        assertEquals(Why.OFF, (decide(NotificationFacts(pkg = "com.slack"), base.copy(pausedUntil = now + 1)) as Decision.Leave).why)
        assertTrue(decide(NotificationFacts(pkg = "com.slack"), base.copy(pausedUntil = now)) is Decision.Repost)
    }

    @Test fun updatesThatAskToAlertOnceAreQuietOnlyIfACopyIsShowing() {
        assertTrue((decide(NotificationFacts(pkg = "com.slack", onlyAlertOnce = true, hasCopy = true)) as Decision.Repost).quiet)
        assertFalse((decide(NotificationFacts(pkg = "com.slack", onlyAlertOnce = true, hasCopy = false)) as Decision.Repost).quiet)
    }

    @Test fun ownReplyUpdatesCopyQuietlyOrIsDropped() {
        val withCopy = decide(NotificationFacts(pkg = "com.whatsapp", fromSelf = true, hasCopy = true)) as Decision.Repost
        assertTrue(withCopy.quiet)
        assertEquals(Decision.Drop(Tier.MUTED), decide(NotificationFacts(pkg = "com.whatsapp", fromSelf = true, hasCopy = false)))
    }
}
