package com.kodz.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val KBg = Color(0xFF0A0A0F)
val KCard = Color(0xFF16161F)
val KChip = Color(0xFF22222E)
val KBlue = Color(0xFF2F7BFF)
val KPurple = Color(0xFF8B3DFF)
val KCyan = Color(0xFF22D3EE)
val KError = Color(0xFFFF6B6B)
val KGradient = Brush.horizontalGradient(listOf(KBlue, KPurple))

@Composable
fun KodzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = KPurple,
            secondary = KCyan,
            background = KBg,
            surface = KCard,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        content = content,
    )
}

// Destaque de foco (controle remoto da TV): borda ciano + leve aumento.
// Deve vir ANTES do .clickable() na cadeia de modifiers.
fun Modifier.tvFocus(radius: Dp = 10.dp): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) 1.06f else 1f, label = "focusScale")
    Modifier
        .onFocusChanged { focused = it.hasFocus }
        .scale(s)
        .drawWithContent {
            drawContent()
            if (focused) {
                drawRoundRect(
                    color = KCyan,
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val brush: Brush = if (enabled) KGradient else SolidColor(Color(0xFF333344))
    Box(
        modifier
            .tvFocus(12.dp)
            .clip(shape)
            .background(brush)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val brush: Brush = if (selected) KGradient else SolidColor(KChip)
    Row(
        Modifier
            .tvFocus(20.dp)
            .clip(shape)
            .background(brush)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
