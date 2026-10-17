package be.wimvandonck.noter.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance

/** Background and text color of a Notion option color, for the current light or dark theme. */
data class OptionColors(val container: Color, val content: Color)

private val light = mapOf(
    "gray" to (0xFFE3E2E0 to 0xFF32302C),
    "brown" to (0xFFEEE0DA to 0xFF442A1E),
    "orange" to (0xFFFADEC9 to 0xFF49290E),
    "yellow" to (0xFFFDECC8 to 0xFF402C1B),
    "green" to (0xFFDBEDDB to 0xFF1C3829),
    "blue" to (0xFFD3E5EF to 0xFF183347),
    "purple" to (0xFFE8DEEE to 0xFF412454),
    "pink" to (0xFFF5E0E9 to 0xFF4C2337),
    "red" to (0xFFFFE2DD to 0xFF5D1715),
)

private val dark = mapOf(
    "gray" to (0xFF5A5A5A to 0xFFF0F0F0),
    "brown" to (0xFF603B2C to 0xFFF3E6DF),
    "orange" to (0xFF854C1D to 0xFFFBE9DA),
    "yellow" to (0xFF89632A to 0xFFFDF2DC),
    "green" to (0xFF2B593F to 0xFFE2F2E6),
    "blue" to (0xFF28456C to 0xFFDDEBF8),
    "purple" to (0xFF492F64 to 0xFFEEE3F7),
    "pink" to (0xFF69314C to 0xFFF9E1EC),
    "red" to (0xFF6E3630 to 0xFFFDE2DE),
)

@Composable
fun optionColors(notionColor: String): OptionColors {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val pair = (if (isDark) dark else light)[notionColor.removeSuffix("_background")]
    return if (pair == null) {
        OptionColors(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
    } else {
        OptionColors(Color(pair.first), Color(pair.second))
    }
}
