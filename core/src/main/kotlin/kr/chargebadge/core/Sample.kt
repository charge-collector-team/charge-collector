package kr.chargebadge.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * The sample year's trip: art key, days after the first-met day, design. Designs are the ones with drawn medal art,
 * so the sample shows them; 송파 is visited ten times so a silver is made by the real rule. 공주 gets two of its three
 * designs, so one (고마곰) shows as not got yet.
 */
private val SAMPLE_TRIP = listOf(
    Triple("41280", 25, 0), Triple("41500", 41, 0), Triple("51730", 63, 0), Triple("47130", 96, 0),
    Triple("47770", 97, 0), Triple("44150", 118, 0), Triple("52180", 140, 0), Triple("44150", 198, 1),
) + (0 until SYNTH_COST).map { Triple("11710", 12 + it * 30, 0) }

/** The trip stop whose charge is the one gold. */
private const val SAMPLE_GOLD_ART = "47130"

/**
 * A year of charging for showing the app without real history: first met a year and two weeks before [today],
 * a trip through the regions with medals and one gold, silvers synthesized by the engine, 늑구 in December, and in
 * December a full tree. Trip stops get the listed design so each drawn medal shows.
 */
fun sampleState(index: StationIndex, today: LocalDate, random: Random = Random(7)): GameState {
    val engine = BadgeEngine(random)
    val zone = ZoneId.of("Asia/Seoul")
    val firstMet = today.minusYears(1).minusDays(14)
    val stationOf = { art: String -> index.stations.firstOrNull { index.region(it.regionIndex).artKey == art } }
    val millis = { day: LocalDate -> day.atTime(14, 0).atZone(zone).toInstant().toEpochMilli() }
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

    // Charges go in by date, so the last record is the latest one, as in real use.
    SAMPLE_TRIP.mapNotNull { (art, d, design) -> stationOf(art)?.let { Triple(firstMet.plusDays(d.toLong()), it, design) } }
        .sortedBy { it.first }
        .forEach { (d, st, design) -> charge(st, d, design) }

    s.owned.keys.filter { it.tier == Tier.BRONZE && engine.synthesizable(s, it.design) }.forEach {
        s = engine.synthesize(s, it.design, millis(firstMet.plusDays(300)))
    }

    // 늑구 on the first day of its period, when that falls before [today]: a 대전 charge through the real rule.
    val daejeon = index.stations.firstOrNull { index.region(it.regionIndex).artKey.startsWith("30") }
    if (daejeon != null && WOLF_DAYS.start < today && WOLF_DAYS.start > firstMet) charge(daejeon, WOLF_DAYS.start, null)

    if (treeEditable(today)) {
        val picks = s.owned.keys.sortedWith(compareByDescending<Badge> { it.tier }.thenBy { it.design.artKey }).distinctBy { it.design.artKey }.take(TREE_SLOTS)
        picks.forEachIndexed { i, b -> s = setTreeSlot(s, today, i, b) }
    }
    return s
}
