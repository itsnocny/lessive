package dev.nocny.lessive.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nocny.lessive.R
import dev.nocny.lessive.data.DialProgram
import dev.nocny.lessive.data.ProgramKind

data class KindStyle(val icon: ImageVector, val container: Color, val content: Color, val label: String)

@Composable
fun ProgramKind.style(): KindStyle {
    val c = MaterialTheme.colorScheme
    return when (this) {
        ProgramKind.Everyday -> KindStyle(Icons.Rounded.LocalLaundryService, c.primaryContainer, c.onPrimaryContainer, stringResource(R.string.kind_everyday))
        ProgramKind.Quick -> KindStyle(Icons.Rounded.Bolt, c.tertiaryContainer, c.onTertiaryContainer, stringResource(R.string.kind_quick))
        ProgramKind.Delicate -> KindStyle(Icons.Rounded.Spa, c.secondaryContainer, c.onSecondaryContainer, stringResource(R.string.kind_delicate))
        ProgramKind.Intensive -> KindStyle(Icons.Rounded.WaterDrop, c.primary, c.onPrimary, stringResource(R.string.kind_intensive))
        ProgramKind.Dry -> KindStyle(Icons.Rounded.Air, c.tertiary, c.onTertiary, stringResource(R.string.kind_dry))
        ProgramKind.Other -> KindStyle(Icons.Rounded.Tune, c.surfaceContainerHighest, c.onSurfaceVariant, stringResource(R.string.kind_other))
    }
}

private val ProgramKind.polygon
    get() = when (this) {
        ProgramKind.Everyday -> MaterialShapes.Cookie9Sided
        ProgramKind.Quick -> MaterialShapes.SoftBurst
        ProgramKind.Delicate -> MaterialShapes.Flower
        ProgramKind.Intensive -> MaterialShapes.Cookie6Sided
        ProgramKind.Dry -> MaterialShapes.Sunny
        ProgramKind.Other -> MaterialShapes.Pill
    }

/** A program's icon on its Material shape: each kind gets its own silhouette. */
@Composable
fun ProgramAvatar(
    kind: ProgramKind,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    container: Color = kind.style().container,
    content: Color = kind.style().content,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(container, kind.polygon.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(kind.style().icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.46f))
    }
}

/** A dial-like pictogram plus the short text Candy prints next to it (20°, 59', 14'…). */
data class Glyph(@param:DrawableRes val icon: Int, val badge: String? = null)

/**
 * Picks the pictogram the machine's dial uses for this program, from the official key
 * when the profile came from Candy's data, otherwise from the name typed in the editor.
 */
fun DialProgram.glyph(): Glyph? {
    val k = (catalogKey ?: "").uppercase() + " " + name.uppercase()
    val minutes = durationMinutes?.let { "$it'" }
    return when {
        "AUTO_CLEAN" in k || "NETTOYAGE" in k -> Glyph(R.drawable.ic_prog_drum)
        "PREWASH" in k || "PRÉLAVAGE" in k -> Glyph(R.drawable.ic_prog_cotton, "Pré")
        "PERFECT_COTTON" in k || "PARFAIT" in k -> Glyph(R.drawable.ic_prog_cotton, minutes)
        "RAPID" in k -> Glyph(R.drawable.ic_prog_stopwatch, minutes)
        "MIX" in k || "COLOU" in k || "COLOR" in k || "MÉLANG" in k -> Glyph(R.drawable.ic_prog_colours, minutes)
        "ECO" in k -> Glyph(R.drawable.ic_prog_leaf, "40-60".takeIf { "40" in k })
        "20_DEGREES" in k || "20°" in k -> Glyph(R.drawable.ic_prog_thermometer, "20°")
        "COTTON" in k || "COTON" in k -> Glyph(R.drawable.ic_prog_cotton)
        "SYNTHETIC" in k || "SYNTHÉT" in k -> Glyph(R.drawable.ic_prog_flask)
        "RINSE" in k || "RINÇAGE" in k -> Glyph(R.drawable.ic_prog_rinse)
        "DRAIN" in k || "SPIN" in k || "ESSOR" in k || "VIDANG" in k -> Glyph(R.drawable.ic_prog_spin)
        "JEANS" in k -> Glyph(R.drawable.ic_prog_jeans)
        "HYGIENE" in k || "HYGIÈNE" in k -> Glyph(R.drawable.ic_prog_hygiene, "60°".takeIf { "60" in k })
        "WOOL" in k || "HANDWASH" in k || "LAINE" in k || "MAIN" in k -> Glyph(R.drawable.ic_prog_handwash)
        "DELICATE" in k || "DÉLICAT" in k -> Glyph(R.drawable.ic_prog_butterfly)
        else -> null
    }
}

/** Program avatar: its dial pictogram on its kind's Material shape, with the dial's badge. */
@Composable
fun ProgramAvatar(
    program: DialProgram,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    container: Color = program.kind.style().container,
    content: Color = program.kind.style().content,
) {
    val glyph = program.glyph() ?: return ProgramAvatar(program.kind, modifier, size, container, content)
    Box(modifier.size(size)) {
        Box(
            modifier = Modifier.fillMaxSize().background(container, program.kind.polygon.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(glyph.icon), contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
        }
        glyph.badge?.let { badge ->
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = (size.value / 5.2f).sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = size * 0.12f, y = size * 0.06f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                    .padding(horizontal = (size.value / 14).dp, vertical = 1.dp),
            )
        }
    }
}
