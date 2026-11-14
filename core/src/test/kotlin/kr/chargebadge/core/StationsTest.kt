package kr.chargebadge.core

import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StationsTest {
    private val index: StationIndex by lazy {
        File("../app/src/main/assets/stations_min.csv").useLines { st ->
            File("../app/src/main/assets/regions.csv").useLines { rg -> StationCsv.load(st, rg) }
        }
    }

    @Test
    fun splitsQuotedFields() {
        assertEquals(listOf("a", "b, \"c\"", "1"), StationCsv.splitLine("a,\"b, \"\"c\"\"\",1\r"))
        assertEquals(listOf("", "x", ""), StationCsv.splitLine(",x,"))
    }

    @Test
    fun loadsRealAssets() {
        assertEquals(100_627, index.stations.size)
        assertEquals(256, index.regions.size)
        assertEquals(230, index.regions.map { it.artKey }.distinct().size)
        assertEquals(4, index.regionsOfArt("41110").size)
        assertEquals("봉화한약우프라자,로컬푸드", index.stations.first { it.id == "ME19C287" }.name)
        assertEquals("47850", index.region(194).artKey)
    }

    @Test
    fun nearestMatchesBruteForceOnRealData() {
        val rnd = Random(7)
        repeat(300) {
            val base = index.stations[rnd.nextInt(index.stations.size)]
            val lat = base.lat + rnd.nextDouble(-0.002, 0.002)
            val lng = base.lng + rnd.nextDouble(-0.002, 0.002)
            val brute = index.stations
                .map { it to distanceMeters(lat, lng, it.lat, it.lng) }
                .filter { it.second <= MATCH_RADIUS_M }
                .minByOrNull { it.second }
            assertEquals(brute?.second, index.nearest(lat, lng)?.let { distanceMeters(lat, lng, it.lat, it.lng) })
        }
    }

    @Test
    fun nearestRespectsRadius() {
        val a = Station("A", "a", 37.0, 127.0, 0)
        val b = Station("B", "b", 37.0005, 127.0, 0)
        val idx = StationIndex(listOf(a, b), listOf(Region(0, "k", "n", "k")))
        assertEquals(a, idx.nearest(37.0001, 127.0))
        assertEquals(b, idx.nearest(37.0004, 127.0))
        // ~1.1 km north of B
        assertNull(idx.nearest(37.0105, 127.0))
        assertEquals(111.2, distanceMeters(37.0, 127.0, 37.001, 127.0), 0.1)
    }

    @Test
    fun loadParsesQuotedAndPlainLines() {
        val idx = StationCsv.load(
            sequenceOf("A,plain,37.1,127.2,0\r", "B,\"x, y\",37.3,127.4,0", ""),
            sequenceOf("0,41280-덕양구,경기도 고양시 덕양구,41280"),
        )
        assertEquals(listOf(Station("A", "plain", 37.1, 127.2, 0), Station("B", "x, y", 37.3, 127.4, 0)), idx.stations)
        assertEquals(Region(0, "41280-덕양구", "경기도 고양시 덕양구", "41280"), idx.region(0))
    }
}
