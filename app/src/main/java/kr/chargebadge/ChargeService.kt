package kr.chargebadge

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.EvChargePortOpenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kr.chargebadge.core.ChargeDetector
import kr.chargebadge.core.DetectedCharge
import kr.chargebadge.core.IgnitionSnapshot
import kr.chargebadge.core.MATCH_RADIUS_M
import kr.chargebadge.core.bootWindowOpen
import kr.chargebadge.core.isSocJump
import kr.chargebadge.core.distanceMeters
import kotlin.concurrent.thread

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "boot receiver action=${intent.action}")
        context.startForegroundService(Intent(context, ChargeService::class.java))
    }
}

/**
 * A charge session starts on port open, switch on, or a SOC rise while parked; it ends on port close,
 * switch off or a SOC drop. A SOC jump is a reset, never a charge. Each service start is an ignition on,
 * and its evidence is judged only within BOOT_JUDGE_WINDOW_MS. Ignition off can kill the process
 * without onDestroy, so the current snapshot is written whenever SOC, location or session changes.
 */
class ChargeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val detector = ChargeDetector()
    private var vehicle: Vehicle? = null
    private var fused: FusedLocation? = null
    private var navi: NaviHelper? = null
    private var lastSoc: Int? = null
    private var portOpen: Boolean? = null
    private var switchOn: Boolean? = null
    private var gear: VehicleGear? = null
    // 화면이 실제 충전 여부를 보도록 Game에 둔다. 서비스가 다시 켜지면 onCreate에서 false로 시작
    private var charging: Boolean
        get() = Game.charging
        set(value) { Game.charging = value }
    private val startedAt = SystemClock.elapsedRealtime()

    /** Evidence from the last ignition off; kept on disk untouched until judged. */
    private var bootSnapshot: IgnitionSnapshot? = null
    private var written: IgnitionSnapshot? = null

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(percent: Int?) {
            if (percent != null) main.post { onSoc(percent) }
        }

        override fun onFailed(error: Exception) {
            Log.w(TAG, "soc listener failed", error)
        }
    }

    private val portListener = object : EvChargePortOpenListener {
        override fun onEvChargePortOpenUpdated(open: Boolean?) {
            if (open != null) main.post { onPort(open) }
        }

        override fun onFailed(error: Exception) {
            Log.w(TAG, "port listener failed", error)
        }
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            main.post { this@ChargeService.gear = gear }
        }

        override fun onFailed(error: Exception) {
            Log.w(TAG, "gear listener failed", error)
        }
    }

    private val fusedListener = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) {
            // Fallback for starting a live session only; on the emulator fused often reports a default US location.
            if (data != null) main.post {
                if (!Game.naviLocation) {
                    Game.lat = data.latitude
                    Game.lng = data.longitude
                }
            }
        }

        override fun onError(error: Exception) {
            Log.w(TAG, "fused location failed", error)
        }
    }

    override fun onCreate() {
        super.onCreate()
        charging = false
        val notification = Notification.Builder(this, "service")
            .setContentTitle("충전 감지 중")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        bootSnapshot = Game.state.ignitionSnapshot
        Log.i(TAG, "service started bootSnapshot=$bootSnapshot")
        try {
            vehicle = Vehicle(this).also {
                it.initialize()
                it.getEvBattery().registerEvBatteryStateOfCharge(socListener)
                it.getEvBattery().registerEvChargePortOpen(portListener)
                it.getGear().registerGearSelection(gearListener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "vehicle init failed", e)
        }
        try {
            fused = FusedLocation(this).also {
                it.initialize()
                it.registerFusedLocationCallback(fusedListener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fused init failed", e)
        }
        try {
            navi = NaviHelper(this).also { helper ->
                helper.addListener(object : NaviHelperEventListener {
                    override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
                        main.post { onNaviLocation(info.latitude, info.longitude) }
                    }
                })
                helper.initialize({ helper.getCurrentLocationInfo() }, { Log.w(TAG, "navi init failed", it) })
            }
        } catch (e: Exception) {
            Log.e(TAG, "navi init failed", e)
        }
        main.postDelayed(::tick, 2_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun tick() {
        navi?.getCurrentLocationInfo()
        try {
            val v = vehicle
            v?.getEvBattery()?.getEvBatteryStateOfCharge({ s -> if (s != null) main.post { onSoc(s) } }, { Log.w(TAG, "soc read failed", it) })
            v?.getEvBattery()?.getEvChargeSwitchState({ on -> if (on != null) main.post { onSwitch(on) } }, { Log.w(TAG, "switch read failed", it) })
            v?.getGear()?.getCurrentGearSelectionState({ g -> main.post { gear = g } }, { Log.w(TAG, "gear read failed", it) })
        } catch (e: Exception) {
            Log.w(TAG, "vehicle read failed", e)
        }
        main.postDelayed(::tick, 10_000)
    }

    private fun onNaviLocation(lat: Double, lng: Double) {
        Game.naviLocation = true
        Game.lat = lat
        Game.lng = lng
        judgeBoot()
        saveSnapshot()
    }

    private fun onPort(open: Boolean) {
        val was = portOpen
        portOpen = open
        if (open && was != true) startCharge("port")
        if (!open && was == true) stopCharge("port")
    }

    private fun onSwitch(on: Boolean) {
        val was = switchOn
        switchOn = on
        if (on && was != true) startCharge("switch")
        if (!on && was == true) stopCharge("switch")
    }

    private fun onSoc(soc: Int) {
        val prev = lastSoc
        lastSoc = soc
        Game.soc = soc
        if (prev != null && isSocJump(prev, soc)) {
            // 값이 다시 설정된 것이라 충전으로 치지 않는다. 시동 끔 증거는 판정 창 안에서만 본다
            Log.i(TAG, "soc jump $prev->$soc ignored")
            stopCharge("soc jump")
            judgeBoot()
            saveSnapshot()
            return
        }
        if (prev != null && soc < prev) stopCharge("soc drop")
        if (prev != null && soc > prev) {
            if (!charging && gear == VehicleGear.GEAR_PARK) startCharge("soc rise while parked", prev)
            detector.onSocChanged(soc, Game.nowMillis())?.let { onDetected(it, fromBoot = false) }
        }
        judgeBoot()
        saveSnapshot()
    }

    private fun startCharge(source: String, soc: Int? = lastSoc) {
        if (charging || soc == null) return
        val lat = Game.lat
        val lng = Game.lng
        if (lat == null || lng == null) {
            Log.w(TAG, "charge start by $source ignored: no location")
            return
        }
        charging = true
        detector.onChargeStart(soc, lat, lng, Game.nowMillis())
        Log.i(TAG, "charge start soc=$soc by=$source at $lat,$lng navi=${Game.naviLocation}")
        saveSnapshot()
    }

    private fun stopCharge(source: String) {
        if (!charging) return
        charging = false
        detector.onChargeStop()
        Log.i(TAG, "charge stop by=$source")
        saveSnapshot()
    }

    /** Boot judgment with navi location only. The evidence stays on disk until the judgment is final. */
    private fun judgeBoot() {
        val snap = bootSnapshot ?: return
        if (!bootWindowOpen(startedAt, SystemClock.elapsedRealtime())) {
            bootSnapshot = null
            written = null
            Log.i(TAG, "boot window closed, snapshot dropped $snap")
            return
        }
        val soc = lastSoc ?: return
        if (!Game.naviLocation) return
        val lat = Game.lat ?: return
        val lng = Game.lng ?: return
        val hit = detector.onBoot(snap, soc, lat, lng, Game.nowMillis())
        val moved = distanceMeters(snap.lat, snap.lng, lat, lng) > MATCH_RADIUS_M
        if (hit == null && !snap.counted && !moved && soc >= snap.soc) return // may still rise
        bootSnapshot = null
        Log.i(TAG, "boot judged snapshot=$snap soc=$soc hit=$hit")
        if (hit != null) onDetected(hit, fromBoot = true)
    }

    private fun saveSnapshot() {
        if (bootSnapshot != null) return
        val soc = lastSoc ?: return
        if (!Game.naviLocation) return
        val lat = Game.lat ?: return
        val lng = Game.lng ?: return
        val snap = detector.snapshot(soc, lat, lng, Game.nowMillis())
        if (written?.copy(timeMillis = 0) == snap.copy(timeMillis = 0)) return
        written = snap
        Game.update { it.copy(ignitionSnapshot = snap) }
    }

    private fun onDetected(charge: DetectedCharge, fromBoot: Boolean) {
        Log.i(TAG, "detected $charge boot=$fromBoot")
        // A live award here supersedes older boot evidence; the counted snapshot must reach disk.
        if (!fromBoot) bootSnapshot = null
        thread {
            Game.stationsReady.await()
            main.post {
                val station = Game.index.nearest(charge.lat, charge.lng)
                when {
                    station != null -> Game.award(station, clearSnapshot = fromBoot)
                    fromBoot -> Game.update { it.copy(ignitionSnapshot = null) }
                }
                if (station == null) Log.i(TAG, "unlisted station at ${charge.lat},${charge.lng}")
                written = null
                saveSnapshot()
            }
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        charging = false
        runCatching { vehicle?.getEvBattery()?.unregisterEvBatteryStateOfCharge(socListener) }
        runCatching { vehicle?.getEvBattery()?.unregisterEvChargePortOpen(portListener) }
        runCatching { vehicle?.getGear()?.unregisterGearSelection(gearListener) }
        runCatching { vehicle?.release() }
        runCatching { fused?.unregisterFusedLocationCallback(fusedListener) }
        runCatching { fused?.release() }
        runCatching { navi?.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
