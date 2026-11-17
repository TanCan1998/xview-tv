package io.github.tancan1998.xviewtv.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import io.github.tancan1998.xviewtv.core.designsystem.*
import io.github.tancan1998.xviewtv.core.designsystem.R as UiR
import io.github.tancan1998.xviewtv.core.domain.*
import io.github.tancan1998.xviewtv.core.model.*
import kotlinx.coroutines.CancellationException

private enum class LibraryPage { HOME, SEARCH, LIKES, FOLLOWING }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(home: TimelineRepository, library: VideoLibrary, translator: PostTranslator,
    language: String, onMediaVisibility: (Boolean) -> Unit,
    image: @Composable (String?, Modifier) -> Unit, media: @Composable (Post, List<Post>, () -> Unit) -> Unit) {
    val ui = rememberStrings()
    var page by rememberSaveable { mutableStateOf(LibraryPage.HOME) }
    var draft by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(page != LibraryPage.HOME) { page = LibraryPage.HOME }
    val navigation: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryPage.entries.forEach { destination ->
                XButton(onClick = { page = destination }, colors = ButtonDefaults.colors(
                    containerColor = if (page == destination) Accent.copy(alpha = .18f) else Panel,
                    contentColor = if (page == destination) Accent else Muted,
                    focusedContainerColor = Accent, focusedContentColor = Canvas)) {
                    Text(ui(when(destination) {
                        LibraryPage.HOME -> UiR.string.videos
                        LibraryPage.SEARCH -> UiR.string.search
                        LibraryPage.LIKES -> UiR.string.my_likes
                        LibraryPage.FOLLOWING -> UiR.string.following_list
                    }))
                }
            }
        }
    }
    val search: @Composable () -> Unit = {
        val submit = { if (draft.trim().isNotEmpty()) { submitted = draft.trim(); keyboard?.hide() }; Unit }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(value = draft, onValueChange = { if (it.length <= 500 && it.none(Char::isISOControl)) draft = it },
                singleLine = true, textStyle = TextStyle(color = XViewColors.text), cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f).border(1.dp, Muted).padding(14.dp),
                decorationBox = { inner -> Box { if (draft.isEmpty()) Text(ui(UiR.string.search_hint), color = Muted); inner() } })
            XButton(onClick = submit, enabled = draft.isNotBlank()) { Text(ui(UiR.string.search)) }
        }
    }
    val title = ui(when(page) {
        LibraryPage.HOME -> UiR.string.following
        LibraryPage.SEARCH -> UiR.string.search_title
        LibraryPage.LIKES -> UiR.string.liked_videos
        LibraryPage.FOLLOWING -> UiR.string.following_list
    })
    when {
        page == LibraryPage.FOLLOWING -> FollowingScreen(library, translator, language, onMediaVisibility, navigation, image, media)
        page == LibraryPage.SEARCH && submitted.isEmpty() -> LibraryFrame(title, navigation) { search(); Text(ui(UiR.string.search_intro), color = Muted) }
        else -> {
            val repository = remember(home, library, page, submitted) {
                val destination = page
                val keyword = submitted
                object : TimelineRepository {
                    override suspend fun getTimeline(request: TimelineRequest): Page<Post> = when(destination) {
                        LibraryPage.SEARCH -> library.searchVideos(keyword, request)
                        LibraryPage.LIKES -> library.likedVideos(request)
                        else -> home.getTimeline(request)
                    }
                }
            }
            key(repository) {
                HomeScreen(repository, translator, language, onMediaVisibility,
                    title = title, header = { navigation(); if (page == LibraryPage.SEARCH) search() }, image = image, media = media)
            }
        }
    }
}

@Composable
private fun LibraryFrame(title: String, navigation: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val ui = rememberStrings()
    Column(Modifier.fillMaxSize().background(Canvas).padding(horizontal = 18.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(ui(UiR.string.app_title), style = MaterialTheme.typography.headlineMedium, color = XViewColors.text, modifier = Modifier.padding(end = 100.dp))
        Text(title, color = Muted)
        navigation()
        content()
    }
}

@Composable
private fun FollowingScreen(library: VideoLibrary, translator: PostTranslator, language: String,
    onMediaVisibility: (Boolean) -> Unit, navigation: @Composable () -> Unit,
    image: @Composable (String?, Modifier) -> Unit, media: @Composable (Post, List<Post>, () -> Unit) -> Unit) {
    val ui = rememberStrings()
    var authors by remember(library) { mutableStateOf<List<Author>>(emptyList()) }
    var cursor by remember(library) { mutableStateOf<String?>(null) }
    var hasMore by remember(library) { mutableStateOf(true) }
    val cursors = remember(library) { LinkedHashSet<String>() }
    var loading by remember(library) { mutableStateOf(true) }
    var message by remember(library) { mutableStateOf("") }
    var request by remember(library) { mutableIntStateOf(0) }
    val tv = isTelevision()
    var selected by remember(library) { mutableStateOf<Author?>(null) }
    val list = rememberLazyListState()
    LaunchedEffect(library, request) {
        if (!hasMore) return@LaunchedEffect
        loading = true; message = ""
        try {
            val firstPage = cursor == null
            val page = library.following(TimelineRequest(limit = 50, cursor = cursor))
            authors = (authors + page.items).distinctBy { it.id }.takeLast(400)
            if (firstPage) list.scrollToItem(0)
            val next = page.nextCursor
            hasMore = page.hasMore && next != null && next != cursor && next !in cursors
            cursor?.let { cursors.add(it) }
            while (cursors.size > 32) cursors.remove(cursors.first())
            cursor = if (hasMore) next else null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: ContentFailure) { message = ui.failure(failure.userMessage) }
        catch (_: Exception) { message = ui(UiR.string.feed_error) }
        finally { loading = false }
    }
    BackHandler(selected != null) { selected = null }
    val author = selected
    if (author != null) {
        val repository = remember(library, author.id) { object : TimelineRepository {
            override suspend fun getTimeline(request: TimelineRequest) = library.authorVideos(author.id, request)
        } }
        HomeScreen(repository, translator, language, onMediaVisibility,
            title = ui(UiR.string.author_videos, author.displayName), header = {
                XButton(onClick = { selected = null }) { Text(ui(UiR.string.back_following)) }
            }, image = image, media = media)
    } else LibraryFrame(ui(UiR.string.following_list), navigation) {
        Text(ui(UiR.string.following_intro), color = Muted)
        PullRefreshLayout(refreshing = loading && authors.isEmpty(),
            atTop = { !list.canScrollBackward },
            onRefresh = { if (!loading) { authors = emptyList(); cursor = null; cursors.clear(); hasMore = true; request++ } },
            modifier = Modifier.weight(1f)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(authors, key = { it.id }) { item ->
                val creatorShape = XViewShapes.creator
                val creatorContent: @Composable () -> Unit = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        image(item.avatarUrl, Modifier.size(46.dp).clip(creatorShape))
                        Column(Modifier.weight(1f)) {
                            Text(item.displayName, maxLines = 1)
                            Text(ui(UiR.string.user_handle, item.username), color = Muted, maxLines = 1)
                        }
                        Text(ui(UiR.string.videos))
                    }
                }
                if (tv) XButton(onClick = { selected = item }, modifier = Modifier.fillMaxWidth(),
                    shape = ButtonDefaults.shape(shape = creatorShape),
                    colors = ButtonDefaults.colors(containerColor = Panel, contentColor = XViewColors.text,
                        focusedContainerColor = XViewColors.focused, focusedContentColor = XViewColors.text)) { creatorContent() }
                else Box(Modifier.fillMaxWidth().clip(creatorShape).background(Panel)
                    .clickable { selected = item }.padding(horizontal = 16.dp, vertical = 10.dp)) { creatorContent() }
            }
            item(key = "following-pagination") {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        loading -> LoadingIndicator(label = ui(UiR.string.loading))
                        message.isNotEmpty() -> XButton(onClick = { request++ }) { Text(ui(UiR.string.retry, message)) }
                        hasMore -> XButton(onClick = { request++ }) { Text(ui(UiR.string.load_more)) }
                        authors.isEmpty() -> Text(ui(UiR.string.no_following), color = Muted)
                        else -> Text(ui(UiR.string.feed_end), color = Muted)
                    }
                    if (tv && !loading) XButton(onClick = { authors = emptyList(); cursor = null; cursors.clear(); hasMore = true; request++ }) { Text(ui(UiR.string.refresh)) }
                }
            }
        }
        }
    }
}
