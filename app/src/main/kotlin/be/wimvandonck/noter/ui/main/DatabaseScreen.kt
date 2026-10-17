package be.wimvandonck.noter.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import be.wimvandonck.noter.R
import kotlinx.coroutines.launch

/**
 * A database opened from Browse or from a link in a note: a child page with its notes and
 * filters. "Add view" makes it a view, with the filters selected now; once it is one, "Edit view"
 * opens its setup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(databaseId: String, onBack: () -> Unit, onOpenNote: (String) -> Unit, onEditView: (String) -> Unit) {
    val viewModel: DatabaseViewModel = viewModel(key = "database-$databaseId", factory = DatabaseViewModel.factory(databaseId))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val untitled = stringResource(R.string.untitled)
    val added = stringResource(R.string.view_added)
    val content = state.content

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        // One view per database from here: once added, the button edits it.
                        val viewId = state.viewId
                        if (viewId == null) {
                            TextButton(
                                onClick = {
                                    viewModel.addView(state.title.ifBlank { untitled })
                                    scope.launch { snackbar.showSnackbar(added) }
                                },
                            ) {
                                Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.add_view))
                            }
                        } else {
                            TextButton(onClick = { onEditView(viewId) }) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.edit_view))
                            }
                        }
                    },
                )
                // A database with several data sources: one tab each (they are peers of one database).
                state.sources?.let { sources ->
                    PrimaryScrollableTabRow(selectedTabIndex = sources.indexOfFirst { it.id == state.sourceId }.coerceAtLeast(0), edgePadding = 16.dp) {
                        sources.forEach { source ->
                            Tab(
                                selected = source.id == state.sourceId,
                                onClick = { viewModel.selectSource(source.id) },
                                text = { Text(source.title.ifBlank { untitled }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (content.hasKey) {
                FloatingActionButton(onClick = { viewModel.createNote(onOpenNote) }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_new_note))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NotesPane(
                state = content,
                previews = previews,
                onLoadPreviews = viewModel::loadPreviews,
                onToggleChip = viewModel::toggleChip,
                onClearChips = viewModel::clearChips,
                onLoadChipTitles = { viewModel.loadChipTitles(it) },
                onRefresh = { viewModel.refresh() },
                onOpen = onOpenNote,
                onTogglePin = { viewModel.togglePin(it) },
                onTrash = { viewModel.trash(it) },
                onReordered = { viewModel.saveOrder(it) },
            )
        }
    }
}
