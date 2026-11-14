package kr.chargebadge.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleTest {
    private val arts = listOf("11710", "41280", "41500", "51730", "47130", "47770", "44150", "52180")
    private val index = StationIndex(
        arts.mapIndexed { i, a -> Station("S$i", "충전소 $a", 37.0 + i * 0.1, 127.0, i) },
        arts.mapIndexed { i, a -> Region(i, a, "지역 $a", a) },
    )

    @Test
    fun sampleYearOpensEveryScreen() {
        val today = LocalDate.of(2026, 12, 3)
        val s = sampleState(index, today)
        assertTrue(anniversaryOpen(s.firstMet, today))
        assertEquals(arts.size, anniversaryPoints(s, today).size)
        assertTrue(s.owned.keys.any { it.tier == Tier.SILVER })
        assertTrue(s.owned.keys.any { it.tier == Tier.GOLD })
        // 공주 shows one of its three designs as not got yet.
        assertEquals(setOf(0, 1), s.owned.keys.filter { it.design.artKey == "44150" }.map { it.design.number }.toSet())
        assertEquals(TREE_SLOTS, treeSlots(s, 2026).count { it != null })
        assertEquals(s.charges.sortedBy { it.timeMillis }, s.charges)
        assertTrue(s.charges.all { it.date < today })
    }

    @Test
    fun decemberSampleHasWolfOnTheTree() {
        val withDaejeon = StationIndex(
            index.stations + Station("SD", "대전 충전소", 36.35, 127.38, arts.size),
            index.regions + Region(arts.size, "30170", "대전광역시 서구", "30170"),
        )
        val s = sampleState(withDaejeon, LocalDate.of(2026, 12, 3))
        assertEquals(1, s.count(WOLF))
        assertEquals(WOLF, s.charges.last().badge)
        assertTrue(WOLF in treeSlots(s, 2026))
        assertEquals(0, sampleState(withDaejeon, LocalDate.of(2026, 11, 14)).count(WOLF))
    }

    @Test
    fun noTreeOutsideDecember() {
        assertTrue(sampleState(index, LocalDate.of(2026, 11, 14)).trees.isEmpty())
    }
}
