package fr.pvlycee.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pvlycee.R

object PvColors {
    val Navy = Color(0xFF13294B)
    val NavyLight = Color(0xFF2B4C7E)
    val NavyPale = Color(0xFFDDE4F0)
    val Yellow = Color(0xFFFFC72C)
    val YellowPale = Color(0xFFFFF1C2)
    val Paper = Color(0xFFF6F4EE)
    val Ink = Color(0xFF111418)
    val Danger = Color(0xFFB3261E)
    val Ok = Color(0xFF2E7D32)
}

val Oswald = FontFamily(Font(R.font.oswald_bold, FontWeight.Bold))
val Mono = FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal), Font(R.font.jetbrains_mono_bold, FontWeight.Bold))

private val colors = lightColorScheme(
    primary = PvColors.Navy,
    onPrimary = Color.White,
    primaryContainer = PvColors.NavyPale,
    onPrimaryContainer = PvColors.Navy,
    secondary = PvColors.Yellow,
    onSecondary = PvColors.Ink,
    secondaryContainer = PvColors.YellowPale,
    onSecondaryContainer = PvColors.Ink,
    tertiary = PvColors.NavyLight,
    background = PvColors.Paper,
    onBackground = PvColors.Ink,
    surface = Color.White,
    onSurface = PvColors.Ink,
    surfaceVariant = Color(0xFFE9E6DD),
    onSurfaceVariant = Color(0xFF45474C),
    error = PvColors.Danger,
)

private val base = Typography()
private val typography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontFamily = Oswald, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Oswald, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    titleMedium = base.titleMedium.copy(fontFamily = Oswald, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
)

val SectionTitle = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp, color = PvColors.Navy)

@Composable
fun PvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

/** Liseré « ruban de signalisation » jaune et noir, discret. */
@Composable
fun HazardStripe(modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        drawRect(PvColors.Yellow)
        val step = size.height * 2.2f
        clipRect {
            var x = -size.height
            while (x < size.width + size.height) {
                val p = Path().apply {
                    moveTo(x, size.height)
                    lineTo(x + size.height, 0f)
                    lineTo(x + size.height + step / 2, 0f)
                    lineTo(x + step / 2, size.height)
                    close()
                }
                drawPath(p, PvColors.Ink)
                x += step
            }
        }
        drawLine(PvColors.Ink, Offset(0f, size.height), Offset(size.width, size.height), 1f)
    }
}
