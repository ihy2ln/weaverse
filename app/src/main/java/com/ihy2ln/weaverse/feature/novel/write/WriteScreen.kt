package com.ihy2ln.weaverse.feature.novel.write

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.text.Mark
import com.ihy2ln.weaverse.core.ui.components.EditTextAction
import com.ihy2ln.weaverse.core.ui.components.EditTextPopupConfig
import com.ihy2ln.weaverse.core.ui.components.FontFamilyPickerDialog
import com.ihy2ln.weaverse.core.ui.components.FontSizePickerDialog
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.InkFilledButton
import com.ihy2ln.weaverse.core.ui.components.InkModeCapsule
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.TextColorPickerDialog
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.components.rememberSpeechToText
import com.ihy2ln.weaverse.core.ui.components.toSpanHex
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.adaptiveContentPadding
import com.ihy2ln.weaverse.feature.novel.write.editor.DocumentEditor
import com.ihy2ln.weaverse.feature.novel.write.editor.FormatToolbar
import com.ihy2ln.weaverse.feature.novel.write.editor.SlashCommandOverlay
import com.ihy2ln.weaverse.feature.novel.write.editor.defaultSlashCommands

@Composable
fun WriteScreen(
    sceneId: String = "scene-1",
    jumpKind: String = "Scene",
    onOpenCodexEntry: (String) -> Unit = {},
    viewModel: WriteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val clipboard = LocalClipboardManager.current
    val startDictate = rememberSpeechToText { spoken ->
        viewModel.pasteIntoSelection(spoken)
    }

    LaunchedEffect(sceneId, jumpKind) { viewModel.loadScene(sceneId, jumpKind) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris)
        } else {
            viewModel.cancelImagePick()
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris)
        } else {
            viewModel.cancelImagePick()
        }
    }

    val beatImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.attachBeatImage(uri)
    }

    LaunchedEffect(state.pickImageRequestId) {
        if (state.pickImageRequestId > 0L && state.pickImageBlockIndex != null) {
            imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
            )
        }
    }

    LaunchedEffect(state.pickAudioRequestId) {
        if (state.pickAudioRequestId > 0L && state.pickImageBlockIndex != null) {
            audioPicker.launch(arrayOf("audio/*", "audio/mpeg", "audio/wav", "audio/x-wav"))
        }
    }

    LaunchedEffect(state.aiOverlay?.pickBeatImageRequestId) {
        val req = state.aiOverlay?.pickBeatImageRequestId ?: 0L
        if (req > 0L) {
            beatImagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    if (state.showColorPicker) {
        TextColorPickerDialog(
            initial = MaterialTheme.colorScheme.primary,
            onDismiss = viewModel::dismissColorPicker,
            onConfirm = { color -> viewModel.applyColorOnSelection(color.toSpanHex()) },
        )
    }
    if (state.showHighlightPicker) {
        TextColorPickerDialog(
            initial = MaterialTheme.colorScheme.primaryContainer,
            onDismiss = viewModel::dismissHighlightPicker,
            onConfirm = { color -> viewModel.applyHighlightOnSelection(color.toSpanHex()) },
            title = "Highlight color",
        )
    }
    if (state.showFontFamilyPicker) {
        FontFamilyPickerDialog(
            current = viewModel.activeFontFamilyKeyInSelection(),
            onDismiss = viewModel::dismissFontFamilyPicker,
            onSelect = viewModel::applyFontFamilyOnSelection,
        )
    }
    if (state.showFontSizePicker) {
        FontSizePickerDialog(
            current = viewModel.activeFontSizeSpInSelection(),
            onDismiss = viewModel::dismissFontSizePicker,
            onSelect = viewModel::applyFontSizeOnSelection,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val contentPad = adaptiveContentPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPad)
                .padding(
                    bottom = when {
                        state.aiOverlay == null -> 0.dp
                        state.aiOverlay?.commandId == "scene_beat" -> 0.dp
                        else -> 120.dp
                    },
                ),
        ) {
            var formatExpanded by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                var mediaMenuOpen by remember { mutableStateOf(false) }
                var promptMenuOpen by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.sceneTitle.ifBlank { "Scene" },
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.weight(1f),
                    )
                    InkTextButton(
                        label = if (formatExpanded) "Aa ▴" else "Aa ▾",
                        onClick = { formatExpanded = !formatExpanded },
                        compact = true,
                    )
                    Box {
                        InkTextButton(
                            label = if (state.isSummarizing) "Prompting…" else "Prompting",
                            onClick = { promptMenuOpen = true },
                            enabled = !state.isSummarizing,
                            compact = true,
                        )
                        DropdownMenu(
                            expanded = promptMenuOpen,
                            onDismissRequest = { promptMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Extend") },
                                onClick = {
                                    promptMenuOpen = false
                                    viewModel.startSelectionAi("extend", "Extend")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Summarize") },
                                onClick = {
                                    promptMenuOpen = false
                                    viewModel.summarizeScene()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Condense") },
                                onClick = {
                                    promptMenuOpen = false
                                    viewModel.startSelectionAi("shorten", "Condense")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Replace") },
                                onClick = {
                                    promptMenuOpen = false
                                    viewModel.startSelectionAi("replace", "Replace")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Retry") },
                                enabled = state.aiOverlay != null,
                                onClick = {
                                    promptMenuOpen = false
                                    viewModel.retryAiGeneration()
                                },
                            )
                        }
                    }
                    Box {
                        InkTextButton(
                            label = "Media",
                            onClick = { mediaMenuOpen = true },
                            compact = true,
                        )
                        DropdownMenu(
                            expanded = mediaMenuOpen,
                            onDismissRequest = { mediaMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Mic") },
                                onClick = {
                                    mediaMenuOpen = false
                                    startDictate()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Audio") },
                                onClick = {
                                    mediaMenuOpen = false
                                    viewModel.requestAddAudio()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Picture") },
                                onClick = {
                                    mediaMenuOpen = false
                                    viewModel.requestAddMedia()
                                },
                            )
                        }
                    }
                }
                Text(
                    "${state.wordCount} words",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
            if (state.statusMessage.isNotBlank()) {
                Text(
                    state.statusMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(bottom = InkSpacing.xs)
                        .then(
                            if (state.pendingCodexEntryId != null) {
                                Modifier.padding(0.dp) // click target via InkTextButton below
                            } else {
                                Modifier
                            },
                        ),
                )
                if (state.pendingCodexEntryId != null) {
                    InkTextButton(
                        label = "Open entry",
                        onClick = {
                            onOpenCodexEntry(state.pendingCodexEntryId!!)
                            viewModel.clearStatus()
                        },
                    )
                }
            }
            FormatToolbar(
                expanded = formatExpanded,
                hasSelection = state.selection.hasSelection,
                activeMarks = viewModel.activeMarksInSelection(),
                activeFontFamilyKey = viewModel.activeFontFamilyKeyInSelection(),
                activeFontSizeSp = viewModel.activeFontSizeSpInSelection(),
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                onToggleMark = viewModel::toggleMarkOnSelection,
                onOpenColorPicker = viewModel::requestColorPicker,
                onOpenHighlightPicker = viewModel::requestHighlightPicker,
                onOpenFontFamilyPicker = viewModel::requestFontFamilyPicker,
                onOpenFontSizePicker = viewModel::requestFontSizePicker,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                modifier = Modifier.padding(bottom = InkSpacing.xs),
            )
            DocumentEditor(
                blocks = state.blocks,
                mediaPaths = state.mediaPaths,
                onParagraphChange = viewModel::updateParagraph,
                onMediaWidthChange = viewModel::updateMediaWidth,
                onMediaSelect = viewModel::selectMediaBlock,
                onMediaRemove = viewModel::removeMediaBlock,
                onMediaMoveBy = viewModel::moveBlock,
                onStackMedia = viewModel::stackMediaWithAdjacent,
                onCycleStack = viewModel::cycleMediaStack,
                onMediaDragRelease = viewModel::onMediaDragRelease,
                canPasteMedia = state.canPasteMedia,
                onMediaEditAction = viewModel::onMediaEditAction,
                selectedMediaBlockIndex = state.selectedMediaBlockIndex,
                onSlashTrigger = viewModel::onSlashTrigger,
                onBackslashTrigger = viewModel::onBackslashTrigger,
                focusedBlockIndex = state.selection.blockIndex,
                editPopupBlockIndex = state.editPopupBlockIndex,
                onSelectionChange = viewModel::onSelectionChange,
                onShowEditPopup = viewModel::setEditPopupBlock,
                popupConfig = EditTextPopupConfig(
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    hasSelection = state.selection.hasSelection,
                    activeMarks = viewModel.activeMarksInSelection(),
                ),
                onSceneBeatPromptChange = viewModel::updateSceneBeatPrompt,
                onToggleSceneBeat = viewModel::toggleSceneBeat,
                onGenerateSceneBeat = viewModel::generateFromSceneBeat,
                onClearSceneBeat = viewModel::clearSceneBeat,
                onAcceptSceneBeat = viewModel::acceptAiResult,
                onRetrySceneBeat = viewModel::retryAiGeneration,
                onRequestBeatImage = viewModel::requestBeatImage,
                beatImageAttached = state.aiOverlay?.imagePath != null,
                sceneBeatResultIndex = state.aiOverlay
                    ?.takeIf { it.commandId == "scene_beat" && it.streamingText.isNotBlank() && !it.isStreaming }
                    ?.insertAfterIndex,
                generatingSceneBeatIndex = state.aiOverlay
                    ?.takeIf { it.commandId == "scene_beat" && it.isStreaming }
                    ?.insertAfterIndex,
                codexNames = state.codexNames,
                codexMentionTargets = state.codexMentionTargets,
                onMentionClick = onOpenCodexEntry,
                onContinuationSubmit = viewModel::insertContinuation,
                showInlineWritingPrompt = state.showInlineWritingPrompt,
                showSceneBeatCard = state.showSceneBeatCard,
                showContinuationBox = state.showContinuationBox,
                onEditAction = { index, action, value ->
                    viewModel.onSelectionChange(index, value.selection)
                    when (action) {
                        EditTextAction.Copy -> {
                            val text = viewModel.selectedText()
                            if (text.isNotBlank()) clipboard.setText(AnnotatedString(text))
                        }
                        EditTextAction.Cut -> {
                            val text = viewModel.cutSelection()
                            if (text.isNotBlank()) clipboard.setText(AnnotatedString(text))
                        }
                        EditTextAction.Paste -> {
                            val clip = clipboard.getText()?.text.orEmpty()
                            viewModel.pasteIntoSelection(clip)
                        }
                        EditTextAction.SelectAll -> viewModel.selectAllInFocusedBlock()
                        EditTextAction.Delete -> viewModel.deleteSelection()
                        EditTextAction.Edit -> Unit
                        EditTextAction.Bold -> viewModel.toggleMarkOnSelection(Mark.Bold)
                        EditTextAction.Italic -> viewModel.toggleMarkOnSelection(Mark.Italic)
                        EditTextAction.Underline -> viewModel.toggleMarkOnSelection(Mark.Underline)
                        EditTextAction.Strikethrough -> viewModel.toggleMarkOnSelection(Mark.Strikethrough)
                        EditTextAction.Superscript -> viewModel.toggleMarkOnSelection(Mark.Superscript)
                        EditTextAction.Subscript -> viewModel.toggleMarkOnSelection(Mark.Subscript)
                        EditTextAction.Color -> viewModel.requestColorPicker()
                        EditTextAction.Highlight -> viewModel.requestHighlightPicker()
                        EditTextAction.FontFamily -> viewModel.requestFontFamilyPicker()
                        EditTextAction.FontSize -> viewModel.requestFontSizePicker()
                        EditTextAction.AddToCodex -> viewModel.addSelectionToCodex()
                        EditTextAction.Shorten -> viewModel.startSelectionAi("shorten", "Shorten")
                        EditTextAction.Extend -> viewModel.startSelectionAi("extend", "Extend")
                        EditTextAction.Replace -> viewModel.startSelectionAi("replace", "Replace")
                        EditTextAction.Undo -> viewModel.undo()
                        EditTextAction.Redo -> viewModel.redo()
                        EditTextAction.Speak -> {
                            val text = viewModel.selectedText().ifBlank {
                                state.aiOverlay?.streamingText.orEmpty()
                            }
                            viewModel.speakText(text)
                        }
                        EditTextAction.Dictate -> startDictate()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (state.slashBlockIndex != null) {
            SlashCommandOverlay(
                commands = defaultSlashCommands,
                filter = state.slashFilter.removePrefix("/"),
                onSelect = viewModel::applySlashCommand,
                onDismiss = viewModel::dismissSlash,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(InkSpacing.lg),
            )
        }
        state.aiOverlay?.takeIf { it.commandId != "scene_beat" }?.let { overlay ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        overlay.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = tokens.secondaryText,
                    )
                    InkTextButton(label = "Hide", onClick = viewModel::dismissAiOverlay)
                }
                VoiceToTextField(
                    value = overlay.prompt,
                    onValueChange = viewModel::updateAiPrompt,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Add a short instruction…",
                    minLines = 1,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                    ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                ) {
                    InkFilledButton(
                        label = if (overlay.isStreaming) "…" else "Generate",
                        onClick = viewModel::runAiGeneration,
                        enabled = !overlay.isStreaming,
                    )
                    InkModeCapsule(
                        label = "Clear Text",
                        onClick = {
                            viewModel.updateAiPrompt("")
                            viewModel.discardAiResult()
                        },
                        enabled = overlay.prompt.isNotBlank() || overlay.streamingText.isNotBlank(),
                    )
                    Text("Words", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = overlay.outputWords.toString(),
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(4)
                            viewModel.updateOutputWords(digits.toIntOrNull() ?: 750)
                        },
                        modifier = Modifier.width(64.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (overlay.streamingText.isNotBlank() && !overlay.isStreaming) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = InkSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                    ) {
                        InkConfirmButton(
                            onClick = viewModel::acceptAiResult,
                            label = "Accept",
                            contentDescription = "Accept",
                        )
                        InkModeCapsule(label = "Retry", onClick = viewModel::retryAiGeneration)
                    }
                }
                if (overlay.errorMessage.isNotBlank()) {
                    Text(
                        overlay.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = InkSpacing.xs),
                    )
                }
                if (overlay.isStreaming) {
                    Text(
                        "Generating…",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText,
                        modifier = Modifier.padding(top = InkSpacing.xs),
                    )
                }
                if (overlay.usageLog.isNotBlank()) {
                    Text(
                        overlay.usageLog,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText,
                    )
                }
            }
        }
    }
}
