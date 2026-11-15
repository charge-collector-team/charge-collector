package kr.chargebadge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kr.chargebadge.core.Badge
import kr.chargebadge.core.Design
import kr.chargebadge.core.KoreaMap
import kr.chargebadge.core.Source
import kr.chargebadge.core.Station
import kr.chargebadge.core.Tier
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Medal height / width: the ring tab rises above the disc by this much. */
const val MEDAL_H = 1.15f

/** Saved images keep the mock's accent whatever the season. */
private val ACCENT = Color(0xFFB7B8BF)
// 저장 이미지의 글자는 Compose 테마와 별개로 어두운 인쇄면에 맞춘다.
private val POSTER_TEXT = Color(0xFFF7F8FA)
private val POSTER_SUB = Color(0xFFB7B8BF)

/** Whole medals (rim, tab and picture) from assets/medals/{artKey}_{design}_{b|s|g}.webp, decoded once off the main thread. */
object Art {
    private var medals by mutableStateOf<Map<Badge, ImageBitmap>>(emptyMap())
    private var started = false

    fun medal(badge: Badge): ImageBitmap? = medals[badge]

    fun load(context: Context) {
        if (started) return
        started = true
        val assets = context.applicationContext.assets
        thread(name = "art") {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            val tiers = mapOf("b" to Tier.BRONZE, "s" to Tier.SILVER, "g" to Tier.GOLD, "l" to Tier.LIMITED)
            val whole = assets.list("medals").orEmpty().mapNotNull { name ->
                val (key, n, t) = name.substringBeforeLast('.').split('_').takeIf { it.size == 3 } ?: return@mapNotNull null
                val bmp = assets.open("medals/$name").use(BitmapFactory::decodeStream) ?: return@mapNotNull null
                bmp.setHasMipMap(true)
                Badge(Design(key, n.toIntOrNull() ?: return@mapNotNull null), tiers[t] ?: return@mapNotNull null) to bmp.asImageBitmap()
            }.toMap()
            Handler(Looper.getMainLooper()).post { medals = whole }
        }
    }
}

/**
 * Medal disc [w] wide with its box's top-left at [at]; the ring tab is drawn above the box.
 * 원본 메달의 금속 음영만 사용한다. 밝은 카드에 번지는 별도 원형 그림자는 그리지 않는다.
 */
@Suppress("UNUSED_PARAMETER") // 기존 호출 시그니처는 유지하며 모든 메달의 외부 그림자를 없앤다.
fun DrawScope.drawMedal(badge: Badge, w: Float, at: Offset = Offset.Zero, bare: Boolean = false) {
    val r = w / 2
    val c = Offset(at.x + r, at.y + r)
    val whole = Art.medal(badge)
    // Still loading: an empty disc.
    if (whole == null) return drawCircle(Color(0x14FFFFFF), r, c)
    // The disc (rim included) of the whole-medal image lands on this medal's disc; its tab rises above.
    val iw = w / WHOLE_DISC_W
    val ih = iw * whole.height / whole.width
    drawImage(
        whole,
        dstOffset = IntOffset((c.x - WHOLE_DISC_X * iw).roundToInt(), (c.y - WHOLE_DISC_Y * ih).roundToInt()),
        dstSize = IntSize(iw.roundToInt(), ih.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

/** Source as a white glyph in a navy circle: shield-check = charge, arrows = trade, sparkle = synthesis. */
fun DrawScope.drawSourceMark(s: Source, c: Offset, r: Float) {
    fun p(x: Float, y: Float) = Offset(c.x + x * r, c.y + y * r)
    drawCircle(Color(0xFF26272B), r * 1.14f, c)
    drawCircle(NAVY, r, c)
    val st = Stroke(r * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (s) {
        Source.CHARGE -> {
            val shield = Path().apply {
                moveTo(c.x, c.y - r * 0.58f)
                lineTo(c.x + r * 0.44f, c.y - r * 0.4f)
                lineTo(c.x + r * 0.44f, c.y)
                cubicTo(c.x + r * 0.44f, c.y + r * 0.3f, c.x + r * 0.2f, c.y + r * 0.5f, c.x, c.y + r * 0.6f)
                cubicTo(c.x - r * 0.2f, c.y + r * 0.5f, c.x - r * 0.44f, c.y + r * 0.3f, c.x - r * 0.44f, c.y)
                lineTo(c.x - r * 0.44f, c.y - r * 0.4f)
                close()
            }
            drawPath(shield, Color.White, style = st)
            val check = Path().apply { moveTo(p(-0.2f, 0f).x, p(-0.2f, 0f).y); lineTo(p(-0.04f, 0.16f).x, p(-0.04f, 0.16f).y); lineTo(p(0.22f, -0.15f).x, p(0.22f, -0.15f).y) }
            drawPath(check, Color.White, style = st)
        }
        Source.TRADE -> {
            drawLine(Color.White, p(-0.45f, -0.2f), p(0.45f, -0.2f), st.width, StrokeCap.Round)
            drawLine(Color.White, p(0.45f, -0.2f), p(0.24f, -0.4f), st.width, StrokeCap.Round)
            drawLine(Color.White, p(0.45f, 0.22f), p(-0.45f, 0.22f), st.width, StrokeCap.Round)
            drawLine(Color.White, p(-0.45f, 0.22f), p(-0.24f, 0.42f), st.width, StrokeCap.Round)
        }
        Source.SYNTHESIS -> {
            val k = 0.12f
            val star = Path().apply {
                moveTo(p(0f, -0.6f).x, p(0f, -0.6f).y)
                cubicTo(p(k, -k).x, p(k, -k).y, p(k, -k).x, p(k, -k).y, p(0.6f, 0f).x, p(0.6f, 0f).y)
                cubicTo(p(k, k).x, p(k, k).y, p(k, k).x, p(k, k).y, p(0f, 0.6f).x, p(0f, 0.6f).y)
                cubicTo(p(-k, k).x, p(-k, k).y, p(-k, k).x, p(-k, k).y, p(-0.6f, 0f).x, p(-0.6f, 0f).y)
                cubicTo(p(-k, -k).x, p(-k, -k).y, p(-k, -k).x, p(-k, -k).y, p(0f, -0.6f).x, p(0f, -0.6f).y)
                close()
            }
            drawPath(star, Color.White)
        }
    }
}

private fun textPaint(color: Color, size: Float, bold: Boolean = false, center: Boolean = false, face: Typeface? = null) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    textSize = size
    typeface = face ?: if (bold) TextFaceBold else TextFace
    if (center) textAlign = Paint.Align.CENTER
}

private fun DrawScope.text(s: String, x: Float, y: Float, paint: Paint) = drawContext.canvas.nativeCanvas.drawText(s, x, y, paint)

fun DrawScope.nightSky() {
    drawRect(Color(0xFF151618))
}

/** The tree's box in a scene; q() maps tree fractions to pixels. */
private class TreeBox(w: Float, h: Float) {
    val th: Float
    val tw: Float
    val left: Float
    val top: Float

    init {
        var th0 = h * 0.8f
        var tw0 = th0 * 0.92f
        if (tw0 > w * 0.9f) {
            tw0 = w * 0.9f
            th0 = tw0 / 0.92f
        }
        th = th0
        tw = tw0
        left = (w - tw) / 2
        // Bottom-aligned, but lifted toward the middle when there is height to spare (keeps clear of the buttons).
        top = min(h * 0.93f - th, max(h * 0.1f, (h - th) / 2))
    }

    fun q(x: Float, y: Float) = Offset(left + x * tw, top + y * th)
}

private const val TIERS = 5
private fun tierTop(i: Int) = 0.08f + i * 0.15f
private fun tierBottom(i: Int) = tierTop(i) + 0.27f
private fun tierHalf(i: Int) = 0.12f + i * 0.088f
private fun halfAt(i: Int, y: Float) = tierHalf(i) * ((y - tierTop(i)) / (tierBottom(i) - tierTop(i))).coerceIn(0f, 1f)

/** Hook points of the six ornaments, as tree fractions. */
private val HOOKS = listOf(0.5f to 0.31f, 0.33f to 0.47f, 0.67f to 0.48f, 0.22f to 0.65f, 0.5f to 0.68f, 0.78f to 0.65f)

/** Ornament width in a tree [th] tall; an ornament hangs this far below its hook. */
private fun ornamentWidth(th: Float) = th * 0.135f
private fun ornamentCenter(box: TreeBox, i: Int): Offset {
    val (hx, hy) = HOOKS[i]
    return box.q(hx, hy) + Offset(0f, box.th * 0.04f + ornamentWidth(box.th) * 0.65f)
}

/** Center of ornament [i] in a tree scene of [size] (before its sway). */
fun treeOrnamentCenter(size: Size, i: Int): Offset = ornamentCenter(TreeBox(size.width, size.height), i)

/** The ornament under [tap] in a tree scene of [size], if any. */
fun treeOrnamentAt(size: Size, tap: Offset): Int? {
    val box = TreeBox(size.width, size.height)
    return HOOKS.indices.firstOrNull { (ornamentCenter(box, it) - tap).getDistance() < ornamentWidth(box.th) * 0.6f }
}
private val BULB_COLORS = listOf(Color(0xFFFFE3A0), Color(0xFFFFF6E0), Color(0xFF7FD6FF), Color(0xFFFFA48C))

/** A sagging light string on each tier but the top one: start, control, end, as tree fractions. */
private val GARLANDS = (1 until TIERS).map { i ->
    val y = tierTop(i) + (tierBottom(i) - tierTop(i)) * 0.55f
    val hw = halfAt(i, y) * 0.96f
    Triple(0.5f - hw to y - 0.01f, 0.5f to y + 0.075f, 0.5f + hw to y + 0.012f)
}

/** Bulbs along the garlands: tree fraction, color, twinkle phase. */
private val BULBS = run {
    val rnd = Random(11)
    GARLANDS.flatMap { (a, c, b) ->
        val n = ((b.first - a.first) / 0.045f).toInt().coerceAtLeast(4)
        (0..n).map { k ->
            val t = k / n.toFloat()
            val x = (1 - t) * (1 - t) * a.first + 2 * (1 - t) * t * c.first + t * t * b.first
            val y = (1 - t) * (1 - t) * a.second + 2 * (1 - t) * t * c.second + t * t * b.second
            Triple(x to y, BULB_COLORS[(k + GARLANDS.size) % BULB_COLORS.size], rnd.nextFloat())
        }
    }
}

private fun quad(a: Offset, c: Offset, b: Offset) = Path().apply {
    moveTo(a.x, a.y)
    quadraticTo(c.x, c.y, b.x, b.y)
}

/**
 * What stays still in the year-end scene: sky, moon, snowy ground, trunk, the tiers with snow on their hems,
 * the garland wires and the gifts. Drawn once; [drawTreeLive] moves on top of it.
 */
fun DrawScope.drawTreeBase() {
    val w = size.width
    val h = size.height
    val u = min(w, h)
    val box = TreeBox(w, h)
    fun q(x: Float, y: Float) = box.q(x, y)
    val th = box.th
    val tw = box.tw
    drawRect(Brush.verticalGradient(listOf(Color(0xFF10204A), Color(0xFF0A1430), Color(0xFF050915))))
    // moon and the light it throws on the tree's left
    val moon = Offset(w * 0.86f, h * 0.13f)
    drawCircle(Brush.radialGradient(0f to Color(0x33BFD4FF), 1f to Color.Transparent, center = moon, radius = u * 0.3f), u * 0.3f, moon)
    drawCircle(Brush.radialGradient(0f to Color(0xFFF4F7FF), 0.8f to Color(0xFFD8E2F5), 1f to Color(0xFFB9C8E6), center = moon - Offset(u * 0.01f, u * 0.01f), radius = u * 0.045f), u * 0.04f, moon)
    val warm = q(0.5f, 0.62f)
    drawCircle(Brush.radialGradient(0f to Color(0x33FFB45A), 1f to Color.Transparent, center = warm, radius = th * 0.7f), th * 0.7f, warm)

    // snowy ground: a soft hill, highest under the tree
    val groundY = q(0f, 0.955f).y
    val ground = Path().apply {
        moveTo(0f, groundY + u * 0.05f)
        cubicTo(w * 0.25f, groundY - u * 0.005f, w * 0.75f, groundY - u * 0.005f, w, groundY + u * 0.04f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(ground, Brush.verticalGradient(listOf(Color(0xFFDCE7F7), Color(0xFFA9BCD8), Color(0xFF6F86AA)), groundY - u * 0.01f, h))
    drawOval(Color(0x553A4E78), q(0.12f, 0.93f), Size(tw * 0.76f, th * 0.06f))
    drawRect(Brush.horizontalGradient(listOf(Color(0xFF2A1606), Color(0xFF5A3414), Color(0xFF2A1606)), q(0.455f, 0f).x, q(0.545f, 0f).x), q(0.455f, 0.82f), Size(tw * 0.09f, th * 0.15f))

    // tiers, bottom first so each upper hem lies over the one below with a soft shadow
    val rnd = Random(7)
    for (i in TIERS - 1 downTo 0) {
        val yTop = tierTop(i)
        val yBot = tierBottom(i)
        val hw = tierHalf(i)
        val n = 5 + i * 2
        val hem = Path()
        val tier = Path().apply {
            val a = q(0.5f, yTop)
            moveTo(a.x, a.y)
            val r0 = q(0.5f + hw, yBot - 0.015f)
            quadraticTo(q(0.5f + hw * 0.45f, yTop + (yBot - yTop) * 0.55f).x, q(0f, yTop + (yBot - yTop) * 0.55f).y, r0.x, r0.y)
            hem.moveTo(r0.x, r0.y)
            // round scallops along the hem, right to left
            for (k in 0 until n) {
                val x0 = 0.5f + hw - k * (2 * hw / n)
                val x1 = x0 - 2 * hw / n
                val c = q((x0 + x1) / 2, yBot + 0.035f)
                val e = q(x1, yBot - 0.015f)
                quadraticTo(c.x, c.y, e.x, e.y)
                hem.quadraticTo(c.x, c.y, e.x, e.y)
            }
            quadraticTo(q(0.5f - hw * 0.45f, 0f).x, q(0f, yTop + (yBot - yTop) * 0.55f).y, a.x, a.y)
            close()
        }
        translate(0f, th * 0.02f) { drawPath(tier, Color(0x99000814)) }
        drawPath(tier, Brush.linearGradient(listOf(Color(0xFF4FA866), Color(0xFF237A45), Color(0xFF0E3F25)), q(0.5f - hw, yTop), q(0.5f + hw, yBot)))
        // needles: short strokes, lighter on the moonlit side
        repeat(20 + i * 10) {
            val y = yTop + 0.06f + rnd.nextFloat() * (yBot - yTop - 0.08f)
            val x = 0.5f + (rnd.nextFloat() * 2 - 1) * halfAt(i, y) * 0.9f
            val dir = if (x < 0.5f) -1f else 1f
            drawLine(if (x < 0.5f) Color(0x405FC07E) else Color(0x40021208), q(x, y), q(x + dir * 0.014f, y + 0.02f), tw * 0.004f, StrokeCap.Round)
        }
        // moonlight along the left slope
        drawPath(quad(q(0.5f, yTop), q(0.5f - hw * 0.45f, yTop + (yBot - yTop) * 0.55f), q(0.5f - hw, yBot - 0.015f)), Color(0x40E6F0FF), style = Stroke(tw * 0.004f, cap = StrokeCap.Round))
        // snow along the hem: a soft white rim with a faint blue shade under it
        translate(0f, th * 0.004f) { drawPath(hem, Color(0x668FA7CC), style = Stroke(th * 0.014f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        drawPath(hem, Color(0xF2F2F7FF), style = Stroke(th * 0.011f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    // garland wires; the bulbs on them light up in drawTreeLive
    GARLANDS.forEach { (a, c, b) -> drawPath(quad(q(a.first, a.second), q(c.first, c.second), q(b.first, b.second)), Color(0x99241A0E), style = Stroke(tw * 0.0035f)) }

    // gifts on the snow: x center, width, height (tree fractions), box and ribbon colors
    listOf(
        Triple(0.27f, 0.17f to 0.11f, Color(0xFF2C9BD1) to Color(0xFFE8C66A)),
        Triple(0.40f, 0.11f to 0.075f, Color(0xFFE0705A) to Color(0xFFF4F7FF)),
        Triple(0.70f, 0.15f to 0.095f, Color(0xFFD9A93E) to Color(0xFF2C9BD1)),
    ).forEach { (cx, dims, colors) ->
        val (bw, bh) = dims
        val (body, ribbon) = colors
        val bottom = q(0f, 0.985f).y
        val l = q(cx - bw / 2, 0f).x
        val bwPx = bw * tw
        val bhPx = bh * th
        val top = bottom - bhPx
        drawRect(Color(0x55000814), Offset(l + bwPx * 0.06f, bottom - th * 0.004f), Size(bwPx, th * 0.012f))
        drawRoundRect(Brush.verticalGradient(listOf(body, body.copy(red = body.red * 0.7f, green = body.green * 0.7f, blue = body.blue * 0.7f)), top, bottom), Offset(l, top), Size(bwPx, bhPx), CornerRadius(u * 0.006f))
        drawRoundRect(body.copy(red = min(1f, body.red * 1.12f), green = min(1f, body.green * 1.12f), blue = min(1f, body.blue * 1.12f)), Offset(l - bwPx * 0.04f, top - bhPx * 0.04f), Size(bwPx * 1.08f, bhPx * 0.24f), CornerRadius(u * 0.006f))
        drawRect(ribbon, Offset(l + bwPx * 0.44f, top - bhPx * 0.04f), Size(bwPx * 0.12f, bhPx * 1.04f))
        val knot = Offset(l + bwPx / 2, top - bhPx * 0.04f)
        val loop = bhPx * 0.28f
        drawOval(ribbon, Offset(knot.x - loop * 1.7f, knot.y - loop * 0.9f), Size(loop * 1.6f, loop))
        drawOval(ribbon, Offset(knot.x + loop * 0.1f, knot.y - loop * 0.9f), Size(loop * 1.6f, loop))
        drawCircle(ribbon.copy(alpha = 1f), loop * 0.3f, knot)
    }
}

/**
 * What moves, for [time] running 0..1 over a 60-second loop: twinkling stars and bulbs, the star's glow, ornaments
 * swaying on their ribbons, and falling snow. Every motion repeats a whole number of times per loop, so it never jumps.
 */
fun DrawScope.drawTreeLive(slots: List<Badge?>, time: Float) {
    val w = size.width
    val h = size.height
    val u = min(w, h)
    val box = TreeBox(w, h)
    fun q(x: Float, y: Float) = box.q(x, y)
    val th = box.th
    val tau = 2 * Math.PI.toFloat()
    val rnd = Random(3)
    repeat(80) {
        val p = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.62f)
        val tw = 0.55f + 0.45f * sin(tau * (time * (25 + rnd.nextInt(30)) + rnd.nextFloat()))
        drawCircle(Color.White.copy(alpha = (0.15f + rnd.nextFloat() * 0.45f) * tw), u * (0.0012f + rnd.nextFloat() * 0.002f), p)
    }
    BULBS.forEach { (f, color, phase) ->
        val c = q(f.first, f.second)
        val glow = 0.35f + 0.65f * (0.5f + 0.5f * sin(tau * (time * 45 + phase)))
        // Stacked plain circles instead of a gradient: the emulator's GPU is slow at gradients.
        drawCircle(color.copy(alpha = 0.12f * glow), th * 0.028f, c)
        drawCircle(color.copy(alpha = 0.3f * glow), th * 0.016f, c)
        drawCircle(color.copy(alpha = 0.55f + 0.45f * glow), th * 0.0085f, c)
    }

    // star: a pulsing glow, four slow sparkle rays, a bevelled body
    val sc = q(0.5f, 0.05f)
    val so = th * 0.055f
    val pulse = 0.5f + 0.5f * sin(tau * time * 30)
    val halo = th * (0.11f + 0.015f * pulse)
    drawCircle(Brush.radialGradient(0f to Color(0x99FFD98A), 1f to Color.Transparent, center = sc, radius = halo), halo, sc)
    rotate(time * 360f * 2, sc) {
        for (k in 0 until 4) {
            rotate(k * 90f, sc) {
                drawPath(Path().apply {
                    moveTo(sc.x, sc.y - so * 2.4f)
                    lineTo(sc.x + so * 0.07f, sc.y)
                    lineTo(sc.x, sc.y + so * 0.2f)
                    lineTo(sc.x - so * 0.07f, sc.y)
                    close()
                }, Color(0xFFFFF4D6).copy(alpha = 0.35f + 0.3f * pulse))
            }
        }
    }
    val tips = (0 until 10).map { k ->
        val rr = if (k % 2 == 0) so else so * 0.42f
        val ang = Math.toRadians(-90.0 + k * 36.0)
        Offset(sc.x + rr * cos(ang).toFloat(), sc.y + rr * sin(ang).toFloat())
    }
    for (k in 0 until 10) {
        // Each point's two halves: lit facing up-left, shaded facing down-right.
        val tri = Path().apply { moveTo(sc.x, sc.y); lineTo(tips[k].x, tips[k].y); lineTo(tips[(k + 1) % 10].x, tips[(k + 1) % 10].y); close() }
        drawPath(tri, if (k in listOf(9, 0, 1, 2, 7, 8)) Color(0xFFFFE9A8) else Color(0xFFD9A23C))
    }

    // ornaments hang from a gold bow and sway a few degrees
    val mw = ornamentWidth(th)
    HOOKS.forEachIndexed { i, (hx, hy) ->
        val hook = q(hx, hy)
        val sway = 3.5f * sin(tau * (time * 20 + i * 0.17f))
        rotate(sway, hook) {
            val c = ornamentCenter(box, i)
            drawLine(Color(0xFFE8C66A), hook, Offset(c.x, c.y - mw * 0.62f), th * 0.003f)
            val b = slots.getOrNull(i)
            if (b != null) {
                drawCircle(Brush.radialGradient(0f to Color(0x55FFE0A0), 1f to Color.Transparent, center = c, radius = mw * 0.9f), mw * 0.9f, c)
                translate(c.x - mw / 2, c.y - mw / 2) { drawMedalCached(b, mw) }
            } else {
                // an empty glass bauble waits for a badge
                val r = mw * 0.4f
                drawCircle(Color(0x14FFFFFF), r, c)
                drawCircle(Color(0x59DCE6F5), r, c, style = Stroke(th * 0.0025f))
                drawArc(Color(0x99FFFFFF), 200f, 60f, false, Offset(c.x - r * 0.75f, c.y - r * 0.75f), Size(r * 1.5f, r * 1.5f), style = Stroke(r * 0.08f, cap = StrokeCap.Round))
            }
        }
        // a red bow: two loops, two tails, a knot
        val loop = th * 0.014f
        val red = Color(0xFFD6404A)
        val dark = Color(0xFF9E2533)
        drawPath(Path().apply { moveTo(hook.x, hook.y); lineTo(hook.x - loop * 1.1f, hook.y + loop * 1.9f); lineTo(hook.x - loop * 0.4f, hook.y + loop * 1.7f); close() }, dark)
        drawPath(Path().apply { moveTo(hook.x, hook.y); lineTo(hook.x + loop * 1.1f, hook.y + loop * 1.9f); lineTo(hook.x + loop * 0.4f, hook.y + loop * 1.7f); close() }, dark)
        rotate(-18f, hook) { drawOval(red, Offset(hook.x - loop * 2.2f, hook.y - loop * 0.7f), Size(loop * 2.1f, loop * 1.4f)) }
        rotate(18f, hook) { drawOval(red, Offset(hook.x + loop * 0.1f, hook.y - loop * 0.7f), Size(loop * 2.1f, loop * 1.4f)) }
        drawCircle(dark, loop * 0.5f, hook)
        drawCircle(Color(0x66FFFFFF), loop * 0.18f, hook - Offset(loop * 0.15f, loop * 0.15f))
    }

    // falling snow: near flakes bigger, faster and brighter
    val snow = Random(5)
    repeat(110) {
        val depth = snow.nextFloat()
        val passes = 3 + (depth * 4).toInt()
        val y = ((snow.nextFloat() + time * passes) % 1f) * h * 1.05f - h * 0.025f
        val x = snow.nextFloat() * w + sin(tau * (time * (4 + snow.nextInt(4)) + snow.nextFloat())) * u * 0.02f
        drawCircle(Color.White.copy(alpha = 0.25f + depth * 0.55f), u * (0.0018f + depth * 0.0042f), Offset(x, y))
    }
}


private const val KX = 0.80f

/** Where lng/lat of the anniversary map land on the image at zoom 1; [mapLeft]..[mapRight] is the window's width there. */
class MapProjection(private val lng0: Float, private val lat1: Float, val scale: Float, private val ox: Float, private val oy: Float, val mapLeft: Float, val mapRight: Float) {
    fun pt(lng: Double, lat: Double) = Offset(ox + (lng.toFloat() - lng0) * KX * scale, oy + (lat1 - lat.toFloat()) * scale)

    /** Draws [block] in world units (x = lng * KX, y = -lat), the units [MapPaths] are built in. */
    fun DrawScope.inWorld(block: DrawScope.() -> Unit) = withTransform({
        translate(ox - lng0 * KX * scale, oy + lat1 * scale)
        scale(scale, scale, Offset.Zero)
    }, block)
}

/**
 * The map part of the anniversary image is the left 60%. It always shows the mainland, its nearby islands and 제주
 * (from korea_map.txt: lng 126.0..129.58, lat 33.1..38.61), plus any visited place beyond, so nothing is cut off;
 * zooming in gives the detail.
 */
fun anniversaryProjection(points: List<Pair<Station, Badge>>, size: Size): MapProjection {
    val area = Rect(0f, 0f, size.width * 0.6f, size.height)
    fun window(values: List<Double>, lo: Double, hi: Double) =
        ((values + lo).min() - 0.15).toFloat() to ((values + hi).max() + 0.15).toFloat()
    val (lat0, lat1) = window(points.map { it.first.lat }, 33.1, 38.61)
    val (lng0, lng1) = window(points.map { it.first.lng }, 126.0, 129.58)
    val scale = min(area.width * 0.92f / ((lng1 - lng0) * KX), area.height * 0.94f / (lat1 - lat0))
    val ox = area.center.x - (lng1 - lng0) * KX * scale / 2
    return MapProjection(lng0, lat1, scale, ox, area.center.y - (lat1 - lat0) * scale / 2, ox, ox + (lng1 - lng0) * KX * scale)
}

/** [KoreaMap] as paths in world units, built once per map. */
class MapPaths private constructor(map: KoreaMap) {
    private val rings = map.rings
    private val regions = HashMap<Int, Path>()
    val land = Path().apply { fillType = PathFillType.EvenOdd; rings.forEach { ring(it.points) } }
    val coast = lines(map.coast)
    val province = lines(map.provinceBorders)
    val municipal = lines(map.municipalBorders)

    fun region(index: Int): Path = regions.getOrPut(index) {
        Path().apply { fillType = PathFillType.EvenOdd; rings.filter { index in it.regions }.forEach { ring(it.points) } }
    }

    private fun Path.ring(p: FloatArray) {
        moveTo(p[0] * KX, -p[1])
        for (i in 2 until p.size step 2) lineTo(p[i] * KX, -p[i + 1])
        close()
    }

    private fun lines(all: List<FloatArray>) = Path().apply {
        all.forEach { p ->
            moveTo(p[0] * KX, -p[1])
            for (i in 2 until p.size step 2) lineTo(p[i] * KX, -p[i + 1])
        }
    }

    companion object {
        private var cached: Pair<KoreaMap, MapPaths>? = null

        fun of(map: KoreaMap): MapPaths = cached?.takeIf { it.first === map }?.second ?: MapPaths(map).also { cached = map to it }
    }
}

/** Land, the regions charged in, and borders, all in world units so the layer can be zoomed as one. */
fun DrawScope.drawAnniversaryMap(paths: MapPaths?, proj: MapProjection, visited: Set<Int>) {
    paths ?: return
    with(proj) {
        inWorld {
            drawPath(paths.land, Color(0xFF292B30))
            visited.forEach { drawPath(paths.region(it), HIGHLIGHT.copy(alpha = 0.42f)) }
            // Width 0 is a hairline: one pixel at any zoom.
            drawPath(paths.municipal, Color(0x1AFFFFFF), style = Stroke(0f))
            drawPath(paths.province, Color(0x47FFFFFF), style = Stroke(0f))
            drawPath(paths.coast, Color(0xFF777A83), style = Stroke(0f))
        }
    }
}

/**
 * The first-visit route and the medals, in image space. At full view the medals stand in two columns beside
 * the map, tied to their places by leader lines, so the map stays visible; zooming in ([zoom] past 1.8) slides
 * them onto their places. [pan] is the zoom layer's translation.
 */
fun DrawScope.drawAnniversaryMarks(
    points: List<Pair<Station, Badge>>,
    label: (Badge) -> String,
    proj: MapProjection,
    zoom: Float = 1f,
    pan: Offset = Offset.Zero,
    selected: Int? = null,
) {
    val u = min(size.width, size.height)
    val (ps, at, mw) = anniversaryMarkLayout(points, proj, size, zoom, pan, selected)
    ps.zipWithNext().forEach { (a, b) ->
        drawLine(HIGHLIGHT.copy(alpha = 0.25f), a, b, u * 0.022f, StrokeCap.Round)
        drawLine(HIGHLIGHT, a, b, u * 0.006f, StrokeCap.Round)
    }
    ps.indices.forEach { if (it != selected && at[it] != ps[it]) drawLine(POSTER_TEXT.copy(alpha = 0.4f), ps[it], at[it], u * 0.0025f) }
    ps.forEach { drawCircle(HIGHLIGHT, u * 0.008f, it) }
    val tag = textPaint(POSTER_TEXT, mw * 0.34f, bold = true, center = true)
    fun medal(i: Int) {
        val q = at[i]
        drawCircle(Brush.radialGradient(0f to ACCENT.copy(alpha = 0.35f), 1f to Color.Transparent, center = q, radius = mw), mw, q)
        translate(q.x - mw / 2, q.y - mw / 2) { drawMedalCached(points[i].second, mw) }
    }
    points.indices.filter { it != selected }.forEach { i ->
        medal(i)
        text(label(points[i].second), at[i].x, at[i].y + mw / 2 + mw * 0.43f, tag)
    }
    // The selected place: a pin at the station, its medal on a stem above it and the station's name below.
    selected?.let { i ->
        val p = ps[i]
        drawLine(HIGHLIGHT, p, at[i] + Offset(0f, mw / 2), u * 0.004f)
        drawCircle(HIGHLIGHT.copy(alpha = 0.3f), u * 0.024f, p)
        drawCircle(Color.White, u * 0.01f, p)
        drawCircle(HIGHLIGHT, u * 0.01f, p, style = Stroke(u * 0.004f))
        medal(i)
        val name = points[i].first.name
        val paint = textPaint(POSTER_TEXT, mw * 0.3f, bold = true, center = true)
        val tw = paint.measureText(name)
        val base = p.y + u * 0.03f + mw * 0.32f
        drawRoundRect(Color(0xE6151618), Offset(p.x - tw / 2 - mw * 0.22f, base - mw * 0.36f), Size(tw + mw * 0.44f, mw * 0.5f), CornerRadius(mw * 0.25f))
        drawRoundRect(HIGHLIGHT.copy(alpha = 0.6f), Offset(p.x - tw / 2 - mw * 0.22f, base - mw * 0.36f), Size(tw + mw * 0.44f, mw * 0.5f), CornerRadius(mw * 0.25f), style = Stroke(u * 0.002f))
        text(name, p.x, base, paint)
    }
}

/** Index of the anniversary medal under [tap] (image space), as [drawAnniversaryMarks] places them. */
fun anniversaryMarkAt(points: List<Pair<Station, Badge>>, proj: MapProjection, size: Size, zoom: Float, pan: Offset, selected: Int?, tap: Offset): Int? {
    val (_, at, mw) = anniversaryMarkLayout(points, proj, size, zoom, pan, selected)
    return at.indices.minByOrNull { (at[it] - tap).getDistance() }?.takeIf { (at[it] - tap).getDistance() < mw * 0.65f }
}

/** Places on the image, medal centers and medal width. */
private data class MarkLayout(val places: List<Offset>, val medals: List<Offset>, val width: Float)

private fun anniversaryMarkLayout(points: List<Pair<Station, Badge>>, proj: MapProjection, size: Size, zoom: Float, pan: Offset, selected: Int?): MarkLayout {
    val u = min(size.width, size.height)
    val area = Rect(0f, 0f, size.width * 0.6f, size.height)
    val ps = points.map { (st, _) -> proj.pt(st.lng, st.lat) * zoom + pan }
    if (ps.isEmpty()) return MarkLayout(ps, ps, 0f)
    val top = u * 0.08f
    val bottom = size.height - u * 0.12f
    val perColumn = (ps.size + 1) / 2
    // A slot holds the ring tab above the medal and the label below it.
    val mw = min(u * 0.1f, (bottom - top) / max(1, perColumn - 1) / 1.8f)
    val gap = mw * 1.8f

    // Columns in the sea on each side of the map window; each place goes to the nearer side, at most half per side.
    val westX = max(area.left + mw * 0.75f, (area.left + proj.mapLeft) / 2)
    val eastX = min(area.right - mw * 0.75f, (proj.mapRight + area.right) / 2)
    val byX = ps.indices.sortedBy { ps[it].x }
    val westCount = byX.count { ps[it].x < (proj.mapLeft + proj.mapRight) / 2 * zoom + pan.x }.coerceIn(ps.size - perColumn, perColumn)
    val slot = arrayOfNulls<Offset>(ps.size)
    listOf(byX.take(westCount) to westX, byX.drop(westCount) to eastX).forEach { (column, x) ->
        // Keep vertical order, then push apart: down from the top, up from the bottom.
        val order = column.sortedBy { ps[it].y }
        val ys = order.map { ps[it].y }.toMutableList()
        for (i in ys.indices) ys[i] = max(ys[i], if (i == 0) top else ys[i - 1] + gap)
        for (i in ys.indices.reversed()) ys[i] = min(ys[i], if (i == ys.lastIndex) bottom else ys[i + 1] - gap)
        order.forEachIndexed { i, k -> slot[k] = Offset(x, ys[i]) }
    }

    // On their places, nearby medals (a metro area) would stack: push each out on a spiral until it clears the others.
    val near = mutableListOf<Offset>()
    val onPlace = ps.map { p ->
        var q = p
        var k = 0
        while (near.any { (it - q).getDistance() < mw * 1.6f } && k < 16) {
            val a = Math.toRadians(-30.0 + k * 50.0)
            q = p + Offset(cos(a).toFloat(), sin(a).toFloat()) * (mw * (1.4f + k * 0.2f))
            k++
        }
        near += q
        q
    }
    val t = ((zoom - 1f) / 0.8f).coerceIn(0f, 1f)
    // On its place, the selected medal rises above the station so the pin under it shows.
    return MarkLayout(ps, ps.indices.map { slot[it]!! + (onPlace[it] - slot[it]!!) * t + if (it == selected) Offset(0f, -mw * 1.1f) * t else Offset.Zero }, mw)
}

/** Title on a fade into the night, not on a separate panel. */
fun DrawScope.drawAnniversaryTitle(count: Int, period: String) {
    val w = size.width
    val h = size.height
    val u = min(w, h)
    val night = Color(0xFF151618)
    drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.4f to night.copy(alpha = 0.8f), 1f to night, startX = w * 0.5f, endX = w), Offset(w * 0.5f, 0f), Size(w * 0.5f, h))
    val pad = u * 0.07f
    val x0 = w * 0.58f + pad
    text("우리의 1주년", x0, pad + u * 0.09f, textPaint(POSTER_TEXT, u * 0.095f, bold = true))
    text("차와 함께 쌓은 충전의 기억", x0, pad + u * 0.165f, textPaint(ACCENT, u * 0.042f, bold = true))
    val mid = h * 0.45f
    text("1년 동안 충전하며 다닌 길", x0, mid, textPaint(POSTER_TEXT, u * 0.05f, bold = true))
    text("직접 충전한 장소 ${count}곳을", x0, mid + u * 0.065f, textPaint(POSTER_SUB, u * 0.036f))
    text("첫 방문 순서로 이었어요", x0, mid + u * 0.112f, textPaint(POSTER_SUB, u * 0.036f))
    text(period, x0, h - pad, textPaint(ACCENT, u * 0.042f, face = NumFaceBold))
}

/** The boundary data's source, bottom right; 공공누리 제1유형 asks for it on every use. */
fun DrawScope.drawAnniversarySource() {
    val u = min(size.width, size.height)
    val paint = textPaint(POSTER_SUB.copy(alpha = 0.75f), u * 0.022f)
    val s = "지도: 통계청 통계지리정보서비스(2018)"
    text(s, size.width - u * 0.03f - paint.measureText(s), size.height - u * 0.025f, paint)
}

private data class MedalKey(val badge: Badge, val px: Int, val art: ImageBitmap?)

// Where the disc sits in a whole-medal image, as fractions: center x, center y, and diameter over the image width (measured by tools/cut_medals.py).
private const val WHOLE_DISC_X = 0.5f
private const val WHOLE_DISC_Y = 0.5405f
private const val WHOLE_DISC_W = 0.916f

// ponytail: fixed 48MB budget; a large showcase at 148dp is about 0.2MB per medal.
private val medalCache = object : LruCache<MedalKey, ImageBitmap>(48 * 1024 * 1024) {
    override fun sizeOf(key: MedalKey, value: ImageBitmap) = value.width * value.height * 4
}

/**
 * [drawMedal] for a [px] wide medal, rendered once per badge, size and art: the emulator's GPU
 * is slow at the gradients and the oval clip, and screens draw many medals.
 * The image is larger than [px] by the same margin on every side.
 */
fun medalImage(badge: Badge, px: Int): ImageBitmap {
    val key = MedalKey(badge, px, Art.medal(badge))
    // 원본 메달 이미지의 여백과 위로 솟은 고리가 잘리지 않도록 같은 캐시 여백을 유지한다.
    val pad = (px * 0.3f).roundToInt()
    return medalCache.get(key) ?: drawToBitmap(px + 2 * pad, px + 2 * pad) { drawMedal(badge, px.toFloat(), Offset(pad.toFloat(), pad.toFloat())) }
        .asImageBitmap().also { medalCache.put(key, it) }
}

fun DrawScope.drawMedalCached(badge: Badge, w: Float, greyed: Boolean = false) {
    val px = w.roundToInt().coerceAtLeast(1)
    val img = medalImage(badge, px)
    val pad = (img.width - px) / 2f
    if (greyed) drawImage(img, Offset(-pad, -pad), alpha = 0.45f, colorFilter = GREY) else drawImage(img, Offset(-pad, -pad))
}

private val GREY = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Renders [draw] into a bitmap of the given size, so saved images match what the screen shows. */
fun drawToBitmap(width: Int, height: Int, draw: DrawScope.() -> Unit): Bitmap {
    val img = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(width.toFloat(), height.toFloat()), draw)
    return img.asAndroidBitmap()
}

