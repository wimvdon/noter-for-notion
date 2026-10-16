package com.impca201.noter.ui.note

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import com.impca201.noter.ui.common.NotionImage
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.impca201.noter.R
import com.impca201.noter.ui.common.SyncButton
import com.impca201.noter.data.model.Block
import com.impca201.noter.data.model.BlockType
import com.impca201.noter.data.model.NoteProperty
import com.impca201.noter.data.model.NoteSummary
import com.impca201.noter.data.model.PropertyValue
import com.impca201.noter.logic.DocumentEditor
import com.impca201.noter.logic.PropertyLayouts
import com.impca201.noter.ui.theme.AppShapes
import com.impca201.noter.ui.theme.SkeletonLine
import androidx.compose.material.icons.automirrored.rounded.ArrowRight
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.impca201.noter.data.notion.BlockCodec
import com.impca201.noter.data.notion.InlineMarkdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(pageId: String, onBack: () -> Unit, onOpenNote: (String) -> Unit, onOpenDatabase: (String) -> Unit) {
    val viewModel: NoteViewModel = viewModel(key = "note-$pageId", factory = NoteViewModel.factory(pageId))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var customize by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val openFailed by viewModel.openFailed.collectAsStateWithLifecycle()
    val openFailedText = stringResource(R.string.page_open_failed)
    LaunchedEffect(openFailed) {
        if (openFailed) {
            viewModel.openFailedShown()
            snackbar.showSnackbar(openFailedText)
        }
    }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val note = state.note

    LaunchedEffect(note?.id) { note?.let { viewModel.loadRelatedFor(it, state.schema) } }

    // Keep the line with the cursor on screen after Enter adds one at the bottom.
    LaunchedEffect(focus) {
        val target = focus ?: return@LaunchedEffect
        val index = state.blocks.indexOfFirst { it.key == target.key }
        if (index < 0) return@LaunchedEffect
        val itemIndex = index + blocksOffset(note)
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (itemIndex !in visible || itemIndex >= (visible.maxOrNull() ?: 0)) listState.animateScrollToItem(itemIndex)
    }

    if (customize && note != null) {
        PropertyLayoutSheet(
            properties = note.properties,
            layout = state.layout,
            onReorder = viewModel::reorderProperties,
            onToggleShown = viewModel::toggleShown,
            onToggleOnCard = viewModel::toggleOnCard,
            onCoverOnCard = viewModel::setCoverOnCard,
            onContentOnCard = viewModel::setContentOnCard,
            onDismiss = { customize = false },
        )
    }

    if (confirmTrash && note != null) {
        TrashDialog(
            title = InlineMarkdown.stripped(note.title).ifBlank { stringResource(R.string.untitled) },
            onConfirm = { confirmTrash = false; viewModel.trash(onBack) },
            onDismiss = { confirmTrash = false },
        )
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (note != null) {
                        SyncButton(state.pendingCount, state.offline, state.syncing, onSync = viewModel::sync)
                        IconButton(onClick = { viewModel.togglePin() }) {
                            Icon(
                                if (state.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(if (state.pinned) R.string.action_unpin else R.string.action_pin),
                                tint = if (state.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (note.url != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_open_in_notion)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                        onClick = {
                                            menu = false
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, note.url.toUri())) }
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_trash)) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                    onClick = { menu = false; confirmTrash = true },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            val block = state.blocks.firstOrNull { it.key == focusedKey }
            AnimatedVisibility(visible = block != null, enter = fadeIn(), exit = fadeOut()) {
                if (block != null) {
                    FormatToolbar(
                        block = block,
                        canChangeType = viewModel.canChangeType(block),
                        onType = { viewModel.setType(block.key, it) },
                        onAddDivider = { viewModel.insertAfter(block.key, BlockType.DIVIDER) },
                        onDelete = { viewModel.delete(block.key) },
                        onDone = { keyboard?.hide(); focusManager.clearFocus() },
                    )
                }
            }
        },
        modifier = Modifier.imePadding(),
    ) { padding ->
        if (note == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (!state.loadingContent) Text(stringResource(R.string.note_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        val numbers = remember(state.blocks) { DocumentEditor.numbers(state.blocks) }
        val headings = remember(state.blocks) { state.blocks.filter { it.type.isHeading && it.text.isNotBlank() } }
        val styling = MarkdownStyling(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.primary)
        val titleProperty = note.properties.firstOrNull { it.type == "title" }
        val lookups = PropertyLookups(state.users, state.relatedPages, viewModel::loadUsers, viewModel::loadRelated)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 12.dp, bottom = 32.dp),
        ) {
            note.cover?.let { cover ->
                item(key = "__cover") {
                    // Notion's header image, from edge to edge.
                    NotionImage(
                        url = cover,
                        expiry = note.coverExpiry,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fullBleed(start = 20.dp, end = 12.dp)
                            .height(COVER_HEIGHT),
                    )
                }
            }
            note.icon?.let { icon ->
                item(key = "__icon") {
                    Text(icon, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
                }
            }
            item(key = "__title") {
                TitleField(
                    note = note,
                    onChange = { text -> titleProperty?.let { viewModel.setProperty(it.copy(value = PropertyValue.Text(text)), debounce = true) } },
                    onEnter = { state.blocks.firstOrNull()?.let { viewModel.focusBlock(it.key, 0) } },
                )
            }
            item(key = "__properties") {
                PropertiesPanel(note, state, lookups, onCustomize = { customize = true }) { prop, debounce ->
                    viewModel.setProperty(prop, debounce)
                }
            }
            item(key = "__divider") {
                HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 8.dp, end = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (state.loadingContent) {
                item(key = "__loading") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
                        repeat(4) { SkeletonLine(Modifier.fillMaxWidth(if (it % 2 == 0) 0.9f else 0.6f)) }
                    }
                }
            } else {
                if (state.contentError) {
                    item(key = "__error") {
                        Text(
                            stringResource(R.string.content_offline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(state.blocks, key = { it.key }) { block ->
                    Column(Modifier.animateItem()) {
                        BlockRow(
                            block = block,
                            number = numbers[block.key],
                            onlyBlock = state.blocks.size == 1,
                            focus = focus,
                            styling = styling,
                            onText = { text, cursor -> viewModel.changeText(block.key, text, cursor) },
                            onBackspaceAtStart = { viewModel.backspaceAtStart(block.key) },
                            onToggle = { viewModel.toggleChecked(block.key) },
                            onDelete = { viewModel.delete(block.key) },
                            onFocusChange = { focused ->
                                if (focused) focusedKey = block.key else if (focusedKey == block.key) focusedKey = null
                            },
                            headings = headings,
                            onOpenPage = { id -> viewModel.openLinkedPage(id, onOpenNote) },
                            onOpenDatabase = onOpenDatabase,
                        )
                    }
                }
                item(key = "__footer") {
                    // As in Notion: tapping below the content continues writing at the end.
                    val label = stringResource(R.string.add_line)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClickLabel = label,
                                role = Role.Button,
                                onClick = viewModel::addLineAtEnd,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trash_title)) },
        text = { Text(stringResource(R.string.trash_body, title)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.trash_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Index of the first block in the list (after cover, icon, title, properties and divider). */
private fun blocksOffset(note: NoteSummary?): Int = 3 + (if (note?.cover != null) 1 else 0) + (if (note?.icon != null) 1 else 0)

private val COVER_HEIGHT = 180.dp

/** Lets an item of a padded list reach the screen's edges. */
private fun Modifier.fullBleed(start: Dp, end: Dp): Modifier = layout { measurable, constraints ->
    val extra = (start + end).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-start.roundToPx(), 0) }
}

@Composable
private fun TitleField(note: NoteSummary, onChange: (String) -> Unit, onEnter: () -> Unit) {
    var text by remember(note.id) { mutableStateOf(TextFieldValue(note.title)) }
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(note.title) { if (!focused && note.title != text.text) text = TextFieldValue(note.title) }
    LaunchedEffect(note.id) {
        // A new, untitled note: start typing the title right away.
        if (note.isLocal && note.title.isEmpty()) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
    BasicTextField(
        value = text,
        onValueChange = { value ->
            if (value.text.contains('\n')) {
                onEnter()
                return@BasicTextField
            }
            val changed = value.text != text.text
            text = value
            if (changed) onChange(value.text)
        },
        textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp, end = 8.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .semantics { heading() },
        decorationBox = { inner ->
            if (text.text.isEmpty()) {
                Text(
                    stringResource(R.string.title_placeholder),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            inner()
        },
    )
}

@Composable
private fun PropertiesPanel(
    note: NoteSummary,
    state: NoteUiState,
    lookups: PropertyLookups,
    onCustomize: () -> Unit,
    onChange: (NoteProperty, Boolean) -> Unit,
) {
    val arranged = remember(note.properties, state.layout) { PropertyLayouts.arrange(note.properties, state.layout) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(end = 8.dp)) {
        arranged.shown.forEach { prop ->
            PropertyRow(prop, state.schema?.property(prop.name), lookups, onChange)
        }
        AnimatedVisibility(showHidden, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                arranged.hidden.forEach { prop -> PropertyRow(prop, state.schema?.property(prop.name), lookups, onChange) }
            }
        }
        // The collapsed properties, and the button to choose which properties are shown.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (arranged.hidden.isNotEmpty()) {
                val rotation by animateFloatAsState(if (showHidden) 180f else 0f, label = "chevron")
                Row(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Button) { showHidden = !showHidden }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showHidden) {
                            stringResource(R.string.hide_properties)
                        } else {
                            pluralStringResource(R.plurals.more_properties, arranged.hidden.size, arranged.hidden.size)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (arranged.shown.isNotEmpty() || arranged.hidden.isNotEmpty()) {
                IconButton(onClick = onCustomize) {
                    Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.customize_properties), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BlockRow(
    block: Block,
    number: Int?,
    onlyBlock: Boolean,
    focus: FocusTarget?,
    styling: MarkdownStyling,
    onText: (String, Int) -> Unit,
    onBackspaceAtStart: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    headings: List<Block>,
    onOpenPage: (String) -> Unit,
    onOpenDatabase: (String) -> Unit,
) {
    when (block.type) {
        BlockType.UNSUPPORTED -> {
            ReadOnlyBlock(block, headings, onOpenPage, onOpenDatabase)
            return
        }
        BlockType.DIVIDER -> {
            HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 12.dp, end = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            return
        }
        else -> Unit
    }
    var value by remember(block.key) { mutableStateOf(TextFieldValue(block.text)) }
    val requester = remember(block.key) { FocusRequester() }
    LaunchedEffect(block.text) {
        if (value.text != block.text) value = TextFieldValue(block.text, TextRange(block.text.length.coerceAtMost(value.selection.end)))
    }
    LaunchedEffect(focus) {
        if (focus?.key == block.key) {
            value = TextFieldValue(block.text, TextRange(focus.cursor.coerceIn(0, block.text.length)))
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }

    val colors = MaterialTheme.colorScheme
    val baseStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
    val style: TextStyle = when (block.type) {
        BlockType.HEADING_1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        BlockType.HEADING_2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        BlockType.HEADING_3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        BlockType.TODO -> if (block.checked) baseStyle.copy(textDecoration = TextDecoration.LineThrough) else baseStyle
        BlockType.CODE -> MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = colors.onSurface)
        BlockType.QUOTE -> baseStyle.copy(fontStyle = FontStyle.Italic)
        else -> baseStyle
    }

    // Code and callouts sit in a container and quotes get a bar, as in Notion; the rest is plain text.
    val container: Modifier = when (block.type) {
        BlockType.CODE, BlockType.CALLOUT -> Modifier
            .padding(top = 4.dp, bottom = 4.dp, end = 8.dp)
            .background(colors.surfaceContainerHigh, AppShapes.Item)
            .padding(horizontal = 12.dp, vertical = 8.dp)
        BlockType.QUOTE -> Modifier
            .padding(vertical = 4.dp)
            .drawBehind { drawRect(colors.onSurface, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 14.dp)
        else -> Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (block.type.isHeading) 12.dp else 0.dp)
            .then(container),
        verticalAlignment = if (block.type == BlockType.TODO) Alignment.CenterVertically else Alignment.Top,
    ) {
        when {
            block.type == BlockType.TODO -> Checkbox(checked = block.checked, onCheckedChange = { onToggle() })
            block.type == BlockType.BULLETED -> Box(Modifier.width(28.dp).height(28.dp), contentAlignment = Alignment.Center) { Bullet() }
            block.type == BlockType.NUMBERED -> Box(Modifier.width(28.dp).padding(top = 2.dp), contentAlignment = Alignment.TopCenter) {
                Text("${number ?: 1}.", style = baseStyle)
            }
            block.type == BlockType.TOGGLE || block.toggleable -> Icon(
                Icons.AutoMirrored.Rounded.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = colors.onSurfaceVariant,
            )
            block.type == BlockType.CALLOUT ->
                Text(block.icon ?: BlockCodec.DEFAULT_CALLOUT_ICON, style = baseStyle, modifier = Modifier.padding(top = 4.dp, end = 10.dp))
        }
        val placeholder = when {
            block.type == BlockType.TODO -> stringResource(R.string.placeholder_item)
            block.type.isHeading -> stringResource(R.string.placeholder_heading)
            block.type == BlockType.TOGGLE -> stringResource(R.string.type_toggle)
            block.type == BlockType.QUOTE -> stringResource(R.string.type_quote)
            block.type == BlockType.CALLOUT -> stringResource(R.string.type_callout)
            block.type == BlockType.CODE -> stringResource(R.string.type_code)
            onlyBlock -> stringResource(R.string.placeholder_start)
            else -> null
        }
        Column(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = { new ->
                    if (new.text == value.text) {
                        value = new
                    } else if (new.text.contains('\n') && block.type != BlockType.CODE) {
                        onText(new.text, new.selection.start)
                    } else {
                        value = new
                        onText(new.text, new.selection.start)
                    }
                },
                textStyle = style,
                cursorBrush = SolidColor(colors.primary),
                // Code is shown exactly as typed: no Markdown styling.
                visualTransformation = if (block.type == BlockType.CODE) VisualTransformation.None else styling,
                keyboardOptions = if (block.type == BlockType.CODE) {
                    KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)
                } else {
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (block.type == BlockType.TODO) 6.dp else 4.dp)
                    .alpha(if (block.type == BlockType.TODO && block.checked) 0.55f else 1f)
                    .focusRequester(requester)
                    .onFocusChanged { onFocusChange(it.isFocused) }
                    .onPreviewKeyEvent { event ->
                        val atStart = value.selection.start == 0 && value.selection.end == 0
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && atStart) {
                            onBackspaceAtStart()
                            true
                        } else {
                            false
                        }
                    }
                    .then(if (block.type.isHeading) Modifier.semantics { heading() } else Modifier),
                decorationBox = { inner ->
                    if (value.text.isEmpty() && placeholder != null) {
                        Text(placeholder, style = style, color = colors.outline)
                    }
                    inner()
                },
            )
            // Nested blocks (inside a toggle, a callout, …) aren't shown here; say so.
            if (block.hasChildren && !block.type.isListItem) {
                Text(
                    stringResource(R.string.nested_content),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        // A checklist item can be removed with one tap, as in Google Keep.
        if (block.type == BlockType.TODO) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_delete_item),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Shown above the keyboard while a block is being edited: change its kind (the block types
 * Notion has that the app can edit), add a divider below it, delete it, hide the keyboard.
 */
@Composable
private fun FormatToolbar(
    block: Block,
    canChangeType: Boolean,
    onType: (BlockType) -> Unit,
    onAddDivider: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPaddingIfNoIme()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            @Composable
            fun typeButton(type: BlockType, description: Int, content: @Composable () -> Unit) {
                val label = stringResource(description)
                IconToggleButton(
                    checked = block.type == type,
                    onCheckedChange = { onType(if (block.type == type) BlockType.PARAGRAPH else type) },
                    enabled = canChangeType,
                    colors = androidx.compose.material3.IconButtonDefaults.iconToggleButtonColors(
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    modifier = Modifier.semantics { contentDescription = label },
                ) {
                    content()
                }
            }
            typeButton(BlockType.PARAGRAPH, R.string.type_text) { Icon(Icons.AutoMirrored.Rounded.Notes, null) }
            typeButton(BlockType.HEADING_1, R.string.type_h1) { Text("H1", style = MaterialTheme.typography.labelLarge) }
            typeButton(BlockType.HEADING_2, R.string.type_h2) { Text("H2", style = MaterialTheme.typography.labelLarge) }
            typeButton(BlockType.HEADING_3, R.string.type_h3) { Text("H3", style = MaterialTheme.typography.labelLarge) }
            typeButton(BlockType.BULLETED, R.string.type_bulleted) { Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, null) }
            typeButton(BlockType.NUMBERED, R.string.type_numbered) { Icon(Icons.Rounded.FormatListNumbered, null) }
            typeButton(BlockType.TODO, R.string.type_checkbox) { Icon(Icons.Rounded.CheckBox, null) }
            typeButton(BlockType.TOGGLE, R.string.type_toggle) { Icon(Icons.AutoMirrored.Rounded.ArrowRight, null) }
            typeButton(BlockType.QUOTE, R.string.type_quote) { Icon(Icons.Rounded.FormatQuote, null) }
            typeButton(BlockType.CALLOUT, R.string.type_callout) { Icon(Icons.Rounded.Lightbulb, null) }
            typeButton(BlockType.CODE, R.string.type_code) { Icon(Icons.Rounded.Code, null) }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
            IconButton(onClick = onAddDivider) { Icon(Icons.Rounded.HorizontalRule, contentDescription = stringResource(R.string.insert_divider)) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete_line)) }
            IconButton(onClick = onDone) { Icon(Icons.Rounded.KeyboardHide, contentDescription = stringResource(R.string.action_hide_keyboard)) }
        }
    }
}

@Composable
private fun Modifier.navigationBarsPaddingIfNoIme(): Modifier {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    return if (imeVisible) this else this.navigationBarsPadding()
}
