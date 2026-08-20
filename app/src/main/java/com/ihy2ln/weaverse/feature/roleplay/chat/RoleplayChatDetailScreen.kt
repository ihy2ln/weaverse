package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.text.MediaGrid
import com.ihy2ln.weaverse.core.ui.components.CollapsibleUsageStrip
import com.ihy2ln.weaverse.core.ui.components.EditTextAction
import com.ihy2ln.weaverse.core.ui.components.EditTextPopup
import com.ihy2ln.weaverse.core.ui.components.EditTextPopupConfig
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.PromptCommandButtons
import com.ihy2ln.weaverse.core.ui.components.AudioMediaPlayer
import com.ihy2ln.weaverse.core.ui.components.MediaEditAction
import com.ihy2ln.weaverse.core.ui.components.MediaEditPopup
import com.ihy2ln.weaverse.core.ui.components.MediaEditPopupConfig
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.components.ZoomableMedia
import com.ihy2ln.weaverse.core.ui.components.mergeSpokenText
import com.ihy2ln.weaverse.core.ui.components.rememberSpeechToText
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.ScrollGutterBackdrop
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.core.ui.util.scrollGutterPadding
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoleplayChatDetailScreen(
    chatId: String,
    onBack: () -> Unit,
    onChromeChange: (RoleplayChatChrome?) -> Unit = {},
    onOpenAiPrompt: () -> Unit = {},
    onOpenManualPrompt: () -> Unit = {},
    promptOverlayOpen: Boolean = false,
    viewModel: RoleplayChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(chatId) { viewModel.bindChat(chatId) }
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val tokens = inkTokens()
    var popupMessageId by remember { mutableStateOf<String?>(null) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var editDraft by remember { mutableStateOf("") }
    val startDictateNew = rememberSpeechToText { spoken ->
        viewModel.insertUserText(spoken)
    }
    val startDictateEdit = rememberSpeechToText { spoken ->
        editDraft = mergeSpokenText(editDraft, spoken)
    }
    val listState = rememberLazyListState()
    val mediaFocus = remember { FocusRequester() }

    LaunchedEffect(state.title, state.displayMode) {
        onChromeChange(
            RoleplayChatChrome(
                title = state.title.ifBlank { "Chat" },
                displayMode = state.displayMode,
                onDisplayMode = viewModel::setDisplayMode,
            ),
        )
    }
    DisposableEffect(Unit) {
        onDispose { onChromeChange(null) }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.attachMedia(uris) else viewModel.clearMediaPickRequest()
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.attachMedia(uris)
    }

    LaunchedEffect(state.mediaPickRequestId) {
        if (state.mediaPickRequestId > 0L) {
            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }

    LaunchedEffect(state.audioPickRequestId) {
        if (state.audioPickRequestId > 0L) {
            audioPicker.launch(arrayOf("audio/*", "audio/mpeg", "audio/wav", "audio/x-wav"))
        }
    }

    LaunchedEffect(state.messages.size, state.streamingText, state.mediaPanels.size) {
        val last = state.messages.lastIndex
        if (last >= 0 && state.displayMode == "messenger") {
            runCatching { listState.animateScrollToItem(last) }
        }
    }

    LaunchedEffect(state.selectedMediaKey) {
        if (state.selectedMediaKey != null) runCatching { mediaFocus.requestFocus() }
    }

    val compactStyle = MaterialTheme.typography.bodySmall.copy(
        lineHeight = 18.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )

    LaunchedEffect(state.displayMode, state.mediaPanels, state.messages.size) {
        if (state.displayMode == "roleplay") viewModel.ensureMangaGridPlacement()
    }

    if (editingMessageId != null) {
        AlertDialog(
            onDismissRequest = { editingMessageId = null },
            title = { Text("Edit message") },
            text = {
                VoiceToTextField(
                    value = editDraft,
                    onValueChange = { editDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 10,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.editMessage(editingMessageId!!, editDraft)
                        editingMessageId = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessageId = null }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(inkTokens().background)
            .focusRequester(mediaFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Backspace, Key.Delete -> {
                        if (state.selectedMediaKey != null) {
                            viewModel.removeSelectedMedia()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
    ) {
        // Title + Messenger|DM|Roleplay live in AppShell WorkspaceChrome (collapsible).
        when (state.displayMode) {
            "roleplay" -> ScrollGutterBackdrop(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = InkSpacing.sm),
            ) {
                MangaSnapGrid(
                    panels = state.mediaPanels,
                    selectedKey = state.selectedMediaKey,
                    canPaste = state.canPasteMedia,
                    compactStyle = compactStyle,
                    gridSize = MediaGrid.DM_SIZE,
                    textEmphasis = false,
                    emptyHint = "Storyboard — a 3×3 grid of panels. Tap + on an empty panel to add media, then hold → Move to reposition or drag corner to resize (this moves other panels out of the way, it won't cover them). Drop onto another picture to stack. Use + Page for more panels than one board holds.\nPress / for AI · \\ for manual text.",
                    onSelect = { msgId, blockId -> viewModel.selectMedia(msgId, blockId) },
                    onRemove = viewModel::removeMedia,
                    onSnap = viewModel::setMediaGridCell,
                    onResizeSpan = viewModel::setMediaGridSpan,
                    onStackOnto = viewModel::stackMediaOnto,
                    onStackMenu = viewModel::stackMedia,
                    onCycleStack = viewModel::cycleMediaStack,
                    onMediaEdit = viewModel::onMediaEditAction,
                    onSetCaption = viewModel::setPanelCaption,
                    showAddCell = true,
                    onAddMedia = { col, row, page -> viewModel.requestMediaPickForCell(col, row, page) },
                    pagingEnabled = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            "dungeonMaster" -> ScrollGutterBackdrop(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = InkSpacing.sm),
            ) {
                DungeonMasterFlow(
                    messages = state.messages,
                    mediaPanels = state.mediaPanels,
                    input = state.input,
                    isStreaming = state.isStreaming,
                    onInputChange = viewModel::onInputChange,
                    onSend = viewModel::generate,
                    onAddMedia = viewModel::requestMediaPick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> ScrollGutterBackdrop(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = scrollGutterPadding(),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    val bubbleColor = if (message.role == "user") {
                        state.userBubbleColor.copy(alpha = 0.15f)
                    } else {
                        state.characterBubbleColor.copy(alpha = 0.15f)
                    }
                    val align = if (message.role == "user") Alignment.End else Alignment.Start
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = align,
                    ) {
                        Box {
                            MessengerBubble(
                                message = message,
                                bubbleColor = bubbleColor,
                                compactStyle = compactStyle,
                                userAlign = message.role == "user",
                                selectedMediaKey = state.selectedMediaKey,
                                onLongPress = { popupMessageId = message.id },
                                canPasteMedia = state.canPasteMedia,
                                onSelectMedia = { blockId ->
                                    viewModel.selectMedia(message.id, blockId)
                                },
                                onRemoveMedia = { blockId ->
                                    viewModel.removeMedia(message.id, blockId)
                                },
                                onMoveMedia = { blockId, delta ->
                                    viewModel.moveMedia(message.id, blockId, delta)
                                },
                                onStackMedia = { blockId ->
                                    viewModel.stackMedia(message.id, blockId)
                                },
                                onStackOnto = { fromId, ontoId ->
                                    viewModel.stackMediaOnto(message.id, fromId, ontoId)
                                },
                                onCycleStack = { blockId ->
                                    viewModel.cycleMediaStack(message.id, blockId)
                                },
                                onMediaEdit = { blockId, action ->
                                    viewModel.onMediaEditAction(message.id, blockId, action)
                                },
                            )
                            EditTextPopup(
                                expanded = popupMessageId == message.id,
                                onDismiss = { popupMessageId = null },
                                config = EditTextPopupConfig(
                                    showFormatting = false,
                                    showWritingAi = false,
                                    showHistory = false,
                                    showMessageEdit = true,
                                    showSpeak = true,
                                    hasSelection = message.text.isNotBlank(),
                                ),
                                onAction = { action ->
                                    when (action) {
                                        EditTextAction.Copy, EditTextAction.SelectAll -> {
                                            if (message.text.isNotBlank()) {
                                                clipboard.setText(AnnotatedString(message.text))
                                            }
                                        }
                                        EditTextAction.Cut -> {
                                            if (message.text.isNotBlank()) {
                                                clipboard.setText(AnnotatedString(message.text))
                                                viewModel.editMessage(message.id, "")
                                            }
                                        }
                                        EditTextAction.Paste -> {
                                            val clip = clipboard.getText()?.text.orEmpty()
                                            editingMessageId = message.id
                                            editDraft = message.text + clip
                                        }
                                        EditTextAction.Delete -> viewModel.deleteMessage(message.id)
                                        EditTextAction.Edit -> {
                                            editingMessageId = message.id
                                            editDraft = message.text
                                        }
                                        EditTextAction.Speak -> viewModel.speakText(message.text)
                                        EditTextAction.Dictate -> {
                                            editingMessageId = message.id
                                            editDraft = message.text
                                            startDictateEdit()
                                        }
                                        else -> Unit
                                    }
                                },
                            )
                        }
                        if (message.role == "char" && message.swipeCount > 1) {
                            Row {
                                InkTextButton(label = "◀", onClick = { viewModel.swipe(message.id, -1) })
                                Text(
                                    "${message.swipeIndex + 1}/${message.swipeCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                InkTextButton(label = "▶", onClick = { viewModel.swipe(message.id, 1) })
                                InkTextButton(label = "Regen", onClick = { viewModel.regenerate(message.id) })
                            }
                        }
                    }
                }
                if (state.isStreaming && state.streamingText.isNotBlank()) {
                    item("streaming") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .clip(RoundedCornerShape(InkSpacing.radiusMd))
                                .background(state.characterBubbleColor.copy(alpha = 0.15f))
                                .padding(InkSpacing.sm),
                        ) {
                            Text("Character · typing…", style = MaterialTheme.typography.labelSmall)
                            Text(state.streamingText, style = compactStyle, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                alwaysScrollEndSpacer()
            }
            }
        }

        if (state.errorMessage.isNotBlank()) {
            Text(
                state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = InkSpacing.lg),
            )
        }
        CollapsibleUsageStrip(
            usageText = state.lastUsage,
            modifier = Modifier.padding(horizontal = InkSpacing.lg),
        )

        // Prompt entry is global: / = AI, \ = manual. Keep Media/Audio here.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkTextButton(label = "Media", onClick = viewModel::requestMediaPick)
            InkTextButton(label = "Audio", onClick = viewModel::requestAudioPick)
            InkTextButton(label = "Mic", onClick = startDictateNew)
            if (state.showExtraPromptSurfaces && !promptOverlayOpen) {
                PromptCommandButtons(
                    onAi = onOpenAiPrompt,
                    onManual = onOpenManualPrompt,
                    enabled = !state.isStreaming,
                    modifier = Modifier.padding(start = InkSpacing.xs),
                )
            }
            if (state.isStreaming) {
                Text(
                    "Generating…",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = InkSpacing.sm),
                )
            }
        }
    }
}

/**
 * Dungeon Master mode: one linear "page" — the current scene picture on top,
 * the DM's narration in the middle, and the player's response pinned at the
 * bottom. Replaces the free-form snap grid this mode used to share with manga.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DungeonMasterFlow(
    messages: List<RpMessageUi>,
    mediaPanels: List<RpMediaRef>,
    input: String,
    isStreaming: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAddMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    val lastNarration = messages.lastOrNull { it.role == "char" }
    val pendingUser = messages.lastOrNull()?.takeIf { it.role == "user" }
    val sceneImage = mediaPanels.lastOrNull { it.path.isNotBlank() && !it.isAudio }

    Column(modifier = modifier.fillMaxSize()) {
        // Large scene picture — a fixed share of the screen, not a scroll-capped thumbnail.
        Box(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxWidth()
                .padding(InkSpacing.md),
            contentAlignment = Alignment.Center,
        ) {
            if (sceneImage != null) {
                ZoomableMedia(
                    path = sceneImage.path,
                    contentDescription = "Scene",
                    contentScale = ContentScale.Fit,
                    decodeOriginal = true,
                    fillPanel = true,
                    modifier = Modifier.fillMaxSize(),
                )
                InkTextButton(
                    label = "+",
                    onClick = onAddMedia,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(InkSpacing.xs)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            RoundedCornerShape(InkSpacing.radiusSm),
                        ),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(InkSpacing.radiusSm))
                        .border(
                            1.dp,
                            tokens.secondaryText.copy(alpha = 0.35f),
                            RoundedCornerShape(InkSpacing.radiusSm),
                        )
                        .combinedClickable(onClick = onAddMedia),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "+",
                        style = MaterialTheme.typography.headlineLarge,
                        color = tokens.secondaryText,
                    )
                    Text(
                        "Tap to add a scene picture",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.secondaryText,
                        modifier = Modifier.padding(top = InkSpacing.xs),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = InkSpacing.md),
        ) {
            Text(
                text = lastNarration?.text?.takeIf { it.isNotBlank() }
                    ?: "The DM hasn't set a scene yet — type an opening below and send it.",
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.primaryText,
            )
            if (pendingUser != null && pendingUser.text.isNotBlank()) {
                Text(
                    "You: ${pendingUser.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(top = InkSpacing.md),
                )
            }
            if (isStreaming) {
                Text(
                    "The DM is thinking…",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(top = InkSpacing.sm),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("What do you do?") },
                minLines = 1,
                maxLines = 4,
            )
            InkTextButton(
                label = if (isStreaming) "…" else "Send",
                onClick = onSend,
                modifier = Modifier.padding(start = InkSpacing.xs),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaSnapGrid(
    panels: List<RpMediaRef>,
    selectedKey: String?,
    canPaste: Boolean,
    compactStyle: androidx.compose.ui.text.TextStyle,
    gridSize: Int = MediaGrid.SIZE,
    textEmphasis: Boolean = false,
    emptyHint: String,
    onSelect: (String, String) -> Unit,
    onRemove: (String, String) -> Unit,
    onSnap: (String, String, Int, Int) -> Unit,
    onResizeSpan: (String, String, Int, Int) -> Unit,
    onStackOnto: (String, String, String) -> Unit,
    onStackMenu: (String, String) -> Unit,
    onCycleStack: (String, String) -> Unit,
    onMediaEdit: (String, String, MediaEditAction) -> Unit,
    onSetCaption: (String, String, String) -> Unit = { _, _, _ -> },
    showAddCell: Boolean = false,
    onAddMedia: (Int, Int, Int) -> Unit = { _, _, _ -> },
    pagingEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    val scroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var captionEditKey by remember { mutableStateOf<String?>(null) }
    var captionEditText by remember { mutableStateOf("") }
    var currentPage by remember { mutableIntStateOf(0) }
    val realPageCount = (panels.maxOfOrNull { it.gridPage } ?: 0) + 1
    val totalPages = maxOf(realPageCount, currentPage + 1)
    val pagePanels = if (pagingEnabled) panels.filter { it.gridPage == currentPage } else panels
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = InkSpacing.sm),
    ) {
        if (pagingEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkTextButton(
                    label = "‹",
                    enabled = currentPage > 0,
                    onClick = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                )
                Text(
                    "Page ${currentPage + 1}/$totalPages",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = InkSpacing.xs),
                )
                InkTextButton(
                    label = "›",
                    enabled = currentPage < realPageCount - 1,
                    onClick = { currentPage = (currentPage + 1).coerceAtMost(realPageCount - 1) },
                )
                InkTextButton(
                    label = "+ Page",
                    onClick = { currentPage = maxOf(realPageCount, currentPage + 1) },
                    modifier = Modifier.padding(start = InkSpacing.sm),
                )
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(InkSpacing.radiusSm))
                .border(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), RoundedCornerShape(InkSpacing.radiusSm))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        ) {
            val cellW = maxWidth / gridSize
            val cellH = maxHeight / gridSize
            // Snap grid stays active for move/resize/stack, but lines are hidden.
            if (pagePanels.isEmpty()) {
                Text(
                    emptyHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.secondaryText,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(InkSpacing.lg),
                )
            }
            val occupiedCells = mutableSetOf<Pair<Int, Int>>()
            pagePanels.forEach { panel ->
                val col = if (MediaGrid.isPlaced(panel.gridCol, panel.gridRow, gridSize)) {
                    panel.gridCol
                } else {
                    0
                }
                val row = if (MediaGrid.isPlaced(panel.gridCol, panel.gridRow, gridSize)) {
                    panel.gridRow
                } else {
                    0
                }
                val colSpan = MediaGrid.clampSpan(panel.gridColSpan, gridSize)
                    .coerceAtMost(gridSize - col)
                val rowSpan = MediaGrid.clampSpan(panel.gridRowSpan, gridSize)
                    .coerceAtMost(gridSize - row)
                occupiedCells += MediaGrid.cellsCovered(col, row, colSpan, rowSpan, gridSize)
                val key = "${panel.messageId}::${panel.blockId}"
                MangaSnapPanel(
                    panel = panel,
                    selected = selectedKey == key,
                    canPaste = canPaste,
                    cellW = cellW,
                    cellH = cellH,
                    col = col,
                    row = row,
                    colSpan = colSpan,
                    rowSpan = rowSpan,
                    gridSize = gridSize,
                    textEmphasis = textEmphasis,
                    compactStyle = compactStyle,
                    panels = pagePanels,
                    onSelect = { onSelect(panel.messageId, panel.blockId) },
                    onRemove = { onRemove(panel.messageId, panel.blockId) },
                    onSnap = { c, r -> onSnap(panel.messageId, panel.blockId, c, r) },
                    onResizeSpan = { cs, rs -> onResizeSpan(panel.messageId, panel.blockId, cs, rs) },
                    onStackOnto = { ontoBlockId ->
                        onStackOnto(panel.messageId, panel.blockId, ontoBlockId)
                    },
                    onStackMenu = { onStackMenu(panel.messageId, panel.blockId) },
                    onCycleStack = { onCycleStack(panel.messageId, panel.blockId) },
                    onMediaEdit = { onMediaEdit(panel.messageId, panel.blockId, it) },
                    onCaptionTap = {
                        captionEditKey = key
                        captionEditText = panel.caption.takeIf { it != "[media]" }.orEmpty()
                    },
                )
            }
            if (showAddCell) {
                for (r in 0 until gridSize) {
                    for (c in 0 until gridSize) {
                        if ((c to r) in occupiedCells) continue
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        x = with(density) { (cellW * c).roundToPx() },
                                        y = with(density) { (cellH * r).roundToPx() },
                                    )
                                }
                                .width(cellW)
                                .height(cellH)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(InkSpacing.radiusSm))
                                .border(
                                    1.dp,
                                    tokens.secondaryText.copy(alpha = 0.35f),
                                    RoundedCornerShape(InkSpacing.radiusSm),
                                )
                                .combinedClickable(onClick = { onAddMedia(c, r, currentPage) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "+",
                                style = MaterialTheme.typography.headlineSmall,
                                color = tokens.secondaryText,
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
    val editingKey = captionEditKey
    if (editingKey != null) {
        AlertDialog(
            onDismissRequest = { captionEditKey = null },
            title = { Text("Panel caption") },
            text = {
                OutlinedTextField(
                    value = captionEditText,
                    onValueChange = { captionEditText = it },
                    placeholder = { Text("What's said or happening in this panel…") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val (messageId, blockId) = editingKey.split("::", limit = 2)
                    onSetCaption(messageId, blockId, captionEditText.trim())
                    captionEditKey = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { captionEditKey = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaSnapPanel(
    panel: RpMediaRef,
    selected: Boolean,
    canPaste: Boolean,
    cellW: androidx.compose.ui.unit.Dp,
    cellH: androidx.compose.ui.unit.Dp,
    col: Int,
    row: Int,
    colSpan: Int,
    rowSpan: Int,
    gridSize: Int,
    textEmphasis: Boolean,
    compactStyle: androidx.compose.ui.text.TextStyle,
    panels: List<RpMediaRef>,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onSnap: (Int, Int) -> Unit,
    onResizeSpan: (Int, Int) -> Unit,
    onStackOnto: (String) -> Unit,
    onStackMenu: () -> Unit,
    onCycleStack: () -> Unit,
    onMediaEdit: (MediaEditAction) -> Unit,
    onCaptionTap: () -> Unit = {},
) {
    var dragX by remember(panel.blockId) { mutableFloatStateOf(0f) }
    var dragY by remember(panel.blockId) { mutableFloatStateOf(0f) }
    var resizeDx by remember(panel.blockId) { mutableFloatStateOf(0f) }
    var resizeDy by remember(panel.blockId) { mutableFloatStateOf(0f) }
    var menuOpen by remember(panel.blockId) { mutableStateOf(false) }
    var moveMode by remember(panel.blockId) { mutableStateOf(false) }
    val border = when {
        moveMode -> MaterialTheme.colorScheme.tertiary
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widthDp = cellW * colSpan
    val heightDp = cellH * rowSpan
    fun commitMovePlacement() {
        val originX = with(density) { cellW.toPx() } * col
        val originY = with(density) { cellH.toPx() } * row
        val cellWPx = with(density) { cellW.toPx() }
        val cellHPx = with(density) { cellH.toPx() }
        val centerX = originX + dragX + (cellWPx * colSpan) / 2f
        val centerY = originY + dragY + (cellHPx * rowSpan) / 2f
        val gridW = cellWPx * gridSize
        val gridH = cellHPx * gridSize
        val snapCol = MediaGrid.snapFraction(centerX / gridW.coerceAtLeast(1f), gridSize)
        val snapRow = MediaGrid.snapFraction(centerY / gridH.coerceAtLeast(1f), gridSize)
        val target = panels.firstOrNull { other ->
            other.blockId != panel.blockId &&
                other.messageId == panel.messageId &&
                !other.isTextTile &&
                !panel.isTextTile &&
                MediaGrid.isPlaced(other.gridCol, other.gridRow, gridSize) &&
                snapCol in other.gridCol until (other.gridCol + MediaGrid.clampSpan(other.gridColSpan, gridSize)) &&
                snapRow in other.gridRow until (other.gridRow + MediaGrid.clampSpan(other.gridRowSpan, gridSize))
        }
        if (target != null) {
            onStackOnto(target.blockId)
        } else {
            onSnap(snapCol, snapRow)
        }
        dragX = 0f
        dragY = 0f
        moveMode = false
    }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = with(density) { (cellW * col).roundToPx() } + dragX.roundToInt(),
                    y = with(density) { (cellH * row).roundToPx() } + dragY.roundToInt(),
                )
            }
            .width(widthDp + with(density) { resizeDx.toDp() }.coerceAtLeast(0.dp))
            .height(heightDp + with(density) { resizeDy.toDp() }.coerceAtLeast(0.dp))
            .padding(2.dp)
            .clip(RoundedCornerShape(InkSpacing.radiusSm))
            .border(if (moveMode || selected) 2.dp else 1.dp, border, RoundedCornerShape(InkSpacing.radiusSm))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .then(
                if (moveMode) {
                    // Immediate drag only — skip combinedClickable so it cannot steal the press.
                    Modifier.pointerInput(panel.blockId, col, row, colSpan, rowSpan, panels) {
                        detectDragGestures(
                            onDragStart = {
                                onSelect()
                                menuOpen = false
                                dragX = 0f
                                dragY = 0f
                            },
                            onDragEnd = { commitMovePlacement() },
                            onDragCancel = {
                                dragX = 0f
                                dragY = 0f
                                moveMode = false
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragX += amount.x
                                dragY += amount.y
                            },
                        )
                    }
                } else {
                    Modifier.combinedClickable(
                        onClick = {
                            when {
                                panel.collapsed -> onMediaEdit(MediaEditAction.Uncollapse)
                                panel.stackedPaths.size > 1 -> onCycleStack()
                                else -> onSelect()
                            }
                        },
                        onLongClick = {
                            onSelect()
                            menuOpen = true
                        },
                    )
                },
            ),
    ) {
        if (panel.collapsed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(InkSpacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text("Collapsed", style = MaterialTheme.typography.labelSmall)
            }
        } else if (panel.isTextTile) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(InkSpacing.sm),
            ) {
                Text(
                    panel.speaker,
                    style = MaterialTheme.typography.labelSmall,
                    color = inkTokens().secondaryText,
                )
                Text(
                    panel.caption,
                    style = if (textEmphasis) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        compactStyle
                    },
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .weight(1f, fill = false),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (panel.isAudio) {
            AudioMediaPlayer(
                path = panel.path,
                label = "Audio",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(InkSpacing.xs),
            )
        } else if (textEmphasis) {
            Column(modifier = Modifier.fillMaxSize()) {
                ZoomableMedia(
                    path = panel.path,
                    contentDescription = "Panel",
                    maxHeight = heightDp * 0.62f,
                    contentScale = ContentScale.Fit,
                    decodeOriginal = true,
                    fillPanel = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onLongPress = if (moveMode) {
                        null
                    } else {
                        {
                            onSelect()
                            menuOpen = true
                        }
                    },
                )
                if (panel.caption.isNotBlank() && panel.caption != "[media]") {
                    Text(
                        text = panel.caption,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
        } else {
            ZoomableMedia(
                path = panel.path,
                contentDescription = "Panel",
                maxHeight = heightDp,
                contentScale = ContentScale.Crop,
                decodeOriginal = true,
                fillPanel = true,
                modifier = Modifier.fillMaxSize(),
                onLongPress = if (moveMode) {
                    null
                } else {
                    {
                        onSelect()
                        menuOpen = true
                    }
                },
            )
        }
        if (moveMode) {
            Text(
                "Drag to place",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(2.dp)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (!panel.collapsed && !panel.isTextTile && panel.stackedPaths.size > 1) {
            Text(
                "x${panel.stackedPaths.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(2.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (!panel.collapsed && !panel.isTextTile && !textEmphasis && !moveMode) {
            val hasCaption = panel.caption.isNotBlank() && panel.caption != "[media]"
            Text(
                text = if (hasCaption) panel.caption.take(40) else "+ Caption",
                style = compactStyle.copy(fontSize = 10.sp),
                color = if (hasCaption) Color.White else Color.White.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .combinedClickable(onClick = onCaptionTap)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        InkTextButton(
            label = "-",
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        if (!panel.collapsed && !moveMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .width(18.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .pointerInput(panel.blockId, col, row, colSpan, rowSpan, gridSize) {
                        detectDragGestures(
                            onDragStart = {
                                onSelect()
                                resizeDx = 0f
                                resizeDy = 0f
                            },
                            onDragEnd = {
                                val newColSpan = (
                                    (colSpan + resizeDx / cellW.toPx()).roundToInt()
                                    ).coerceIn(1, gridSize - col)
                                val newRowSpan = (
                                    (rowSpan + resizeDy / cellH.toPx()).roundToInt()
                                    ).coerceIn(1, gridSize - row)
                                onResizeSpan(newColSpan, newRowSpan)
                                resizeDx = 0f
                                resizeDy = 0f
                            },
                            onDragCancel = {
                                resizeDx = 0f
                                resizeDy = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                resizeDx += amount.x
                                resizeDy += amount.y
                            },
                        )
                    },
            )
        }
        MediaEditPopup(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            config = MediaEditPopupConfig(
                canPaste = canPaste,
                isCollapsed = panel.collapsed,
                canShrink = colSpan > 1 || rowSpan > 1,
                canExpand = colSpan < gridSize - col || rowSpan < gridSize - row,
                showStack = !panel.isTextTile,
                showMove = true,
            ),
            onAction = { action ->
                when (action) {
                    MediaEditAction.Delete -> onRemove()
                    MediaEditAction.Stack -> onStackMenu()
                    MediaEditAction.Move -> {
                        onSelect()
                        menuOpen = false
                        moveMode = true
                        dragX = 0f
                        dragY = 0f
                    }
                    MediaEditAction.Expand -> onResizeSpan(
                        (colSpan + 1).coerceAtMost(gridSize - col),
                        (rowSpan + 1).coerceAtMost(gridSize - row),
                    )
                    MediaEditAction.Shrink -> onResizeSpan(
                        (colSpan - 1).coerceAtLeast(1),
                        (rowSpan - 1).coerceAtLeast(1),
                    )
                    else -> onMediaEdit(action)
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessengerBubble(
    message: RpMessageUi,
    bubbleColor: Color,
    compactStyle: androidx.compose.ui.text.TextStyle,
    userAlign: Boolean,
    selectedMediaKey: String?,
    canPasteMedia: Boolean,
    onLongPress: () -> Unit,
    onSelectMedia: (String) -> Unit,
    onRemoveMedia: (String) -> Unit,
    onMoveMedia: (String, Int) -> Unit,
    onStackMedia: (String) -> Unit,
    onStackOnto: (String, String) -> Unit,
    onCycleStack: (String) -> Unit,
    onMediaEdit: (String, MediaEditAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(if (userAlign) 0.78f else 0.85f)
            .clip(RoundedCornerShape(InkSpacing.radiusMd))
            .background(bubbleColor)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(InkSpacing.sm),
    ) {
        Text(message.speaker, style = MaterialTheme.typography.labelSmall)
        if (message.text.isNotBlank()) {
            Text(message.text, style = compactStyle, modifier = Modifier.padding(top = 2.dp))
        }
        message.mediaPaths.zip(message.mediaBlockIds).forEachIndexed { index, (path, blockId) ->
            RemovableMedia(
                path = path,
                blockId = blockId,
                selected = selectedMediaKey == "${message.id}::$blockId",
                maxHeight = 200.dp,
                contentScale = ContentScale.FillWidth,
                stacked = (message.mediaStackPaths[blockId]?.size ?: 0) > 1,
                siblingBlockIds = message.mediaBlockIds,
                isAudio = message.mediaIsAudio.getOrElse(index) { false },
                canPaste = canPasteMedia,
                collapsed = message.mediaCollapsed[blockId] == true,
                onSelect = { onSelectMedia(blockId) },
                onRemove = { onRemoveMedia(blockId) },
                onMove = { onMoveMedia(blockId, it) },
                onStack = { onStackMedia(blockId) },
                onStackOnto = onStackOnto,
                onCycle = { onCycleStack(blockId) },
                onMediaEdit = { onMediaEdit(blockId, it) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemovableMedia(
    path: String,
    blockId: String,
    selected: Boolean,
    maxHeight: androidx.compose.ui.unit.Dp,
    contentScale: ContentScale,
    stacked: Boolean,
    siblingBlockIds: List<String>,
    isAudio: Boolean = false,
    canPaste: Boolean = false,
    collapsed: Boolean = false,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onStack: () -> Unit,
    onStackOnto: (String, String) -> Unit,
    onCycle: () -> Unit,
    onMediaEdit: (MediaEditAction) -> Unit = {},
) {
    var dragY by remember(blockId) { mutableFloatStateOf(0f) }
    var menuOpen by remember(blockId) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(top = InkSpacing.xs)
            .offset { IntOffset(0, dragY.roundToInt()) }
            .border(
                if (selected) 2.dp else 0.dp,
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(InkSpacing.radiusSm),
            )
            .combinedClickable(
                onClick = {
                    onSelect()
                    when {
                        collapsed -> onMediaEdit(MediaEditAction.Uncollapse)
                        stacked -> onCycle()
                    }
                },
                onLongClick = {
                    onSelect()
                    menuOpen = true
                },
            )
            .pointerInput(blockId, siblingBlockIds) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        onSelect()
                        menuOpen = false
                        dragY = 0f
                    },
                    onDragEnd = {
                        val approx = 180f
                        val steps = (dragY / approx).toInt()
                        if (steps != 0) {
                            val index = siblingBlockIds.indexOf(blockId)
                            val targetIndex = (index + steps).coerceIn(0, siblingBlockIds.lastIndex)
                            if (targetIndex != index && index >= 0) {
                                onStackOnto(blockId, siblingBlockIds[targetIndex])
                                dragY = 0f
                                return@detectDragGesturesAfterLongPress
                            }
                        }
                        when {
                            dragY < -40f -> onMove(-1)
                            dragY > 40f -> onMove(1)
                        }
                        dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragY += amount.y
                    },
                )
            },
    ) {
        if (collapsed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(inkTokens().hover)
                    .padding(horizontal = InkSpacing.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "Media collapsed · tap to uncollapse · hold for menu",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (isAudio) {
            AudioMediaPlayer(path = path, label = "Audio")
        } else {
            ZoomableMedia(
                path = path,
                contentDescription = null,
                maxHeight = maxHeight,
                contentScale = contentScale,
                onLongPress = {
                    onSelect()
                    menuOpen = true
                },
            )
        }
        if (!collapsed) {
            InkTextButton(
                label = "-",
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        MediaEditPopup(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            config = MediaEditPopupConfig(
                canPaste = canPaste,
                isCollapsed = collapsed,
                canShrink = true,
                canExpand = true,
                showStack = true,
            ),
            onAction = { action ->
                when (action) {
                    MediaEditAction.Delete -> onRemove()
                    MediaEditAction.Stack -> onStack()
                    else -> onMediaEdit(action)
                }
            },
        )
    }
}
