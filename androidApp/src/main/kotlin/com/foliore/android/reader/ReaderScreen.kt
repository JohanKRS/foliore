package com.foliore.android.reader

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foliore.android.R
import com.foliore.android.ui.library.FolioreOptionsDialog
import com.foliore.android.ui.theme.FolioreTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReaderScreen(viewModel: ReaderViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<ReaderWebController?>(null) }
    var chrome by rememberSaveable { mutableStateOf(true) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var highlight by rememberSaveable { mutableStateOf("") }
    var linkText by remember { mutableStateOf<String?>(null) }
    var imageDocument by remember { mutableStateOf<String?>(null) }
    var imagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var imageIndex by rememberSaveable { mutableIntStateOf(-1) }
    var imageSource by rememberSaveable { mutableStateOf("") }
    var renderingFailed by remember(state.publication) { mutableStateOf(false) }
    var slider by remember { mutableStateOf<Float?>(null) }
    val close = { controller?.flush(onClose) ?: onClose() }
    BackHandler(onBack = close)
    val prefs = state.preferences
    val dark = prefs.theme == "dark"
    val paper = Color(when (prefs.theme) { "dark" -> 0xff191c1b; "light" -> 0xffffffff; else -> 0xfff7f2e8 })

    DisposableEffect(activity) {
        val oldBrightness = activity.window.attributes.screenBrightness
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = oldBrightness }
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(activity, prefs.keepScreenOn, prefs.brightness, chrome, dark) {
        if (prefs.keepScreenOn) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = prefs.brightness }
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (chrome) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) controller?.flush() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    // Capture the controller during composition. AndroidView's factory may assign it before
    // effects run, especially when an Activity is recreated with an already loaded ViewModel.
    val activeController = controller
    DisposableEffect(activeController) { onDispose { activeController?.dispose() } }
    LaunchedEffect(controller, state.navigation?.token) { state.navigation?.let { controller?.navigate(it, prefs, highlight) } }
    LaunchedEffect(controller, prefs) { controller?.updateSettings(prefs, state.navigation?.locator) }
    LaunchedEffect(dialog, state.publication, imagePath, imageIndex, imageSource) {
        val publication = state.publication
        val path = imagePath
        if (dialog == "image" && publication != null && path != null) {
            imageDocument = null
            imageDocument = withContext(Dispatchers.IO) { runCatching { publication.imageDocument(path, imageIndex, imageSource) }.getOrNull() } ?: ""
        }
    }

    FolioreTheme(themeMode = if (dark) com.foliore.android.ui.theme.ThemeMode.DARK else com.foliore.android.ui.theme.ThemeMode.LIGHT) {
        Surface(color = paper, contentColor = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxSize()) {
            ReaderViewportLayout(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(
                    WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout),
                ),
                chromeVisible = chrome,
                reserveChromeSpace = prefs.mode == "pages" &&
                    state.publication?.sections?.getOrNull(state.navigation?.section ?: 0)?.fixedLayout == false,
                content = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val publication = state.publication
                        if (state.loading) CircularProgressIndicator(Modifier.size(28.dp))
                        else if (state.failed || renderingFailed || publication == null) {
                            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(stringResource(R.string.reader_open_failed))
                                TextButton(onClick = { renderingFailed = false; controller = null; viewModel.load() }) { Text(stringResource(R.string.original_files_retry)) }
                                TextButton(onClick = close) { Text(stringResource(R.string.reader_back)) }
                            }
                        } else {
                            key(publication) {
                                AndroidView(
                                    factory = { ctx -> ReaderContentWebView(ctx).also { web ->
                                        controller = ReaderWebController(web, publication, viewModel::position, viewModel::chapter,
                                            onToggleChrome = { chrome = !chrome },
                                            onLink = { target ->
                                                val uri = Uri.parse(target)
                                                if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net" && uri.path?.startsWith("/publication/") == true) {
                                                    val path = uri.path!!.removePrefix("/publication/")
                                                    val index = publication.sections.indexOfFirst { it.href == path }
                                                    if (index >= 0) viewModel.navigate(index, fragment = uri.fragment)
                                                    else scope.launch { linkText = withContext(Dispatchers.IO) { runCatching { publication.linkedText(path, uri.fragment) }.getOrNull() }; dialog = "note" }
                                                } else if (uri.scheme in setOf("https", "http", "mailto")) { linkText = target; dialog = "external" }
                                            }, onImage = { index, source ->
                                                val path = state.navigation?.locator?.href
                                                if (path != null) {
                                                    imagePath = path; imageIndex = index; imageSource = source
                                                    imageDocument = null; dialog = "image"
                                                }
                                            }, onError = { renderingFailed = true; controller = null }).also { it.initialize() }
                                    } },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                },
                topBar = {
                    Column(Modifier.fillMaxWidth().background(paper)) {
                        Row(Modifier.widthIn(max = 900.dp).fillMaxWidth().align(Alignment.CenterHorizontally).heightIn(min = 44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            ReaderToolbarIcon(Icons.AutoMirrored.Outlined.ArrowBack, R.string.reader_back, close)
                            Text(state.book?.metadata?.title ?: stringResource(R.string.reader_title), Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            ReaderToolbarIcon(Icons.Outlined.Search, R.string.reader_search) { dialog = "search" }
                            ReaderToolbarIcon(Icons.Outlined.TextFields, R.string.reader_settings) { dialog = "settings" }
                            ReaderToolbarIcon(Icons.Outlined.Fullscreen, R.string.reader_fullscreen) { chrome = false }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .25f))
                    }
                },
                bottomBar = {
                    if (state.publication != null && !state.loading) {
                        Column(Modifier.fillMaxWidth().background(paper)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .25f))
                            val nav = state.navigation
                            val section = nav?.section ?: 0
                            val publication = state.publication!!
                            val paginated = prefs.mode == "pages" && !publication.sections[section].fixedLayout
                            val loc = state.locator
                            val progressLabel = stringResource(R.string.reader_page_progress)
                            val overall = ((section + (loc?.progression ?: 0.0)) / publication.sections.size * 100).toInt().coerceIn(0,100)
                            Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 8.dp)) {
                                Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(publication.sections.getOrNull(section)?.title.orEmpty(), Modifier.weight(1f).padding(end = 12.dp),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val position = stringResource(if (paginated) R.string.reader_page else R.string.reader_chapter,
                                        if (paginated) state.page else section + 1, if (paginated) state.pages else publication.sections.size)
                                    Text(position + " · " + stringResource(R.string.reader_percent, overall),
                                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ReaderIcon(Icons.AutoMirrored.Outlined.FormatListBulleted, R.string.reader_contents) { dialog = "toc" }
                                    ReaderIcon(Icons.AutoMirrored.Outlined.NavigateBefore, R.string.reader_previous, enabled = section > 0 || (if (paginated) state.page > 1 else (loc?.progression ?: 0.0) > 0)) { controller?.step(-1) }
                                    Slider(value = slider ?: (loc?.progression?.toFloat() ?: 0f), onValueChange = { slider = it },
                                        onValueChangeFinished = { slider?.let { controller?.seek(it) }; slider = null }, modifier = Modifier.weight(1f).semantics { contentDescription = progressLabel },
                                        thumb = { Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) },
                                        colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)),
                                        track = { sliderState -> SliderDefaults.Track(sliderState, modifier = Modifier.height(3.dp), drawStopIndicator = null, thumbTrackGapSize = 0.dp,
                                            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))) })
                                    ReaderIcon(Icons.AutoMirrored.Outlined.NavigateNext, R.string.reader_next, enabled = section < publication.sections.lastIndex || (if (paginated) state.page < state.pages else (loc?.progression ?: 0.0) < 1)) { controller?.step(1) }
                                    ReaderIcon(Icons.Outlined.Bookmarks, R.string.reader_bookmarks) { dialog = "bookmarks" }
                                }
                            }
                        }
                    }
                },
            )
        }
        when (dialog) {
            "image" -> state.publication?.let { ReaderImageDialog(it, imageDocument) { dialog = null; imageDocument = null } }
            "settings" -> ReaderSettingsDialog(prefs, state.publication?.sections?.getOrNull(state.navigation?.section ?: 0)?.fixedLayout == true, viewModel::settings) { dialog = null }
            "toc" -> FolioreOptionsDialog(stringResource(R.string.reader_contents), onDismiss = { dialog = null }, scrollContent = false) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    items(state.publication?.toc.orEmpty()) { entry ->
                        Text(entry.title, Modifier.fillMaxWidth().clickable {
                            val index = state.publication?.sections?.indexOfFirst { it.href == entry.href } ?: -1
                            if (index >= 0) viewModel.navigate(index, fragment = entry.fragment)
                            dialog = null
                        }.padding(start = (entry.depth * 16).dp, top = 14.dp, bottom = 14.dp), style = MaterialTheme.typography.bodyLarge,
                            color = if (entry.href == state.locator?.href) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            "bookmarks" -> FolioreOptionsDialog(stringResource(R.string.reader_bookmarks), onDismiss = { dialog = null }, scrollContent = false) {
                Column {
                    TextButton(onClick = viewModel::bookmark) { Icon(Icons.Outlined.BookmarkAdd, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.reader_add_bookmark)) }
                    if (state.bookmarks.isEmpty()) Text(stringResource(R.string.reader_no_bookmarks), Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                        items(state.bookmarks, key = { it.id }) { bookmark ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable {
                                    val index = state.publication?.sections?.indexOfFirst { it.href == bookmark.locator.href } ?: -1
                                    if (index >= 0) viewModel.navigate(index, bookmark.locator)
                                    dialog = null
                                }.padding(vertical = 14.dp)) {
                                    Text(bookmark.title, style = MaterialTheme.typography.titleSmall)
                                    Text(bookmark.locator.quote.ifBlank { stringResource(R.string.reader_percent, (bookmark.locator.progression * 100).toInt()) }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                ReaderIcon(Icons.Outlined.DeleteOutline, R.string.reader_delete_bookmark) { viewModel.deleteBookmark(bookmark.id) }
                            }
                        }
                    }
                }
            }
            "search" -> FolioreOptionsDialog(stringResource(R.string.reader_search), onDismiss = { dialog = null }, scrollContent = false) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(query, onValueChange = { query = it.take(160); viewModel.search(query) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.reader_search_hint)) }, trailingIcon = { if (query.isNotEmpty()) ReaderIcon(Icons.Outlined.Close, R.string.reader_clear_search) { query = ""; highlight = ""; controller?.clearHighlight(); viewModel.search("") } })
                    if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else if (state.searchFailed) Text(stringResource(R.string.reader_search_failed), style = MaterialTheme.typography.bodySmall)
                    else if (query.length >= 2) Text(stringResource(if (state.searchResults.isEmpty()) R.string.reader_no_results else R.string.reader_results, state.searchResults.size), style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                        items(state.searchResults) { result ->
                            Column(Modifier.fillMaxWidth().clickable {
                                val section = state.publication!!.sections[result.section]
                                highlight = result.query
                                viewModel.navigate(result.section, ReadingLocator(section.href, textOffset = result.offset, quote = result.query))
                                dialog = null
                            }.padding(vertical = 12.dp)) {
                                Text(state.publication?.sections?.get(result.section)?.title.orEmpty(), style = MaterialTheme.typography.titleSmall)
                                Text(result.excerpt, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            "note" -> FolioreOptionsDialog(stringResource(R.string.reader_note), onDismiss = { dialog = null }) { Text(linkText ?: stringResource(R.string.reader_link_unavailable), style = MaterialTheme.typography.bodyLarge) }
            "external" -> FolioreOptionsDialog(stringResource(R.string.reader_external_link), onDismiss = { dialog = null }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(linkText.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(linkText))) }
                        dialog = null
                    }) { Text(stringResource(R.string.reader_open_link)) }
                }
            }
        }
    }
}

@Composable
private fun ReaderIcon(icon: ImageVector, label: Int, onClick: () -> Unit) = ReaderIcon(icon, label, true, onClick)
@Composable
private fun ReaderIcon(icon: ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = stringResource(label)) }
}

@Composable
private fun ReaderToolbarIcon(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(width = 48.dp, height = 44.dp)) {
        Icon(icon, contentDescription = stringResource(label), modifier = Modifier.size(20.dp))
    }
}

/** Measure the actual bars even when hidden, so paginated text always keeps its clearance. */
@Composable
private fun ReaderViewportLayout(
    modifier: Modifier,
    chromeVisible: Boolean,
    reserveChromeSpace: Boolean,
    content: @Composable () -> Unit,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val chromeSemantics = if (chromeVisible) Modifier else Modifier.clearAndSetSemantics {}
    Layout(modifier = modifier, content = {
        Box { content() }
        Box(chromeSemantics) { topBar() }
        Box(chromeSemantics) { bottomBar() }
    }) { measurables, constraints ->
        val barConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val top = measurables[1].measure(barConstraints)
        val bottom = measurables[2].measure(barConstraints.copy(maxHeight = (constraints.maxHeight - top.height).coerceAtLeast(0)))
        val contentTop = if (reserveChromeSpace) top.height else 0
        val contentHeight = if (reserveChromeSpace) (constraints.maxHeight - top.height - bottom.height).coerceAtLeast(0) else constraints.maxHeight
        val page = measurables[0].measure(constraints.copy(minHeight = contentHeight, maxHeight = contentHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            page.placeRelative(0, contentTop)
            // Keep hidden controls out of drawing and touch handling; semantics are cleared above.
            if (chromeVisible) {
                top.placeRelative(0, 0)
                bottom.placeRelative(0, constraints.maxHeight - bottom.height)
            }
        }
    }
}
