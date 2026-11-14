package kr.chargebadge

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.chargebadge.core.Badge
import kr.chargebadge.core.DESIGNS_PER_ART
import kr.chargebadge.core.Design
import kr.chargebadge.core.Friend
import kr.chargebadge.core.GOLD_CHANCE
import kr.chargebadge.core.GameState
import kr.chargebadge.core.MIN_SOC_GAIN
import kr.chargebadge.core.Region
import kr.chargebadge.core.SYNTH_COST
import kr.chargebadge.core.Source
import kr.chargebadge.core.StationIndex
import kr.chargebadge.core.TRADE_EXPIRY_MS
import kr.chargebadge.core.TREE_SLOTS
import kr.chargebadge.core.Tier
import kr.chargebadge.core.anniversaryCharges
import kr.chargebadge.core.anniversaryOpen
import kr.chargebadge.core.anniversaryPoints
import kr.chargebadge.core.designName
import kr.chargebadge.core.latestAnniversary
import kr.chargebadge.core.setTreeSlot
import kr.chargebadge.core.treeEditable
import kr.chargebadge.core.treeSlots
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val GAP = Arrangement.spacedBy(24.dp)
private val PAGE = PaddingValues(horizontal = 48.dp, vertical = 32.dp)

/** Name of the art (city for general-gu, which share one art key). */
fun artName(idx: StationIndex, artKey: String): String {
    val regions = idx.regionsOfArt(artKey)
    val name = regions.firstOrNull()?.name ?: artKey
    return if (regions.size > 1) name.substringBeforeLast(' ') else name
}

fun badgeName(idx: StationIndex, b: Badge) = "${artName(idx, b.design.artKey)} ${designName(b.design)} ${tierLabel(b.tier)}"

fun shortArt(idx: StationIndex, artKey: String) = artName(idx, artKey).substringAfterLast(' ')

private val PROVINCE = mapOf(
    "서울특별시" to "서울", "부산광역시" to "부산", "대구광역시" to "대구", "인천광역시" to "인천", "대전광역시" to "대전",
    "울산광역시" to "울산", "세종특별자치시" to "세종", "경기도" to "경기", "강원특별자치도" to "강원", "충청북도" to "충북",
    "충청남도" to "충남", "전북특별자치도" to "전북", "전남광주통합특별시" to "전남광주", "경상북도" to "경북",
    "경상남도" to "경남", "제주특별자치도" to "제주",
)

/** "경상북도 칠곡군" -> "경북 · 칠곡군". */
private fun regionLabel(r: Region): String {
    val province = r.name.substringBefore(' ')
    val short = PROVINCE[province] ?: province
    val rest = r.name.substringAfter(' ', "")
    return if (rest.isEmpty()) short else "$short · $rest"
}

/** "경상북도 칠곡군" -> "칠곡군", "경기도 수원시 장안구" -> "수원시 장안구". */
private fun regionShort(r: Region) = r.name.substringAfter(' ', r.name)

private fun pct(p: Double) = "${"%.0f".format(p * 100)}%"

private fun date(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun dots(d: LocalDate) = d.toString().replace('-', '.')

private fun badgeKey(b: Badge) = "${b.design.artKey}:${b.design.number}:${b.tier}"

private fun hasBadgeOrVisit(state: GameState, idx: StationIndex, r: Region) =
    Game.engine.cellTier(state, idx, r.index) != null || Game.engine.visited(state, r.index)

/** Region cell a badge opens in: a visited cell of its art if any, else the first. */
private fun regionFor(idx: StationIndex, state: GameState, b: Badge): Int {
    val rs = idx.regionsOfArt(b.design.artKey)
    return (rs.firstOrNull { Game.engine.visited(state, it.index) } ?: rs.first()).index
}

private fun tierGlow(t: Tier) = when (t) {
    Tier.BRONZE -> Color(0xFFE3A77F)
    Tier.SILVER -> Color(0xFFDDE6F0)
    Tier.GOLD -> Color(0xFFFFE08A)
}

/** "은까지 같은 도안 동 7 / 10" with the count in the accent color. */
@Composable
private fun SilverProgress(have: Int, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val text = if (have >= SYNTH_COST) buildAnnotatedString { withStyle(SpanStyle(color = accent)) { append("지금 은으로 합성할 수 있어요") } }
    else buildAnnotatedString {
        append("은까지 같은 도안 동 ")
        withStyle(SpanStyle(color = accent)) { append("$have / $SYNTH_COST") }
    }
    Text(text, modifier, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = TXT, textAlign = TextAlign.Center)
}

/** Accent ring that fills up to [progress] when shown; its glow breathes while [pulse]. */
@Composable
private fun ProgressRing(size: Dp, progress: Float, pulse: Boolean = false, content: @Composable () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val fill = remember { Animatable(0f) }
    LaunchedEffect(progress) { fill.animateTo(progress.coerceIn(0f, 1f), tween(1100, easing = Emphasized)) }
    val breath = if (pulse) rememberInfiniteTransition(label = "ring").animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "breath") else null
    Box(
        Modifier.size(size).drawBehind {
            val r = this.size.minDimension / 2
            val stroke = r * 18f / 170f
            val p = fill.value
            val b = breath?.value ?: 0f
            val halo = r * (1.2f + 0.06f * b)
            drawCircle(Brush.radialGradient(0.78f to accent.copy(alpha = 0.08f + 0.24f * p + 0.2f * b), 1f to Color.Transparent, radius = halo), halo)
            drawCircle(BG1, r)
            drawCircle(Color(0x14FFFFFF), r - stroke / 2, style = Stroke(stroke))
            if (p > 0f) drawArc(accent, -90f, 360f * p, false, Offset(stroke / 2, stroke / 2), Size(2 * r - stroke, 2 * r - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            drawCircle(BG1, r - stroke)
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Accent glow at the top-right corner of the hero-style cards. */
private fun Modifier.cornerGlow(accent: Color) = drawBehind {
    val c = Offset(size.width * 0.8f, -size.height * 0.14f)
    val r = 360.dp.toPx()
    drawCircle(Brush.radialGradient(0f to accent.copy(alpha = 0.25f), 0.6f to Color.Transparent, center = c, radius = r), r, c)
}

// S4
@Composable
fun CollectionScreen(go: (Screen) -> Unit, detail: Screen.Detail? = null, onCloseDetail: () -> Unit = {}) {
    val idx = Game.index
    val state = Game.state
    var regionMode by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val filter = rememberSaveable { mutableStateOf("전체") }
    val listState = rememberLazyListState()
    val openRegion: (Int?) -> Unit = { regionMode = true; selected = it }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 1200.dp) {
            // Wide: the visited region (else the first with badges) stands in until one is picked.
            val shown = selected
                ?: (idx.regions.firstOrNull { Game.engine.visited(state, it.index) } ?: idx.regions.firstOrNull { hasBadgeOrVisit(state, idx, it) })?.index
            Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                // Each panel animates on its own; the outgoing one keeps the state it was showing.
                AnimatedContent(detail ?: if (regionMode) "regions" else "hero", Modifier.width(520.dp).fillMaxHeight(), { fadeThrough() }, label = "side") { k ->
                    when (k) {
                        is Screen.Detail -> DetailCard(k, onCloseDetail, Modifier.fillMaxSize().glass())
                        "regions" -> RegionList(Modifier.fillMaxSize().glass(), shown, listState, { selected = it }) { regionMode = false }
                        else -> Hero(Modifier.fillMaxSize(), go, openRegion)
                    }
                }
                AnimatedContent(if (regionMode) shown else "showcase", Modifier.weight(1f).fillMaxHeight().case(), { fadeThrough() }, label = "case") { k ->
                    when (k) {
                        is Int -> RegionCase(idx.region(k), go, detail?.badge, null)
                        null -> Column(Modifier.padding(36.dp)) { Header("시·군·구를 골라 주세요", "도안 3개마다 동·은·금 칸과 수량을 볼 수 있어요") }
                        else -> Showcase(filter, go, detail?.badge) { regionMode = true }
                    }
                }
            }
        } else {
            AnimatedContent(if (regionMode) selected ?: "list" else "showcase", Modifier.fillMaxSize(), { fadeThrough() }, label = "collection") { k ->
                when (k) {
                    "list" -> RegionList(Modifier.fillMaxSize().padding(24.dp).glass(), null, listState, { selected = it }) { regionMode = false }
                    is Int -> RegionCase(idx.region(k), go, null) { selected = null }
                    else -> Showcase(filter, go, null, hero = { Hero(Modifier.fillMaxWidth(), go, openRegion, compact = true) }) { regionMode = true }
                }
            }
        }
    }
}

/** Where the car is now (else the last charge): region, progress to silver, totals and the next step. */
@Composable
private fun Hero(modifier: Modifier, go: (Screen) -> Unit, openRegion: (Int?) -> Unit, compact: Boolean = false) {
    val idx = Game.index
    val state = Game.state
    val accent = MaterialTheme.colorScheme.primary
    val la = Game.lat
    val ln = Game.lng
    val here = if (Game.stationsLoaded && la != null && ln != null) idx.nearest(la, ln) else null
    val last = state.charges.lastOrNull()
    val station = here ?: last?.station
    val region = station?.let { idx.region(it.regionIndex) }
    // The bronze design of this art closest to a silver.
    val target = region?.let { r -> (0 until DESIGNS_PER_ART).map { Badge(Design(r.artKey, it), Tier.BRONZE) }.maxBy { state.available(it) } }
    val have = target?.let { max(0, state.available(it)) } ?: 0
    val ring = if (compact) 280.dp else 340.dp
    Column(modifier.glass().cornerGlow(accent).padding(40.dp)) {
        Eyebrow(
            when {
                here != null -> "지금 있는 곳"
                last != null -> "최근 충전한 곳"
                else -> "충전 수집첩"
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            region?.name?.let { "${it.substringBeforeLast(' ')}\n${it.substringAfterLast(' ')}" } ?: "첫 충전을\n기다리는 중",
            fontSize = if (compact) 44.sp else 52.sp, lineHeight = if (compact) 50.sp else 60.sp, fontWeight = FontWeight.ExtraBold, color = TXT,
        )
        Spacer(Modifier.height(8.dp))
        Sub(
            when {
                here != null -> "${here.name} · 등재 충전소"
                last != null -> "${last.station.name} · ${last.date}"
                else -> "공공 충전소에서 충전율이 ${MIN_SOC_GAIN}%p 오르면 배지를 받아요"
            },
        )
        Box(Modifier.fillMaxWidth().padding(top = if (compact) 28.dp else 44.dp, bottom = 28.dp), contentAlignment = Alignment.Center) {
            ProgressRing(ring, have / SYNTH_COST.toFloat()) {
                if (target != null && state.count(target) > 0) Medal(target, ring * 0.62f) else Unknown(ring * 0.6f)
            }
        }
        SilverProgress(have, Modifier.fillMaxWidth())
        if (compact) Spacer(Modifier.height(28.dp)) else Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(
                idx.regions.count { Game.engine.visited(state, it.index) } to "방문 지역",
                state.owned.values.sum() to "모은 배지",
                state.owned.filterKeys { it.tier == Tier.GOLD }.values.sum() to "금 배지",
            ).forEach { (n, label) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Color(0x0DFFFFFF))
                        .border(1.dp, LINE, RoundedCornerShape(20.dp)).padding(20.dp),
                ) {
                    Text("$n", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = TXT)
                    Text(label, fontSize = 20.sp, color = SUB)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        when {
            Game.charging && last != null -> Btn("충전 중 화면 보기", Modifier.fillMaxWidth().height(84.dp)) { go(Screen.Charging) }
            region != null -> Btn("이 지역 배지 보기", Modifier.fillMaxWidth().height(84.dp)) { openRegion(region.index) }
            else -> Btn("시·군·구별로 둘러보기", Modifier.fillMaxWidth().height(84.dp)) { openRegion(null) }
        }
    }
}

/** "나의 진열장": owned badges (gold first), then designs not collected yet. [hero] goes on top in half width. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Showcase(
    filter: MutableState<String>,
    go: (Screen) -> Unit,
    selectedBadge: Badge?,
    hero: (@Composable () -> Unit)? = null,
    onRegions: () -> Unit,
) {
    val idx = Game.index
    val state = Game.state
    val owned = state.owned.keys
    val traded = state.acquisitions.filter { it.source == Source.TRADE }.map { it.badge }.toSet()
    val arts = owned.map { it.design.artKey }.distinct()
    val artOrder = arts.associateWith { a -> idx.regionsOfArt(a).firstOrNull()?.index ?: Int.MAX_VALUE }
    val shown = owned.filter {
        when (filter.value) {
            "동" -> it.tier == Tier.BRONZE
            "은" -> it.tier == Tier.SILVER
            "금" -> it.tier == Tier.GOLD
            "교환" -> it in traded
            else -> true
        }
    }.sortedWith(compareByDescending<Badge> { it.tier }.thenBy { artOrder[it.design.artKey] }.thenBy { it.design.number })
    val ownedDesigns = owned.map { it.design }.toSet()
    val missing = if (filter.value == "전체") {
        arts.sortedBy { artOrder[it] }.flatMap { a -> (0 until DESIGNS_PER_ART).map { Design(a, it) } }.filter { it !in ownedDesigns }
    } else emptyList()
    LazyVerticalGrid(
        GridCells.Adaptive(180.dp),
        Modifier.fillMaxSize(),
        state = rememberLazyGridState(),
        contentPadding = if (hero != null) PaddingValues(24.dp) else PaddingValues(horizontal = 32.dp, vertical = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        hero?.let { h -> item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.padding(bottom = 28.dp)) { h() } } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Title("나의 진열장")
                    Text(
                        "배지 ${state.owned.values.sum()}개 · 지역 ${idx.regions.count { Game.engine.cellTier(state, idx, it.index) != null }}곳",
                        fontSize = 22.sp, color = SUB,
                    )
                }
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    listOf("전체", "동", "은", "금", "교환").forEach { Pill(it, filter.value == it) { filter.value = it } }
                    Pill("시·군·구", false, onClick = onRegions)
                }
            }
        }
        when {
            owned.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                StateCard("처음 시작", "아직 모은 배지가 없어요. 공공 충전소에서 충전율이 ${MIN_SOC_GAIN}%p 이상 오르면 그 시·군·구 배지를 받아요.", "시·군·구별로 둘러보기", onAction = onRegions)
            }
            shown.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                StateCard("필터 결과 없음", "선택한 등급의 배지가 없어요.", "필터 초기화") { filter.value = "전체" }
            }
        }
        items(shown, key = ::badgeKey) { b ->
            Box(Modifier.animateItem(), contentAlignment = Alignment.Center) {
                BadgeSlot(b, state.count(b), selected = b == selectedBadge) { go(Screen.Detail(regionFor(idx, state, b), b)) }
            }
        }
        items(missing, key = { "${it.artKey}:${it.number}:missing" }) { d ->
            val b = Badge(d, Tier.BRONZE)
            Box(Modifier.animateItem(), contentAlignment = Alignment.Center) {
                BadgeSlot(b, 0, selected = b == selectedBadge) { go(Screen.Detail(regionFor(idx, state, b), b)) }
            }
        }
    }
}

@Composable
private fun RegionList(modifier: Modifier, selected: Int?, listState: LazyListState, onSelect: (Int) -> Unit, onExit: () -> Unit) {
    val idx = Game.index
    val state = Game.state
    Column(modifier) {
        Column(Modifier.padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow("시·군·구 선택")
            Text("전체 ${idx.regions.size}곳", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = TXT)
            Sub("방문 ${idx.regions.count { Game.engine.visited(state, it.index) }}곳 · 배지가 있는 칸 ${idx.regions.count { Game.engine.cellTier(state, idx, it.index) != null }}곳")
        }
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Regions with badges or visits first, each group in region order.
            items(idx.regions.sortedBy { if (hasBadgeOrVisit(state, idx, it)) 0 else 1 }, key = { it.index }) { r ->
                val tier = Game.engine.cellTier(state, idx, r.index)
                val visited = Game.engine.visited(state, r.index)
                RegionRow(regionLabel(r), listOfNotNull(if (visited) "방문" else null, tier?.let(::tierLabel)).joinToString(" · "), r.index == selected) { onSelect(r.index) }
            }
        }
        OutlineBtn("전체 진열장", Modifier.fillMaxWidth().padding(24.dp), onClick = onExit)
    }
}

@Composable
private fun RegionRow(label: String, trailing: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    val bg by animateColorAsState(if (selected) accent.copy(alpha = 0.14f) else Color(0x08FFFFFF), tween(220), label = "rowBg")
    val edge by animateColorAsState(if (selected) accent else LINE, tween(220), label = "rowEdge")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).press(onClick = onClick).clip(shape)
            .background(bg)
            .border(if (selected) 2.dp else 1.dp, edge, shape)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TXT, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing.isNotEmpty()) Text(trailing, fontSize = 20.sp, color = if (selected) accent else SUB)
    }
}

/** One region: 3 designs x 동·은·금 slots, then the stations charged there. */
@Composable
private fun RegionCase(region: Region, go: (Screen) -> Unit, selectedBadge: Badge?, onBack: (() -> Unit)?) {
    val state = Game.state
    val rep = state.representatives[region.index]
    val charges = state.charges.filter { it.station.regionIndex == region.index }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 36.dp, vertical = 32.dp), verticalArrangement = GAP) {
        onBack?.let { OutlineBtn("← 지역 목록", onClick = it) }
        Header(
            "${regionShort(region)}의 배지",
            listOfNotNull(
                if (Game.engine.visited(state, region.index)) "직접 충전한 곳" else "아직 충전하지 않은 곳",
                rep?.let { "지역 대표 ${designName(it.design)} ${tierLabel(it.tier)}" },
            ).joinToString(" · "),
        )
        for (n in 0 until DESIGNS_PER_ART) {
            val design = Design(region.artKey, n)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Heading(designName(design))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (tier in Tier.entries) {
                        val b = Badge(design, tier)
                        BadgeSlot(b, state.count(b), well = 132.dp, selected = b == selectedBadge, lockedTier = true) { go(Screen.Detail(region.index, b)) }
                    }
                }
            }
        }
        Heading("직접 충전한 장소")
        if (charges.isEmpty()) Sub("아직 이 지역에서 충전한 기록이 없어요")
        charges.groupBy { it.station.id }.values.sortedByDescending { list -> list.maxOf { it.timeMillis } }.forEach { list ->
            val last = list.maxBy { it.timeMillis }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x08FFFFFF)).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Medal(last.badge, 60.dp)
                Column(Modifier.weight(1f)) {
                    Text(last.station.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TXT)
                    Note("최근 ${last.date} · ${list.size}회")
                }
            }
        }
    }
}

// S6
/** Half width: the detail fills the screen. */
@Composable
fun DetailScreen(s: Screen.Detail, onClose: () -> Unit) = DetailCard(s, onClose, Modifier.fillMaxSize().padding(24.dp).glass())

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailCard(s: Screen.Detail, onClose: () -> Unit, modifier: Modifier) {
    val idx = Game.index
    val state = Game.state
    val badge = s.badge
    val count = state.count(badge)
    var msg by remember(badge) { mutableStateOf("") }
    val acquired = state.acquisitions.filter { it.badge == badge }
    val isRep = state.representatives[s.regionIndex] == badge
    val glow = tierGlow(badge.tier)
    ToastBox(msg, modifier.cornerGlow(MaterialTheme.colorScheme.primary)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 40.dp, end = 32.dp, top = 28.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("배지 상세", Modifier.weight(1f))
                CloseBtn(onClose)
            }
            Box(
                Modifier.fillMaxWidth().height(232.dp).drawBehind {
                    val r = 150.dp.toPx()
                    val c = Offset(center.x, center.y + 12.dp.toPx())
                    drawCircle(Brush.radialGradient(0f to glow.copy(alpha = 0.28f), 1f to Color.Transparent, center = c, radius = r), r, c)
                },
                contentAlignment = Alignment.Center,
            ) { Medal(badge, 176.dp, count > 0) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${shortArt(idx, badge.design.artKey)} · ${tierColorName(badge.tier)}", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = TXT)
                Sub("${designName(badge.design)} · ${idx.region(s.regionIndex).name}")
            }
            // Actions before the table, so both stay above the fold in the card.
            if (badge.tier == Tier.BRONZE) {
                Btn("합성하기 · 동 ${SYNTH_COST}개 → 은 1개", Modifier.fillMaxWidth(), enabled = Game.engine.synthesizable(state, badge.design)) {
                    msg = runCatching {
                        Game.update { Game.engine.synthesize(Game.engine.expireTrades(it, Game.nowMillis()), badge.design, Game.nowMillis()) }
                        "은 배지를 만들었어요"
                    }.getOrElse { "동이 ${SYNTH_COST}개보다 적어서 합성할 수 없어요" }
                }
            }
            OutlineBtn(if (isRep) "지역 대표로 지정됨" else "지역 대표로 지정", Modifier.fillMaxWidth(), enabled = count > 0) {
                Game.update { Game.engine.setRepresentative(it, s.regionIndex, badge) }
                msg = "지역 대표로 지정했어요"
            }
            if (badge.tier == Tier.BRONZE) Note("합성에 쓸 수 있는 같은 도안 동 ${max(0, state.available(badge))} / ${SYNTH_COST}개")
            HorizontalDivider(color = LINE)
            Info(
                "획득 조건",
                when (badge.tier) {
                    Tier.BRONZE -> "이 지역 등재 충전소에서 충전율 +${MIN_SOC_GAIN}%p 이상 충전 (${pct(1 - GOLD_CHANCE)})"
                    Tier.SILVER -> "같은 도안 동 ${SYNTH_COST}개를 [합성하기]"
                    Tier.GOLD -> "이 지역 등재 충전소에서 충전할 때 ${pct(GOLD_CHANCE)} 확률"
                },
            )
            Info("획득 경로") {
                if (acquired.isEmpty()) InfoText("아직 없어요")
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Source.entries.forEach { src ->
                        val n = acquired.count { it.source == src }
                        if (n > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SourceMark(src)
                            InfoText("${sourceLabel(src)} ${n}회")
                        }
                    }
                }
            }
            Info("소장 수량", "${count}개" + if (state.locked(badge) > 0) " · 교환 제안 중 ${state.locked(badge)}개 잠금" else "")
            Info("처음 얻은 날", acquired.minOfOrNull { it.timeMillis }?.let { date(it).toString() } ?: "아직 없어요")
            Info("지역 칸", idx.region(s.regionIndex).name)
            Spacer(Modifier.height(80.dp))
        }
    }
}

/** Content with the last message pinned bottom-left over it. */
@Composable
private fun ToastBox(msg: String, modifier: Modifier = Modifier.fillMaxSize(), content: @Composable () -> Unit) {
    Box(modifier) {
        content()
        if (msg.isNotEmpty()) Toast(msg, Modifier.align(Alignment.BottomStart).padding(24.dp))
    }
}

@Composable
private fun Info(label: String, value: String) = Info(label) { InfoText(value) }

@Composable
private fun Info(label: String, value: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.width(132.dp).padding(top = 4.dp), fontSize = 20.sp, color = SUB)
        Box(Modifier.weight(1f)) { value() }
    }
}

@Composable
private fun InfoText(s: String) = Text(s, fontSize = 24.sp, lineHeight = 34.sp, color = TXT)

fun sourceLabel(s: Source) = when (s) {
    Source.CHARGE -> "직접 충전"
    Source.TRADE -> "교환"
    Source.SYNTHESIS -> "합성"
}

/** Source by shape as well as label: shield-check = charge, arrows = trade, sparkle = synthesis. */
@Composable
fun SourceMark(s: Source) {
    Canvas(Modifier.size(36.dp)) { drawSourceMark(s, center, size.minDimension / 2 / 1.14f) }
}

@Composable
private fun AccentChip(text: String) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text,
        Modifier.clip(CircleShape).background(accent.copy(alpha = 0.14f)).padding(horizontal = 24.dp, vertical = 10.dp),
        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = accent,
    )
}

/** Glow, faint rays and sparkles behind a medal at the moment it is won. */
private fun Modifier.burst(accent: Color, glow: Color) = drawBehind {
    val r = size.minDimension / 2
    drawCircle(Brush.radialGradient(0f to glow.copy(alpha = 0.45f), 0.4f to accent.copy(alpha = 0.18f), 1f to Color.Transparent, radius = r), r)
    for (i in 0 until 12) {
        rotate(i * 30f) {
            val ray = Path().apply {
                moveTo(center.x - r * 0.03f, center.y)
                lineTo(center.x, center.y - r)
                lineTo(center.x + r * 0.03f, center.y)
                close()
            }
            drawPath(ray, Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.10f)), center.y - r, center.y))
        }
    }
    listOf(20f to 0.82f, 75f to 0.7f, 140f to 0.88f, 200f to 0.74f, 255f to 0.86f, 320f to 0.72f).forEach { (deg, f) ->
        val a = Math.toRadians(deg.toDouble())
        val p = Offset(center.x + r * f * cos(a).toFloat(), center.y + r * f * sin(a).toFloat())
        val s = r * 0.035f
        drawLine(TXT.copy(alpha = 0.8f), Offset(p.x - s, p.y), Offset(p.x + s, p.y), s * 0.35f, StrokeCap.Round)
        drawLine(TXT.copy(alpha = 0.8f), Offset(p.x, p.y - s), Offset(p.x, p.y + s), s * 0.35f, StrokeCap.Round)
    }
}

// S3
@Composable
fun AcquiredScreen(onDone: () -> Unit) {
    val idx = Game.index
    val state = Game.state
    val rec = state.charges.lastOrNull() ?: return Column(Modifier.padding(PAGE)) { StateCard("충전 확인 대기", "아직 받은 배지가 없어요.") }
    val b = rec.badge
    val left = SYNTH_COST - state.available(Badge(b.design, Tier.BRONZE))
    val accent = MaterialTheme.colorScheme.primary
    // The medal spins in over about a second and lands with a small bounce, then a light band sweeps across it once.
    val land = remember { Animatable(0f) }
    val sweep = remember { Animatable(-0.3f) }
    val medalPx = with(LocalDensity.current) { 250.dp.roundToPx() }
    LaunchedEffect(rec.timeMillis) {
        medalImage(b, medalPx) // render before the flip, not in its first frame
        delay(160)
        launch { land.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 50f)) }
        delay(850)
        sweep.animateTo(1.3f, tween(900, easing = FastOutSlowInEasing))
    }
    val spin by rememberInfiniteTransition(label = "rays").animateFloat(0f, 360f, infiniteRepeatable(tween(90_000, easing = LinearEasing)), label = "spin")
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Eyebrow("${rec.station.name} · ${rec.date}", Modifier.enter(0))
        Spacer(Modifier.height(10.dp))
        Text("이번 충전의 기록이 더해졌어요", Modifier.enter(80), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = TXT, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(460.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.matchParentSize().graphicsLayer {
                    val v = land.value.coerceIn(0f, 1f)
                    alpha = v
                    scaleX = 0.6f + 0.4f * v
                    scaleY = scaleX
                    rotationZ = spin
                }.burst(accent, tierGlow(b.tier)),
            )
            Medal(
                b, 250.dp,
                modifier = Modifier.graphicsLayer {
                    val v = land.value
                    scaleX = 0.25f + 0.75f * v
                    scaleY = scaleX
                    rotationY = (1f - v) * 360f
                    alpha = (v * 4f).coerceIn(0f, 1f)
                    cameraDistance = 14f * density
                }.shine { sweep.value },
            )
        }
        Text("${shortArt(idx, b.design.artKey)} · ${tierColorName(b.tier)} +1", Modifier.enter(560), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = TXT)
        Spacer(Modifier.height(6.dp))
        Sub(designName(b.design), Modifier.enter(620))
        Spacer(Modifier.height(18.dp))
        Box(Modifier.enter(700)) {
            AccentChip(
                when {
                    b.tier == Tier.GOLD -> "${pct(GOLD_CHANCE)} 확률로 나온 금 배지예요"
                    left > 0 -> "은까지 같은 도안 동 ${left}개"
                    else -> "지금 은으로 합성할 수 있어요"
                },
            )
        }
        Spacer(Modifier.weight(1f))
        Btn("확인", Modifier.widthIn(min = 520.dp).enter(820), onClick = onDone)
        Spacer(Modifier.height(16.dp))
        Note("같은 충전소는 하루 한 번만 판정해요 · 금 배지는 충전할 때마다 ${pct(GOLD_CHANCE)} 확률", Modifier.enter(880))
    }
}

// S2a
@Composable
fun ChargingScreen(onDone: () -> Unit) {
    val idx = Game.index
    val state = Game.state
    val rec = state.charges.lastOrNull() ?: return Column(Modifier.padding(PAGE)) { StateCard("충전 확인 대기", "충전 기록이 없어요.") }
    val available = max(0, state.available(Badge(rec.badge.design, Tier.BRONZE)))
    val soc = Game.soc
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1200.dp
        val gauge: @Composable (Modifier) -> Unit = { m ->
            Column(m.glass().cornerGlow(accent).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Eyebrow(if (Game.charging) "충전 중" else "충전 끝남 · 마지막 충전", Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text(rec.station.name, Modifier.fillMaxWidth(), fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, color = TXT)
                Sub(idx.region(rec.station.regionIndex).name, Modifier.fillMaxWidth())
                Spacer(Modifier.height(36.dp))
                ProgressRing(if (wide) 380.dp else 320.dp, (soc ?: 0) / 100f, pulse = Game.charging) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Odometer: a rising value rolls up, a falling one rolls down.
                        AnimatedContent(
                            soc,
                            transitionSpec = {
                                val up = (targetState ?: 0) >= (initialState ?: 0)
                                (slideInVertically(tween(420, easing = Emphasized)) { if (up) it else -it } + fadeIn(tween(200))) togetherWith
                                    (slideOutVertically(tween(420, easing = Emphasized)) { if (up) -it else it } + fadeOut(tween(160)))
                            },
                            label = "soc",
                        ) { v -> Text(v?.toString() ?: "--", fontSize = 96.sp, lineHeight = 100.sp, fontWeight = FontWeight.ExtraBold, color = TXT) }
                        Text("% 충전율", fontSize = 22.sp, color = SUB)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Note("충전율은 차량이 측정한 값이에요")
            }
        }
        val bar = remember { Animatable(0f) }
        LaunchedEffect(available) { bar.animateTo(min(1f, available / SYNTH_COST.toFloat()), tween(1000, delayMillis = 200, easing = Emphasized)) }
        val info: @Composable (Modifier, Boolean) -> Unit = { m, fill ->
            Column(m.enter(120).case().padding(36.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Eyebrow("이번에 받은 배지")
                Row(Modifier.padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    Well(if (wide) 260.dp else 200.dp) { Medal(rec.badge, if (wide) 230.dp else 178.dp, loopSpace = false) }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${shortArt(idx, rec.badge.design.artKey)} · ${designName(rec.badge.design)}", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = TXT)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TierTag(rec.badge.tier)
                            Sub("${rec.date} 획득")
                        }
                    }
                }
                HorizontalDivider(color = LINE)
                SilverProgress(available, Modifier.fillMaxWidth())
                LinearProgressIndicator(
                    progress = { bar.value },
                    modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)),
                    color = accent,
                    trackColor = Color(0x14FFFFFF),
                )
                Note("같은 도안 동 ${SYNTH_COST}개를 모으면 도감에서 [합성하기]로 은 1개를 만들 수 있어요")
                if (fill) Spacer(Modifier.weight(1f))
                Btn("도감에서 보기", Modifier.fillMaxWidth().height(84.dp), onClick = onDone)
            }
        }
        if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            gauge(Modifier.width(520.dp).fillMaxHeight())
            info(Modifier.weight(1f).fillMaxHeight(), true)
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = GAP) {
            gauge(Modifier.fillMaxWidth())
            info(Modifier.fillMaxWidth(), false)
        }
    }
}

// S8
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FriendsScreen(go: (Screen) -> Unit) {
    val state = Game.state
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PAGE), verticalArrangement = GAP) {
        Header("교환", "동 배지끼리 1:1로 바꿔요 · 친구가 받기 전에는 언제든 취소할 수 있어요")
        TradeSteps()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = GAP) {
            Heading("친구 ${state.friends.size}명", Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField("", {}, enabled = false, label = { Text("친구 코드") }, colors = darkFieldColors(), shape = RoundedCornerShape(16.dp), modifier = Modifier.width(320.dp).heightIn(min = 76.dp))
                Note("친구 코드 입력은 서버 연동 후 열려요")
            }
        }
        BoxWithConstraints {
            val columns = when {
                maxWidth >= 1100.dp -> 3
                maxWidth >= 700.dp -> 2
                else -> 1
            }
            FlowRow(maxItemsInEachRow = columns, horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                state.friends.forEachIndexed { i, f ->
                    FriendCard(f, state.trades.count { it.friendId == f.id }, Modifier.weight(1f).enter(60 + i * 80)) { go(Screen.Trade(f.id)) }
                }
            }
        }
    }
}

/** How a trade goes, in three numbered steps. */
@Composable
private fun TradeSteps() {
    val accent = MaterialTheme.colorScheme.primary
    val steps = listOf("내 동 배지 하나 고르기", "친구 동 배지 하나 고르기", "친구가 확인하면 1:1 교환")
    Row(Modifier.fillMaxWidth().glass(24.dp).padding(horizontal = 28.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, step ->
            Box(Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text("${i + 1}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = accent)
            }
            Spacer(Modifier.width(14.dp))
            Text(step, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TXT, maxLines = 2)
            if (i < steps.lastIndex) Text("›", Modifier.padding(horizontal = 14.dp), fontSize = 36.sp, color = SUB)
        }
    }
}

/** Avatar gradients; a friend keeps the same one. */
private val AVATARS = listOf(
    Color(0xFF2C9BD1) to Color(0xFF7FD6FF),
    Color(0xFFE0705A) to Color(0xFFF7B08F),
    Color(0xFFD9A93E) to Color(0xFFF5DA8A),
    Color(0xFF3FB68F) to Color(0xFF9BE8CC),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FriendCard(f: Friend, pending: Int, modifier: Modifier, onTrade: () -> Unit) {
    val (c1, c2) = AVATARS[Math.floorMod(f.id.hashCode(), AVATARS.size)]
    Column(modifier.glass().cornerGlow(c1).padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c1, c2))), contentAlignment = Alignment.Center) {
                Text(f.name.substringAfterLast(' ').take(1), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = INK)
            }
            Column(Modifier.weight(1f)) {
                Text(f.name, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = TXT, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Note(f.id)
            }
            if (pending > 0) AccentChip("제안 중 ${pending}건")
        }
        Note("바꿀 수 있는 동 배지 ${f.badges.filterKeys { it.tier == Tier.BRONZE }.values.sum()}개")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            f.badges.forEach { (b, n) -> BadgeSlot(b, n, owned = true, well = 96.dp) }
        }
        if (pending > 0) Btn("제안 확인하기", Modifier.fillMaxWidth(), onClick = onTrade) else OutlineBtn("교환하기", Modifier.fillMaxWidth(), onClick = onTrade)
    }
}

// S9
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TradeScreen(friendId: String, onClose: () -> Unit) {
    val idx = Game.index
    LaunchedEffect(Unit) { Game.update { Game.engine.expireTrades(it, Game.nowMillis()) } }
    val state = Game.state
    val friend = state.friends.first { it.id == friendId }
    var give by remember { mutableStateOf<Badge?>(null) }
    var want by remember { mutableStateOf<Badge?>(null) }
    var msg by remember { mutableStateOf("") }
    val mine = state.owned.keys.sortedWith(compareBy({ it.design.artKey }, { it.design.number }, { it.tier }))
    val offers = state.trades.filter { it.friendId == friendId }
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1200.dp
        val well = if (wide) 220.dp else 160.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PAGE), verticalArrangement = GAP) {
            if (offers.isEmpty()) Header("${friend.name}와 교환", "동끼리 1:1 · 내 동 하나와 친구 동 하나를 골라요", onClose)
            else Header("교환할 배지를 확인해 주세요", "${friend.name} 확인 완료 · 내 확인을 기다리고 있어요", onClose)
            offers.forEach { t ->
                val days = (TRADE_EXPIRY_MS - (Game.nowMillis() - t.createdMillis)) / 86_400_000 + 1
                Column(Modifier.fillMaxWidth().glass().cornerGlow(accent).padding(36.dp), verticalArrangement = GAP) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SwapSlot(t.give, "내가 보내는 배지", "교환 뒤 내 수량 ${state.count(t.give) - 1}개", Modifier.weight(1f), well)
                        FlowArrows(Modifier.size(if (wide) 180.dp else 96.dp, 110.dp), active = true)
                        SwapSlot(t.want, "내가 받는 배지", "획득 출처는 교환으로 표시", Modifier.weight(1f), well)
                    }
                    Sub("제안 내용이 바뀌면 양쪽 확인을 다시 받아요 · ${days}일 뒤 자동 만료")
                    Row(horizontalArrangement = GAP, verticalAlignment = Alignment.CenterVertically) {
                        OutlineBtn("제안 취소") {
                            Game.update { Game.engine.cancelTrade(it, t.id) }
                            msg = "제안을 취소했어요"
                        }
                        Spacer(Modifier.weight(1f))
                        Btn("확인하고 교환", Modifier.widthIn(min = if (wide) 480.dp else 300.dp)) {
                            Game.update { Game.engine.acceptTrade(it, t.id, Game.nowMillis()) }
                            msg = "교환했어요"
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().case(28.dp).padding(32.dp), verticalArrangement = GAP) {
                Heading("새 교환 제안")
                // The swap at a glance; picking a badge below pops it into its slot.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SwapSlot(give, "내가 보낼 배지", if (give == null) "아래 내 배지에서 골라요" else "보내면 내 수량 ${state.count(give!!)}개 → ${state.count(give!!) - 1}개", Modifier.weight(1f), well)
                    FlowArrows(Modifier.size(if (wide) 180.dp else 96.dp, 110.dp), active = give != null && want != null)
                    SwapSlot(want, "받을 배지", if (want == null) "아래 ${friend.name}의 배지에서 골라요" else "획득 출처는 교환으로 표시", Modifier.weight(1f), well)
                }
                Btn("교환 제안하기", Modifier.align(Alignment.CenterHorizontally).widthIn(min = if (wide) 480.dp else 300.dp), enabled = give != null && want != null) {
                    msg = runCatching {
                        Game.update { Game.engine.proposeTrade(it, friendId, give!!, want!!, Game.nowMillis()).first }
                        "제안했어요. 친구 확인 전까지 내 배지는 잠겨요"
                    }.getOrElse { "이 배지로는 지금 제안할 수 없어요" }
                    give = null
                    want = null
                }
                HorizontalDivider(color = LINE)
                val myPicker: @Composable (Modifier) -> Unit = { m ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("내 배지", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TXT)
                        if (mine.isEmpty()) Sub("소장한 배지가 없어요")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            mine.forEach { b ->
                                val ok = b.tier == Tier.BRONZE && state.available(b) > 0
                                val note = when {
                                    b.tier != Tier.BRONZE -> "동만 교환"
                                    state.locked(b) > 0 -> "×${state.count(b)} · 잠금 ${state.locked(b)}"
                                    else -> "×${state.count(b)}"
                                }
                                BadgeSlot(b, state.count(b), well = 112.dp, selected = give == b, enabled = ok, note = note) { give = b }
                            }
                        }
                    }
                }
                val friendPicker: @Composable (Modifier) -> Unit = { m ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${friend.name}의 배지", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TXT)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            friend.badges.forEach { (b, n) ->
                                BadgeSlot(b, n, owned = true, well = 112.dp, selected = want == b, enabled = b.tier == Tier.BRONZE) { want = b }
                            }
                        }
                    }
                }
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    myPicker(Modifier.weight(1f))
                    friendPicker(Modifier.weight(1f))
                } else {
                    myPicker(Modifier)
                    friendPicker(Modifier)
                }
            }
            Spacer(Modifier.height(72.dp))
        }
        if (msg.isNotEmpty()) Toast(msg, Modifier.align(Alignment.BottomStart).padding(24.dp))
    }
}

/** One side of a swap: a well that pops the picked badge in, its name, and a line under it. */
@Composable
private fun SwapSlot(b: Badge?, title: String, sub: String, modifier: Modifier, well: Dp) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SUB, textAlign = TextAlign.Center)
        Box(Modifier.padding(top = well * 0.12f)) {
            Well(well) {
                AnimatedContent(
                    b,
                    transitionSpec = { (scaleIn(spring(dampingRatio = 0.55f, stiffness = 320f), initialScale = 0.5f) + fadeIn(tween(160))) togetherWith (scaleOut(tween(140), targetScale = 0.8f) + fadeOut(tween(140))) },
                    label = "slot",
                ) { x -> if (x == null) Unknown(well * 0.72f) else Medal(x, well * 0.86f, loopSpace = false) }
            }
        }
        if (b != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${shortArt(Game.index, b.design.artKey)} · ${designName(b.design)}", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = TXT)
            TierTag(b.tier)
        }
        Sub(sub)
    }
}

/** Two opposite arrows; while [active], a light runs along each, showing both badges move. */
@Composable
private fun FlowArrows(modifier: Modifier, active: Boolean) {
    val c = MaterialTheme.colorScheme.primary
    // Only ticks while active, so an idle trade screen requests no frames.
    val flow = if (active) rememberInfiniteTransition(label = "flow").animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "flow") else null
    // Own layer: the running light redraws only this.
    Canvas(modifier.graphicsLayer()) {
        val w = size.width
        val sw = 5.dp.toPx()
        val head = size.height * 0.15f
        val y1 = size.height * 0.32f
        val y2 = size.height * 0.68f
        val line = if (active) c else c.copy(alpha = 0.35f)
        drawLine(line, Offset(0f, y1), Offset(w, y1), sw, StrokeCap.Round)
        drawLine(line, Offset(w, y1), Offset(w - head, y1 - head), sw, StrokeCap.Round)
        drawLine(line, Offset(w, y1), Offset(w - head, y1 + head), sw, StrokeCap.Round)
        drawLine(line, Offset(w, y2), Offset(0f, y2), sw, StrokeCap.Round)
        drawLine(line, Offset(0f, y2), Offset(head, y2 - head), sw, StrokeCap.Round)
        drawLine(line, Offset(0f, y2), Offset(head, y2 + head), sw, StrokeCap.Round)
        if (flow != null) {
            val t = flow.value
            listOf(Offset(w * t, y1), Offset(w * (1 - t), y2)).forEach { p ->
                drawCircle(c.copy(alpha = 0.25f), sw * 2.6f, p)
                drawCircle(Color.White, sw * 0.9f, p)
            }
        }
    }
}

// S7
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TreeScreen() {
    val context = LocalContext.current
    val state = Game.state
    val today = Game.today()
    val editable = treeEditable(today)
    val slots = treeSlots(state, today.year)
    var editing by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var msg by remember { mutableStateOf("") }
    var savedVersion by remember { mutableStateOf(0) }
    val treeTime by rememberInfiniteTransition(label = "tree").animateFloat(0f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "treeTime")
    val title = "${today.year}, 나의 충전 여행"
    val sub = "올해의 기억을 골라 트리에 걸어보세요"
    val onSet: (Int, Badge?) -> Unit = { slot, b ->
        msg = runCatching {
            Game.update { setTreeSlot(it, today, slot, b) }
            ""
        }.getOrElse { "이 배지는 더 넣을 수 없어요" }
        picking = null
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1200.dp
        val sceneH = if (wide) maxHeight else 760.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (!editable) {
                Column(Modifier.padding(PAGE), verticalArrangement = GAP) {
                    Header(title, "올해의 기억을 트리에 걸어 두는 곳이에요")
                    StateCard("12월에 열려요", "트리는 12월 1~31일에만 만들고 고칠 수 있어요. 저장한 트리는 언제든 볼 수 있어요.")
                }
            } else {
                val count = slots.count { it != null }
                val controls: @Composable () -> Unit = {
                    OutlineBtn("이미지 저장") {
                        savePng(context, treeBitmap(slots, title, sub), "tree_${today.year}.png")
                        savedVersion++
                        msg = "저장했어요"
                    }
                    Btn("올해의 배지 고르기") {
                        editing = !editing
                        picking = null
                    }
                }
                val editor: @Composable (Modifier) -> Unit = { m ->
                    TreeEditor(m, slots, picking, { picking = it }, onSet) {
                        editing = false
                        picking = null
                    }
                }
                Row(Modifier.fillMaxWidth().height(sceneH)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        // Separate layers: the moving one redraws every frame without redrawing the tree under it.
                        Canvas(Modifier.fillMaxSize().graphicsLayer(), contentDescription = "연말 트리 미리보기") { drawTreeBase() }
                        Canvas(Modifier.fillMaxSize().graphicsLayer()) { drawTreeLive(slots, treeTime) }
                        Column(Modifier.padding(PAGE), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Title(title)
                            Sub(sub)
                        }
                        if (wide) {
                            if (!editing) OrnamentCard(count, Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 40.dp))
                            Row(Modifier.align(Alignment.BottomEnd).padding(40.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { controls() }
                        }
                    }
                    if (wide && editing) editor(Modifier.width(560.dp).fillMaxHeight().background(Color(0xFF0A1222)).verticalScroll(rememberScrollState()))
                }
                if (!wide) Column(Modifier.padding(PAGE), verticalArrangement = GAP) {
                    OrnamentCard(count, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { controls() }
                    if (editing) editor(Modifier.fillMaxWidth().case(28.dp))
                }
            }
            Column(Modifier.padding(PAGE), verticalArrangement = GAP) {
                if (msg.isNotEmpty()) Toast(msg)
                Heading("저장한 트리")
                val saved = remember(savedVersion) { savedImages(context, "tree_") }
                if (saved.isEmpty()) Sub("아직 없어요")
                FlowRow(horizontalArrangement = GAP, verticalArrangement = GAP) {
                    saved.forEach { f ->
                        val img = remember(f.path, savedVersion) { BitmapFactory.decodeFile(f.path) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (img != null) Image(
                                img.asImageBitmap(), f.name,
                                Modifier.width(480.dp).aspectRatio(img.width.toFloat() / img.height).clip(RoundedCornerShape(20.dp)).border(1.dp, LINE, RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Fit,
                            )
                            Note(f.nameWithoutExtension.removePrefix("tree_") + "년 트리")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrnamentCard(count: Int, modifier: Modifier) {
    GlassCard(modifier.widthIn(min = 320.dp).background(Color(0x99070B14), RoundedCornerShape(28.dp))) {
        Heading("올해의 오너먼트")
        Text("올해 고른 배지 ${count}개 / $TREE_SLOTS", fontSize = 24.sp, color = TXT)
        Note("12월 편집 · 연도별 보관")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TreeEditor(modifier: Modifier, slots: List<Badge?>, picking: Int?, onPick: (Int?) -> Unit, onSet: (Int, Badge?) -> Unit, onClose: () -> Unit) {
    val idx = Game.index
    val state = Game.state
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("오너먼트 고르기", Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = TXT)
            CloseBtn(onClose)
        }
        for (i in 0 until TREE_SLOTS) {
            val b = slots[i]
            val shape = RoundedCornerShape(18.dp)
            Row(
                Modifier.fillMaxWidth().press { onPick(i) }.clip(shape)
                    .background(if (picking == i) accent.copy(alpha = 0.12f) else Color(0x08FFFFFF))
                    .then(if (picking == i) Modifier.border(2.dp, accent, shape) else Modifier)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Medal(b ?: Badge(Design("", 0), Tier.BRONZE), 60.dp, owned = b != null)
                Column(Modifier.weight(1f)) {
                    Text("오너먼트 ${i + 1}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TXT)
                    Note(b?.let { badgeName(idx, it) } ?: "비어 있음")
                }
                OutlineBtn("바꾸기") { onPick(i) }
            }
        }
        picking?.let { slot ->
            HorizontalDivider(color = LINE)
            Heading("오너먼트 ${slot + 1}에 넣을 배지")
            OutlineBtn("비우기") { onSet(slot, null) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.owned.keys.sortedWith(compareByDescending<Badge> { it.tier }.thenBy { it.design.artKey }.thenBy { it.design.number }).forEach { b ->
                    BadgeSlot(b, state.count(b), well = 112.dp) { onSet(slot, b) }
                }
            }
        }
    }
}

private fun savedImages(context: android.content.Context, prefix: String): List<File> =
    File(context.filesDir, "images").listFiles { f -> f.name.startsWith(prefix) }.orEmpty().sortedByDescending { it.name }

// S10
@Composable
fun AnniversaryScreen() {
    val idx = Game.index
    val context = LocalContext.current
    val state = Game.state
    val today = Game.today()
    var msg by remember { mutableStateOf("") }
    val firstMet = state.firstMet ?: today
    val end = latestAnniversary(firstMet, today)?.takeIf { anniversaryOpen(firstMet, today) }
    if (end == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PAGE), verticalArrangement = GAP) {
            Eyebrow("1주년 기념")
            Header("내 차와 함께한 첫해", "앱을 설치한 날에서 1년이 지나면 그해 충전한 곳을 지도로 만들어 드려요")
            StateCard("${firstMet.plusYears(1)}에 열려요", "앱을 설치한 날 ${firstMet}부터 1년 동안 직접 충전한 곳이 지도에 이어져요.")
        }
        return
    }
    val charges = anniversaryCharges(state, today)
    // Each place shows the latest badge received there.
    val points = anniversaryPoints(state, today).map { p -> p.station to charges.filter { it.station.id == p.station.id }.maxBy { it.timeMillis }.badge }
    val label: (Badge) -> String = { shortArt(idx, it.design.artKey) }
    val period = "${dots(end.minusYears(1))} - ${dots(end.minusDays(1))}"
    val paths = Game.koreaMap?.let { MapPaths.of(it) }
    val visited = points.map { it.first.regionIndex }.toSet()
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    // screen = map * zoom + pan; the map always covers the frame.
    fun clamp(p: Offset, z: Float, frame: IntSize) = Offset(p.x.coerceIn(frame.width * (1 - z), 0f), p.y.coerceIn(frame.height * (1 - z), 0f))
    fun zoomAt(c: Offset, factor: Float, drag: Offset, frame: IntSize) {
        val z = (zoom * factor).coerceIn(1f, 6f)
        pan = clamp(c - (c - pan) / zoom * z + drag, z, frame)
        zoom = z
    }
    fun animateTo(z: Float, p: Offset) {
        val z0 = zoom
        val p0 = pan
        scope.launch {
            animate(0f, 1f, animationSpec = tween(360, easing = Emphasized)) { t, _ ->
                zoom = z0 + (z - z0) * t
                pan = p0 + (p - p0) * t
            }
        }
    }
    // The image is the screen: as large as fits above one row of controls.
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        val width = minOf(maxWidth, (maxHeight - 100.dp) * 16f / 9f)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.width(width).aspectRatio(16 / 9f).clip(RoundedCornerShape(28.dp)).border(1.dp, LINE, RoundedCornerShape(28.dp))
                    .pointerInput(Unit) { detectTransformGestures { c, drag, factor, _ -> zoomAt(c, factor, drag, size) } }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { c ->
                            if (zoom < 1.5f) animateTo(2.5f, clamp(c - c / zoom * 2.5f + (pan / zoom) * 2.5f, 2.5f, size))
                            else animateTo(1f, Offset.Zero)
                        })
                    }
                    .pointerInput(Unit) {
                        // Mouse wheel on the emulator.
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                if (e.type != PointerEventType.Scroll) continue
                                val ch = e.changes.first()
                                zoomAt(ch.position, if (ch.scrollDelta.y < 0) 1.2f else 1 / 1.2f, Offset.Zero, size)
                                e.changes.forEach { it.consume() }
                            }
                        }
                    }
                    .semantics { contentDescription = "1주년 지도. 두 손가락으로 벌리거나 두 번 눌러 확대" },
            ) {
                Canvas(Modifier.fillMaxSize()) { nightSky() }
                Canvas(
                    Modifier.fillMaxSize().graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    },
                ) { drawAnniversaryMap(paths, anniversaryProjection(points, size), visited) }
                Canvas(Modifier.fillMaxSize()) { drawAnniversaryMarks(points, label, anniversaryProjection(points, size), zoom, pan) }
                // Zoomed in, the title fades away so the whole frame is map; it comes back at full view.
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - (zoom - 1f) / 0.6f).coerceIn(0f, 1f) }) { drawAnniversaryTitle(points.size, period) }
                Canvas(Modifier.fillMaxSize()) { drawAnniversarySource() }
            }
            Row(Modifier.width(width), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Note("두 손가락으로 벌리거나 두 번 눌러 확대해요", Modifier.weight(1f))
                if (zoom > 1.01f) OutlineBtn("전체 보기") { animateTo(1f, Offset.Zero) }
                Btn("배경 이미지 저장") {
                    savePng(context, mapBitmap(1920, 1080, points, label, period, paths), "anniv_landscape.png")
                    msg = "배경 이미지를 저장했어요"
                }
            }
        }
        if (msg.isNotEmpty()) Toast(msg, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color(0x0DFFFFFF),
    unfocusedContainerColor = Color(0x08FFFFFF),
    disabledContainerColor = Color(0x05FFFFFF),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color(0x2EFFFFFF),
    disabledBorderColor = Color(0x1AFFFFFF),
    focusedTextColor = TXT,
    unfocusedTextColor = TXT,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = SUB,
    disabledLabelColor = SUB.copy(alpha = 0.7f),
    cursorColor = MaterialTheme.colorScheme.primary,
)

