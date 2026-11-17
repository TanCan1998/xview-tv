package io.github.tancan1998.xviewtv.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.compose.ui.platform.LocalConfiguration
import io.github.tancan1998.xviewtv.core.designsystem.*
import io.github.tancan1998.xviewtv.core.designsystem.R as UiR
import io.github.tancan1998.xviewtv.core.domain.*
import io.github.tancan1998.xviewtv.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(repository: TimelineRepository, translator: PostTranslator, language: String, onMediaVisibility: (Boolean) -> Unit = {},
    title: String? = null, header: @Composable () -> Unit = {}, secondaryHeader: @Composable () -> Unit = {},
    initialFocus: FocusRequester? = null,
    image: @Composable (String?, Modifier) -> Unit, media: @Composable (Post, List<Post>, () -> Unit) -> Unit) {
    val ui = rememberStrings()
    val tv = isTelevision()
    val compact = LocalConfiguration.current.screenWidthDp < 600
    val window = remember(repository) { TimelineWindow() }
    var posts by remember(repository) { mutableStateOf<List<Post>>(emptyList()) }
    var hasMore by remember(repository) { mutableStateOf(true) }
    var loading by remember(repository) { mutableStateOf(true) }
    var message by remember(repository) { mutableStateOf("") }
    val filter = MediaFilter.VIDEOS
    var selected by remember { mutableStateOf<Post?>(null) }
    var visibleIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var restoreId by remember { mutableStateOf<String?>(null) }
    var pageRequest by remember(repository) { mutableIntStateOf(1) }
    var reset by remember(repository) { mutableStateOf(false) }
    val refreshFocus = remember { FocusRequester() }
    val grid = rememberLazyGridState()
    LaunchedEffect(repository, pageRequest) {
        if (reset) { window.clear(); posts = emptyList(); hasMore = true; reset = false }
        if (!window.hasMore) return@LaunchedEffect
        loading = true; message = ""
        try {
            val anchor = posts.firstOrNull()?.id
            window.append(repository.getTimeline(TimelineRequest(limit = 40, cursor = window.cursor)))
            posts = window.posts; hasMore = window.hasMore
            if (anchor != null && posts.none { it.id == anchor }) grid.scrollToItem(0)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: ContentFailure) { message = ui.failure(failure.userMessage) }
        catch (_: Exception) { message = ui(UiR.string.feed_error) }
        finally { loading = false }
    }
    LaunchedEffect(repository) { if (tv) (initialFocus ?: refreshFocus).requestFocus() }
    val visible = remember(posts) { FilterMedia()(posts, filter).map { post ->
        post.copy(media = post.media.filter { it is Video || it is AnimatedGif })
    } }
    LaunchedEffect(grid, visible) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet() }
            .distinctUntilChanged().collectLatest { visibleIds = it }
    }
    LaunchedEffect(visibleIds, loading, hasMore, selected) {
        val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (!loading && selected == null && hasMore && message.isEmpty() && visible.isNotEmpty() && last >= visible.size - 4) pageRequest++
    }
    DisposableEffect(Unit) { onDispose { onMediaVisibility(false) } }
    BackHandler(enabled = selected != null) { restoreId = selected?.id; selected = null; onMediaVisibility(false) }
    Box(Modifier.fillMaxSize().background(Canvas)) {
        if (selected == null) Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 14.dp else 32.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.padding(end = if (tv) 0.dp else 100.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(ui(UiR.string.app_title), style = MaterialTheme.typography.headlineMedium, color = XViewColors.text)
                    Text(title ?: ui(UiR.string.following), style = MaterialTheme.typography.labelMedium, color = Muted)
                }
                if (tv) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { header() }
                    XButton(onClick = { if (!loading) { reset = true; pageRequest++ } }, enabled = !loading,
                        modifier = Modifier.focusRequester(refreshFocus),
                        colors = ButtonDefaults.colors(containerColor = Panel, contentColor = Muted,
                            focusedContainerColor = Accent, focusedContentColor = Canvas)) { Text(ui(UiR.string.refresh)) }
                } else header()
                secondaryHeader()
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(ui(UiR.string.latest), style = MaterialTheme.typography.titleMedium, color = XViewColors.text, modifier = Modifier.weight(1f))
                Text(ui(UiR.string.feed_count, visible.size, language), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            PullRefreshLayout(refreshing = loading && posts.isEmpty(),
                atTop = { grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0 },
                onRefresh = { if (!loading) { reset = true; pageRequest++ } }, modifier = Modifier.weight(1f)) {
            LazyVerticalGrid(columns = GridCells.Adaptive(if (compact) 280.dp else 260.dp), state = grid, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(if (compact) 4.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (loading && posts.isEmpty()) {
                    items(if (compact) 3 else 6, key = { "skeleton-$it" }, contentType = { "skeleton" }) { MediaSkeleton() }
                }
                items(visible, key = { it.id }, contentType = { "media-post" }) { post ->
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(restoreId, selected) {
                        if (restoreId == post.id && selected == null) { if (tv) focus.requestFocus(); restoreId = null }
                    }
                    MediaPostCard(post, translator, language, selected == null && post.id in visibleIds, image,
                        Modifier.focusRequester(focus)) { selected = post; onMediaVisibility(true) }
                }
                item(key = "pagination", span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        when {
                            loading -> LoadingIndicator(label = ui(UiR.string.loading))
                            message.isNotEmpty() -> XButton(onClick = { pageRequest++ }) { Text(ui(UiR.string.retry, message), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            hasMore -> XButton(onClick = { pageRequest++ }) { Text(ui(if (visible.isEmpty()) UiR.string.empty_more else UiR.string.load_more)) }
                            else -> Text(ui(if (visible.isEmpty()) UiR.string.no_videos else UiR.string.feed_end), color = Muted)
                        }
                    }
                }
            }
            }
            if (tv) Text(ui(UiR.string.nav_hint), style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        selected?.let { post -> media(post, visible) { restoreId = post.id; selected = null; onMediaVisibility(false) } }
    }
}
