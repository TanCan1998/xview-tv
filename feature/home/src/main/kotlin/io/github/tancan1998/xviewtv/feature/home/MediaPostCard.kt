package io.github.tancan1998.xviewtv.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import io.github.tancan1998.xviewtv.core.designsystem.*
import io.github.tancan1998.xviewtv.core.designsystem.R as UiR
import io.github.tancan1998.xviewtv.core.domain.*
import io.github.tancan1998.xviewtv.core.model.*
import kotlinx.coroutines.CancellationException

internal val Canvas: Color @Composable get() = XViewColors.background
internal val Panel: Color @Composable get() = XViewColors.surface
internal val Accent: Color @Composable get() = XViewColors.primary
internal val Muted: Color @Composable get() = XViewColors.muted

@Composable
internal fun MediaPostCard(post: Post, translator: PostTranslator, language: String, visible: Boolean,
    image: @Composable (String?, Modifier) -> Unit, modifier: Modifier, onClick: () -> Unit) {
    val ui = rememberStrings()
    var translation by remember(post.id, language) { mutableStateOf<PostTranslation?>(null) }
    var failed by remember(post.id, language) { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(post.id, language, translator, visible) {
        if (!visible || failed || translation != null || post.text.isBlank() || post.language in setOf("zxx", "und") ||
            post.language?.equals(language, true) == true) return@LaunchedEffect
        try { translation = translator.translate(post, language) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val shape = XViewShapes.card
    XButton(onClick = onClick, modifier = modifier.fillMaxWidth().height(286.dp)
        .onFocusChanged { focused = it.isFocused }
        .border(if (focused) 2.dp else 1.dp, if (focused) Accent else XViewColors.outline, shape),
        shape = ButtonDefaults.shape(shape = shape),
        scale = ButtonDefaults.scale(focusedScale = 1.025f),
        colors = ButtonDefaults.colors(containerColor = Panel, contentColor = XViewColors.text,
            focusedContainerColor = XViewColors.focused, focusedContentColor = XViewColors.text),
        contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(180.dp).clip(XViewShapes.cardTop).background(XViewColors.placeholder)) {
                image(post.media.firstOrNull()?.previewUrl, Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(66.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(XViewColors.transparent, XViewColors.scrim.copy(alpha = .7f)))))
                val video = post.media.any { it is Video || it is AnimatedGif }
                Row(Modifier.align(Alignment.BottomStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(ui(if (video) UiR.string.video_badge else UiR.string.photo_badge))
                    if (post.media.size > 1) Badge(ui(UiR.string.item_count, post.media.size))
                }
                if (video) {
                    val duration = post.media.filterIsInstance<Video>().firstOrNull()?.durationMs
                    duration?.let { Badge("${it / 60000}:${((it / 1000) % 60).toString().padStart(2, '0')}",
                        Modifier.align(Alignment.BottomEnd).padding(12.dp)) }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(post.author.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), color = XViewColors.text)
                    if (translation != null) Text(ui(UiR.string.translated), style = MaterialTheme.typography.labelSmall, color = Accent)
                }
                Text(translation?.text ?: post.text.ifBlank { ui(UiR.string.view_media) }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = XViewColors.muted)
            }
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.background(XViewColors.scrim.copy(alpha = .52f), XViewShapes.small).padding(horizontal = 7.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall, color = XViewColors.text)
}
