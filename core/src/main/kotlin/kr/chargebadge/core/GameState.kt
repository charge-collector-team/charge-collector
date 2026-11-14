package kr.chargebadge.core

import java.time.LocalDate

enum class Tier { BRONZE, SILVER, GOLD }
data class Design(val artKey: String, val number: Int)
data class Badge(val design: Design, val tier: Tier)

private val NAMED_DESIGNS = mapOf(
    "47850" to listOf("철교", "낙동강", "꿀참외"),
    "11710" to listOf("잠실 롯데타워"),
    "41500" to listOf("쌀"),
    "47130" to listOf("첨성대"),
    "47770" to listOf("대게"),
)

fun designName(design: Design): String = NAMED_DESIGNS[design.artKey]?.getOrNull(design.number) ?: "도안 ${design.number + 1}"

enum class Source { CHARGE, TRADE, SYNTHESIS }
data class Acquisition(val badge: Badge, val source: Source, val timeMillis: Long, val friendId: String? = null)
data class ChargeRecord(val station: Station, val date: LocalDate, val timeMillis: Long, val badge: Badge)
data class Friend(val id: String, val name: String, val badges: Map<Badge, Int>)
data class TradeOffer(val id: Int, val friendId: String, val give: Badge, val want: Badge, val createdMillis: Long)

data class GameState(
    val owned: Map<Badge, Int> = emptyMap(),
    val acquisitions: List<Acquisition> = emptyList(),
    val charges: List<ChargeRecord> = emptyList(),
    val representatives: Map<Int, Badge> = emptyMap(),
    val friends: List<Friend> = fakeFriends(),
    val trades: List<TradeOffer> = emptyList(),
    val nextTradeId: Int = 1,
    val trees: Map<Int, List<Badge?>> = emptyMap(),
    val firstMet: LocalDate? = null,
    val ignitionSnapshot: IgnitionSnapshot? = null,
) {
    fun count(badge: Badge): Int = owned[badge] ?: 0
    fun locked(badge: Badge): Int = trades.count { it.give == badge }
    fun available(badge: Badge): Int = count(badge) - locked(badge)
}

internal fun Map<Badge, Int>.add(badge: Badge, delta: Int): Map<Badge, Int> {
    val n = (this[badge] ?: 0) + delta
    check(n >= 0) { "negative count for $badge" }
    return if (n == 0) this - badge else this + (badge to n)
}

private fun bronze(artKey: String, number: Int) = Badge(Design(artKey, number), Tier.BRONZE)

fun fakeFriends(): List<Friend> = listOf(
    Friend("CB-1001", "송파 민지", mapOf(bronze("11710", 0) to 2, bronze("26350", 0) to 3, bronze("26350", 2) to 1)),
    Friend("CB-1002", "제주 현우", mapOf(bronze("47770", 0) to 1, bronze("41500", 0) to 1, bronze("50110", 0) to 2, bronze("50110", 1) to 2)),
    Friend("CB-1003", "칠곡 서연", mapOf(bronze("47130", 0) to 2, bronze("47850", 0) to 2, bronze("47850", 2) to 4)),
)
