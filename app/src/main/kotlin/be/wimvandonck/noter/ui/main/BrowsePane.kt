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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.noter.R
import be.wimvandonck.noter.data.model.WorkspaceItem
import be.wimvandonck.noter.logic.NotionDates
import be.wimvandonck.noter.ui.theme.EmptyState
import be.wimvandonck.noter.ui.theme.SkeletonLine

/**
 * Browse: the databases and pages shared with the integration, most recently edited first, with
 * a search field. A database opens its notes; a page opens in the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowsePane(
    state: BrowseState,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenPage: (WorkspaceItem) -> Unit,
    onOpenDatabase: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onSearch,
            placeholder = { Text(stringResource(R.string.browse_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onSearch("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear))
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        PullToRefreshBox(
            isRefreshing = state.loading && state.items != null,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            val items = state.items
            when {
                items == null && state.error == null -> LoadingRows()
                state.error != null && items.isNullOrEmpty() ->
                    LoadFailed(state.error, onRefresh)
                items.isNullOrEmpty() && state.query.isBlank() -> EmptyState(
                    Icons.Rounded.Explore,
                    stringResource(R.string.browse_empty_title),
                    body = stringResource(R.string.browse_empty),
                )
                items.isNullOrEmpty() -> EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.browse_nothing_found))
                else -> BrowseList(items, onOpenPage, onOpenDatabase)
            }
        }
    }
}

@Composable
private fun BrowseList(items: List<WorkspaceItem>, onOpenPage: (WorkspaceItem) -> Unit, onOpenDatabase: (String) -> Unit) {
    val (databases, pages) = items.partition { it.isDatabase }
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (databases.isNotEmpty()) {
            item(key = "__databases") { Header(stringResource(R.string.browse_databases)) }
            items(databases, key = { "d" + it.id }) { item ->
                Row(item, locale, Icons.Rounded.TableChart) { onOpenDatabase(item.id) }
            }
        }
        if (pages.isNotEmpty()) {
            item(key = "__pages") { Header(stringResource(R.string.browse_pages)) }
            items(pages, key = { "p" + it.id }) { item ->
                Row(item, locale, Icons.Rounded.Description) { onOpenPage(item) }
            }
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
private fun Row(item: WorkspaceItem, locale: java.util.Locale, fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(item.title.ifBlank { stringResource(R.string.untitled) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = if (item.lastEditedTime.isNotBlank()) {
            { Text(stringResource(R.string.browse_edited, NotionDates.display(item.lastEditedTime, locale))) }
        } else {
            null
        },
        leadingContent = {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (item.icon != null) {
                    Text(item.icon, style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(fallbackIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun LoadingRows() {
    val loading = stringResource(R.string.loading)
    Column(
        Modifier.fillMaxSize().padding(16.dp).semantics(mergeDescendants = true) { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        repeat(6) { SkeletonLine(Modifier.fillMaxWidth(if (it % 2 == 0) 0.7f else 0.5f)) }
    }
}
