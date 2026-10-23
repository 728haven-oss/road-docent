package kr.roaddocent.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
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
fun RoadDocentTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(Card, RoundedCornerShape(22.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        content = content
    )
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.background(Chip, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp), color = ChipInk, fontSize = 13.sp)

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, selected: Boolean = false) =
    Text(text, modifier.background(if (selected) Accent else Chip, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 6.dp), color = if (selected) AccentInk else ChipInk, fontSize = 14.sp)

@Composable
fun Headline(text: String, size: Int = 32) = Text(text, color = Ink, fontSize = size.sp, fontWeight = FontWeight.Bold, lineHeight = (size * 1.25).sp)

/** Car-distance type: everything body-like is at least 22sp; `TypeScale.factor` is the user's 설정 글자 크기. */
object TypeScale { var factor = 1.0f }
fun carSp(size: Int) = (maxOf(size, 22) * TypeScale.factor).sp
@Composable
fun Body(text: String, color: Color = Quiet, size: Int = 16, maxLines: Int = Int.MAX_VALUE) = Text(text, color = color, fontSize = carSp(size), lineHeight = (maxOf(size, 22) * TypeScale.factor * 1.4).sp, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
