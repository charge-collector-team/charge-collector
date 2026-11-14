package kr.chargebadge.core

import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BadgeEngineTest {
    private val gwonseon = Region(0, "41110-권선구", "수원시 권선구", "41110")
    private val yeongtong = Region(1, "41110-영통구", "수원시 영통구", "41110")
    private val chilgok = Region(2, "47850", "칠곡군", "47850")
    private val s1 = Station("S1", "권선 충전소", 37.25, 127.0, 0)
    private val s2 = Station("S2", "영통 충전소", 37.26, 127.05, 1)
    private val s3 = Station("S3", "칠곡 충전소", 35.99, 128.4, 2)
    private val index = StationIndex(listOf(s1, s2, s3), listOf(gwonseon, yeongtong, chilgok))
    private val day = LocalDate.of(2026, 11, 13)

    private fun fixed(double: Double, int: Int) = object : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextDouble() = double
        override fun nextInt(until: Int) = int
    }

    private fun bronze(art: String, n: Int) = Badge(Design(art, n), Tier.BRONZE)

    @Test
    fun goldBelowOnePercentElseBronze() {
        val (_, gold) = BadgeEngine(fixed(0.0099, 2)).onValidCharge(GameState(), s3, day, 1, index)
        assertEquals(ChargeOutcome.Awarded(Badge(Design("47850", 2), Tier.GOLD)), gold)
        val (state, bronze) = BadgeEngine(fixed(0.01, 0)).onValidCharge(GameState(), s1, day, 1, index)
        assertEquals(ChargeOutcome.Awarded(bronze("41110", 0)), bronze)
        assertEquals(1, state.count(bronze("41110", 0)))
        assertEquals(listOf(Acquisition(bronze("41110", 0), Source.CHARGE, 1)), state.acquisitions)
        assertEquals(listOf(ChargeRecord(s1, day, 1, bronze("41110", 0))), state.charges)
    }

    @Test
    fun distributionIsRoughlyOnePercentGoldAndEvenDesigns() {
        val engine = BadgeEngine(Random(42))
        val badges = (1..100_000).map {
            (engine.onValidCharge(GameState(), s1, day, 0, index).second as ChargeOutcome.Awarded).badge
        }
        val gold = badges.count { it.tier == Tier.GOLD }
        assertTrue(gold in 800..1200, "gold=$gold")
        badges.groupingBy { it.design.number }.eachCount().values.forEach { assertTrue(it in 32_000..34_700, "design=$it") }
    }

    @Test
    fun sameStationOncePerDay() {
        val engine = BadgeEngine(fixed(0.5, 0))
        val (a, _) = engine.onValidCharge(GameState(), s1, day, 1, index)
        val (b, second) = engine.onValidCharge(a, s1, day, 2, index)
        assertEquals(ChargeOutcome.DailyLimit, second)
        assertSame(a, b)
        val (c, other) = engine.onValidCharge(a, s2, day, 3, index)
        assertTrue(other is ChargeOutcome.Awarded)
        val (_, nextDay) = engine.onValidCharge(c, s1, day.plusDays(1), 4, index)
        assertTrue(nextDay is ChargeOutcome.Awarded)
    }

    @Test
    fun synthesisCountsSameArtAcrossGuAndSkipsLocked() {
        val engine = BadgeEngine(fixed(0.5, 1))
        var state = GameState()
        // 5 days x 2 gu of 수원 = 10 bronze of 41110 design 1
        for (d in 0L until 5) for (s in listOf(s1, s2)) state = engine.onValidCharge(state, s, day.plusDays(d), d, index).first
        val design = Design("41110", 1)
        assertEquals(10, state.count(Badge(design, Tier.BRONZE)))
        assertTrue(engine.synthesizable(state, design))

        val friendWant = bronze("26350", 0)
        val (locked, offer) = engine.proposeTrade(state, "CB-1001", Badge(design, Tier.BRONZE), friendWant, 0)
        assertFalse(engine.synthesizable(locked, design))
        assertFailsWith<IllegalArgumentException> { engine.synthesize(locked, design, 9) }

        val made = engine.synthesize(engine.cancelTrade(locked, offer.id), design, 9)
        assertEquals(0, made.count(Badge(design, Tier.BRONZE)))
        assertEquals(1, made.count(Badge(design, Tier.SILVER)))
        assertEquals(Acquisition(Badge(design, Tier.SILVER), Source.SYNTHESIS, 9), made.acquisitions.last())
        assertEquals(Tier.SILVER, engine.cellTier(made, index, 0))
        assertEquals(Tier.SILVER, engine.cellTier(made, index, 1))
        assertNull(engine.cellTier(made, index, 2))
    }

    @Test
    fun cellTierIsHighestAndVisitedIsPerGu() {
        val engine = BadgeEngine(fixed(0.0, 0))
        val (state, _) = engine.onValidCharge(GameState(owned = mapOf(bronze("41110", 2) to 3)), s1, day, 1, index)
        assertEquals(Tier.GOLD, engine.cellTier(state, index, 1))
        assertTrue(engine.visited(state, 0))
        assertFalse(engine.visited(state, 1))
    }

    @Test
    fun tradeLocksMovesAndLeavesNoVisit() {
        val engine = BadgeEngine(fixed(0.5, 0))
        val mine = bronze("41110", 0)
        val theirs = bronze("50110", 1)
        val start = engine.setRepresentative(GameState(owned = mapOf(mine to 1)), 0, mine)

        assertFailsWith<IllegalArgumentException> {
            engine.proposeTrade(start, "CB-1002", Badge(mine.design, Tier.SILVER), theirs, 0)
        }
        val (proposed, offer) = engine.proposeTrade(start, "CB-1002", mine, theirs, 0)
        assertEquals(1, proposed.locked(mine))
        assertFailsWith<IllegalArgumentException> { engine.proposeTrade(proposed, "CB-1002", mine, theirs, 0) }

        val done = engine.acceptTrade(proposed, offer.id, 1000)
        assertEquals(0, done.count(mine))
        assertEquals(1, done.count(theirs))
        assertEquals(emptyList(), done.trades)
        assertEquals(emptyList(), done.charges)
        assertFalse(engine.visited(done, 0))
        assertEquals(Acquisition(theirs, Source.TRADE, 1000, "CB-1002"), done.acquisitions.last())
        val friend = done.friends.first { it.id == "CB-1002" }
        assertEquals(1, friend.badges[theirs])
        assertEquals(1, friend.badges[mine])
        assertEquals(mine, done.representatives[0])
    }

    @Test
    fun tradesExpireAfterSevenDays() {
        val engine = BadgeEngine(fixed(0.5, 0))
        val mine = bronze("41110", 0)
        val (proposed, offer) = engine.proposeTrade(GameState(owned = mapOf(mine to 1)), "CB-1003", mine, bronze("47850", 2), 0)
        assertEquals(1, engine.expireTrades(proposed, TRADE_EXPIRY_MS - 1).trades.size)
        assertEquals(0, engine.expireTrades(proposed, TRADE_EXPIRY_MS).trades.size)
        val late = engine.acceptTrade(proposed, offer.id, TRADE_EXPIRY_MS)
        assertEquals(1, late.count(mine))
        assertEquals(0, late.locked(mine))
    }
}
