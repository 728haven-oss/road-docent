package kr.roaddocent.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0B0F17)
val Card = Color(0x0DFFFFFF)
val CardBorder = Color(0x14FFFFFF)
val Ink = Color(0xFFE8EDF5)
val Quiet = Color(0xFF8FA0B8)
val Accent = Color(0xFF5AA9FF)
val AccentInk = Color(0xFF06111F)
val Warm = Color(0xFFFFB454)
val Chip = Color(0xFF1C2A40)
val ChipInk = Color(0xFF9FD0FF)

private val scheme: ColorScheme = darkColorScheme(
    primary = Accent, onPrimary = AccentInk, background = Bg, surface = Bg, onSurface = Ink, onBackground = Ink
)

@Composable
fun RoadDocentTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme) {
    // Keep everything clear of the status bar and the system navigation bar (edge-to-edge on API 35).
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) { content() }
}

/** Phone-sized screen (portrait or narrow): tighter paddings, stacked panes, shorter lists. */
@Composable
fun isCompact(): Boolean { val c = androidx.compose.ui.platform.LocalConfiguration.current; return c.screenWidthDp < 700 || c.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val compact = isCompact()
    Column(
        modifier
            .background(Card, RoundedCornerShape(if (compact) 16.dp else 22.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(if (compact) 16.dp else 22.dp))
            .padding(horizontal = if (compact) 14.dp else 24.dp, vertical = if (compact) 12.dp else 18.dp),
        content = content
    )
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.background(Chip, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp), color = ChipInk, fontSize = 13.sp, maxLines = 1, softWrap = false)

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, selected: Boolean = false) =
    Text(text, modifier.background(if (selected) Accent else Chip, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 6.dp), color = if (selected) AccentInk else ChipInk, fontSize = 14.sp, maxLines = 1, softWrap = false)

/** A row of chips/buttons that scrolls sideways instead of wrapping letters vertically. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    androidx.compose.foundation.layout.Row(modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, content = content)

@Composable
fun Headline(text: String, size: Int = 32) = Text(text, color = Ink, fontSize = size.sp, fontWeight = FontWeight.Bold, lineHeight = (size * 1.25).sp)

/** Car-distance type: everything body-like is at least 22sp; `TypeScale.factor` is the user's 설정 글자 크기. */
object TypeScale { var factor = 1.0f }
fun carSp(size: Int) = (maxOf(size, 22) * TypeScale.factor).sp
@Composable
fun Body(text: String, color: Color = Quiet, size: Int = 16, maxLines: Int = Int.MAX_VALUE) = Text(text, color = color, fontSize = carSp(size), lineHeight = (maxOf(size, 22) * TypeScale.factor * 1.4).sp, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)


/** Two panes side by side in landscape (car), stacked and scrollable in portrait (phone). Children use `Modifier.weight()` either way. */
@Composable
fun TwoPane(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        androidx.compose.foundation.layout.Row(modifier.fillMaxSize(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp), content = content)
    } else {
        // Portrait: a vertically scrolling column; the Row scope is emulated so `weight` and `fillMaxHeight` still compile and the panes size by content.
        val scroll = androidx.compose.foundation.rememberScrollState()
        androidx.compose.foundation.layout.Column(modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            PortraitRowScope(this).content()
        }
    }
}

/** RowScope shim for portrait: weight() becomes "wrap content, full width"; fillMaxHeight is ignored. */
private class PortraitRowScope(val col: androidx.compose.foundation.layout.ColumnScope) : androidx.compose.foundation.layout.RowScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = this.then(Modifier.fillMaxWidth())
    override fun Modifier.align(alignment: androidx.compose.ui.Alignment.Vertical): Modifier = this
    override fun Modifier.alignBy(alignmentLine: androidx.compose.ui.layout.HorizontalAlignmentLine): Modifier = this
    override fun Modifier.alignByBaseline(): Modifier = this
    override fun Modifier.alignBy(alignmentLineBlock: (androidx.compose.ui.layout.Measured) -> Int): Modifier = this
}
