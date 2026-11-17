package io.github.tancan1998.xviewtv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/** Consume only downward overscroll at the top. Child scrolling and horizontal/player gestures remain owned by children. */
@Composable
fun PullRefreshLayout(refreshing: Boolean, atTop: () -> Boolean, onRefresh: () -> Unit,
    modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tv = isTelevision()
    val threshold = with(LocalDensity.current) { dimensionResource(R.dimen.pull_refresh_threshold).toPx() }
    var distance by remember { mutableFloatStateOf(0f) }
    val latestRefresh by rememberUpdatedState(onRefresh)
    val latestTop by rememberUpdatedState(atTop)
    val latestBusy by rememberUpdatedState(refreshing)
    val connection = remember(threshold) { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            if (available.y > 0f && !latestBusy && latestTop()) {
                distance = (distance + available.y * .5f).coerceAtMost(threshold * 1.6f)
                return Offset(0f, available.y)
            }
            if (distance <= 0f || available.y >= 0f) return Offset.Zero
            val consumed = available.y.coerceAtLeast(-distance)
            distance += consumed
            return Offset(0f, consumed)
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || latestBusy || !latestTop() || available.y <= 0f) return Offset.Zero
            distance = (distance + available.y * .5f).coerceAtMost(threshold * 1.6f)
            return Offset(0f, available.y)
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            val pulled = distance > 0f
            if (distance >= threshold && !latestBusy) latestRefresh()
            distance = 0f
            return if (pulled) available else Velocity.Zero
        }
    } }
    LaunchedEffect(refreshing) { if (refreshing) distance = 0f }
    val ui = rememberStrings()
    Box(modifier.then(if (tv) Modifier else Modifier.nestedScroll(connection))) {
        content()
        if (!tv && (distance > 0f || refreshing)) {
            Row(Modifier.align(Alignment.TopCenter).padding(8.dp).background(XViewColors.surface, XViewShapes.medium).padding(12.dp)) {
                if (refreshing) LoadingIndicator(label = ui(R.string.refreshing))
                else Text(ui(if (distance >= threshold) R.string.release_refresh else R.string.pull_refresh))
            }
        }
    }
}
