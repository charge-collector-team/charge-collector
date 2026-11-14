package kr.chargebadge.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KoreaMapTest {
    @Test
    fun decodesDeltas() {
        val map = KoreaMap.parse(sequenceOf("# 출처", "c 1270000,375000 10,-20 5,5 ", "r 3,7 1260000,370000 100,0 0,100 -100,-100", "r - 1250000,340000 1,1 1,1 -2,-2"))
        assertEquals(listOf(127f, 37.5f, 127.001f, 37.498f, 127.0015f, 37.4985f), map.coast.single().toList())
        assertEquals(listOf(3, 7), map.rings[0].regions)
        assertEquals(emptyList(), map.rings[1].regions)
        assertEquals(126.01f, map.rings[0].points[2], 1e-4f)
    }

    @Test
    fun shippedMapCoversEveryRegion() {
        val assets = File("../app/src/main/assets")
        val map = KoreaMap.parse(File(assets, "korea_map.txt").readLines().asSequence())
        val regions = File(assets, "regions.csv").readLines().filter { it.isNotBlank() }.map { it.substringBefore(',').toInt() }.toSet()
        assertEquals(regions, map.rings.flatMap { it.regions }.toSet())
        assertTrue(map.coast.isNotEmpty() && map.provinceBorders.isNotEmpty() && map.municipalBorders.isNotEmpty())
    }
}
