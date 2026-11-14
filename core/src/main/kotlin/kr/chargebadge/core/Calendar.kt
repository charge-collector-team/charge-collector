package kr.chargebadge.core

import java.time.LocalDate

enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

fun season(date: LocalDate): Season = when (date.monthValue) {
    in 3..5 -> Season.SPRING
    in 6..8 -> Season.SUMMER
    in 9..11 -> Season.AUTUMN
    else -> Season.WINTER
}

fun treeEditable(today: LocalDate): Boolean = today.monthValue == 12

fun treeSlots(state: GameState, year: Int): List<Badge?> = state.trees[year] ?: List(TREE_SLOTS) { null }

/** Any owned badge, in at most as many slots as copies owned; [badge] null empties the slot. */
fun setTreeSlot(state: GameState, today: LocalDate, slot: Int, badge: Badge?): GameState {
    require(treeEditable(today)) { "tree is editable in December only" }
    require(slot in 0 until TREE_SLOTS) { "slot $slot" }
    val slots = treeSlots(state, today.year).mapIndexed { i, b -> if (i == slot) badge else b }
    require(badge == null || slots.count { it == badge } <= state.count(badge)) { "not enough owned: $badge" }
    return state.copy(trees = state.trees + (today.year to slots))
}

fun anniversaryOpen(firstMet: LocalDate?, today: LocalDate): Boolean =
    firstMet != null && !today.isBefore(firstMet.plusYears(1))

/** One point per station in first-visit order. Trades never create charge records, so they never add points. */
fun anniversaryPoints(state: GameState): List<ChargeRecord> =
    state.charges.sortedBy { it.timeMillis }.distinctBy { it.station.id }

/** The most recent anniversary (first met + N years, N >= 1) on or before [today]. */
fun latestAnniversary(firstMet: LocalDate, today: LocalDate): LocalDate? {
    var n = today.year - firstMet.year.toLong()
    while (n >= 1 && firstMet.plusYears(n).isAfter(today)) n--
    return if (n >= 1) firstMet.plusYears(n) else null
}

/** Charges in the year before the latest anniversary, [anniversary - 1 year, anniversary). */
fun anniversaryCharges(state: GameState, today: LocalDate): List<ChargeRecord> {
    val end = state.firstMet?.let { latestAnniversary(it, today) } ?: return emptyList()
    val start = end.minusYears(1)
    return state.charges.filter { !it.date.isBefore(start) && it.date.isBefore(end) }
}

/** [anniversaryPoints] limited to [anniversaryCharges]. */
fun anniversaryPoints(state: GameState, today: LocalDate): List<ChargeRecord> =
    anniversaryPoints(state.copy(charges = anniversaryCharges(state, today)))
