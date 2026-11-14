package kr.chargebadge

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.chargebadge.core.Badge
import kr.chargebadge.core.Season
import kr.chargebadge.core.Tier
import kr.chargebadge.core.designName
import kr.chargebadge.core.season

sealed interface Screen {
    data object Collection : Screen
    data class Detail(val regionIndex: Int, val badge: Badge) : Screen
    data object Acquired : Screen
    data object Charging : Screen
    data object Friends : Screen
    data class Trade(val friendId: String) : Screen
    data object Tree : Screen
    data object Anniversary : Screen
}

class MainActivity : ComponentActivity() {
    private val stack = mutableStateListOf<Screen>(Screen.Collection)
    // Restricted until the car service says otherwise.
    private var restricted by mutableStateOf(true)
    private var car: Car? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS", "pleos.car.permission.FUSED_LOCATION"), 1)
        startForegroundService(Intent(this, ChargeService::class.java))
        watchUxRestrictions()
        Art.load(this)
        open(intent)
        setContent {
            ChargeTheme(season(Game.today())) {
                Box(Modifier.fillMaxSize().nightBackdrop()) {
                    Crossfade(restricted, label = "restricted") { if (it) Restricted() else Shell() }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    private fun open(intent: Intent?) {
        // ponytail: demo hook any app can send; remove before a real release.
        intent?.getStringExtra(EXTRA_SAMPLE)?.let {
            Game.showSample(it)
            stack.clear()
            stack += Screen.Collection
        }
        if (intent?.getStringExtra(EXTRA_OPEN) == "acquired") {
            stack.clear()
            stack += listOf(Screen.Collection, Screen.Acquired)
        }
    }

    private fun watchUxRestrictions() {
        // Car service can restart (and is slow right after boot); the listener reconnects each time.
        car = Car.createCar(this, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
            if (!ready) {
                restricted = true
                return@createCar
            }
            try {
                val manager = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                restricted = manager.currentCarUxRestrictions.isRequiresDistractionOptimization
                manager.registerListener { restricted = it.isRequiresDistractionOptimization }
            } catch (e: Exception) {
                restricted = true
                Log.w(TAG, "ux restrictions unavailable", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Game.update { Game.engine.expireTrades(it, Game.nowMillis()) }
    }

    override fun onDestroy() {
        car?.disconnect()
        super.onDestroy()
    }

    @Composable
    private fun Restricted() {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 56.dp), verticalAlignment = Alignment.CenterVertically) { Brand() }
            HorizontalDivider(color = LINE)
            Column(
                Modifier.fillMaxSize().padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                Eyebrow("주행 중")
                Text("정차 후 이용해 주세요", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = TXT, textAlign = TextAlign.Center)
                Sub("주행 중에는 화면을 잠시 닫아 둬요. 충전 확인은 계속돼요.")
            }
        }
    }

    @Composable
    private fun Shell() {
        val replace: (Screen) -> Unit = { s -> stack.removeAt(stack.lastIndex); stack += s }
        // Another badge tapped while a detail is open replaces it instead of stacking.
        val go: (Screen) -> Unit = { s -> if (s is Screen.Detail && stack.last() is Screen.Detail) replace(s) else stack += s }
        val back: () -> Unit = { stack.removeAt(stack.lastIndex) }
        BackHandler(enabled = stack.size > 1, onBack = back)
        val saved = rememberSaveableStateHolder()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 1200.dp
            Column(Modifier.fillMaxSize()) {
                TopBar(wide, stack.first(), onTab = { stack.clear(); stack += it })
                val s = stack.last()
                // Wide: the detail takes the left card of the collection, which stays composed underneath.
                val detail = (s as? Screen.Detail)?.takeIf { wide }
                val base = if (detail != null) stack.getOrElse(stack.lastIndex - 1) { Screen.Collection } else s
                // The depth only picks the direction; a wide detail keeps the collection in place.
                AnimatedContent(
                    base to stack.size,
                    Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = { navTransition(initialState, targetState) },
                    contentKey = { it.first.toString() },
                    label = "screen",
                ) { (screen, _) ->
                    saved.SaveableStateProvider(screen.toString()) {
                        when (screen) {
                            Screen.Collection -> CollectionScreen(go, detail, back)
                            is Screen.Detail -> DetailScreen(screen, back)
                            Screen.Acquired -> AcquiredScreen { replace(Screen.Charging) }
                            Screen.Charging -> ChargingScreen { stack.clear(); stack += Screen.Collection }
                            Screen.Friends -> FriendsScreen(go)
                            is Screen.Trade -> TradeScreen(screen.friendId, back)
                            Screen.Tree -> TreeScreen()
                            Screen.Anniversary -> AnniversaryScreen()
                        }
                    }
                }
            }
        }
    }
}

// Night showcase palette (mock A).
val BG0 = Color(0xFF070B14)
val BG1 = Color(0xFF0E1626)
val LINE = Color(0x14FFFFFF)
val TXT = Color(0xFFEEF2F8)
val SUB = Color(0xFF8A97AD)
val GOLD = Color(0xFFE8C66A)
/** Text on the accent gradient. */
val INK = Color(0xFF04121F)
/** Inner ring of medals and count chips. */
val NAVY = Color(0xFF0B2350)

private val GLOW = Color(0xFF18284A)
private val ACCENT_START = Color(0xFF2C9BD1)

/** Top-right navy glow over a vertical night gradient. */
fun Modifier.nightBackdrop() = drawBehind {
    drawRect(Brush.verticalGradient(listOf(BG1, BG0)))
    val c = Offset(size.width * 0.7f, size.height * 0.1f)
    val r = 1200.dp.toPx()
    scale(1f, 700f / 1200f, pivot = c) {
        drawCircle(Brush.radialGradient(0f to GLOW, 0.6f to Color.Transparent, center = c, radius = r), r, c)
    }
}

/** Frosted card: faint white gradient, hairline border. */
fun Modifier.glass(radius: Dp = 32.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape).background(Brush.verticalGradient(listOf(Color(0x12FFFFFF), Color(0x05FFFFFF)))).border(1.dp, LINE, shape)
}

/** Dark display case. */
fun Modifier.case(radius: Dp = 32.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF0F1A30), Color(0xFF0A1222)))).border(1.dp, LINE, shape)
}

private val TABS = listOf("도감" to Screen.Collection, "교환" to Screen.Friends, "1주년 기념" to Screen.Anniversary, "연말 회고" to Screen.Tree)

@Composable
private fun Brand() {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        buildAnnotatedString {
            append("충전 ")
            withStyle(SpanStyle(color = accent)) { append("수집첩") }
        },
        fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TXT,
    )
}

/**
 * Deeper screens come in from the right and going back reverses it; tabs move in tab order.
 * The old screen drifts a little and fades fast so the two never read as one.
 */
private fun navTransition(from: Pair<Screen, Int>, to: Pair<Screen, Int>): ContentTransform {
    val tabOrder = { s: Screen -> TABS.indexOfFirst { it.second == s } }
    val dir = when {
        to.second > from.second -> 1
        to.second < from.second -> -1
        tabOrder(from.first) >= 0 && tabOrder(to.first) >= 0 -> if (tabOrder(to.first) > tabOrder(from.first)) 1 else -1
        else -> 1
    }
    val slide = tween<IntOffset>(420, easing = Emphasized)
    return (slideInHorizontally(slide) { dir * it / 7 } + fadeIn(tween(260, delayMillis = 60))) togetherWith
        (slideOutHorizontally(slide) { -dir * it / 14 } + fadeOut(tween(150)))
}

@Composable
private fun TopBar(wide: Boolean, root: Screen, onTab: (Screen) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    // Underline slots of each tab in root coordinates; one accent bar slides between them.
    val slots = remember { mutableStateMapOf<Screen, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val target = slots[root]
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        if (right.value == 0f) {
            left.snapTo(t.left)
            right.snapTo(t.right)
            return@LaunchedEffect
        }
        // The leading edge is stiffer, so the bar stretches toward the new tab and then catches up.
        val toRight = t.left > left.value
        launch { left.animateTo(t.left, spring(dampingRatio = 0.85f, stiffness = if (toRight) 260f else 900f)) }
        launch { right.animateTo(t.right, spring(dampingRatio = 0.85f, stiffness = if (toRight) 900f else 260f)) }
    }
    val status: @Composable RowScope.() -> Unit = {
        Text(
            "P · 정차 중",
            Modifier.clip(CircleShape).background(accent.copy(alpha = 0.12f)).padding(horizontal = 18.dp, vertical = 10.dp),
            fontSize = 20.sp, fontWeight = FontWeight.Medium, color = accent,
        )
    }
    val tab: @Composable (String, Screen) -> Unit = { label, s -> Tab(label, root == s, { slots[s] = it }) { onTab(s) } }
    Column(
        Modifier.onGloballyPositioned { origin = it.positionInRoot() }.drawWithContent {
            drawContent()
            val t = target ?: return@drawWithContent
            val h = 4.dp.toPx()
            drawRoundRect(accent, Offset(left.value - origin.x, t.bottom - origin.y - h), Size(right.value - left.value, h), CornerRadius(h / 2))
        },
    ) {
        if (wide) {
            Row(Modifier.fillMaxWidth().height(112.dp).padding(start = 56.dp, end = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Brand()
                Row(Modifier.padding(start = 96.dp).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    TABS.forEach { (label, s) -> tab(label, s) }
                }
                Spacer(Modifier.weight(1f))
                status()
            }
        } else {
            Row(Modifier.fillMaxWidth().height(88.dp).padding(start = 32.dp, end = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Brand()
                Spacer(Modifier.weight(1f))
                status()
            }
            Row(Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 12.dp)) {
                TABS.forEach { (label, s) -> Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { tab(label, s) } }
            }
        }
        HorizontalDivider(color = LINE)
    }
}

/** Tab label; [onSlot] reports where its underline goes, the top bar draws it. */
@Composable
private fun Tab(label: String, active: Boolean, onSlot: (Rect) -> Unit, onClick: () -> Unit) {
    val color by animateColorAsState(if (active) TXT else SUB, tween(250), label = "tab")
    Column(
        Modifier.fillMaxHeight().press(role = Role.Tab, onClick = onClick).padding(horizontal = 12.dp).width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(label, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.fillMaxWidth().height(4.dp).onGloballyPositioned { onSlot(it.boundsInRoot()) })
    }
}

@Composable
fun ChargeTheme(season: Season, content: @Composable () -> Unit) {
    // One night theme; the season only nudges the accent hue.
    val accent = when (season) {
        Season.SPRING -> Color(0xFF4CC0DC)
        Season.SUMMER -> Color(0xFF3FB6E8)
        Season.AUTUMN -> Color(0xFF3FB6E8)
        Season.WINTER -> Color(0xFF62C2F0)
    }
    val t = Typography()
    val typography = t.copy(
        bodyLarge = t.bodyLarge.copy(fontFamily = TextFont, fontSize = 24.sp, lineHeight = 34.sp),
        bodyMedium = t.bodyMedium.copy(fontFamily = TextFont, fontSize = 24.sp, lineHeight = 34.sp),
        bodySmall = t.bodySmall.copy(fontFamily = TextFont, fontSize = 20.sp, lineHeight = 28.sp),
        labelLarge = t.labelLarge.copy(fontFamily = TextFont, fontSize = 24.sp),
    )
    val colors = darkColorScheme(
        primary = accent,
        onPrimary = INK,
        background = BG1,
        onBackground = TXT,
        surface = Color(0xFF0F1A30),
        onSurface = TXT,
        onSurfaceVariant = SUB,
        outline = Color(0x2EFFFFFF),
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

/** Primary action: accent gradient, dark bold label. */
@Composable
fun Btn(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier.heightIn(min = 80.dp).press(enabled, onClick = onClick).clip(shape)
            .background(if (enabled) Brush.horizontalGradient(listOf(ACCENT_START, accent)) else SolidColor(Color(0x14FFFFFF)))
            .padding(horizontal = 36.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (enabled) INK else SUB, textAlign = TextAlign.Center) }
}

/** Secondary action: hairline outline, light label. */
@Composable
fun OutlineBtn(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.heightIn(min = 76.dp).press(enabled, onClick = onClick).clip(shape).background(Color(0x0AFFFFFF)).border(2.dp, Color(0x2EFFFFFF), shape)
            .padding(horizontal = 32.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = if (enabled) TXT else SUB.copy(alpha = 0.6f), textAlign = TextAlign.Center) }
}

/** Filter pill: the visible pill is about 56dp, the touch area 76dp. Active is light on dark. */
@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) TXT else TXT.copy(alpha = 0f), tween(220), label = "pill")
    val fg by animateColorAsState(if (selected) Color(0xFF0A1222) else SUB, tween(220), label = "pillText")
    Box(modifier.heightIn(min = 76.dp).press(role = Role.Tab, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.heightIn(min = 56.dp).widthIn(min = 84.dp).clip(CircleShape)
                .background(bg)
                .border(1.dp, Color(0x24FFFFFF), CircleShape)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, fontSize = 20.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = fg, maxLines = 1)
        }
    }
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier) = Text(text, modifier, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, color = TXT)

@Composable
fun Heading(text: String, modifier: Modifier = Modifier) = Text(text, modifier, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = TXT)

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary)

@Composable
fun Sub(text: String, modifier: Modifier = Modifier) = Text(text, modifier, fontSize = 24.sp, lineHeight = 34.sp, color = SUB)

@Composable
fun Note(text: String, modifier: Modifier = Modifier) = Text(text, modifier, fontSize = 20.sp, lineHeight = 28.sp, color = SUB)

/** Title, optional subtitle, and a close button on the right. */
@Composable
fun Header(title: String, sub: String? = null, onClose: (() -> Unit)? = null, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Title(title)
            sub?.let { Sub(it) }
        }
        action()
        onClose?.let { CloseBtn(it) }
    }
}

@Composable
fun CloseBtn(onClick: () -> Unit) {
    Box(Modifier.size(76.dp).press(onClick = onClick).clip(CircleShape).background(Color(0x0FFFFFFF)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(26.dp)) {
            val w = 3.dp.toPx()
            drawLine(TXT, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
            drawLine(TXT, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
        }
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.glass(28.dp).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

/** Status card in the style of the state screens: title, message, one action. */
@Composable
fun StateCard(title: String, body: String, action: String? = null, modifier: Modifier = Modifier, onAction: () -> Unit = {}) {
    GlassCard(modifier.widthIn(max = 760.dp)) {
        Heading(title)
        Sub(body)
        action?.let {
            Box(Modifier.heightIn(min = 76.dp).press(onClick = onAction), contentAlignment = Alignment.CenterStart) {
                Text(it, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Each new message pops up and leaves on its own after a few seconds. */
@Composable
fun Toast(msg: String, modifier: Modifier = Modifier) {
    val shown = remember(msg) { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(msg) {
        delay(2800)
        shown.targetState = false
    }
    AnimatedVisibility(
        shown, modifier,
        enter = fadeIn(tween(180)) + slideInVertically(tween(380, easing = Emphasized)) { it } + scaleIn(tween(380, easing = Emphasized), initialScale = 0.9f),
        exit = fadeOut(tween(220)) + slideOutVertically(tween(260)) { it / 2 },
        label = "toast",
    ) {
        Text(
            msg,
            Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 28.dp, vertical = 18.dp),
            color = INK, fontSize = 24.sp, fontWeight = FontWeight.Medium,
        )
    }
}

fun tierLabel(t: Tier) = when (t) {
    Tier.BRONZE -> "동"
    Tier.SILVER -> "은"
    Tier.GOLD -> "금"
    Tier.LIMITED -> "한정"
}

/** 동/은/금 chip: the tier is always spelled out, never shown by color alone. */
@Composable
fun TierTag(t: Tier) {
    val (bg, fg) = when (t) {
        Tier.BRONZE -> Color(0xFF5A3A26) to Color(0xFFF2C29F)
        Tier.SILVER -> Color(0xFF3A4654) to Color(0xFFE8EEF4)
        Tier.GOLD -> Color(0xFF5C4614) to Color(0xFFFFE08A)
        Tier.LIMITED -> Color(0xFF1E3A66) to Color(0xFFBFDBFF)
    }
    Text(tierLabel(t), Modifier.clip(CircleShape).background(bg).padding(horizontal = 12.dp, vertical = 1.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = fg)
}

/**
 * Medal disc [width] wide; its ring tab is drawn above the box, so [loopSpace] reserves that room.
 * [owned] false draws the empty "?" slot.
 */
@Composable
fun Medal(badge: Badge, width: Dp, owned: Boolean = true, modifier: Modifier = Modifier, loopSpace: Boolean = true) {
    val m = if (loopSpace) modifier.padding(top = width * (MEDAL_H - 1)) else modifier
    if (!owned) return Box(m.size(width), contentAlignment = Alignment.Center) { Unknown(width * 0.82f) }
    Canvas(m.size(width), contentDescription = badgeName(Game.index, badge)) { drawMedalCached(badge, size.width) }
}

/** Dashed circle with a question mark: a badge not collected yet. */
@Composable
fun Unknown(size: Dp) {
    Box(
        Modifier.size(size).drawBehind {
            val w = 3.dp.toPx()
            drawCircle(Color(0x1FFFFFFF), this.size.minDimension / 2 - w, style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx()))))
        },
        contentAlignment = Alignment.Center,
    ) { Text("?", fontSize = (size.value * 0.3f).sp, color = Color(0x40FFFFFF), fontWeight = FontWeight.Medium) }
}

/** Recessed round slot a medal sits in. */
@Composable
fun Well(size: Dp, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).drawBehind {
            val r = this.size.minDimension / 2
            drawCircle(Brush.radialGradient(0f to Color(0xFF16233F), 0.7f to Color(0xFF0A1120), center = Offset(center.x, center.y - r * 0.2f), radius = r), r)
            // inset shadow from the top edge, faint light on the bottom lip
            drawCircle(Brush.radialGradient(0.72f to Color.Transparent, 1f to Color(0xB3000000), center = Offset(center.x, center.y + r * 0.12f), radius = r * 1.12f), r)
            drawArc(Color(0x0DFFFFFF), 30f, 120f, false, Offset(center.x - r + 1f, center.y - r + 1f), androidx.compose.ui.geometry.Size(r * 2 - 2f, r * 2 - 2f), style = Stroke(2.dp.toPx()))
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Showcase slot: medal in a well with the count at its bottom left, then "지역 · 도안". */
@Composable
fun BadgeSlot(
    badge: Badge,
    count: Int,
    owned: Boolean = count > 0,
    well: Dp = 166.dp,
    selected: Boolean = false,
    enabled: Boolean = true,
    note: String? = null,
    /** Unowned slots stand for a whole design unless this asks for the tier chip too. */
    lockedTier: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(26.dp)
    val accent = MaterialTheme.colorScheme.primary
    val ring by animateColorAsState(if (selected) accent else accent.copy(alpha = 0f), tween(220), label = "slot")
    Column(
        (if (onClick != null) Modifier.press(enabled, onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(ring.copy(alpha = ring.alpha * 0.08f)).border(3.dp, ring, shape)
            .padding(start = 6.dp, end = 6.dp, top = 22.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Well(well) { Medal(badge, well * 0.89f, owned, loopSpace = false) }
            if (owned && count > 0) Text(
                "×$count",
                Modifier.align(Alignment.BottomStart).offset(x = 4.dp, y = (-4).dp)
                    .clip(CircleShape).background(Color(0xFF0F1A30)).padding(3.dp)
                    .clip(CircleShape).background(NAVY).padding(horizontal = 10.dp, vertical = 1.dp),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = NumFont, color = Color.White,
            )
        }
        Spacer(Modifier.height(16.dp))
        // Slot labels are narrow: "잠실 롯데타워" shows as "롯데타워" (as in the mock), but "도안 2" stays whole.
        val design = designName(badge.design)
        Text(
            "${shortArt(Game.index, badge.design.artKey)} · ${design.substringAfterLast(' ').takeIf { it.length > 1 } ?: design}",
            fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (owned) TXT else SUB,
        )
        // The count is on the medal; this line is only for a region's 동·은·금 slots, empty slots and trade notes.
        val text = note ?: if (owned) null else "아직 없음"
        if (lockedTier || text != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (lockedTier) TierTag(badge.tier)
                if (text != null) Text(text, fontSize = 20.sp, color = SUB, maxLines = 1)
            }
        }
    }
}
