package be.wimvandonck.noter.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.WorkspaceItem
import be.wimvandonck.noter.logic.SearchKind
import be.wimvandonck.noter.ui.theme.readableWidth
import be.wimvandonck.noter.ui.theme.EmptyState

/**
 * Search, opened from Home's search bar: one list of pages and databases (#60), the notes on this
 * phone (at once, also offline) mixed with what Notion finds, best matches first. Before anything
 * is typed, Notion's most recently edited pages and databases. Chips on top show only pages, only
 * databases or, with several workspaces, one workspace; each row says which workspace it is in. A
 * database opens its notes; a page opens in the editor; a long press pins or unpins it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenDatabase: (String) -> Unit,
) {
    val state by viewModel.search.collectAsStateWithLifecycle()
    val home by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (state.results == null || state.query.isNotEmpty()) viewModel.search(state.query)
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchChips(state, onKind = viewModel::toggleSearchKind, onWorkspace = viewModel::toggleSearchWorkspace)
            PullToRefreshBox(
                isRefreshing = state.loading && state.results != null,
                onRefresh = { viewModel.search(state.query) },
                modifier = Modifier.fillMaxSize(),
            ) {
                val results = state.results
                val error = state.error
                when {
                    !results.isNullOrEmpty() -> Results(
                        results,
                        searching = state.loading,
                        workspaceNames = state.workspaceNames,
                        pinnedDatabases = home.pinnedDatabases.map { it.id.replace("-", "") }.toSet(),
                        pinnedPages = home.notes.pinnedIds.map { it.replace("-", "") }.toSet(),
                        onOpenPage = { item -> viewModel.openItem(item, onOpenNote) },
                        onOpenDatabase = onOpenDatabase,
                        onTogglePin = viewModel::togglePinItem,
                    )
                    results == null && error == null -> LoadingRows()
                    error != null && results.isNullOrEmpty() -> LoadFailed(error) { viewModel.search(state.query) }
                    state.loading -> LoadingRows()
                    state.query.isBlank() && state.kind == SearchKind.ALL && state.workspace == null -> EmptyState(
                        Icons.Rounded.Explore,
                        stringResource(R.string.browse_empty_title),
                        body = stringResource(R.string.browse_empty),
                    )
                    else -> EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.browse_nothing_found))
                }
            }
        }
    }
}

/** Only pages, only databases and, with several workspaces, one workspace: a tap again shows everything. */
@Composable
private fun SearchChips(state: SearchUiState, onKind: (SearchKind) -> Unit, onWorkspace: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        item(key = "pages") {
            FilterChip(
                selected = state.kind == SearchKind.PAGES,
                onClick = { onKind(SearchKind.PAGES) },
                label = { Text(stringResource(R.string.browse_pages)) },
                leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
        item(key = "databases") {
            FilterChip(
                selected = state.kind == SearchKind.DATABASES,
                onClick = { onKind(SearchKind.DATABASES) },
                label = { Text(stringResource(R.string.browse_databases)) },
                leadingIcon = { Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
        items(state.workspaces, key = { "w:" + it.id }) { workspace ->
            FilterChip(
                selected = state.workspace == workspace.id,
                onClick = { onWorkspace(workspace.id) },
                label = { Text(workspace.name.ifBlank { stringResource(R.string.workspace_unnamed) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Workspaces, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
    }
}

@Composable
private fun Results(
    results: List<WorkspaceItem>,
    searching: Boolean,
    workspaceNames: Map<String, String>,
    pinnedDatabases: Set<String>,
    pinnedPages: Set<String>,
    onOpenPage: (WorkspaceItem) -> Unit,
    onOpenDatabase: (String) -> Unit,
    onTogglePin: (WorkspaceItem) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().readableWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(results, key = { (if (it.isDatabase) "d" else "p") + it.id }) { item ->
            val plain = item.id.replace("-", "")
            WorkspaceRow(
                item,
                pinned = if (item.isDatabase) plain in pinnedDatabases else plain in pinnedPages,
                onClick = { if (item.isDatabase) onOpenDatabase(item.id) else onOpenPage(item) },
                onTogglePin = { onTogglePin(item) },
                modifier = Modifier.animateItem(),
                workspace = workspaceNames[item.id],
            )
        }
        if (searching) {
            item(key = "__searching") { LoadingRows(count = 2) }
        }
    }
}
