package kr.chargebadge.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarAndJsonTest {
    private val gold = Badge(Design("47850", 1), Tier.GOLD)
    private val silver = Badge(Design("11680", 0), Tier.SILVER)

    @Test
    fun seasons() {
        assertEquals(Season.WINTER, season(LocalDate.of(2026, 2, 28)))
        assertEquals(Season.SPRING, season(LocalDate.of(2026, 3, 1)))
        assertEquals(Season.SUMMER, season(LocalDate.of(2026, 8, 31)))
        assertEquals(Season.AUTUMN, season(LocalDate.of(2026, 11, 30)))
        assertEquals(Season.WINTER, season(LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun treeOnlyInDecemberWithOwnedBadgesUpToCount() {
        val dec = LocalDate.of(2026, 12, 31)
        val state = GameState(owned = mapOf(gold to 2))
        assertFalse(treeEditable(LocalDate.of(2026, 11, 30)))
        assertFailsWith<IllegalArgumentException> { setTreeSlot(state, LocalDate.of(2026, 11, 30), 0, gold) }
        assertFailsWith<IllegalArgumentException> { setTreeSlot(state, dec, 0, silver) }
        assertFailsWith<IllegalArgumentException> { setTreeSlot(state, dec, TREE_SLOTS, gold) }
        val twice = setTreeSlot(setTreeSlot(state, dec, 2, gold), dec, 4, gold)
        assertEquals(listOf(null, null, gold, null, gold, null), treeSlots(twice, 2026))
        assertFailsWith<IllegalArgumentException> { setTreeSlot(twice, dec, 0, gold) }
        assertEquals(gold, treeSlots(setTreeSlot(twice, dec, 2, gold), 2026)[2])
        assertEquals(listOf(null, null, null, null, gold, null), treeSlots(setTreeSlot(twice, dec, 2, null), 2026))
        assertEquals(List(TREE_SLOTS) { null }, treeSlots(twice, 2027))
    }

    @Test
    fun anniversaryOpensAfterOneYear() {
        val met = LocalDate.of(2025, 12, 3)
        assertFalse(anniversaryOpen(null, met))
        assertFalse(anniversaryOpen(met, LocalDate.of(2026, 12, 2)))
        assertTrue(anniversaryOpen(met, LocalDate.of(2026, 12, 3)))
    }

    @Test
    fun anniversaryPointsAreFirstVisitsInOrder() {
        val a = Station("A", "a", 37.0, 127.0, 0)
        val b = Station("B", "b", 36.0, 128.0, 1)
        val d = LocalDate.of(2026, 1, 1)
        val charges = listOf(
            ChargeRecord(b, d.plusDays(1), 20, silver),
            ChargeRecord(a, d, 10, silver),
            ChargeRecord(a, d.plusDays(2), 30, silver),
        )
        assertEquals(listOf("A", "B"), anniversaryPoints(GameState(charges = charges)).map { it.station.id })
    }

    @Test
    fun designNames() {
        assertEquals("꿀참외", designName(Design("47850", 2)))
        assertEquals("잠실 롯데타워", designName(Design("11710", 0)))
        assertEquals("도안 2", designName(Design("11710", 1)))
        assertEquals("도안 1", designName(Design("11110", 0)))
    }

    @Test
    fun jsonRoundTrip() {
        val weird = Station("ME\"1", "이름, \"따옴표\" \\ 줄\n바꿈\t🚗\u0001", 37.4763, 126.95839, 20)
        val state = GameState(
            owned = mapOf(gold to 2, silver to 1),
            acquisitions = listOf(Acquisition(gold, Source.CHARGE, 1_763_000_000_123), Acquisition(silver, Source.TRADE, 5, "CB-1001")),
            charges = listOf(ChargeRecord(weird, LocalDate.of(2026, 11, 13), 1_763_000_000_123, gold)),
            representatives = mapOf(194 to gold, 22 to silver),
            trades = listOf(TradeOffer(3, "CB-1002", Badge(Design("11680", 1), Tier.BRONZE), Badge(Design("50110", 0), Tier.BRONZE), 99)),
            nextTradeId = 4,
            trees = mapOf(2026 to listOf(gold, null, silver, null, null, null)),
            firstMet = LocalDate.of(2025, 12, 3),
            ignitionSnapshot = IgnitionSnapshot(48, 37.1, 127.2, 77, counted = true),
        )
        assertEquals(state, GameStateJson.decode(GameStateJson.encode(state)))
        val empty = GameState()
        assertEquals(empty, GameStateJson.decode(GameStateJson.encode(empty)))
    }

    @Test
    fun anniversaryUsesYearBeforeLatestAnniversary() {
        val met = LocalDate.of(2025, 12, 3)
        assertEquals(null, latestAnniversary(met, LocalDate.of(2026, 12, 2)))
        assertEquals(LocalDate.of(2026, 12, 3), latestAnniversary(met, LocalDate.of(2027, 12, 2)))
        assertEquals(LocalDate.of(2027, 12, 3), latestAnniversary(met, LocalDate.of(2027, 12, 3)))
        val s = Station("A", "a", 37.0, 127.0, 0)
        val t = Station("T", "t", 36.0, 128.0, 0)
        val charges = listOf(
            ChargeRecord(s, LocalDate.of(2025, 12, 2), 1, silver), // before first met
            ChargeRecord(t, LocalDate.of(2025, 12, 3), 2, silver),
            ChargeRecord(s, LocalDate.of(2026, 12, 2), 3, gold),
            ChargeRecord(s, LocalDate.of(2026, 12, 3), 4, gold), // on the anniversary: next year
        )
        val state = GameState(charges = charges, firstMet = met)
        val today = LocalDate.of(2027, 1, 10)
        assertEquals(listOf(2L, 3L), anniversaryCharges(state, today).map { it.timeMillis })
        assertEquals(listOf("T", "A"), anniversaryPoints(state, today).map { it.station.id })
        assertEquals(emptyList(), anniversaryPoints(state.copy(firstMet = null), today))
    }
}
