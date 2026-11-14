package kr.chargebadge.core

import java.time.LocalDate

/** Hand-rolled JSON so core stays free of Android APIs. Numbers are read as Double (exact for millis < 2^53). */
object GameStateJson {

    fun encode(state: GameState): String {
        val tree = mapOf(
            "v" to 1,
            "owned" to counts(state.owned),
            "acquisitions" to state.acquisitions.map {
                mapOf("b" to badge(it.badge), "s" to it.source.name, "t" to it.timeMillis, "f" to it.friendId)
            },
            "charges" to state.charges.map {
                mapOf("st" to station(it.station), "d" to it.date.toString(), "t" to it.timeMillis, "b" to badge(it.badge))
            },
            "representatives" to state.representatives.entries.associate { (k, v) -> k.toString() to badge(v) },
            "friends" to state.friends.map { mapOf("id" to it.id, "name" to it.name, "badges" to counts(it.badges)) },
            "trades" to state.trades.map {
                mapOf("id" to it.id, "f" to it.friendId, "give" to badge(it.give), "want" to badge(it.want), "t" to it.createdMillis)
            },
            "nextTradeId" to state.nextTradeId,
            "trees" to state.trees.entries.associate { (y, slots) -> y.toString() to slots.map { it?.let(::badge) } },
            "firstMet" to state.firstMet?.toString(),
            "snapshot" to state.ignitionSnapshot?.let {
                mapOf("soc" to it.soc, "lat" to it.lat, "lng" to it.lng, "t" to it.timeMillis, "counted" to it.counted)
            },
        )
        return StringBuilder().also { write(tree, it) }.toString()
    }

    fun decode(text: String): GameState {
        val o = Parser(text).parse().obj()
        return GameState(
            owned = counts(o["owned"]),
            acquisitions = o["acquisitions"].list().map {
                val a = it.obj()
                Acquisition(badge(a["b"]), Source.valueOf(a["s"] as String), a["t"].long(), a["f"] as String?)
            },
            charges = o["charges"].list().map {
                val c = it.obj()
                ChargeRecord(station(c["st"]), LocalDate.parse(c["d"] as String), c["t"].long(), badge(c["b"]))
            },
            representatives = o["representatives"].obj().entries.associate { (k, v) -> k.toInt() to badge(v) },
            friends = o["friends"].list().map {
                val f = it.obj()
                Friend(f["id"] as String, f["name"] as String, counts(f["badges"]))
            },
            trades = o["trades"].list().map {
                val t = it.obj()
                TradeOffer(t["id"].int(), t["f"] as String, badge(t["give"]), badge(t["want"]), t["t"].long())
            },
            nextTradeId = o["nextTradeId"].int(),
            trees = o["trees"].obj().entries.associate { (y, slots) -> y.toInt() to slots.list().map { it?.let(::badge) } },
            firstMet = (o["firstMet"] as String?)?.let(LocalDate::parse),
            ignitionSnapshot = (o["snapshot"] as Map<*, *>?)?.let {
                IgnitionSnapshot(it["soc"].int(), it["lat"] as Double, it["lng"] as Double, it["t"].long(), it["counted"] as Boolean)
            },
        )
    }

    private fun badge(b: Badge) = "${b.design.artKey}:${b.design.number}:${b.tier}"
    private fun badge(v: Any?): Badge {
        val (art, number, tier) = (v as String).split(':')
        return Badge(Design(art, number.toInt()), Tier.valueOf(tier))
    }

    private fun counts(m: Map<Badge, Int>) = m.entries.associate { (b, n) -> badge(b) to n }
    private fun counts(v: Any?) = v.obj().entries.associate { (k, n) -> badge(k) to n.int() }

    private fun station(s: Station) = mapOf("id" to s.id, "name" to s.name, "lat" to s.lat, "lng" to s.lng, "r" to s.regionIndex)
    private fun station(v: Any?): Station {
        val s = v.obj()
        return Station(s["id"] as String, s["name"] as String, s["lat"] as Double, s["lng"] as Double, s["r"].int())
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.obj() = this as Map<String, Any?>
    private fun Any?.list() = this as List<Any?>
    private fun Any?.int() = (this as Double).toInt()
    private fun Any?.long() = (this as Double).toLong()

    private fun write(v: Any?, sb: StringBuilder) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(v, sb)
            is Boolean, is Int, is Long, is Double -> sb.append(v)
            is Map<*, *> -> {
                sb.append('{')
                v.entries.forEachIndexed { i, (k, value) ->
                    if (i > 0) sb.append(',')
                    writeString(k as String, sb)
                    sb.append(':')
                    write(value, sb)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                v.forEachIndexed { i, value ->
                    if (i > 0) sb.append(',')
                    write(value, sb)
                }
                sb.append(']')
            }
            else -> error("cannot write ${v::class}")
        }
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Any? = value().also {
            ws()
            require(i == s.length) { "trailing data at $i" }
        }

        private fun value(): Any? {
            ws()
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> num()
            }
        }

        private fun obj(): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws()
                val k = str()
                ws()
                expect(':')
                m[k] = value()
                ws()
                when (s[i++]) {
                    '}' -> return m
                    ',' -> {}
                    else -> error("bad json at ${i - 1}")
                }
            }
        }

        private fun arr(): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value()
                ws()
                when (s[i++]) {
                    ']' -> return l
                    ',' -> {}
                    else -> error("bad json at ${i - 1}")
                }
            }
        }

        private fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = i
            while (i < s.length && s[i] in "+-0123456789.eE") i++
            return s.substring(start, i).toDouble()
        }

        private fun literal(word: String, v: Any?): Any? {
            require(s.startsWith(word, i)) { "bad json at $i" }
            i += word.length
            return v
        }

        private fun expect(c: Char) {
            require(s[i] == c) { "expected '$c' at $i" }
            i++
        }

        private fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }
    }
}
