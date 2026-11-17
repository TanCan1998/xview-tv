package io.github.tancan1998.xviewtv.core.designsystem

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Pure draw animation, no fake data, focus targets or network work. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "shimmer")
    val base = MaterialTheme.colorScheme.surface
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)
    val transparent = XViewColors.transparent
    Canvas(modifier.clip(XViewShapes.medium).clearAndSetSemantics { }) {
        drawRect(base)
        val x = progress * size.width
        drawRect(Brush.linearGradient(listOf(transparent, highlight, transparent),
            Offset(x - size.width * .6f, 0f), Offset(x + size.width * .6f, size.height)))
    }
}

@Composable
fun MediaSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().height(286.dp).clip(XViewShapes.card)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = .7f)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(180.dp))
        SkeletonBlock(Modifier.padding(horizontal = 14.dp).fillMaxWidth(.45f).height(16.dp))
        SkeletonBlock(Modifier.padding(horizontal = 14.dp).fillMaxWidth(.85f).height(12.dp))
        SkeletonBlock(Modifier.padding(horizontal = 14.dp).fillMaxWidth(.65f).height(12.dp))
    }
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier, label: String? = null) {
    val text = label ?: rememberStrings()(R.string.loading)
    val transition = rememberInfiniteTransition(label = "loading")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin")
    val color = MaterialTheme.colorScheme.primary
    Row(modifier.semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate; contentDescription = text },
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 2.5.dp.toPx()
            drawArc(color.copy(alpha = .15f), 0f, 360f, false, Offset(stroke / 2, stroke / 2), Size(size.width-stroke,size.height-stroke), style = Stroke(stroke))
            drawArc(color, angle, 250f, false, Offset(stroke / 2, stroke / 2), Size(size.width-stroke,size.height-stroke), style = Stroke(stroke))
        }
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}
