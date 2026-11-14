package kr.chargebadge.core

import java.time.LocalDate
import kotlin.random.Random

sealed interface ChargeOutcome {
    data class Awarded(val badge: Badge) : ChargeOutcome
    data object DailyLimit : ChargeOutcome
}

class BadgeEngine(private val random: Random) {

    fun onValidCharge(
        state: GameState,
        station: Station,
        date: LocalDate,
        nowMillis: Long,
        index: StationIndex,
    ): Pair<GameState, ChargeOutcome> {
        if (state.charges.any { it.date == date && it.station.id == station.id }) return state to ChargeOutcome.DailyLimit
        val tier = if (random.nextDouble() < GOLD_CHANCE) Tier.GOLD else Tier.BRONZE
        val badge = Badge(Design(index.region(station.regionIndex).artKey, random.nextInt(DESIGNS_PER_ART)), tier)
        val next = state.copy(
            owned = state.owned.add(badge, 1),
            acquisitions = state.acquisitions + Acquisition(badge, Source.CHARGE, nowMillis),
            charges = state.charges + ChargeRecord(station, date, nowMillis, badge),
        )
        return next to ChargeOutcome.Awarded(badge)
    }

    fun synthesizable(state: GameState, design: Design): Boolean =
        state.available(Badge(design, Tier.BRONZE)) >= SYNTH_COST

    fun synthesize(state: GameState, design: Design, nowMillis: Long = System.currentTimeMillis()): GameState {
        require(synthesizable(state, design)) { "not enough unlocked bronze for $design" }
        val silver = Badge(design, Tier.SILVER)
        return state.copy(
            owned = state.owned.add(Badge(design, Tier.BRONZE), -SYNTH_COST).add(silver, 1),
            acquisitions = state.acquisitions + Acquisition(silver, Source.SYNTHESIS, nowMillis),
        )
    }

    /** Highest tier owned for the cell's art key; general-gu cells of one city share it. */
    fun cellTier(state: GameState, index: StationIndex, regionIndex: Int): Tier? {
        val art = index.region(regionIndex).artKey
        return state.owned.keys.filter { it.design.artKey == art }.maxOfOrNull { it.tier }
    }

    fun visited(state: GameState, regionIndex: Int): Boolean =
        state.charges.any { it.station.regionIndex == regionIndex }

    /** Never changed automatically, even if the badge is later traded away. */
    fun setRepresentative(state: GameState, regionIndex: Int, badge: Badge): GameState {
        require(state.count(badge) > 0) { "not owned: $badge" }
        return state.copy(representatives = state.representatives + (regionIndex to badge))
    }

    fun proposeTrade(state: GameState, friendId: String, give: Badge, want: Badge, nowMillis: Long): Pair<GameState, TradeOffer> {
        require(give.tier == Tier.BRONZE && want.tier == Tier.BRONZE) { "bronze only" }
        require(give != want) { "same badge" }
        require(state.available(give) > 0) { "no unlocked $give" }
        val friend = state.friends.first { it.id == friendId }
        val promised = state.trades.count { it.friendId == friendId && it.want == want }
        require((friend.badges[want] ?: 0) > promised) { "$friendId has no $want to trade" }
        val offer = TradeOffer(state.nextTradeId, friendId, give, want, nowMillis)
        return state.copy(trades = state.trades + offer, nextTradeId = state.nextTradeId + 1) to offer
    }

    /** Unknown, cancelled or expired offer: nothing moves. */
    fun acceptTrade(state: GameState, offerId: Int, nowMillis: Long): GameState {
        val s = expireTrades(state, nowMillis)
        val offer = s.trades.find { it.id == offerId } ?: return s
        val friends = s.friends.map {
            if (it.id == offer.friendId) it.copy(badges = it.badges.add(offer.want, -1).add(offer.give, 1)) else it
        }
        return s.copy(
            owned = s.owned.add(offer.give, -1).add(offer.want, 1),
            acquisitions = s.acquisitions + Acquisition(offer.want, Source.TRADE, nowMillis, offer.friendId),
            friends = friends,
            trades = s.trades - offer,
        )
    }

    fun cancelTrade(state: GameState, offerId: Int): GameState =
        state.copy(trades = state.trades.filterNot { it.id == offerId })

    fun expireTrades(state: GameState, nowMillis: Long): GameState =
        state.copy(trades = state.trades.filter { nowMillis - it.createdMillis < TRADE_EXPIRY_MS })
}
