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
import androidx.compose.ui.graphics.drawscope.clipPath
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
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Medal height / width: the ring tab rises above the disc by this much. */
const val MEDAL_H = 1.15f

private val CREAM = Color(0xFFF3F1EA)
private val CYAN = Color(0xFF1BA5D0)
private val LIGHT_BLUE = Color(0xFFA9C9E6)
private val PALE_BLUE = Color(0xFFDCE8F3)
private val TEAL = Color(0xFF3B7F86)
/** Saved images keep the mock's accent whatever the season. */
private val ACCENT = Color(0xFF3FB6E8)

/**
 * Drawn badge art from assets/art/{artKey}_{design}.png, decoded once off the main thread.
 * Medals without a file fall back to the vector motif; until loading ends every medal uses the motif.
 */
object Art {
    private var images by mutableStateOf<Map<Design, ImageBitmap>>(emptyMap())
    private var started = false

    fun of(design: Design): ImageBitmap? = images[design]

    fun load(context: Context) {
        if (started) return
        started = true
        val assets = context.applicationContext.assets
        thread(name = "art") {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            val loaded = assets.list("art").orEmpty().mapNotNull { name ->
                val (key, n) = name.removeSuffix(".png").split('_').takeIf { it.size == 2 } ?: return@mapNotNull null
                val bmp = assets.open("art/$name").use(BitmapFactory::decodeStream) ?: return@mapNotNull null
                bmp.setHasMipMap(true)
                Design(key, n.toIntOrNull() ?: return@mapNotNull null) to bmp.asImageBitmap()
            }.toMap()
            Handler(Looper.getMainLooper()).post { images = loaded }
        }
    }
}

private class Metal(val loop: Color, val stops: List<Color>)

// Conic stops and ring-tab colors from mock A (.medal.b/.s/.g).
private fun metal(t: Tier) = when (t) {
    Tier.BRONZE -> Metal(Color(0xFFC88A62), listOf(Color(0xFF7A4A2C), Color(0xFFE3A77F), Color(0xFF9C603C), Color(0xFFF2C29F), Color(0xFF7A4A2C)))
    Tier.SILVER -> Metal(Color(0xFFC9D2DC), listOf(Color(0xFF6E7C8C), Color(0xFFF4F7FA), Color(0xFF8E9BAA), Color(0xFFFFFFFF), Color(0xFF6E7C8C)))
    Tier.GOLD -> Metal(Color(0xFFE5C066), listOf(Color(0xFF8C6416), Color(0xFFFFE08A), Color(0xFFB8892A), Color(0xFFFFF2C2), Color(0xFF8C6416)))
}

/**
 * Medal disc [w] wide with its box's top-left at [at]; the ring tab is drawn above the box.
 * [bare] skips tab, drop shadow and gold glow (for medals inside glass ornaments).
 */
fun DrawScope.drawMedal(badge: Badge, w: Float, at: Offset = Offset.Zero, bare: Boolean = false) {
    val r = w / 2
    val c = Offset(at.x + r, at.y + r)
    val m = metal(badge.tier)
    if (!bare) {
        val sc = Offset(c.x, c.y + r * 0.19f)
        drawCircle(Brush.radialGradient(0f to Color(0x8C000000), 0.7f to Color(0x8C000000), 1f to Color.Transparent, center = sc, radius = r * 1.4f), r * 1.4f, sc)
        if (badge.tier == Tier.GOLD) {
            drawCircle(Brush.radialGradient(0.62f to Color(0x8CE8C66A), 1f to Color.Transparent, center = c, radius = r * 1.5f), r * 1.5f, c)
        }
        val ls = w * 0.047f
        val lw = w * 0.297f
        val lh = w * 0.203f
        drawRoundRect(
            m.loop, Offset(c.x - lw / 2 + ls / 2, at.y - w * 0.149f + ls / 2), Size(lw - ls, lh - ls),
            CornerRadius(w * 0.068f - ls / 2), style = Stroke(ls),
        )
    }
    // CSS conic "from 210deg" starts at 7 o'clock; a sweep gradient starts at 3, so turn it 120 degrees.
    rotate(120f, c) { drawCircle(Brush.sweepGradient(m.stops, c), r, c) }
    drawCircle(NAVY, r * 0.865f, c)
    val inner = r * 0.77f
    drawCircle(CREAM, inner, c)
    clipPath(Path().apply { addOval(Rect(c, inner)) }) {
        val art = Art.of(badge.design)
        if (art == null) drawMotif(badge.design.number, c, inner)
        else drawImage(
            art,
            dstOffset = IntOffset((c.x - inner).roundToInt(), (c.y - inner).roundToInt()),
            dstSize = IntSize((inner * 2).roundToInt(), (inner * 2).roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
    val s = r * 0.865f
    drawCircle(Brush.linearGradient(0f to Color(0x59FFFFFF), 0.4f to Color.Transparent, start = Offset(c.x - s, c.y - s), end = Offset(c.x + s, c.y + s)), s, c)
}

/** Source as a white glyph in a navy circle: shield-check = charge, arrows = trade, sparkle = synthesis. */
fun DrawScope.drawSourceMark(s: Source, c: Offset, r: Float) {
    fun p(x: Float, y: Float) = Offset(c.x + x * r, c.y + y * r)
    drawCircle(Color(0xFF0F1A30), r * 1.14f, c)
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

/** One of three simple motifs by design number: 0 bridge over water, 1 birch forest, 2 traditional gate. */
private fun DrawScope.drawMotif(n: Int, c: Offset, r: Float) {
    fun p(x: Float, y: Float) = Offset(c.x + x * r, c.y + y * r)
    fun Path.to(x: Float, y: Float) = lineTo(c.x + x * r, c.y + y * r)
    fun Path.from(x: Float, y: Float) = moveTo(c.x + x * r, c.y + y * r)
    fun Path.curve(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        cubicTo(c.x + x1 * r, c.y + y1 * r, c.x + x2 * r, c.y + y2 * r, c.x + x3 * r, c.y + y3 * r)
    fun wave(y: Float, amp: Float, x0: Float, x1: Float, color: Color, width: Float) {
        val path = Path().apply { from(x0, y) }
        val half = 0.3f
        var x = x0
        var up = true
        while (x < x1) {
            val a = if (up) -amp else amp
            path.curve(x + half * 0.35f, y + a, x + half * 0.65f, y + a, x + half, y)
            x += half
            up = !up
        }
        drawPath(path, color, style = Stroke(width * r, cap = StrokeCap.Round))
    }
    when (n % 3) {
        0 -> {
            drawLine(NAVY, p(-1.1f, -0.1f), p(1.1f, -0.1f), r * 0.075f)
            val truss = Path().apply {
                from(-0.8f, -0.1f)
                to(-0.64f, -0.47f)
                to(0.64f, -0.47f)
                to(0.8f, -0.1f)
                for (i in 0 until 5) {
                    val x = -0.64f + i * 0.256f
                    from(x, -0.47f)
                    to(x + 0.128f, -0.1f)
                    to(x + 0.256f, -0.47f)
                }
            }
            drawPath(truss, NAVY, style = Stroke(r * 0.055f, join = StrokeJoin.Round))
            wave(0.2f, 0.07f, -1.1f, 1.1f, CYAN, 0.075f)
            wave(0.45f, 0.06f, -0.78f, 0.6f, LIGHT_BLUE, 0.075f)
            wave(0.68f, 0.05f, -0.6f, 0.45f, PALE_BLUE, 0.06f)
        }
        1 -> {
            val bark = Color(0xFFFBFBF6)
            for ((x, tw, top) in listOf(Triple(-0.6f, 0.15f, -0.72f), Triple(-0.28f, 0.17f, -0.92f), Triple(0.08f, 0.15f, -0.68f), Triple(0.42f, 0.13f, -0.58f))) {
                val tl = p(x - tw / 2, top)
                val sz = Size(tw * r, (0.7f - top) * r)
                drawRect(bark, tl, sz)
                drawRect(Color(0xFFD3D4CC), tl, sz, style = Stroke(r * 0.015f))
                for (f in listOf(0.22f, 0.5f)) {
                    val y = top + (0.6f - top) * f
                    drawLine(Color(0xFF2E2E2E), p(x - tw * 0.2f, y), p(x + tw * 0.15f, y), r * 0.03f)
                }
            }
            val ground = Path().apply {
                from(-1.1f, 0.42f)
                curve(-0.5f, 0.28f, 0.2f, 0.56f, 1.1f, 0.34f)
                to(1.1f, 1.1f)
                to(-1.1f, 1.1f)
                close()
            }
            drawPath(ground, TEAL)
        }
        else -> {
            val body = Color(0xFFEAE4DA)
            drawRect(Color(0xFFD6DEE4), p(-1.1f, 0.12f), Size(2.2f * r, r))
            drawLine(Color(0xFFC3CDD6), p(-1.1f, 0.13f), p(1.1f, 0.13f), r * 0.03f)
            drawRect(body, p(-0.46f, -0.3f), Size(0.92f * r, 0.44f * r))
            drawLine(CYAN, p(-0.38f, 0.02f), p(0.38f, 0.02f), r * 0.035f)
            val door = Path().apply {
                from(-0.17f, 0.95f)
                to(-0.17f, 0.45f)
                arcTo(Rect(p(-0.17f, 0.28f), p(0.17f, 0.62f)), 180f, 180f, false)
                to(0.17f, 0.95f)
                close()
            }
            drawPath(door, NAVY)
            val roof = Path().apply {
                from(-0.9f, -0.02f)
                to(-0.5f, -0.25f)
                to(0.5f, -0.25f)
                to(0.9f, -0.02f)
                curve(0.45f, -0.1f, -0.45f, -0.1f, -0.9f, -0.02f)
                close()
            }
            drawPath(roof, NAVY)
            drawRect(body, p(-0.28f, -0.46f), Size(0.56f * r, 0.22f * r))
            drawLine(CYAN, p(-0.22f, -0.33f), p(0.22f, -0.33f), r * 0.035f)
            val top = Path().apply {
                from(-0.62f, -0.42f)
                to(-0.2f, -0.66f)
                to(0.2f, -0.66f)
                to(0.62f, -0.42f)
                curve(0.3f, -0.5f, -0.3f, -0.5f, -0.62f, -0.42f)
                close()
            }
            drawPath(top, NAVY)
        }
    }
}

private fun textPaint(color: Color, size: Float, bold: Boolean = false, center: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    textSize = size
    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    if (center) textAlign = Paint.Align.CENTER
}

private fun DrawScope.text(s: String, x: Float, y: Float, paint: Paint) = drawContext.canvas.nativeCanvas.drawText(s, x, y, paint)

fun DrawScope.nightSky() {
    drawRect(Brush.verticalGradient(listOf(BG1, BG0)))
    val c = Offset(size.width * 0.7f, size.height * 0.1f)
    val r = max(size.width, size.height) * 0.7f
    scale(1f, 0.58f, pivot = c) { drawCircle(Brush.radialGradient(0f to Color(0xFF18284A), 0.6f to Color.Transparent, center = c, radius = r), r, c) }
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
    val mw = th * 0.135f
    HOOKS.forEachIndexed { i, (hx, hy) ->
        val hook = q(hx, hy)
        val sway = 3.5f * sin(tau * (time * 20 + i * 0.17f))
        rotate(sway, hook) {
            val c = Offset(hook.x, hook.y + th * 0.04f + mw * 0.65f)
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

/** The whole scene, still, as saved. [title] and [sub] go on the image's top-left. */
fun DrawScope.drawTreeScene(slots: List<Badge?>, title: String? = null, sub: String? = null) {
    drawTreeBase()
    drawTreeLive(slots, 0.21f)
    if (title != null) {
        val u = min(size.width, size.height)
        text(title, u * 0.05f, u * 0.1f, textPaint(TXT, u * 0.055f, bold = true))
        sub?.let { text(it, u * 0.05f, u * 0.155f, textPaint(SUB, u * 0.03f)) }
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
 * The map part of the anniversary image is the left 60%. It fits the visited places, with at least a
 * 3 x 2.4 degree window so a local year still shows its surroundings.
 */
fun anniversaryProjection(points: List<Pair<Station, Badge>>, size: Size): MapProjection {
    val area = Rect(0f, 0f, size.width * 0.6f, size.height)
    fun window(values: List<Double>, fallback: Pair<Double, Double>, minSpan: Double): Pair<Float, Float> {
        val lo = values.minOrNull() ?: fallback.first
        val hi = values.maxOrNull() ?: fallback.second
        val span = max(hi - lo + 0.9, minSpan)
        return ((lo + hi) / 2 - span / 2).toFloat() to ((lo + hi) / 2 + span / 2).toFloat()
    }
    val (lat0, lat1) = window(points.map { it.first.lat }, 33.2 to 38.6, 3.0)
    val (lng0, lng1) = window(points.map { it.first.lng }, 126.1 to 129.6, 2.4)
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
            drawPath(paths.land, Color(0xFF16233F))
            visited.forEach { drawPath(paths.region(it), ACCENT.copy(alpha = 0.42f)) }
            // Width 0 is a hairline: one pixel at any zoom.
            drawPath(paths.municipal, Color(0x1AFFFFFF), style = Stroke(0f))
            drawPath(paths.province, Color(0x47FFFFFF), style = Stroke(0f))
            drawPath(paths.coast, Color(0xFF4A6A9A), style = Stroke(0f))
        }
    }
}

/**
 * The first-visit route and the medals, in image space. At full view the medals stand in two columns beside
 * the map, tied to their places by leader lines, so the map stays visible; zooming in ([zoom] past 1.8) slides
 * them onto their places. [pan] is the zoom layer's translation.
 */
fun DrawScope.drawAnniversaryMarks(points: List<Pair<Station, Badge>>, label: (Badge) -> String, proj: MapProjection, zoom: Float = 1f, pan: Offset = Offset.Zero) {
    val u = min(size.width, size.height)
    val area = Rect(0f, 0f, size.width * 0.6f, size.height)
    val ps = points.map { (st, _) -> proj.pt(st.lng, st.lat) * zoom + pan }
    ps.zipWithNext().forEach { (a, b) ->
        drawLine(ACCENT.copy(alpha = 0.25f), a, b, u * 0.022f, StrokeCap.Round)
        drawLine(ACCENT, a, b, u * 0.006f, StrokeCap.Round)
    }
    if (ps.isEmpty()) return
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
    val at = ps.indices.map { slot[it]!! + (onPlace[it] - slot[it]!!) * t }

    ps.indices.forEach { if (at[it] != ps[it]) drawLine(TXT.copy(alpha = 0.4f), ps[it], at[it], u * 0.0025f) }
    ps.forEach { drawCircle(ACCENT, u * 0.008f, it) }
    val tag = textPaint(TXT, mw * 0.34f, bold = true, center = true)
    points.forEachIndexed { i, (_, b) ->
        val q = at[i]
        drawCircle(Brush.radialGradient(0f to ACCENT.copy(alpha = 0.35f), 1f to Color.Transparent, center = q, radius = mw), mw, q)
        translate(q.x - mw / 2, q.y - mw / 2) { drawMedalCached(b, mw) }
        text(label(b), q.x, q.y + mw / 2 + mw * 0.43f, tag)
    }
}

/** Title on a fade into the night, not on a separate panel. */
fun DrawScope.drawAnniversaryTitle(count: Int, period: String) {
    val w = size.width
    val h = size.height
    val u = min(w, h)
    val night = Color(0xFF0A1222)
    drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.4f to night.copy(alpha = 0.8f), 1f to night, startX = w * 0.5f, endX = w), Offset(w * 0.5f, 0f), Size(w * 0.5f, h))
    val pad = u * 0.07f
    val x0 = w * 0.58f + pad
    text("우리의 1주년", x0, pad + u * 0.09f, textPaint(TXT, u * 0.095f, bold = true))
    text("차와 함께 쌓은 충전의 기억", x0, pad + u * 0.165f, textPaint(ACCENT, u * 0.042f, bold = true))
    val mid = h * 0.45f
    text("배지가 있는 장소를 잇다", x0, mid, textPaint(TXT, u * 0.05f, bold = true))
    text("직접 충전한 장소 ${count}곳을", x0, mid + u * 0.065f, textPaint(SUB, u * 0.036f))
    text("첫 방문 순서로 이었어요", x0, mid + u * 0.112f, textPaint(SUB, u * 0.036f))
    text(period, x0, h - pad, textPaint(ACCENT, u * 0.042f, bold = true))
}

/** The boundary data's source, bottom right; 공공누리 제1유형 asks for it on every use. */
fun DrawScope.drawAnniversarySource() {
    val u = min(size.width, size.height)
    val paint = textPaint(SUB.copy(alpha = 0.75f), u * 0.022f)
    val s = "지도: 통계청 통계지리정보서비스(2018)"
    text(s, size.width - u * 0.03f - paint.measureText(s), size.height - u * 0.025f, paint)
}

/** The whole anniversary image, unzoomed, as saved. */
fun DrawScope.drawAnniversary(points: List<Pair<Station, Badge>>, label: (Badge) -> String, period: String, paths: MapPaths?) {
    nightSky()
    val proj = anniversaryProjection(points, size)
    drawAnniversaryMap(paths, proj, points.map { it.first.regionIndex }.toSet())
    drawAnniversaryMarks(points, label, proj)
    drawAnniversaryTitle(points.size, period)
    drawAnniversarySource()
}

private data class MedalKey(val badge: Badge, val px: Int, val art: ImageBitmap?)

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
    val key = MedalKey(badge, px, Art.of(badge.design))
    // Shadow, gold glow and the ring tab reach 0.3w past the medal box.
    val pad = (px * 0.3f).roundToInt()
    return medalCache.get(key) ?: drawToBitmap(px + 2 * pad, px + 2 * pad) { drawMedal(badge, px.toFloat(), Offset(pad.toFloat(), pad.toFloat())) }
        .asImageBitmap().also { medalCache.put(key, it) }
}

fun DrawScope.drawMedalCached(badge: Badge, w: Float) {
    val px = w.roundToInt().coerceAtLeast(1)
    val img = medalImage(badge, px)
    val pad = (img.width - px) / 2f
    drawImage(img, Offset(-pad, -pad))
}

/** Renders [draw] into a bitmap of the given size, so saved images match what the screen shows. */
fun drawToBitmap(width: Int, height: Int, draw: DrawScope.() -> Unit): Bitmap {
    val img = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(width.toFloat(), height.toFloat()), draw)
    return img.asAndroidBitmap()
}

fun treeBitmap(slots: List<Badge?>, title: String, sub: String): Bitmap = drawToBitmap(1600, 1200) { drawTreeScene(slots, title, sub) }

fun mapBitmap(width: Int, height: Int, points: List<Pair<Station, Badge>>, label: (Badge) -> String, period: String, paths: MapPaths?): Bitmap =
    drawToBitmap(width, height) { drawAnniversary(points, label, period, paths) }

fun savePng(context: Context, bmp: Bitmap, name: String): File {
    val dir = File(context.filesDir, "images").apply { mkdirs() }
    val f = File(dir, name)
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return f
}
