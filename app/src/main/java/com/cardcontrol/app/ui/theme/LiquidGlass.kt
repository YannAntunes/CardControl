package com.cardcontrol.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GlassPalette {
    val ObsidianBg = Color(0xFF070A12)
    val DeepSlateBg = Color(0xFF0D1322)
    val CyanGlow = Color(0xFF38BDF8)
    val IndigoGlow = Color(0xFF6366F1)
    val EmeraldAccent = Color(0xFF34D399)
    val CoralDanger = Color(0xFFFB7185)
    val AmberWarn = Color(0xFFFBBF24)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val SurfaceTintTop = Color(0x28FFFFFF)
    val SurfaceTintBottom = Color(0x0DFFFFFF)
    val SpecularHighlight = Color(0x66FFFFFF)
    val SpecularShadow = Color(0x14FFFFFF)
}

fun Modifier.liquidGlassSurface(
    cornerRadius: Dp = 22.dp,
    accentTint: Color = GlassPalette.CyanGlow
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    GlassPalette.SurfaceTintTop,
                    accentTint.copy(alpha = 0.06f),
                    GlassPalette.SurfaceTintBottom
                ),
                start = Offset(0f, 0f),
                end = Offset(900f, 900f)
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    GlassPalette.SpecularHighlight,
                    accentTint.copy(alpha = 0.28f),
                    GlassPalette.SpecularShadow
                ),
                start = Offset(0f, 0f),
                end = Offset(600f, 600f)
            ),
            shape = shape
        )
}

@Composable
fun LiquidAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GlassPalette.ObsidianBg, GlassPalette.DeepSlateBg)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlassPalette.CyanGlow.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.12f),
                    radius = size.minDimension * 0.65f
                ),
                center = Offset(size.width * 0.15f, size.height * 0.12f),
                radius = size.minDimension * 0.65f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlassPalette.IndigoGlow.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.48f),
                    radius = size.minDimension * 0.70f
                ),
                center = Offset(size.width * 0.88f, size.height * 0.48f),
                radius = size.minDimension * 0.70f
            )
        }
        content()
    }
}

@Composable
fun LiquidGlassBar(
    progress: Float,
    modifier: Modifier = Modifier,
    accentColor: Color = GlassPalette.CyanGlow
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.75f), accentColor)))
        )
    }
}

@Composable
fun LiquidPillSelector(
    options: List<Pair<Long, String>>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (id, label) ->
            val isSelected = id == selectedId
            val accent = if (isSelected) GlassPalette.CyanGlow else GlassPalette.TextSecondary
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) GlassPalette.CyanGlow.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                    .border(
                        width = 1.dp,
                        color = if (isSelected) GlassPalette.CyanGlow else Color.White.copy(alpha = 0.12f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) GlassPalette.TextPrimary else accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
