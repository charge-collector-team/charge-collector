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
        assertEquals(TREE_SLOTS, treeSlots(s, 2026).count { it != null })
        assertEquals(s.charges.sortedBy { it.timeMillis }, s.charges)
        assertTrue(s.charges.all { it.date < today })
    }

    @Test
    fun noTreeOutsideDecember() {
        assertTrue(sampleState(index, LocalDate.of(2026, 11, 14)).trees.isEmpty())
    }
}
