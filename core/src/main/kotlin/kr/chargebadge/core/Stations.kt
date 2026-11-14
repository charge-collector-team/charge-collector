package kr.chargebadge.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Station(val id: String, val name: String, val lat: Double, val lng: Double, val regionIndex: Int)
data class Region(val index: Int, val key: String, val name: String, val artKey: String)

private const val EARTH_RADIUS_M = 6_371_008.8
private const val METERS_PER_DEG_LAT = EARTH_RADIUS_M * Math.PI / 180

fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(min(1.0, a)))
}

class StationIndex(val stations: List<Station>, val regions: List<Region>) {
    private val byLat = stations.sortedBy { it.lat }
    private val regionByIndex = regions.associateBy { it.index }
    private val regionsByArt = regions.groupBy { it.artKey }

    fun nearest(lat: Double, lng: Double, maxMeters: Double = MATCH_RADIUS_M): Station? {
        // Great-circle distance is never shorter than the latitude arc, so the window can't drop a match.
        val dLat = maxMeters / METERS_PER_DEG_LAT
        val start = -(byLat.binarySearch { if (it.lat < lat - dLat) -1 else 1 }) - 1
        var best: Station? = null
        var bestMeters = maxMeters
        for (i in start until byLat.size) {
            val s = byLat[i]
            if (s.lat > lat + dLat) break
            val m = distanceMeters(lat, lng, s.lat, s.lng)
            if (m <= bestMeters) {
                best = s
                bestMeters = m
            }
        }
        return best
    }

    fun region(index: Int): Region = regionByIndex.getValue(index)
    fun regionsOfArt(artKey: String): List<Region> = regionsByArt[artKey].orEmpty()
}

object StationCsv {
    fun load(stationLines: Sequence<String>, regionLines: Sequence<String>): StationIndex {
        val regions = regionLines.filter { it.isNotBlank() }.map { line ->
            val (index, key, name, art) = if ('"' in line) splitLine(line) else line.trimEnd('\r').split(',')
            Region(index.toInt(), key, name, art)
        }.toList()
        val stations = stationLines.filter { it.isNotBlank() }.map { line ->
            val (id, name, lat, lng, region) = if ('"' in line) splitLine(line) else line.trimEnd('\r').split(',')
            Station(id, name, lat.toDouble(), lng.toDouble(), region.toInt())
        }.toList()
        return StationIndex(stations, regions)
    }

    /** One RFC 4180 record per line (no embedded newlines in the assets). */
    fun splitLine(line: String): List<String> {
        val s = line.trimEnd('\r', '\n')
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                quoted && c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { fields += field.toString(); field.clear() }
                else -> field.append(c)
            }
            i++
        }
        fields += field.toString()
        return fields
    }
}
