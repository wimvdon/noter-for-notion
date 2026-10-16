package com.impca201.noter.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.impca201.noter.R
import kotlinx.coroutines.launch

/**
 * A database opened from Browse or from a link in a note: a child page with its notes and
 * filters. "Add to navigation" makes it a view, with the filters selected now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(databaseId: String, onBack: () -> Unit, onOpenNote: (String) -> Unit) {
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
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.addView(state.title.ifBlank { untitled })
                            scope.launch { snackbar.showSnackbar(added) }
                        },
                    ) {
                        Icon(
                            if (state.viewCount > 0) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                            contentDescription = stringResource(R.string.add_view),
                        )
                    }
                },
            )
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
                onRefresh = { viewModel.refresh() },
                onOpen = onOpenNote,
                onTogglePin = { viewModel.togglePin(it) },
                onTrash = { viewModel.trash(it) },
                onReordered = { viewModel.saveOrder(it) },
            )
        }
    }
}
