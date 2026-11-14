package kr.chargebadge.core

/**
 * Simplified 2018 municipal boundaries from korea_map.txt (built by data-mapping/build_korea_map.py).
 * Points are lng,lat pairs. Each boundary line is stored once; rings are what gets filled.
 */
class KoreaMap(
    val coast: List<FloatArray>,
    val provinceBorders: List<FloatArray>,
    val municipalBorders: List<FloatArray>,
    val rings: List<Ring>,
) {
    /** A ring to fill. [regions] are our region indices it stands for (several when one 2018 boundary covers newer ones). */
    class Ring(val regions: List<Int>, val points: FloatArray)

    companion object {
        fun parse(lines: Sequence<String>): KoreaMap {
            val coast = mutableListOf<FloatArray>()
            val province = mutableListOf<FloatArray>()
            val municipal = mutableListOf<FloatArray>()
            val rings = mutableListOf<Ring>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split(' ')
                when (parts[0]) {
                    "c" -> coast += decode(parts, 1)
                    "p" -> province += decode(parts, 1)
                    "m" -> municipal += decode(parts, 1)
                    "r" -> rings += Ring(if (parts[1] == "-") emptyList() else parts[1].split(',').map(String::toInt), decode(parts, 2))
                }
            }
            return KoreaMap(coast, province, municipal, rings)
        }

        /** First point absolute, the rest as differences, all in 1e-4 degrees. */
        private fun decode(parts: List<String>, from: Int): FloatArray {
            val out = FloatArray((parts.size - from) * 2)
            var x = 0
            var y = 0
            for (i in from until parts.size) {
                val c = parts[i].indexOf(',')
                x += parts[i].substring(0, c).toInt()
                y += parts[i].substring(c + 1).toInt()
                out[(i - from) * 2] = x / 1e4f
                out[(i - from) * 2 + 1] = y / 1e4f
            }
            return out
        }
    }
}
