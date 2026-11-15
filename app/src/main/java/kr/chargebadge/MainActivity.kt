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
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
        // ponytail: demo hooks any app can send; remove before a real release.
        intent?.getStringExtra(EXTRA_HERE)?.let(Game::pinHere)
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
                            Screen.Tree -> DisplaySurface { TreeScreen() }
                            Screen.Anniversary -> AnniversaryScreen()
                        }
                    }
                }
            }
        }
    }
}

// 승인된 Pleos 중립색: 화면 바탕과 메달 진열면의 역할을 분리한다.
val BG0 = Color(0xFFF7F8FA)
val BG1 = Color(0xFFF7F8FA)
val LINE: Color @Composable get() = MaterialTheme.colorScheme.outline
val TXT: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val SUB: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val GOLD = Color(0xFFB39459)
val INK = Color.White
val NAVY = Color(0xFF44464E)
/** The one accent, for state only (charging, route, selection): at least 3:1 on the white page and the black case. */
val HIGHLIGHT = Color(0xFF0091C8)

// 함수명은 기존 호출을 유지하며, 장식용 파란 조명을 평평한 바탕으로 바꾼다.
fun Modifier.nightBackdrop() = background(BG0)

// 기존 카드 크기와 배치는 유지하고, 불투명한 중립색 면과 테두리만 적용한다.
@Composable
fun Modifier.glass(radius: Dp = 32.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, LINE, shape)
}

// 검은 벨벳 진열면: 기존 패딩을 바꾸지 않고 프레임과 음영을 안쪽에 그린다.
fun Modifier.case(radius: Dp = 32.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return shadow(8.dp, shape).clip(shape).background(Color(0xFF151618)).drawWithContent {
        drawContent()
        val edge = 9.dp.toPx()
        drawRoundRect(Color(0xFF35363A), cornerRadius = CornerRadius(radius.toPx()), style = Stroke(edge))
        drawLine(Color(0xFF66676C), Offset(radius.toPx(), edge / 2), Offset(size.width - radius.toPx(), edge / 2), 1.dp.toPx())
    }
}

// 어두운 콘텐츠 영역에만 밝은 글자와 조작부를 공급한다. 앱의 상태·동작은 바꾸지 않는다.
@Composable
fun DisplaySurface(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme.copy(
        primary = Color.White, onPrimary = Color(0xFF1D1E21),
        surface = Color(0xFF26272B), surfaceVariant = Color(0xFF303137),
        onSurface = Color(0xFFF7F8FA),
        onSurfaceVariant = Color(0xFFB7B8BF), outline = Color(0xFF44464E),
    )
    MaterialTheme(colorScheme = colors, content = content)
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
    // 계절 표현은 콘텐츠에 남기고 공통 조작부는 같은 중립색을 사용한다.
    val accent = Color(0xFF44464E)
    val t = Typography()
    val typography = t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = TextFont),
        displayMedium = t.displayMedium.copy(fontFamily = TextFont),
        displaySmall = t.displaySmall.copy(fontFamily = TextFont),
        headlineLarge = t.headlineLarge.copy(fontFamily = TextFont),
        headlineMedium = t.headlineMedium.copy(fontFamily = TextFont),
        headlineSmall = t.headlineSmall.copy(fontFamily = TextFont),
        titleLarge = t.titleLarge.copy(fontFamily = TextFont),
        titleMedium = t.titleMedium.copy(fontFamily = TextFont),
        titleSmall = t.titleSmall.copy(fontFamily = TextFont),
        labelMedium = t.labelMedium.copy(fontFamily = TextFont),
        labelSmall = t.labelSmall.copy(fontFamily = TextFont),
        bodyLarge = t.bodyLarge.copy(fontFamily = TextFont, fontSize = 24.sp, lineHeight = 34.sp),
        bodyMedium = t.bodyMedium.copy(fontFamily = TextFont, fontSize = 24.sp, lineHeight = 34.sp),
        bodySmall = t.bodySmall.copy(fontFamily = TextFont, fontSize = 20.sp, lineHeight = 28.sp),
        labelLarge = t.labelLarge.copy(fontFamily = TextFont, fontSize = 24.sp),
    )
    val colors = lightColorScheme(
        primary = accent,
        onPrimary = INK,
        background = BG1,
        onBackground = Color(0xFF1D1E21),
        surface = Color.White,
        surfaceVariant = Color(0xFFEDEEF2),
        onSurface = Color(0xFF1D1E21),
        onSurfaceVariant = Color(0xFF686A72),
        outline = Color(0xFFE0E1E6),
    )
    // Everything (text, badges, spacing) is drawn UI_SCALE times larger so the screen is filled, not padded.
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * UI_SCALE, d.fontScale)) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

/** Below about 1.39 the 1672px-wide car split view drops under the 1200dp wide-layout breakpoint. */
private const val UI_SCALE = 1.2f

/** 주 동작은 불투명한 단색 면과 대비되는 글자로 표시한다. */
@Composable
fun Btn(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier.heightIn(min = 80.dp).press(enabled, onClick = onClick).clip(shape)
            .background(if (enabled) accent else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 36.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (enabled) MaterialTheme.colorScheme.onPrimary else SUB, textAlign = TextAlign.Center) }
}

/** Secondary action: hairline outline, light label. */
@Composable
fun OutlineBtn(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.heightIn(min = 76.dp).press(enabled, onClick = onClick).clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, LINE, shape)
            .padding(horizontal = 32.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = if (enabled) TXT else SUB.copy(alpha = 0.6f), textAlign = TextAlign.Center) }
}

/** Filter pill: the visible pill is about 56dp, the touch area 76dp. Active is light on dark. */
@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) TXT else TXT.copy(alpha = 0f), tween(220), label = "pill")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.surface else SUB, tween(220), label = "pillText")
    Box(modifier.heightIn(min = 76.dp).press(role = Role.Tab, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.heightIn(min = 56.dp).widthIn(min = 84.dp).clip(CircleShape)
                .background(bg)
                .border(1.dp, LINE, CircleShape)
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
    val iconColor = TXT
    Box(Modifier.size(76.dp).press(onClick = onClick).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(26.dp)) {
            val w = 3.dp.toPx()
            drawLine(iconColor, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
            drawLine(iconColor, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
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
            color = MaterialTheme.colorScheme.onPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium,
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
 * [owned] false draws the medal in grey: a badge not got yet.
 */
@Composable
fun Medal(badge: Badge, width: Dp, owned: Boolean = true, modifier: Modifier = Modifier, loopSpace: Boolean = true) {
    val m = if (loopSpace) modifier.padding(top = width * (MEDAL_H - 1)) else modifier
    Canvas(m.size(width), contentDescription = badgeName(Game.index, badge)) { drawMedalCached(badge, size.width, greyed = !owned) }
}

/**
 * "×3" in the bottom-left corner of a round medal [medal] wide: sized to the medal and meant to sit in the empty
 * corner outside the disc (place it with a small negative x offset), so it touches only the rim, not the picture.
 */
@Composable
fun CountChip(count: Int, medal: Dp, modifier: Modifier = Modifier) {
    val size = (medal.value * 0.1f).coerceIn(13f, 18f).sp
    Text(
        "×$count",
        modifier.clip(CircleShape).background(Color(0xFF1D1E21)).border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .padding(horizontal = 7.dp),
        fontSize = size, lineHeight = size * 1.25f, fontWeight = FontWeight.Bold, fontFamily = NumFont, color = Color.White,
    )
}

/** Dashed empty circle: a slot nothing has been picked for yet. */
@Composable
fun EmptySlot(size: Dp) {
    Box(
        Modifier.size(size).drawBehind {
            val w = 3.dp.toPx()
            drawCircle(Color(0x1FFFFFFF), this.size.minDimension / 2 - w, style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx()))))
        },
    )
}

/** Recessed round slot a medal sits in. */
@Composable
fun Well(size: Dp, selected: Boolean = false, selectionColor: Color = Color.White, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).drawBehind {
            val r = this.size.minDimension / 2
            drawCircle(Brush.radialGradient(0f to Color(0xFF252629), 0.7f to Color(0xFF121315), center = Offset(center.x, center.y - r * 0.2f), radius = r), r)
            // inset shadow from the top edge, faint light on the bottom lip
            drawCircle(Brush.radialGradient(0.72f to Color.Transparent, 1f to Color(0xB3000000), center = Offset(center.x, center.y + r * 0.12f), radius = r * 1.12f), r)
            drawArc(Color(0x0DFFFFFF), 30f, 120f, false, Offset(center.x - r + 1f, center.y - r + 1f), androidx.compose.ui.geometry.Size(r * 2 - 2f, r * 2 - 2f), style = Stroke(2.dp.toPx()))
        },
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (selected) Canvas(Modifier.fillMaxSize()) {
            val r = this.size.width * 0.89f / 2f
            val c = center
            val disc = Path().apply { addOval(Rect(c, r)) }
            val loop = Path().apply { addOval(Rect(c.x - r * 0.19f, c.y - r * 1.32f, c.x + r * 0.19f, c.y - r * 0.86f)) }
            drawPath(Path.combine(PathOperation.Union, disc, loop), selectionColor, style = Stroke(5.dp.toPx()))
        }
    }
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
    Column(
        (if (onClick != null) Modifier.press(enabled, onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .padding(start = 6.dp, end = 6.dp, top = 22.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Well(well, selected = selected, selectionColor = HIGHLIGHT) { Medal(badge, well * 0.89f, owned, loopSpace = false) }
            if (owned && count > 0) CountChip(count, well, Modifier.align(Alignment.BottomStart).offset(x = (-4).dp))
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
