package com.impca201.noter.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.impca201.noter.R

// Shared design kit: this file is identical in every app apart from the package and R imports
// (see DESIGN.md). It needs R.drawable.ic_arrow_back, R.drawable.ic_expand_more and the strings
// section_expanded and section_collapsed.

/**
 * The settings screen: a large top app bar that collapses on scroll, over a list of
 * [CollapsibleSection]s in the order DESIGN.md gives, ending with a [SettingsFooter].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Edge-to-edge: the keyboard does not shrink the screen, so the list makes room for it.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        snackbarHost = snackbarHost,
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = backLabel)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/**
 * A settings section: a card whose header (icon, title and the current value as [summary])
 * expands and collapses its content. Expanded state survives rotation.
 */
@Composable
fun CollapsibleSection(
    key: String,
    title: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(key) { mutableStateOf(initiallyExpanded) }
    Card(
        shape = AppShapes.Group,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        ExpandableHeader(
            title = title,
            summary = summary,
            icon = icon,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            titleStyleLarge = true,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

/** A nested, collapsible part of a [CollapsibleSection]. */
@Composable
fun CollapsibleSubsection(
    key: String,
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(key) { mutableStateOf(initiallyExpanded) }
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ExpandableHeader(
            title = title,
            summary = null,
            icon = null,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            titleStyleLarge = false,
            horizontalPadding = 0,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun ExpandableHeader(
    title: String,
    summary: String?,
    icon: Painter?,
    expanded: Boolean,
    onToggle: () -> Unit,
    titleStyleLarge: Boolean,
    horizontalPadding: Int = 20,
) {
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val stateText = stringResource(if (expanded) R.string.section_expanded else R.string.section_collapsed)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = stateText }
                .padding(horizontal = horizontalPadding.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (titleStyleLarge) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            modifier = Modifier.rotate(chevronRotation),
        )
    }
}

/** A setting with a switch. The whole row is one accessible switch. */
@Composable
fun SwitchSetting(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    warning: String? = null,
    onWarningClick: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
        if (warning != null) {
            Text(
                text = warning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    if (onWarningClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onWarningClick).padding(vertical = 8.dp)
                    } else {
                        Modifier
                    },
            )
        }
    }
}

/**
 * The content of every app's Appearance section: the theme as segmented buttons
 * (System / Light / Dark) and, on Android 12+, the Dynamic color switch.
 */
@Composable
fun AppearanceSettings(
    themeLabel: String,
    themeOptions: List<String>,
    selectedTheme: Int,
    onThemeSelect: (Int) -> Unit,
    dynamicColorTitle: String,
    dynamicColorSummary: String,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    dynamicColorSupported: Boolean,
) {
    Text(themeLabel, style = MaterialTheme.typography.bodyLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        themeOptions.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedTheme,
                onClick = { onThemeSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, themeOptions.size),
            ) { Text(label) }
        }
    }
    if (dynamicColorSupported) {
        SwitchSetting(
            title = dynamicColorTitle,
            summary = dynamicColorSummary,
            checked = dynamicColor,
            onCheckedChange = onDynamicColorChange,
        )
    }
}

/** The last item of the settings list: the app name and version, plus optional links. */
@Composable
fun SettingsFooter(
    text: String,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The installed version name, for the [SettingsFooter]. */
@Composable
fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
    }
}
