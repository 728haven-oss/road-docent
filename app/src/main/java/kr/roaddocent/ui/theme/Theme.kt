package kr.roaddocent.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Colour tokens (light, Hyundai-inspired: navy + sky on warm sand) -------------------------------------------
val Bg = Color(0xFFF5F2EC)          // warm sand background
val Card = Color(0xFFFFFFFF)        // surfaces
val CardBorder = Color(0xFFE7E1D6)
val Ink = Color(0xFF14233C)         // primary text
val Quiet = Color(0xFF64708A)       // secondary text
val Navy = Color(0xFF002C5F)        // brand primary
val Sky = Color(0xFF00AAD2)         // brand accent
val Accent = Navy                   // filled buttons, selection, emphasis text
val AccentInk = Color(0xFFFFFFFF)   // text on Accent
val Warm = Color(0xFFE39A2D)        // highlights, destination flag
val Chip = Color(0xFFEDF1F6)        // neutral fill for chips / secondary buttons
val ChipInk = Color(0xFF243552)
val SkySoft = Color(0xFFE3F5FA)     // selected chip / info box
val WarmSoft = Color(0xFFFCF1E1)    // tip box
val MapBg = Color(0xFFE9EEF3)       // map canvas
val HeroTop = Color(0xFF002C5F)
val HeroBottom = Color(0xFF0A5E8F)

private val scheme = lightColorScheme(primary = Navy, onPrimary = Color.White, secondary = Sky, background = Bg, surface = Card, onSurface = Ink, onBackground = Ink)

@Composable
fun RoadDocentTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme) {
    // Keep everything clear of the status bar and the system navigation bar (edge-to-edge on API 35).
    Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) { content() }
}

/** Phone-sized screen (portrait or narrow): stacked panes, phone type sizes. */
@Composable
fun isCompact(): Boolean { val c = androidx.compose.ui.platform.LocalConfiguration.current; return c.screenWidthDp < 700 || c.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }

/** White card with a soft shadow. Kept the old name so every screen picks up the new look. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val compact = isCompact()
    val r = RoundedCornerShape(if (compact) 18.dp else 22.dp)
    Column(
        modifier.shadow(2.dp, r, clip = false, ambientColor = Color(0x22002C5F), spotColor = Color(0x22002C5F))
            .background(Card, r).border(1.dp, CardBorder, r)
            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 14.dp else 18.dp),
        content = content
    )
}

/** Navy gradient band for headers (login, arrival). */
@Composable
fun HeroBand(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(HeroTop, HeroBottom)), RoundedCornerShape(22.dp)).padding(20.dp), content = content)

@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = SkySoft, ink: Color = Navy) =
    Text(text, modifier.background(color, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp), color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)

/** Selectable chip. Wraps with siblings (see [ChipRow]); never splits letters vertically. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, selected: Boolean = false) =
    Text(text, modifier.background(if (selected) Navy else Card, RoundedCornerShape(999.dp)).border(1.dp, if (selected) Navy else CardBorder, RoundedCornerShape(999.dp))
        .padding(horizontal = 14.dp, vertical = 8.dp), color = if (selected) Color.White else ChipInk, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, softWrap = false)

/** Chips/buttons that wrap onto the next line — no sideways scrolling. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) =
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)

/** Buttons that wrap onto the next line on narrow screens instead of being cut off. */
@Composable
fun ButtonFlow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) =
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)

@Composable
fun Headline(text: String, size: Int = 28) = Text(text, color = Ink, fontSize = (size * TypeScale.factor).sp, fontWeight = FontWeight.Bold, lineHeight = (size * TypeScale.factor * 1.28).sp)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = Text(text, modifier, color = Ink, fontSize = (17 * TypeScale.factor).sp, fontWeight = FontWeight.Bold)

/** User's text-size setting (1.0 / 1.2). */
object TypeScale { var factor = 1.0f }
/** Car-distance type for the driving view only: never below 22sp. */
fun carSp(size: Int) = (maxOf(size, 22) * TypeScale.factor).sp

@Composable
fun Body(text: String, color: Color = Quiet, size: Int = 15, maxLines: Int = Int.MAX_VALUE) =
    Text(text, color = color, fontSize = (size * TypeScale.factor).sp, lineHeight = (size * TypeScale.factor * 1.5).sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/** Two panes side by side in landscape (car), stacked and scrollable in portrait (phone). Children use `Modifier.weight()` either way. */
@Composable
fun TwoPane(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp), content = content)
    } else {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) { PortraitRowScope.content() }
    }
}

/** RowScope shim for portrait: weight() becomes "full width, wrap height". */
private object PortraitRowScope : RowScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = this.then(Modifier.fillMaxWidth())
    override fun Modifier.align(alignment: Alignment.Vertical): Modifier = this
    override fun Modifier.alignBy(alignmentLine: HorizontalAlignmentLine): Modifier = this
    override fun Modifier.alignByBaseline(): Modifier = this
    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this
}

/** Equal-width icon + short label button for control bars (all items always visible). */
@Composable
fun RowScope.IconAction(icon: String, label: String, onClick: () -> Unit, highlight: Boolean = false) {
    Column(
        Modifier.weight(1f).heightIn(min = 56.dp).background(if (highlight) Navy else Chip, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 19.sp)
        Text(label, color = if (highlight) Color.White else ChipInk, fontSize = 11.sp, maxLines = 1, softWrap = false)
    }
}
