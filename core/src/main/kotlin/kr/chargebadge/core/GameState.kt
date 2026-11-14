package kr.chargebadge.core

import java.time.LocalDate

/** LIMITED (한정) is its own category, not a step above gold: only limited badges such as [WOLF] have it. */
enum class Tier { BRONZE, SILVER, GOLD, LIMITED }
data class Design(val artKey: String, val number: Int)
data class Badge(val design: Design, val tier: Tier)

/**
 * The regions that give badges, by art key, with their designs' names. Each design has a drawn medal for 동·은·금
 * in the app (assets/medals); a region without an entry gives no badge.
 */
val MEDAL_DESIGNS: Map<String, List<String>> = mapOf(
    "41280" to listOf("고양이"),
    "47130" to listOf("첨성대"),
    "47770" to listOf("대게"),
    "41500" to listOf("쌀"),
    "11710" to listOf("잠실 롯데타워"),
    "51730" to listOf("한우"),
    "52180" to listOf("단풍"),
    "44150" to listOf("공산성", "밤", "고마곰"),
)

fun designCount(artKey: String): Int = MEDAL_DESIGNS[artKey]?.size ?: 0

/**
 * 늑구, the limited badge: a single 한정 badge (no 동·은·금), given once for the first charge in any 대전 gu during
 * [WOLF_DAYS]. It sits in the 대전 중구 cell (where 오월드 is); 중구 has no entry in [MEDAL_DESIGNS], so ordinary charges
 * never give it.
 */
val WOLF = Badge(Design("30140", 0), Tier.LIMITED)
val WOLF_DAYS: ClosedRange<LocalDate> = LocalDate.of(2026, 12, 1)..LocalDate.of(2026, 12, 31)

/** 대전's gu all have art keys starting with its province code 30. */
fun wolfDue(state: GameState, artKey: String, date: LocalDate): Boolean =
    artKey.startsWith("30") && date in WOLF_DAYS && state.acquisitions.none { it.badge == WOLF }

fun designName(design: Design): String =
    if (design == WOLF.design) "늑구" else MEDAL_DESIGNS[design.artKey]?.getOrNull(design.number) ?: "도안 ${design.number + 1}"

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
    Friend("CB-1001", "송파 민지", mapOf(bronze("11710", 0) to 2, bronze("41500", 0) to 1, bronze("41280", 0) to 1)),
    Friend("CB-1002", "공주 현우", mapOf(bronze("44150", 0) to 2, bronze("44150", 1) to 1, bronze("47770", 0) to 1)),
    Friend("CB-1003", "경주 서연", mapOf(bronze("47130", 0) to 2, bronze("51730", 0) to 1, bronze("52180", 0) to 2)),
)
