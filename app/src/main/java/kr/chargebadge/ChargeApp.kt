package kr.chargebadge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kr.chargebadge.core.BadgeEngine
import kr.chargebadge.core.ChargeOutcome
import kr.chargebadge.core.GameState
import kr.chargebadge.core.GameStateJson
import kr.chargebadge.core.KoreaMap
import kr.chargebadge.core.Station
import kr.chargebadge.core.StationCsv
import kr.chargebadge.core.StationIndex
import kr.chargebadge.core.sampleState
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.random.Random

const val TAG = "CHARGEBADGE"
const val EXTRA_OPEN = "open"
const val EXTRA_SAMPLE = "sample"
const val EXTRA_HERE = "here"

class ChargeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Game.init(this)
    }
}

/** Process-wide state. Mutate only on the main thread (Compose reads it). */
object Game {
    private lateinit var app: Context
    private lateinit var file: AtomicFile
    val engine = BadgeEngine(Random.Default)
    val stationsReady = CountDownLatch(1)

    /** Regions are there from the start; stations (for matching) arrive when [stationsLoaded]. */
    var index by mutableStateOf(StationIndex(emptyList(), emptyList()))
        private set
    var stationsLoaded by mutableStateOf(false)
        private set
    /** Boundaries for the anniversary map; loaded after the stations. */
    var koreaMap by mutableStateOf<KoreaMap?>(null)
        private set
    var state by mutableStateOf(GameState())
        private set

    // Live readings from ChargeService, shown on screens. lat/lng come from navi when [naviLocation].
    var soc by mutableStateOf<Int?>(null)
    var charging by mutableStateOf(false)
    var lat by mutableStateOf<Double?>(null)
    var lng by mutableStateOf<Double?>(null)
    var naviLocation = false

    /** Set only by [pinHere]; while true, ChargeService leaves [lat]/[lng] alone. */
    var locationPinned = false
        private set

    private fun regionLines(lines: (Sequence<String>) -> StationIndex) =
        app.assets.open("regions.csv").bufferedReader().useLines(lines)

    fun init(context: Context) {
        app = context.applicationContext
        file = AtomicFile(File(app.filesDir, "state.json"))
        state = load()
        shownDay = prefs().getString("day", null)?.let(LocalDate::parse)
        prefs().getString("here", null)?.let(::pinHere)
        // The day the app was installed is the day the driver met the car.
        if (state.firstMet == null) update { it.copy(firstMet = installDay()) }
        index = regionLines { StationCsv.load(emptySequence(), it) }
        update { engine.expireTrades(it, nowMillis()) }
        thread(name = "stations") {
            // 화면이 먼저 그려지도록 UI 스레드보다 낮은 우선순위로 읽는다
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            val t0 = SystemClock.elapsedRealtime()
            val loaded = app.assets.open("stations_min.csv").bufferedReader().useLines { s -> regionLines { r -> StationCsv.load(s, r) } }
            Log.i(TAG, "stations loaded n=${loaded.stations.size} regions=${loaded.regions.size} ms=${SystemClock.elapsedRealtime() - t0}")
            Handler(app.mainLooper).post {
                index = loaded
                stationsLoaded = true
                stationsReady.countDown()
            }
            val map = app.assets.open("korea_map.txt").bufferedReader().useLines { KoreaMap.parse(it) }
            Handler(app.mainLooper).post { koreaMap = map }
        }
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("service", "충전 감지", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("badge", "새 배지 알림", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun installDay(): LocalDate =
        Instant.ofEpochMilli(app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun load(): GameState {
        if (!file.baseFile.exists()) return GameState()
        return try {
            GameStateJson.decode(String(file.readFully()))
        } catch (e: Exception) {
            // Keep the unreadable file instead of overwriting the only copy.
            Log.e(TAG, "state decode failed", e)
            file.baseFile.renameTo(File(app.filesDir, "state.bad.${System.currentTimeMillis()}.json"))
            GameState()
        }
    }

    /** Written to disk first; memory changes only if the write succeeded. */
    fun update(change: (GameState) -> GameState) {
        val next = change(state)
        val out = file.startWrite()
        try {
            out.write(GameStateJson.encode(next).toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
        state = next
    }

    /** Set only by [showSample]; kept across restarts so the service judges with the same day. */
    private var shownDay by mutableStateOf<LocalDate?>(null)

    private fun prefs() = app.getSharedPreferences("show", Context.MODE_PRIVATE)

    fun today(): LocalDate = shownDay ?: LocalDate.now()

    fun nowMillis(): Long {
        val d = shownDay ?: return System.currentTimeMillis()
        return d.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * For showing the app (adb only, no screen): a date in [arg] becomes "today" and the state is replaced
     * by [sampleState]; "off" brings back the real date and keeps the state.
     */
    fun showSample(arg: String) {
        if (arg == "off") return changeShownDay(null)
        val day = runCatching { LocalDate.parse(arg) }.getOrElse {
            Log.w(TAG, "sample: bad date $arg")
            return
        }
        changeShownDay(day)
        thread(name = "sample") {
            stationsReady.await()
            Handler(app.mainLooper).post {
                update { sampleState(index, day) }
                Log.i(TAG, "sample loaded day=$day charges=${state.charges.size}")
            }
        }
    }

    /**
     * For showing the app (adb only): "lat,lng" becomes the car's position, kept across restarts, because the
     * emulator's navigation stays at one fixed place; "off" goes back to the navigation's position.
     */
    fun pinHere(arg: String) {
        if (arg == "off") {
            locationPinned = false
            prefs().edit().remove("here").apply()
            return
        }
        val p = arg.split(',').mapNotNull { it.trim().toDoubleOrNull() }
        if (p.size != 2) return Log.w(TAG, "here: bad lat,lng $arg").let {}
        locationPinned = true
        lat = p[0]
        lng = p[1]
        prefs().edit().putString("here", arg).apply()
        Log.i(TAG, "here pinned ${p[0]},${p[1]}")
    }

    private fun changeShownDay(day: LocalDate?) {
        shownDay = day
        prefs().edit().putString("day", day?.toString()).apply()
    }

    /**
     * Valid charge at a station: award, save, notify. [clearSnapshot] = this is the boot judgment,
     * so its evidence is dropped in the same write.
     */
    fun award(station: Station, clearSnapshot: Boolean = false) {
        val (next, outcome) = engine.onValidCharge(state, station, today(), nowMillis(), index)
        update { if (clearSnapshot) next.copy(ignitionSnapshot = null) else next }
        Log.i(TAG, "valid charge station=${station.id} outcome=$outcome")
        if (outcome is ChargeOutcome.Awarded) notifyBadge(badgeName(index, outcome.badge))
    }

    private fun notifyBadge(badge: String) {
        val open = PendingIntent.getActivity(
            app, 1,
            Intent(app, MainActivity::class.java).putExtra(EXTRA_OPEN, "acquired").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = android.app.Notification.Builder(app, "badge")
            .setContentTitle("충전이 확인됐어요")
            .setContentText("$badge 배지를 받았어요")
            .setSmallIcon(android.R.drawable.star_on)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        app.getSystemService(NotificationManager::class.java).notify(2, n)
    }
}
