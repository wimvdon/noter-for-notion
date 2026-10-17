package be.wimvandonck.noter.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PageIcon
import be.wimvandonck.noter.data.model.WorkspaceItem
import be.wimvandonck.noter.data.notion.InlineMarkdown
import be.wimvandonck.noter.logic.NotionDates
import be.wimvandonck.noter.ui.common.PageIconView
import be.wimvandonck.noter.ui.theme.EmptyState
import be.wimvandonck.noter.ui.theme.SkeletonLine
import java.util.Locale

/**
 * Search, from the search button: the notes on this phone whose title matches (at once, also
 * offline), then the pages and databases Notion finds. Before anything is typed, Notion's most
 * recently edited pages and databases. A database opens its notes (and can become a notebook); a
 * page opens in the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenDatabase: (String) -> Unit,
) {
    val state by viewModel.browse.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (state.items == null || state.query.isNotEmpty()) viewModel.search(state.query)
        runCatching { focus.requestFocus() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    TextField(
                        value = state.query,
                        onValueChange = viewModel::search,
                        placeholder = { Text(stringResource(R.string.browse_search)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                actions = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.items != null,
            onRefresh = { viewModel.search(state.query) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val items = state.items
            val error = state.error
            when {
                state.notes.isNotEmpty() || !items.isNullOrEmpty() ->
                    Results(state.notes, items.orEmpty(), searching = state.loading, onOpenNote, { viewModel.openItem(it, onOpenNote) }, onOpenDatabase)
                items == null && error == null -> LoadingRows()
                error != null -> LoadFailed(error) { viewModel.search(state.query) }
                state.query.isBlank() -> EmptyState(
                    Icons.Rounded.Explore,
                    stringResource(R.string.browse_empty_title),
                    body = stringResource(R.string.browse_empty),
                )
                else -> EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.browse_nothing_found))
            }
        }
    }
}

@Composable
private fun Results(
    notes: List<NoteSummary>,
    items: List<WorkspaceItem>,
    searching: Boolean,
    onOpenNote: (String) -> Unit,
    onOpenPage: (WorkspaceItem) -> Unit,
    onOpenDatabase: (String) -> Unit,
) {
    val noteIds = notes.map { it.id.replace("-", "") }.toSet()
    // A page already listed among the notes isn't listed again under Notion's results.
    val (databases, pages) = items.filterNot { !it.isDatabase && it.id.replace("-", "") in noteIds }.partition { it.isDatabase }
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (notes.isNotEmpty()) {
            item(key = "__notes") { Header(stringResource(R.string.search_notes)) }
            items(notes, key = { "n" + it.id }) { note ->
                ResultRow(InlineMarkdown.stripped(note.title), note.lastEditedTime, note.icon, locale, Icons.AutoMirrored.Rounded.StickyNote2) {
                    onOpenNote(note.id)
                }
            }
        }
        if (databases.isNotEmpty()) {
            item(key = "__databases") { Header(stringResource(R.string.browse_databases)) }
            items(databases, key = { "d" + it.id }) { item ->
                ResultRow(item.title, item.lastEditedTime, item.icon, locale, Icons.Rounded.TableChart) { onOpenDatabase(item.id) }
            }
        }
        if (pages.isNotEmpty()) {
            item(key = "__pages") { Header(stringResource(R.string.browse_pages)) }
            items(pages, key = { "p" + it.id }) { item ->
                ResultRow(item.title, item.lastEditedTime, item.icon, locale, Icons.Rounded.Description) { onOpenPage(item) }
            }
        }
        if (searching) {
            item(key = "__searching") { LoadingRows(count = 2) }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun ResultRow(title: String, edited: String, icon: PageIcon?, locale: Locale, fallbackIcon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(title.ifBlank { stringResource(R.string.untitled) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = if (edited.isNotBlank()) {
            { Text(stringResource(R.string.browse_edited, NotionDates.display(edited, locale))) }
        } else {
            null
        },
        leadingContent = {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    PageIconView(icon, size = 24.dp)
                } else {
                    Icon(fallbackIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun LoadingRows(count: Int = 6) {
    val loading = stringResource(R.string.loading)
    Column(
        Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        repeat(count) { SkeletonLine(Modifier.fillMaxWidth(if (it % 2 == 0) 0.7f else 0.5f)) }
    }
}
