package kr.roaddocent.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

private const val WJ = '⁠' // WORD JOINER: invisible, forbids a line break at its position

private fun isHangul(c: Char) = c in '가'..'힣' || c in 'ㄱ'..'ㆎ' || c in 'ᄀ'..'ᇿ'

/**
 * Korean "keep-all": Android breaks Hangul between any two syllables, so "세종대왕" can become "세종대 / 왕".
 * Gluing the letters of each word together with invisible word joiners makes lines break only at spaces
 * (and after punctuation), like CSS `word-break: keep-all`. A word wider than the whole line still breaks.
 */
fun keepAll(s: String): String {
    if (s.none(::isHangul)) return s
    val b = StringBuilder(s.length + s.length / 2)
    for (i in s.indices) {
        val c = s[i]
        if (i > 0) {
            val p = s[i - 1]
            if ((isHangul(p) || isHangul(c)) && p.isLetterOrDigit() && c.isLetterOrDigit()) b.append(WJ)
        }
        b.append(c)
    }
    return b.toString()
}

/**
 * Drop-in for material3 Text that never breaks a Korean word in the middle.
 * Single-line labels (maxLines = 1: buttons, tiles, chips, quiz options) shrink to fit, down to 75 %,
 * instead of being cut or pushed onto a second line; only below that do they end with "…".
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
) {
    val shown = keepAll(text)
    if (maxLines != 1) {
        androidx.compose.material3.Text(shown, modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration,
            textAlign, lineHeight, overflow, softWrap, maxLines, minLines, onTextLayout, style)
        return
    }
    val base = when { fontSize.isSpecified -> fontSize; style.fontSize.isSpecified -> style.fontSize; else -> 14.sp }
    var scale by remember(text, base) { mutableFloatStateOf(1f) }
    var fitted by remember(text, base) { mutableStateOf(false) }
    androidx.compose.material3.Text(
        shown, modifier.drawWithContent { if (fitted) drawContent() }, color, base * scale, fontStyle, fontWeight, fontFamily,
        if (letterSpacing.isSpecified) letterSpacing * scale else letterSpacing, textDecoration, textAlign,
        if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
        if (scale <= MIN_SCALE) TextOverflow.Ellipsis else overflow, softWrap, 1, minLines,
        { r ->
            val tooWide = r.multiParagraph.didExceedMaxLines || (r.lineCount > 0 && r.getLineRight(0) > r.size.width + 0.5f)
            if (tooWide && scale > MIN_SCALE) scale = maxOf(MIN_SCALE, scale * 0.92f) else { fitted = true; onTextLayout?.invoke(r) }
        }, style
    )
}

private const val MIN_SCALE = 0.75f
