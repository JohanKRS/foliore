package com.foliore.android.share

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.foliore.android.R
import com.foliore.android.allowDisplayCutout
import com.foliore.android.di.AppContainer
import com.foliore.android.ui.library.EditorialDivider
import com.foliore.android.ui.library.noticeMessage
import com.foliore.android.ui.theme.FolioreTheme
import com.foliore.android.viewmodel.LibraryViewModel
import com.foliore.android.viewmodel.LibraryViewModelFactory
import com.foliore.shared.domain.LibraryBook
import java.io.File

class CoverShareReceiverActivity : ComponentActivity() {
    private val appContainer by lazy { AppContainer(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        allowDisplayCutout()


        setContent {
            var incomingCoverUri by remember { mutableStateOf<Uri?>(null) }
            var receivingCover by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                incomingCoverUri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    IncomingCoverRepository(this@CoverShareReceiverActivity).copyFromIntent(intent)
                }
                receivingCover = false
            }
            if (receivingCover) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
                return@setContent
            }
            val themeMode = appContainer.themePreferenceRepository.themeMode.collectAsStateWithLifecycle()
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (themeMode.value) {
                com.foliore.android.ui.theme.ThemeMode.SYSTEM -> systemDarkTheme
                com.foliore.android.ui.theme.ThemeMode.LIGHT -> false
                com.foliore.android.ui.theme.ThemeMode.DARK -> true
            }

            LaunchedEffect(darkTheme) {
                val lightSystemBar = Color.rgb(248, 243, 234)
                val darkSystemBar = Color.rgb(17, 20, 17)
                val systemBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(darkSystemBar)
                } else {
                    SystemBarStyle.light(lightSystemBar, darkSystemBar)
                }
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle,
                    navigationBarStyle = systemBarStyle,
                )
                allowDisplayCutout()
            }

            FolioreTheme(themeMode = themeMode.value) {
                val factory = remember {
                    LibraryViewModelFactory(
                        billingRepository = appContainer.billingRepository,
                                libraryRepository = appContainer.libraryRepository,
                        epubImporter = appContainer.epubImporter,
                        bookEditor = appContainer.bookEditor,
                        bookDeleter = appContainer.bookDeleter,
                            preparedEpubService = appContainer.preparedEpubService,
                        originalFilesService = appContainer.originalFilesService,
                        syncService = appContainer.syncService,
                        syncWorkScheduler = appContainer.syncWorkScheduler,
                        aiPreferenceRepository = appContainer.aiPreferenceRepository,
                        aiCoverPromptRepository = appContainer.aiCoverPromptRepository,
                        kindleEmailPreferenceRepository = appContainer.kindleEmailPreferenceRepository,
                                gmailAuthService = appContainer.gmailAuthService,
                        libraryDisplayPreferenceRepository = appContainer.libraryDisplayPreferenceRepository,
                        openAiApiService = appContainer.openAiApiService,
                        openMetadataLookupService = appContainer.openMetadataLookupService,
                    )
                }
                val viewModel: LibraryViewModel = viewModel(factory = factory)
                CoverShareRoute(
                    viewModel = viewModel,
                    incomingCoverUri = incomingCoverUri,
                    onBack = ::finish,
                )
            }
        }
    }
}

@Composable
private fun CoverShareRoute(
    viewModel: LibraryViewModel,
    incomingCoverUri: Uri?,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = notice?.type?.let { noticeMessage(it) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
    ) { paddingValues ->
        CoverShareScreen(
            books = state.books,
            incomingCoverUri = incomingCoverUri,
            onBookSelected = { book ->
                if (incomingCoverUri != null) {
                    viewModel.replaceCover(book.book.id, incomingCoverUri)
                }
            },
            onBack = onBack,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun CoverShareScreen(
    books: List<LibraryBook>,
    incomingCoverUri: Uri?,
    onBookSelected: (LibraryBook) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleBooks = remember(books, query) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            books
        } else {
            books.filter { book ->
                book.metadata.title.contains(cleanQuery, ignoreCase = true) ||
                    book.metadata.authors.any { it.contains(cleanQuery, ignoreCase = true) }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 20.dp, end = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.share_cover_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.share_cover_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        EditorialDivider(modifier = Modifier.fillMaxWidth())

        if (incomingCoverUri == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.share_cover_invalid_image),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = incomingCoverUri,
                contentDescription = stringResource(R.string.share_cover_preview_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(88.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                    )
                },
                placeholder = { Text(text = stringResource(R.string.share_cover_search)) },
            )
        }

        if (books.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.share_cover_empty_library),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(156.dp),
            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(visibleBooks, key = { it.book.id }) { book ->
                ShareBookChoice(
                    book = book,
                    onClick = { onBookSelected(book) },
                )
            }
        }
    }
}

@Composable
private fun ShareBookChoice(
    book: LibraryBook,
    onClick: () -> Unit,
) {
    val coverPath = book.book.coverThumbPath ?: book.book.coverImagePath

    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            if (coverPath != null) {
                AsyncImage(
                    model = File(coverPath),
                    contentDescription = stringResource(
                        R.string.cover_content_description,
                        book.metadata.title,
                    ),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.cover_placeholder),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = book.metadata.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = book.metadata.authors.firstOrNull().orEmpty()
                .ifBlank { stringResource(R.string.book_unknown_author) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
