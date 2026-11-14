package kr.chargebadge

import android.graphics.Typeface
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/*
 * Pleos' own faces, read from the platform so nothing is bundled: AstaSans (42dot) for text and Bai Jamjuree for
 * numbers. Each weight comes from its own file because the platform's "sans-serif" maps 500 to Regular and 700 to
 * ExtraBold, so a plain Bold came out as the heaviest cut. Off Pleos (no such files) the default font stands in.
 */
private const val DIR = "/product/fonts/"

@OptIn(ExperimentalTextApi::class)
private fun family(vararg files: Pair<FontWeight, String>): FontFamily {
    val fonts = files.mapNotNull { (w, f) -> File(DIR + f).takeIf(File::canRead)?.let { Font(it, w) } }
    return if (fonts.size == files.size) FontFamily(fonts) else FontFamily.Default
}

/** Text in three weights: Normal for body, Medium for labels and headings, Bold for titles and names. */
val TextFont = family(
    FontWeight.Normal to "AstaSansOTF-Regular.otf",
    FontWeight.Medium to "AstaSansOTF-Medium.otf",
    FontWeight.Bold to "AstaSansOTF-Bold.otf",
)

/** Numbers that stand alone: the charge gauge, counts, dates. */
val NumFont = family(
    FontWeight.Normal to "BaiJamjuree-Regular.ttf",
    FontWeight.Medium to "BaiJamjuree-Medium.ttf",
    FontWeight.Bold to "BaiJamjuree-SemiBold.ttf",
)

private fun face(f: String, fallback: Typeface): Typeface = File(DIR + f).takeIf(File::canRead)?.let(Typeface::createFromFile) ?: fallback

/** The same faces for text drawn on canvases (the anniversary map). */
val TextFace: Typeface = face("AstaSansOTF-Regular.otf", Typeface.DEFAULT)
val TextFaceBold: Typeface = face("AstaSansOTF-Bold.otf", Typeface.DEFAULT_BOLD)
val NumFaceBold: Typeface = face("BaiJamjuree-SemiBold.ttf", Typeface.DEFAULT_BOLD)
