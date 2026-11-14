package kr.chargebadge.core

/** Saved at ignition off. [counted] = the charge in progress was already awarded, so boot must not award it again. */
data class IgnitionSnapshot(val soc: Int, val lat: Double, val lng: Double, val timeMillis: Long, val counted: Boolean = false)

/** A valid charge. The app resolves the station with [StationIndex.nearest] on [lat]/[lng]; null there = unlisted, no badge. */
data class DetectedCharge(val lat: Double, val lng: Double, val startSoc: Int, val soc: Int, val timeMillis: Long)

class ChargeDetector {
    private var startSoc: Int? = null
    private var startLat = 0.0
    private var startLng = 0.0
    private var counted = false

    fun onChargeStart(soc: Int, lat: Double, lng: Double, nowMillis: Long) {
        startSoc = soc
        startLat = lat
        startLng = lng
        counted = false
    }

    fun onChargeStop() {
        startSoc = null
    }

    /** Non-null once per charge session, when SOC first reaches start + [MIN_SOC_GAIN]. */
    fun onSocChanged(soc: Int, nowMillis: Long): DetectedCharge? {
        val from = startSoc ?: return null
        if (counted || soc - from < MIN_SOC_GAIN) return null
        counted = true
        return DetectedCharge(startLat, startLng, from, soc, nowMillis)
    }

    /** What [onIgnitionOff] would save, without ending the session; the app saves it continuously because ignition off may kill it unannounced. */
    fun snapshot(soc: Int, lat: Double, lng: Double, nowMillis: Long): IgnitionSnapshot {
        val from = startSoc
        return when {
            from == null -> IgnitionSnapshot(soc, lat, lng, nowMillis)
            counted -> IgnitionSnapshot(soc, lat, lng, nowMillis, counted = true)
            else -> IgnitionSnapshot(minOf(from, soc), lat, lng, nowMillis)
        }
    }

    fun onIgnitionOff(soc: Int, lat: Double, lng: Double, nowMillis: Long): IgnitionSnapshot =
        snapshot(soc, lat, lng, nowMillis).also { startSoc = null }

    fun onBoot(snapshot: IgnitionSnapshot?, soc: Int, lat: Double, lng: Double, nowMillis: Long): DetectedCharge? {
        if (snapshot == null || snapshot.counted) return null
        if (distanceMeters(snapshot.lat, snapshot.lng, lat, lng) > MATCH_RADIUS_M) return null
        if (soc - snapshot.soc < MIN_SOC_GAIN) return null
        return DetectedCharge(snapshot.lat, snapshot.lng, snapshot.soc, soc, nowMillis)
    }
}
