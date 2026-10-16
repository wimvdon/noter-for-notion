package com.impca201.noter.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.impca201.noter.NoterApp
import com.impca201.noter.R
import com.impca201.noter.data.icons.IconCatalog

/**
 * Choose an icon from the whole Material Symbols set: a search field (English names and topics)
 * above a grid of matching icons, suggestions first when nothing is typed.
 */
@Composable
fun IconPicker(selected: String?, onSelect: (String) -> Unit) {
    val catalog = (LocalContext.current.applicationContext as NoterApp).iconCatalog
    val current = IconCatalog.fromStored(selected)
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, catalog) { catalog.search(query) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.icon_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (results.isEmpty()) {
            Text(
                stringResource(R.string.icon_none_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(GRID_HEIGHT),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(48.dp),
                modifier = Modifier.fillMaxWidth().height(GRID_HEIGHT),
            ) {
                items(results, key = { it.name }) { option ->
                    IconToggleButton(
                        checked = option.name == current,
                        onCheckedChange = { onSelect(option.name) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.iconToggleButtonColors(
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(DatabaseIcons.vector(option), contentDescription = option.label)
                    }
                }
            }
        }
    }
}

private val GRID_HEIGHT = 240.dp
