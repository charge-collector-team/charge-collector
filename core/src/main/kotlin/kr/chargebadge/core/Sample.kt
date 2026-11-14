package kr.chargebadge.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * The sample year's trip: art key, days after the first-met day, design. Designs are the ones with drawn medal art,
 * so the sample shows them; 송파 is visited ten times so a silver is made by the real rule.
 */
private val SAMPLE_TRIP = listOf(
    Triple("41280", 25, 0), Triple("41500", 41, 0), Triple("51730", 63, 0), Triple("47130", 96, 0),
    Triple("47770", 97, 0), Triple("44150", 118, 0), Triple("52180", 140, 0), Triple("44150", 198, 1),
    Triple("44150", 268, 2),
) + (0 until SYNTH_COST).map { Triple("11710", 12 + it * 30, 0) }

/** The trip stop whose charge is the one gold. */
private const val SAMPLE_GOLD_ART = "47130"

/**
 * A year of charging for showing the app without real history: first met a year and two weeks before [today],
 * charges at [home] every 9 days until one design can become a silver (only if home gives badges), a trip through
 * the regions with medals and one gold, silvers synthesized by the engine, and in December a full tree. Home badges
 * come from the real engine; trip stops get the listed design so each drawn medal shows.
 */
fun sampleState(index: StationIndex, today: LocalDate, home: Station? = null, random: Random = Random(7)): GameState {
    val engine = BadgeEngine(random)
    val zone = ZoneId.of("Asia/Seoul")
    val firstMet = today.minusYears(1).minusDays(14)
    val stationOf = { art: String -> index.stations.firstOrNull { index.region(it.regionIndex).artKey == art } }
    val millis = { day: LocalDate -> day.atTime(14, 0).atZone(zone).toInstant().toEpochMilli() }
    val homeStation = home?.takeIf { designCount(index.region(it.regionIndex).artKey) > 0 }
    var s = GameState(firstMet = firstMet)

    fun charge(st: Station, day: LocalDate, design: Int?) {
        if (design == null) {
            s = engine.onValidCharge(s, st, day, millis(day), index).first
            return
        }
        val art = index.region(st.regionIndex).artKey
        val badge = Badge(Design(art, design), if (art == SAMPLE_GOLD_ART) Tier.GOLD else Tier.BRONZE)
        s = s.copy(
            owned = s.owned.add(badge, 1),
            acquisitions = s.acquisitions + Acquisition(badge, Source.CHARGE, millis(day)),
            charges = s.charges + ChargeRecord(st, day, millis(day), badge),
        )
    }

    fun homeReady(): Boolean {
        val art = homeStation?.let { index.region(it.regionIndex).artKey } ?: return true
        return (0 until designCount(art)).any { s.count(Badge(Design(art, it), Tier.BRONZE)) >= SYNTH_COST }
    }

    // Charges go in by date, so the last record is the latest one, as in real use.
    val trip = SAMPLE_TRIP.mapNotNull { (art, d, design) -> stationOf(art)?.let { Triple(firstMet.plusDays(d.toLong()), it, design) } }.sortedBy { it.first }
    var next = 0
    var day = 3L
    while (day < 360) {
        val date = firstMet.plusDays(day)
        while (next < trip.size && trip[next].first <= date) trip[next++].let { (d, st, design) -> charge(st, d, design) }
        if (homeStation != null && !homeReady()) charge(homeStation, date, null)
        day += 9
    }
    trip.drop(next).forEach { (d, st, design) -> charge(st, d, design) }

    s.owned.keys.filter { it.tier == Tier.BRONZE && engine.synthesizable(s, it.design) }.forEach {
        s = engine.synthesize(s, it.design, millis(firstMet.plusDays(300)))
    }

    if (treeEditable(today)) {
        val picks = s.owned.keys.sortedWith(compareByDescending<Badge> { it.tier }.thenBy { it.design.artKey }).distinctBy { it.design.artKey }.take(TREE_SLOTS)
        picks.forEachIndexed { i, b -> s = setTreeSlot(s, today, i, b) }
    }
    return s
}
