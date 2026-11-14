package kr.chargebadge.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** The sample year's trip: art key and days after the first-met day. */
private val SAMPLE_TRIP = listOf(
    "11710" to 12, "41500" to 41, "47850" to 95, "47130" to 96, "47770" to 97,
    "26350" to 152, "51150" to 223, "50110" to 284,
)

/** The trip stop whose charge is the one gold. */
private const val SAMPLE_GOLD_ART = "47130"

/**
 * A year of charging for showing the app without real history: first met a year and two weeks before [today],
 * charges at [home] (else a 성남시 station) every 9 days until one design can become a silver, a trip across the
 * country with one gold, and in December a full tree. Badges come from the real engine except the one gold.
 */
fun sampleState(index: StationIndex, today: LocalDate, home: Station? = null, random: Random = Random(7)): GameState {
    val engine = BadgeEngine(random)
    val zone = ZoneId.of("Asia/Seoul")
    val firstMet = today.minusYears(1).minusDays(14)
    val stationOf = { art: String -> index.stations.firstOrNull { index.region(it.regionIndex).artKey == art } }
    val millis = { day: LocalDate -> day.atTime(14, 0).atZone(zone).toInstant().toEpochMilli() }
    val homeStation = home ?: stationOf("41130")
    var s = GameState(firstMet = firstMet)

    fun charge(st: Station, day: LocalDate) {
        if (index.region(st.regionIndex).artKey != SAMPLE_GOLD_ART) {
            s = engine.onValidCharge(s, st, day, millis(day), index).first
            return
        }
        val gold = Badge(Design(SAMPLE_GOLD_ART, 0), Tier.GOLD)
        s = s.copy(
            owned = s.owned.add(gold, 1),
            acquisitions = s.acquisitions + Acquisition(gold, Source.CHARGE, millis(day)),
            charges = s.charges + ChargeRecord(st, day, millis(day), gold),
        )
    }

    fun homeReady(): Boolean {
        val art = homeStation?.let { index.region(it.regionIndex).artKey } ?: return true
        return (0 until DESIGNS_PER_ART).any { s.count(Badge(Design(art, it), Tier.BRONZE)) >= SYNTH_COST }
    }

    // Charges go in by date, so the last record is the latest one, as in real use.
    val trip = SAMPLE_TRIP.mapNotNull { (art, d) -> stationOf(art)?.let { firstMet.plusDays(d.toLong()) to it } }.sortedBy { it.first }
    var next = 0
    var day = 3L
    while (day < 360) {
        val date = firstMet.plusDays(day)
        while (next < trip.size && trip[next].first <= date) charge(trip[next].second, trip[next++].first)
        if (homeStation != null && !homeReady()) charge(homeStation, date)
        day += 9
    }
    trip.drop(next).forEach { (d, st) -> charge(st, d) }

    s.owned.keys.firstOrNull { it.tier == Tier.BRONZE && engine.synthesizable(s, it.design) }?.let {
        s = engine.synthesize(s, it.design, millis(firstMet.plusDays(300)))
    }

    if (treeEditable(today)) {
        val picks = s.owned.keys.sortedWith(compareByDescending<Badge> { it.tier }.thenBy { it.design.artKey }).distinctBy { it.design.artKey }.take(TREE_SLOTS)
        picks.forEachIndexed { i, b -> s = setTreeSlot(s, today, i, b) }
    }
    return s
}
