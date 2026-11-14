package kr.chargebadge.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChargeDetectorTest {
    private val lat = 37.5
    private val lng = 127.0

    @Test
    fun socJumpAndBootWindow() {
        assertTrue(!isSocJump(60, 61) && !isSocJump(60, 63))
        assertTrue(isSocJump(63, 100) && isSocJump(60, 64))
        assertTrue(bootWindowOpen(0, BOOT_JUDGE_WINDOW_MS))
        assertTrue(!bootWindowOpen(0, BOOT_JUDGE_WINDOW_MS + 1))
    }

    @Test
    fun awardsOncePerSessionAtPlusThree() {
        val d = ChargeDetector()
        d.onChargeStart(50, lat, lng, 0)
        assertNull(d.onSocChanged(52, 1))
        val hit = assertNotNull(d.onSocChanged(53, 2))
        assertEquals(DetectedCharge(lat, lng, 50, 53, 2), hit)
        assertNull(d.onSocChanged(60, 3))
        d.onChargeStart(60, lat, lng, 4)
        assertNotNull(d.onSocChanged(63, 5))
    }

    @Test
    fun ignoresRiseWithoutSession() {
        val d = ChargeDetector()
        assertNull(d.onSocChanged(90, 1))
        d.onChargeStart(50, lat, lng, 0)
        d.onChargeStop()
        assertNull(d.onSocChanged(70, 1))
    }

    @Test
    fun bootAfterIgnitionOffChecksPlaceAndGain() {
        val d = ChargeDetector()
        val snap = d.onIgnitionOff(50, lat, lng, 0)
        assertNull(d.onBoot(snap, 52, lat, lng, 1))
        assertNotNull(d.onBoot(snap, 53, lat + 0.0003, lng, 1)) // ~33 m
        assertNull(d.onBoot(snap, 53, lat + 0.0005, lng, 1)) // ~56 m
        assertNull(d.onBoot(snap, 80, lat + 0.0015, lng, 1)) // ~167 m
        assertNull(d.onBoot(null, 80, lat, lng, 1))
    }

    @Test
    fun ignitionOffMidSessionKeepsStartSocOrCountedFlag() {
        val d = ChargeDetector()
        d.onChargeStart(50, lat, lng, 0)
        val uncounted = d.onIgnitionOff(51, lat, lng, 1)
        assertEquals(50, uncounted.soc)
        assertNotNull(d.onBoot(uncounted, 53, lat, lng, 2))

        d.onChargeStart(50, lat, lng, 3)
        assertNotNull(d.onSocChanged(53, 4))
        val counted = d.onIgnitionOff(54, lat, lng, 5)
        assertTrue(counted.counted)
        assertNull(d.onBoot(counted, 90, lat, lng, 6))
    }

    @Test
    fun snapshotDoesNotEndSession() {
        val d = ChargeDetector()
        d.onChargeStart(50, lat, lng, 0)
        assertEquals(IgnitionSnapshot(50, lat, lng, 1), d.snapshot(52, lat, lng, 1))
        assertNotNull(d.onSocChanged(53, 2))
        assertTrue(d.snapshot(54, lat, lng, 3).counted)
    }
}
