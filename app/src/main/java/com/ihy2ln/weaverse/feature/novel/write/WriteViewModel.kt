package com.ihy2ln.weaverse.feature.novel.write

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.prompt.PromptComponents
import com.ihy2ln.weaverse.ai.prompt.PromptRenderContext
import com.ihy2ln.weaverse.ai.prompt.PromptRenderer
import com.ihy2ln.weaverse.ai.prompt.PromptTokenContext
import com.ihy2ln.weaverse.ai.prompt.PromptTokens
import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.ai.context.ContextBuilder
import com.ihy2ln.weaverse.ai.context.ContextBuildRequest
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.text.Block
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.Mark
import com.ihy2ln.weaverse.core.text.MediaBlock
import com.ihy2ln.weaverse.core.text.MediaKind
import com.ihy2ln.weaverse.core.text.MediaStackBlock
import com.ihy2ln.weaverse.core.text.Paragraph
import com.ihy2ln.weaverse.core.text.SceneBeatBlock
import com.ihy2ln.weaverse.core.text.Span
import com.ihy2ln.weaverse.core.text.appendParagraphs
import com.ihy2ln.weaverse.core.text.appendSceneBeat
import com.ihy2ln.weaverse.core.text.insertGeneratedProseAfter
import com.ihy2ln.weaverse.core.text.withSceneBeatCollapsedToggled
import com.ihy2ln.weaverse.core.text.withSceneBeatPrompt
import com.ihy2ln.weaverse.core.text.applyColor
import com.ihy2ln.weaverse.core.text.applyFontFamily
import com.ihy2ln.weaverse.core.text.applyFontSize
import com.ihy2ln.weaverse.core.text.applyHighlight
import com.ihy2ln.weaverse.core.text.fontFamilyKeyInRange
import com.ihy2ln.weaverse.core.text.fontSizeSpInRange
import com.ihy2ln.weaverse.core.text.marksInRange
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.replaceRangeText
import com.ihy2ln.weaverse.core.text.isMediaBlockAt
import com.ihy2ln.weaverse.core.text.stackMediaOnto
import com.ihy2ln.weaverse.core.text.stackMediaWithAdjacent
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.withGridCell
import com.ihy2ln.weaverse.core.text.toggleMark
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.core.ui.util.UsageFormat
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.PromptEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import com.ihy2ln.weaverse.data.repo.CodexRepository
import com.ihy2ln.weaverse.data.repo.ManuscriptRepository
import com.ihy2ln.weaverse.data.repo.PromptRepository
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.novel.write.editor.SlashCommand
import com.ihy2ln.weaverse.core.media.MediaClipboard
import com.ihy2ln.weaverse.core.media.MediaClipboardPayload
import com.ihy2ln.weaverse.core.ui.components.MediaEditAction
import com.ihy2ln.weaverse.feature.prompt.PromptEntryBus
import com.ihy2ln.weaverse.feature.prompt.PromptEntryKind
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import android.util.Base64
import com.ihy2ln.weaverse.ai.ImageAttachment
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class SelectionState(
    val blockIndex: Int = 0,
    val start: Int = 0,
    val end: Int = 0,
) {
    val hasSelection: Boolean get() = start != end
    val min: Int get() = minOf(start, end)
    val max: Int get() = maxOf(start, end)
}

data class AiOverlayState(
    val commandId: String = "",
    val label: String = "",
    /** Compact user-editable beat/request — not library instructions. */
    val prompt: String = "",
    /** Injected into the model system context; hidden from overlay UI. */
    val systemInstructions: String = "",
    val promptId: String? = null,
    val outputWords: Int = 750,
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val errorMessage: String = "",
    val usageLog: String = "",
    val insertAfterIndex: Int = 0,
    /** When set, Accept replaces this range instead of inserting new paragraphs. */
    val replaceBlockIndex: Int? = null,
    val replaceStart: Int? = null,
    val replaceEnd: Int? = null,
    /** Optional image for picture-to-text / describe-image scene beat generation. */
    val imageMediaId: String? = null,
    val imagePath: String? = null,
    val pickBeatImageRequestId: Long = 0L,
)

data class WriteUiState(
    val sceneId: String = "scene-1",
    val sceneTitle: String = "",
    val blocks: List<Block> = emptyList(),
    val mediaPaths: Map<String, String> = emptyMap(),
    val wordCount: Int = 0,
    val slashBlockIndex: Int? = null,
    val slashFilter: String = "",
    /** Target block for insert; -1 = append at end. Null = no pending pick. */
    val pickImageBlockIndex: Int? = null,
    /** Bumped on each pick request so LaunchedEffect re-fires even if index is unchanged. */
    val pickImageRequestId: Long = 0L,
    val pickAudioRequestId: Long = 0L,
    val aiOverlay: AiOverlayState? = null,
    val selection: SelectionState = SelectionState(),
    /** When set, Backspace/Delete removes this media block. */
    val selectedMediaBlockIndex: Int? = null,
    val canPasteMedia: Boolean = false,
    val editPopupBlockIndex: Int? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val statusMessage: String = "",
    val isSummarizing: Boolean = false,
    val showColorPicker: Boolean = false,
    val showHighlightPicker: Boolean = false,
    val showFontFamilyPicker: Boolean = false,
    val showFontSizePicker: Boolean = false,
    val pendingCodexEntryId: String? = null,
    /** Codex names and aliases highlighted inside the scene-beat prompt. */
    val codexNames: List<String> = emptyList(),
    /** Codex entries eligible for hyperlinking in the manuscript editor. */
    val codexMentionTargets: List<com.ihy2ln.weaverse.core.text.CodexMentionTarget> = emptyList(),
    val showInlineWritingPrompt: Boolean = false,
    val showSceneBeatCard: Boolean = false,
    val showContinuationBox: Boolean = false,
)

private data class LibraryPromptBundle(
    val promptId: String?,
    val systemInstructions: String,
    /** Resolved User/AI turns before the final instruction turn — real multi-message structure. */
    val historyMessages: List<Pair<String, String>> = emptyList(),
    /** The resolved final User turn, when the prompt is multi-message (replaces the hand-built beat text). */
    val finalUserMessage: String? = null,
)

@HiltViewModel
class WriteViewModel @Inject constructor(
    private val manuscriptRepository: ManuscriptRepository,
    private val mediaRepository: MediaRepository,
    private val aiGeneration: AiGenerationService,
    private val promptRepository: PromptRepository,
    private val settings: SettingsRepository,
    private val codexRepository: CodexRepository,
    private val db: WeaverseDatabase,
    private val tts: com.ihy2ln.weaverse.core.tts.TtsService,
    private val promptEntryBus: PromptEntryBus,
    private val mediaClipboard: MediaClipboard,
    private val workspaceHistory: WorkspaceHistory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WriteUiState())
    val uiState: StateFlow<WriteUiState> = _uiState.asStateFlow()
    private val contextBuilder = ContextBuilder()
    private val json = Json { ignoreUnknownKeys = true }
    private var bookId: String = "book-adams-haven-1"

    private var loadedScene: SceneEntity? = null
    private var sceneJob: Job? = null
    private var generationJob: Job? = null
    private var applyingHistory = false
    /** Snapshot taken at the start of a typing burst; flushed before discrete edits. */
    private var typingBaseline: List<Block>? = null
    private val unregisterHistoryFlush = workspaceHistory.registerPreUndo { flushTypingHistory() }

    init {
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                bookId = prefs.selectedBookId
                _uiState.update {
                    it.copy(
                        showInlineWritingPrompt = prefs.extraPromptSurfaces.inlineWriting,
                        showSceneBeatCard = prefs.extraPromptSurfaces.sceneBeatCard,
                        showContinuationBox = prefs.extraPromptSurfaces.continuation,
                    )
                }
            }
        }
        viewModelScope.launch {
            workspaceHistory.state.collect { hist ->
                _uiState.update { it.copy(canUndo = hist.canUndo, canRedo = hist.canRedo) }
            }
        }
        viewModelScope.launch {
            codexRepository.observeAllEntries().collect { entries ->
                val names = entries
                    .filter { !it.disabled }
                    .flatMap { entry ->
                        val aliases = runCatching {
                            json.decodeFromString<List<String>>(entry.aliasesJson)
                        }.getOrDefault(emptyList())
                        listOf(entry.name) + aliases
                    }
                    .map { it.trim() }
                    .filter { it.length >= 2 }
                    .distinct()
                val mentionTargets = entries
                    .filter { !it.disabled && it.trackMentions }
                    .map { entry ->
                        com.ihy2ln.weaverse.core.text.CodexMentionTarget(
                            entryId = entry.id,
                            name = entry.name,
                            aliases = com.ihy2ln.weaverse.core.text.decodeAliases(entry.aliasesJson),
                            caseSensitive = entry.caseSensitiveMatching,
                        )
                    }
                    .filter { it.name.trim().length >= 2 }
                _uiState.update { it.copy(codexNames = names, codexMentionTargets = mentionTargets) }
            }
        }
    }

    private var pendingJumpKind: String = "Scene"

    fun loadScene(sceneId: String, jumpKind: String = "Scene") {
        pendingJumpKind = jumpKind
        if (loadedScene?.id == sceneId && sceneJob?.isActive == true) {
            if (jumpKind == "SceneBeat") startSceneBeatFromPlan()
            return
        }
        sceneJob?.cancel()
        if (typingBaseline != null) {
            typingBaseline = null
            workspaceHistory.removePendingUndo()
        }
        sceneJob = viewModelScope.launch {
            manuscriptRepository.observeScene(sceneId).collect { scene ->
                if (scene != null) applyScene(scene)
            }
        }
    }

    fun startSceneBeatFromPlan() {
        updateBlocksSync(recordHistory = true) { blocks ->
            val last = blocks.lastOrNull() as? SceneBeatBlock
            if (last != null && last.prompt.isBlank()) return@updateBlocksSync
            val next = blocks.appendSceneBeat()
            blocks.clear()
            blocks.addAll(next)
        }
    }

    fun insertContinuation(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        updateBlocksSync(recordHistory = true) { blocks ->
            val next = blocks.appendParagraphs(trimmed)
            blocks.clear()
            blocks.addAll(next)
        }
    }

    fun updateSceneBeatPrompt(index: Int, prompt: String) {
        beginTypingHistory()
        updateBlocks(recordHistory = false) { blocks ->
            val next = blocks.withSceneBeatPrompt(index, prompt)
            blocks.clear()
            blocks.addAll(next)
        }
        _uiState.update { state ->
            val overlay = state.aiOverlay
            if (overlay != null &&
                overlay.commandId == "scene_beat" &&
                overlay.insertAfterIndex == index
            ) {
                state.copy(aiOverlay = overlay.copy(prompt = prompt, errorMessage = ""))
            } else {
                state
            }
        }
    }

    fun toggleSceneBeat(index: Int) {
        updateBlocks(recordHistory = false) { blocks ->
            val next = blocks.withSceneBeatCollapsedToggled(index)
            blocks.clear()
            blocks.addAll(next)
        }
    }

    fun generateFromSceneBeat(index: Int) {
        val beat = _uiState.value.blocks.getOrNull(index) as? SceneBeatBlock ?: return
        if (beat.prompt.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Write a scene beat prompt first") }
            return
        }
        viewModelScope.launch {
            val library = libraryPromptBundle("scene_beat", PromptRenderContext())
            _uiState.update {
                it.copy(
                    aiOverlay = AiOverlayState(
                        commandId = "scene_beat",
                        label = "SCENE BEAT",
                        insertAfterIndex = index,
                        prompt = beat.prompt,
                        systemInstructions = library.systemInstructions,
                        promptId = library.promptId,
                    ),
                )
            }
            runAiGeneration()
        }
    }

    fun clearSceneBeat(index: Int) {
        updateSceneBeatPrompt(index, "")
        val overlay = _uiState.value.aiOverlay
        if (overlay?.commandId == "scene_beat" && overlay.insertAfterIndex == index) {
            discardAiResult()
        }
    }

    private fun applyScene(scene: SceneEntity) {
        loadedScene = scene
        val doc = documentFromJson(scene.docJson)
        val blocks = doc.blocks.ifEmpty { listOf(Paragraph("new-p", listOf(Span("")))) }
        viewModelScope.launch {
            val paths = mutableMapOf<String, String>()
            val mediaIds = buildList {
                blocks.forEach { block ->
                    when (block) {
                        is MediaBlock -> add(block.mediaId)
                        is MediaStackBlock -> addAll(block.mediaIds)
                        else -> Unit
                    }
                }
            }.distinct()
            mediaIds.forEach { id ->
                mediaRepository.getById(id)?.let { media ->
                    paths[id] = mediaRepository.resolveFile(media).absolutePath
                }
            }
            _uiState.update {
                it.copy(
                    sceneId = scene.id,
                    sceneTitle = scene.title,
                    blocks = blocks,
                    mediaPaths = paths,
                    wordCount = doc.wordCount(),
                    canUndo = workspaceHistory.state.value.canUndo,
                    canRedo = workspaceHistory.state.value.canRedo,
                )
            }
            val jump = pendingJumpKind
            pendingJumpKind = "Scene"
            if (jump == "SceneBeat") startSceneBeatFromPlan()
        }
    }

    fun updateParagraph(index: Int, paragraph: Paragraph) {
        beginTypingHistory()
        updateBlocks(recordHistory = false) { blocks ->
            blocks[index] = paragraph
        }
    }

    fun onPromptShortcut(kind: PromptEntryKind) {
        promptEntryBus.requestOpen(kind)
    }

    fun onSelectionChange(blockIndex: Int, range: TextRange) {
        _uiState.update {
            it.copy(
                selection = SelectionState(
                    blockIndex = blockIndex,
                    start = range.start,
                    end = range.end,
                ),
            )
        }
    }

    fun setEditPopupBlock(index: Int?) {
        _uiState.update { it.copy(editPopupBlockIndex = index) }
    }

    fun clearStatus() = _uiState.update { it.copy(statusMessage = "", pendingCodexEntryId = null) }

    fun dismissColorPicker() = _uiState.update { it.copy(showColorPicker = false) }

    fun toggleMarkOnSelection(mark: Mark) {
        val sel = _uiState.value.selection
        if (!sel.hasSelection) return
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return
        val next = block.copy(spans = block.spans.toggleMark(sel.min, sel.max, mark))
        flushTypingHistory()
        updateBlocks(recordHistory = true) { it[sel.blockIndex] = next }
    }

    fun applyColorOnSelection(colorHex: String) {
        val sel = _uiState.value.selection
        if (!sel.hasSelection) return
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return
        val next = block.copy(spans = block.spans.applyColor(sel.min, sel.max, colorHex))
        flushTypingHistory()
        updateBlocks(recordHistory = true) { it[sel.blockIndex] = next }
        _uiState.update { it.copy(showColorPicker = false) }
    }

    fun requestColorPicker() {
        if (!_uiState.value.selection.hasSelection) return
        _uiState.update { it.copy(showColorPicker = true, editPopupBlockIndex = null) }
    }

    fun dismissHighlightPicker() = _uiState.update { it.copy(showHighlightPicker = false) }

    fun applyHighlightOnSelection(colorHex: String?) {
        val sel = _uiState.value.selection
        if (!sel.hasSelection) return
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return
        val next = block.copy(spans = block.spans.applyHighlight(sel.min, sel.max, colorHex))
        flushTypingHistory()
        updateBlocks(recordHistory = true) { it[sel.blockIndex] = next }
        _uiState.update { it.copy(showHighlightPicker = false) }
    }

    fun requestHighlightPicker() {
        if (!_uiState.value.selection.hasSelection) return
        _uiState.update { it.copy(showHighlightPicker = true, editPopupBlockIndex = null) }
    }

    fun dismissFontFamilyPicker() = _uiState.update { it.copy(showFontFamilyPicker = false) }

    fun applyFontFamilyOnSelection(fontFamilyKey: String) {
        val sel = _uiState.value.selection
        if (!sel.hasSelection) return
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return
        val next = block.copy(spans = block.spans.applyFontFamily(sel.min, sel.max, fontFamilyKey))
        flushTypingHistory()
        updateBlocks(recordHistory = true) { it[sel.blockIndex] = next }
        _uiState.update { it.copy(showFontFamilyPicker = false) }
    }

    fun requestFontFamilyPicker() {
        if (!_uiState.value.selection.hasSelection) return
        _uiState.update { it.copy(showFontFamilyPicker = true, editPopupBlockIndex = null) }
    }

    fun dismissFontSizePicker() = _uiState.update { it.copy(showFontSizePicker = false) }

    fun applyFontSizeOnSelection(fontSizeSp: Float) {
        val sel = _uiState.value.selection
        if (!sel.hasSelection) return
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return
        val next = block.copy(spans = block.spans.applyFontSize(sel.min, sel.max, fontSizeSp))
        flushTypingHistory()
        updateBlocks(recordHistory = true) { it[sel.blockIndex] = next }
        _uiState.update { it.copy(showFontSizePicker = false) }
    }

    fun requestFontSizePicker() {
        if (!_uiState.value.selection.hasSelection) return
        _uiState.update { it.copy(showFontSizePicker = true, editPopupBlockIndex = null) }
    }

    /** Marks shared by the current selection — drives ✓ indicators and toolbar toggle state. */
    fun activeMarksInSelection(): Set<Mark> {
        val sel = _uiState.value.selection
        if (!sel.hasSelection) return emptySet()
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return emptySet()
        return block.spans.marksInRange(sel.min, sel.max)
    }

    /** Font family key shared by the current selection, or null if unset/mixed. */
    fun activeFontFamilyKeyInSelection(): String? {
        val sel = _uiState.value.selection
        if (!sel.hasSelection) return null
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return null
        return block.spans.fontFamilyKeyInRange(sel.min, sel.max)
    }

    /** Font size shared by the current selection, or null if unset/mixed. */
    fun activeFontSizeSpInSelection(): Float? {
        val sel = _uiState.value.selection
        if (!sel.hasSelection) return null
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return null
        return block.spans.fontSizeSpInRange(sel.min, sel.max)
    }

    fun selectAllInFocusedBlock() {
        val sel = _uiState.value.selection
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return
        val len = block.plainText().length
        _uiState.update {
            it.copy(selection = sel.copy(start = 0, end = len))
        }
    }

    fun selectedText(): String {
        val sel = _uiState.value.selection
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return ""
        val text = block.plainText()
        if (!sel.hasSelection) return text
        return text.substring(sel.min.coerceIn(0, text.length), sel.max.coerceIn(0, text.length))
    }

    fun pasteIntoSelection(clipboardText: String) {
        val sel = _uiState.value.selection
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph ?: return
        val next = block.copy(
            spans = block.spans.replaceRangeText(sel.min, sel.max, clipboardText),
        )
        flushTypingHistory()
        updateBlocks(recordHistory = true) { it[sel.blockIndex] = next }
        val caret = sel.min + clipboardText.length
        _uiState.update {
            it.copy(selection = sel.copy(start = caret, end = caret))
        }
    }

    /** Returns cut text for the clipboard; removes the selection. */
    fun cutSelection(): String {
        val text = selectedText()
        if (text.isEmpty() || !_uiState.value.selection.hasSelection) return ""
        deleteSelection()
        return text
    }

    fun deleteSelection() {
        val sel = _uiState.value.selection
        if (!sel.hasSelection) return
        pasteIntoSelection("")
    }

    fun selectMediaBlock(index: Int?) {
        _uiState.update { it.copy(selectedMediaBlockIndex = index) }
    }

    fun removeMediaBlock(index: Int) {
        val block = _uiState.value.blocks.getOrNull(index) ?: return
        val removedIds = when (block) {
            is MediaBlock -> listOf(block.mediaId)
            is MediaStackBlock -> block.mediaIds
            else -> return
        }
        updateBlocks(recordHistory = true) { blocks ->
            blocks.removeAt(index)
        }
        _uiState.update {
            it.copy(
                selectedMediaBlockIndex = null,
                mediaPaths = it.mediaPaths - removedIds.toSet(),
            )
        }
    }

    fun removeSelectedMediaBlock() {
        val index = _uiState.value.selectedMediaBlockIndex ?: return
        removeMediaBlock(index)
    }

    fun onMediaEditAction(index: Int, action: MediaEditAction) {
        when (action) {
            MediaEditAction.Cut -> cutMediaBlock(index)
            MediaEditAction.Copy -> copyMediaBlock(index)
            MediaEditAction.Paste -> pasteMediaBlock(afterIndex = index)
            MediaEditAction.Delete -> removeMediaBlock(index)
            MediaEditAction.Shrink -> adjustMediaWidth(index, -15f)
            MediaEditAction.Expand -> adjustMediaWidth(index, 15f)
            MediaEditAction.Collapse -> setMediaCollapsed(index, true)
            MediaEditAction.Uncollapse -> setMediaCollapsed(index, false)
            MediaEditAction.Stack -> stackMediaWithAdjacent(index)
            MediaEditAction.Move -> Unit
        }
    }

    private fun copyMediaBlock(index: Int) {
        val block = _uiState.value.blocks.getOrNull(index) ?: return
        val payload = when (block) {
            is MediaBlock -> MediaClipboardPayload(
                mediaId = block.mediaId,
                kind = block.kind,
                widthPercent = block.widthPercent,
                gridColSpan = block.gridColSpan,
                gridRowSpan = block.gridRowSpan,
            )
            is MediaStackBlock -> MediaClipboardPayload(
                mediaId = block.mediaIds.firstOrNull().orEmpty(),
                kind = MediaKind.Image,
                gridColSpan = block.gridColSpan,
                gridRowSpan = block.gridRowSpan,
                stackedMediaIds = block.mediaIds,
            )
            else -> return
        }
        if (payload.mediaId.isBlank() && payload.stackedMediaIds.isEmpty()) return
        mediaClipboard.set(payload)
        _uiState.update { it.copy(canPasteMedia = true, statusMessage = "Media copied") }
    }

    private fun cutMediaBlock(index: Int) {
        copyMediaBlock(index)
        removeMediaBlock(index)
        _uiState.update { it.copy(statusMessage = "Media cut") }
    }

    fun pasteMediaBlock(afterIndex: Int? = _uiState.value.selectedMediaBlockIndex) {
        val payload = mediaClipboard.payload ?: return
        viewModelScope.launch {
            val insertAt = ((afterIndex ?: (_uiState.value.blocks.lastIndex)) + 1)
                .coerceIn(0, _uiState.value.blocks.size)
            val block = if (payload.stackedMediaIds.size > 1) {
                MediaStackBlock(
                    id = UUID.randomUUID().toString(),
                    mediaIds = payload.stackedMediaIds,
                    gridColSpan = payload.gridColSpan,
                    gridRowSpan = payload.gridRowSpan,
                )
            } else {
                MediaBlock(
                    id = UUID.randomUUID().toString(),
                    mediaId = payload.mediaId,
                    kind = payload.kind,
                    widthPercent = payload.widthPercent,
                    gridColSpan = payload.gridColSpan,
                    gridRowSpan = payload.gridRowSpan,
                )
            }
            val ids = when (block) {
                is MediaBlock -> listOf(block.mediaId)
                is MediaStackBlock -> block.mediaIds
                else -> emptyList()
            }
            val paths = ids.mapNotNull { id ->
                mediaRepository.getById(id)?.let { id to mediaRepository.resolveFile(it).absolutePath }
            }.toMap()
            updateBlocks(recordHistory = true) { blocks ->
                blocks.add(insertAt, block)
            }
            _uiState.update {
                it.copy(
                    selectedMediaBlockIndex = insertAt,
                    mediaPaths = it.mediaPaths + paths,
                    canPasteMedia = mediaClipboard.hasPayload,
                    statusMessage = "Media pasted",
                )
            }
        }
    }

    private fun adjustMediaWidth(index: Int, delta: Float) {
        updateBlocks(recordHistory = true) { blocks ->
            when (val block = blocks.getOrNull(index)) {
                is MediaBlock -> {
                    blocks[index] = block.copy(
                        widthPercent = (block.widthPercent + delta).coerceIn(25f, 100f),
                    )
                }
                is MediaStackBlock -> {
                    // Stacks use span for size in manga; widthPercent N/A — bump span.
                    val next = if (delta < 0) {
                        block.copy(
                            gridColSpan = (block.gridColSpan - 1).coerceAtLeast(1),
                            gridRowSpan = (block.gridRowSpan - 1).coerceAtLeast(1),
                        )
                    } else {
                        block.copy(
                            gridColSpan = (block.gridColSpan + 1).coerceAtMost(6),
                            gridRowSpan = (block.gridRowSpan + 1).coerceAtMost(6),
                        )
                    }
                    blocks[index] = next
                }
                else -> Unit
            }
        }
    }

    private fun setMediaCollapsed(index: Int, collapsed: Boolean) {
        updateBlocks(recordHistory = true) { blocks ->
            when (val block = blocks.getOrNull(index)) {
                is MediaBlock -> blocks[index] = block.copy(collapsed = collapsed)
                is MediaStackBlock -> blocks[index] = block.copy(collapsed = collapsed)
                else -> Unit
            }
        }
    }

    /** Stack the media at [index] with an adjacent media/stack block; persists JSON. */
    fun stackMediaWithAdjacent(index: Int) {
        val next = _uiState.value.blocks.stackMediaWithAdjacent(index)
        if (next == null) {
            _uiState.update {
                it.copy(statusMessage = "Drag this picture onto another to stack them.")
            }
            return
        }
        updateBlocks(recordHistory = true) { blocks ->
            blocks.clear()
            blocks.addAll(next)
        }
        _uiState.update {
            it.copy(
                selectedMediaBlockIndex = index.coerceAtMost(next.lastIndex),
                statusMessage = "Pictures stacked",
            )
        }
    }

    /** Drag-onto stack: merge [fromIndex] onto [ontoIndex]. */
    fun stackMediaOnto(fromIndex: Int, ontoIndex: Int) {
        val next = _uiState.value.blocks.stackMediaOnto(fromIndex, ontoIndex) ?: return
        updateBlocks(recordHistory = true) { blocks ->
            blocks.clear()
            blocks.addAll(next)
        }
        _uiState.update {
            it.copy(
                selectedMediaBlockIndex = minOf(fromIndex, ontoIndex).coerceAtMost(next.lastIndex),
                statusMessage = "Pictures stacked",
            )
        }
    }

    /**
     * After a long-press drag: if released over another media block, stack onto it;
     * otherwise reorder by vertical threshold (legacy).
     */
    fun onMediaDragRelease(index: Int, dragOffsetY: Float) {
        val blocks = _uiState.value.blocks
        if (index !in blocks.indices || !blocks.isMediaBlockAt(index)) return
        val approxRow = 220f
        val steps = (dragOffsetY / approxRow).toInt()
        if (steps != 0) {
            var target = index
            var remaining = steps
            val direction = if (remaining > 0) 1 else -1
            while (remaining != 0) {
                val next = target + direction
                if (next !in blocks.indices) break
                target = next
                remaining -= direction
                if (blocks.isMediaBlockAt(target) && target != index) {
                    stackMediaOnto(index, target)
                    return
                }
            }
        }
        val threshold = 48f
        when {
            dragOffsetY < -threshold -> moveBlock(index, -1)
            dragOffsetY > threshold -> moveBlock(index, 1)
        }
    }

    fun setMediaGridCell(index: Int, col: Int, row: Int) {
        updateBlocks(recordHistory = true) { blocks ->
            if (index !in blocks.indices) return@updateBlocks
            blocks[index] = blocks[index].withGridCell(col, row)
        }
    }

    fun cycleMediaStack(index: Int) {
        updateBlocks(recordHistory = false) { blocks ->
            val stack = blocks.getOrNull(index) as? MediaStackBlock ?: return@updateBlocks
            if (stack.mediaIds.isEmpty()) return@updateBlocks
            val nextIndex = (stack.currentIndex + 1) % stack.mediaIds.size
            blocks[index] = stack.copy(currentIndex = nextIndex)
        }
    }

    /** Move a media (or any) block by [delta] slots; persists document order. */
    fun moveBlock(index: Int, delta: Int) {
        if (delta == 0) return
        updateBlocks(recordHistory = true) { blocks ->
            val target = (index + delta).coerceIn(0, blocks.lastIndex)
            if (target == index) return@updateBlocks
            val item = blocks.removeAt(index)
            blocks.add(target, item)
        }
        _uiState.update { state ->
            val selected = state.selectedMediaBlockIndex
            val nextSelected = when {
                selected == null -> null
                selected == index -> (index + delta).coerceIn(0, state.blocks.lastIndex)
                else -> selected
            }
            state.copy(selectedMediaBlockIndex = nextSelected)
        }
    }

    fun undo() {
        flushTypingHistory()
        viewModelScope.launch { workspaceHistory.undo() }
    }

    fun redo() {
        flushTypingHistory()
        viewModelScope.launch { workspaceHistory.redo() }
    }

    fun startSelectionAi(commandId: String, label: String) {
        val sel = _uiState.value.selection
        val block = _uiState.value.blocks.getOrNull(sel.blockIndex) as? Paragraph
        val sceneText = Document(_uiState.value.blocks).plainText()
        val selected = selectedText().ifBlank { sceneText }
        viewModelScope.launch {
            val library = libraryPromptBundle(commandId, PromptRenderContext())
            val replaceInPlace = sel.hasSelection && block != null
            _uiState.update {
                it.copy(
                    editPopupBlockIndex = null,
                    aiOverlay = AiOverlayState(
                        commandId = commandId,
                        label = label.uppercase(),
                        insertAfterIndex = sel.blockIndex,
                        prompt = "",
                        systemInstructions = buildString {
                            append(library.systemInstructions)
                            if (selected.isNotBlank()) {
                                append("\n\nPassage:\n")
                                append(selected)
                            }
                        },
                        promptId = library.promptId,
                        outputWords = when (commandId) {
                            "shorten" -> 300
                            else -> 750
                        },
                        replaceBlockIndex = if (replaceInPlace) sel.blockIndex else null,
                        replaceStart = if (replaceInPlace) sel.min else null,
                        replaceEnd = if (replaceInPlace) sel.max else null,
                    ),
                )
            }
        }
    }

    fun addSelectionToCodex() {
        val text = selectedText().trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Select text to add to Codex") }
            return
        }
        viewModelScope.launch {
            val categories = db.codexDao().getCategories(bookId)
            val category = categories.firstOrNull()
                ?: run {
                    _uiState.update { it.copy(statusMessage = "No Codex categories in this book") }
                    return@launch
                }
            val name = text.lineSequence().first().trim().take(48).ifBlank { "New entry" }
            val entry = codexRepository.addEntry(category.id, bookId, name)
            codexRepository.updateEntryText(entry.id, name, text)
            _uiState.update {
                it.copy(
                    editPopupBlockIndex = null,
                    statusMessage = "Added to Codex: $name",
                    pendingCodexEntryId = entry.id,
                )
            }
        }
    }

    fun onSlashTrigger(index: Int) {
        promptEntryBus.requestOpen(PromptEntryKind.Ai)
        _uiState.update { it.copy(slashBlockIndex = null, slashFilter = "") }
    }

    fun onBackslashTrigger(index: Int) {
        promptEntryBus.requestOpen(PromptEntryKind.Manual)
        _uiState.update { it.copy(slashBlockIndex = null, slashFilter = "", statusMessage = "") }
    }

    fun dismissSlash() {
        _uiState.update { it.copy(slashBlockIndex = null, slashFilter = "") }
    }

    fun applySlashCommand(command: SlashCommand) {
        val index = _uiState.value.slashBlockIndex ?: return
        viewModelScope.launch {
            when (command.id) {
                "image" -> {
                    _uiState.update {
                        it.copy(
                            pickImageBlockIndex = index,
                            pickImageRequestId = it.pickImageRequestId + 1,
                            slashBlockIndex = null,
                            slashFilter = "",
                        )
                    }
                    return@launch
                }
                "video" -> {
                    val media = mediaRepository.registerPlaceholderImage()
                    val path = mediaRepository.resolveFile(media).absolutePath
                    val block = MediaBlock(
                        id = UUID.randomUUID().toString(),
                        mediaId = media.id,
                        kind = MediaKind.Video,
                    )
                    updateBlocksSync(recordHistory = true) { blocks ->
                        blocks[index] = Paragraph(blocks[index].id, listOf(Span("")))
                        blocks.add(index + 1, block)
                    }
                    _uiState.update { it.copy(mediaPaths = it.mediaPaths + (media.id to path)) }
                }
                "scene_beat" -> {
                    updateBlocksSync(recordHistory = true) { blocks ->
                        if (blocks[index] is Paragraph) {
                            blocks[index] = Paragraph(blocks[index].id, listOf(Span("")))
                        }
                        blocks.add(
                            index + 1,
                            SceneBeatBlock(
                                id = UUID.randomUUID().toString(),
                                prompt = "",
                            ),
                        )
                    }
                    _uiState.update { it.copy(slashBlockIndex = null, slashFilter = "") }
                    return@launch
                }
                "continue", "expand", "shorten", "extend", "replace" -> {
                    updateBlocksSync(recordHistory = true) { blocks ->
                        if (blocks[index] is Paragraph) {
                            blocks[index] = Paragraph(blocks[index].id, listOf(Span("")))
                        }
                    }
                    val library = libraryPromptBundle(command.id, PromptRenderContext())
                    _uiState.update {
                        it.copy(
                            aiOverlay = AiOverlayState(
                                commandId = command.id,
                                label = command.label.uppercase(),
                                insertAfterIndex = index,
                                prompt = "",
                                systemInstructions = library.systemInstructions,
                                promptId = library.promptId,
                            ),
                            slashBlockIndex = null,
                            slashFilter = "",
                        )
                    }
                    return@launch
                }
                else -> updateBlocksSync(recordHistory = true) { blocks ->
                    if (blocks[index] is Paragraph) {
                        blocks[index] = Paragraph(blocks[index].id, listOf(Span("")))
                    }
                }
            }
            dismissSlash()
        }
    }

    fun updateAiPrompt(value: String) {
        _uiState.update { state ->
            state.copy(aiOverlay = state.aiOverlay?.copy(prompt = value, errorMessage = ""))
        }
        val overlay = _uiState.value.aiOverlay ?: return
        if (overlay.commandId == "scene_beat") {
            updateBlocks(recordHistory = false) { blocks ->
                val next = blocks.withSceneBeatPrompt(overlay.insertAfterIndex, value)
                blocks.clear()
                blocks.addAll(next)
            }
        }
    }

    fun updateOutputWords(words: Int) {
        _uiState.update { state ->
            state.copy(aiOverlay = state.aiOverlay?.copy(outputWords = words.coerceIn(50, 4000)))
        }
    }

    fun requestBeatImage() {
        _uiState.update { state ->
            val overlay = state.aiOverlay ?: return@update state
            state.copy(
                aiOverlay = overlay.copy(pickBeatImageRequestId = overlay.pickBeatImageRequestId + 1),
            )
        }
    }

    fun attachBeatImage(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val media = mediaRepository.importFromUri(uri)
                val path = mediaRepository.resolveFile(media).absolutePath
                _uiState.update { state ->
                    state.copy(
                        aiOverlay = state.aiOverlay?.copy(
                            imageMediaId = media.id,
                            imagePath = path,
                            errorMessage = "",
                        ),
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        aiOverlay = it.aiOverlay?.copy(
                            errorMessage = err.message ?: "Failed to attach image",
                        ),
                    )
                }
            }
        }
    }

    fun clearBeatImage() {
        _uiState.update {
            it.copy(aiOverlay = it.aiOverlay?.copy(imageMediaId = null, imagePath = null))
        }
    }

    fun speakText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { tts.speak(text) }
        }
    }

    fun dismissAiOverlay() {
        generationJob?.cancel()
        _uiState.update { it.copy(aiOverlay = null) }
    }

    fun runAiGeneration() {
        val overlay = _uiState.value.aiOverlay ?: return
        if (overlay.isStreaming) return
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            if (!aiGeneration.hasApiKey()) {
                _uiState.update {
                    it.copy(
                        aiOverlay = it.aiOverlay?.copy(
                            errorMessage = AIError.NoApiKey().message.orEmpty(),
                        ),
                    )
                }
                return@launch
            }
            val hasImage = !overlay.imageMediaId.isNullOrBlank() && !overlay.imagePath.isNullOrBlank()
            val commandForPrompt = if (hasImage) "describe_image" else overlay.commandId
            if (hasImage && !aiGeneration.modelSupportsImages()) {
                _uiState.update {
                    it.copy(
                        aiOverlay = it.aiOverlay?.copy(
                            errorMessage = "Selected model does not support images. Pick a Vision-capable model in Settings, or clear the attached picture.",
                        ),
                    )
                }
                return@launch
            }
            val sceneText = Document(_uiState.value.blocks).plainText()
            val scene = loadedScene
            val entries = db.codexDao().observeEntries(bookId).first()
            val assembled = contextBuilder.build(
                entries,
                ContextBuildRequest(
                    scanText = sceneText + " " + overlay.prompt + " " + (scene?.pov.orEmpty()),
                    userMessage = overlay.prompt,
                ),
            )
            val renderCtx = buildPromptRenderContext(
                sceneText = sceneText,
                scene = scene,
                entries = entries,
                codexBlock = assembled.codexBlock,
                message = overlay.prompt,
                outputWords = overlay.outputWords,
            )
            val fresh = libraryPromptBundle(commandForPrompt, renderCtx).let { bundle ->
                if (bundle.systemInstructions.isBlank() && hasImage) {
                    libraryPromptBundle("scene_beat", renderCtx)
                } else {
                    bundle
                }
            }
            val activeOverlay = overlay.copy(
                systemInstructions = fresh.systemInstructions.ifBlank { overlay.systemInstructions },
                promptId = fresh.promptId ?: overlay.promptId,
            )
            _uiState.update {
                it.copy(
                    aiOverlay = activeOverlay.copy(
                        isStreaming = true,
                        streamingText = "",
                        errorMessage = "",
                        usageLog = "",
                    ),
                )
            }
            // When the active prompt is multi-message (real User/AI turns from PromptRenderer), the codex
            // block already arrived via {include("Weaverse/Codex")} in one of those turns — skip the
            // duplicate legacy codex injection from ContextBuilder and keep just the baseline framing line.
            val usingMultiMessagePrompt = fresh.historyMessages.isNotEmpty() || fresh.finalUserMessage != null
            val systemBlocks = buildList {
                if (usingMultiMessagePrompt) {
                    add("You are a creative writing assistant.")
                } else {
                    addAll(assembled.systemBlocks)
                }
                val povBlock = buildPovSystemBlock(scene, entries)
                if (povBlock.isNotBlank()) add(povBlock)
                if (activeOverlay.systemInstructions.isNotBlank()) {
                    add("Prompt instructions:\n${activeOverlay.systemInstructions}")
                }
                if (hasImage) {
                    add(
                        "Describe the attached picture as prose suitable for a scene beat. " +
                            "Turn visual detail into narrative text; do not mention that you are describing an image.",
                    )
                }
            }
            val maxTokens = (activeOverlay.outputWords * 1.4).toInt().coerceIn(64, 8192)
            val userMessage = fresh.finalUserMessage ?: buildUserMessage(activeOverlay, sceneText, hasImage)
            val imageAttachments = if (hasImage) {
                listOfNotNull(loadImageAttachment(activeOverlay.imagePath!!))
            } else {
                emptyList()
            }
            val builder = StringBuilder()
            var usageLog = ""
            runCatching {
                aiGeneration.stream(
                    userMessage = userMessage,
                    assembled = AssembledPrompt(
                        systemBlocks = systemBlocks,
                        messages = fresh.historyMessages,
                        usedEntries = assembled.usedEntries,
                        tokenBreakdown = assembled.tokenBreakdown,
                    ),
                    maxTokens = maxTokens,
                    imageAttachments = imageAttachments,
                ).collect { chunk ->
                    when (chunk) {
                        is AIChunk.Delta -> {
                            builder.append(chunk.text)
                            _uiState.update {
                                it.copy(aiOverlay = it.aiOverlay?.copy(streamingText = builder.toString()))
                            }
                        }
                        is AIChunk.Usage -> {
                            usageLog = UsageFormat.formatUsage(
                                promptTokens = chunk.promptTokens,
                                completionTokens = chunk.completionTokens,
                                totalTokens = chunk.totalTokens,
                                cost = chunk.cost,
                            )
                        }
                        AIChunk.Done -> Unit
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        aiOverlay = it.aiOverlay?.copy(
                            isStreaming = false,
                            errorMessage = when (err) {
                                is AIError.HttpFailure -> "HTTP ${err.statusCode}: ${err.message}"
                                is AIError -> err.message.orEmpty()
                                else -> err.message ?: err.toString()
                            },
                        ),
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    aiOverlay = it.aiOverlay?.copy(
                        isStreaming = false,
                        streamingText = builder.toString(),
                        usageLog = usageLog,
                    ),
                )
            }
        }
    }

    /** Runs the Scene Summarizations prompt against the current scene and saves the result into its summary. */
    fun summarizeScene() {
        val scene = loadedScene ?: return
        if (_uiState.value.isSummarizing) return
        viewModelScope.launch {
            if (!aiGeneration.hasApiKey()) {
                _uiState.update { it.copy(statusMessage = AIError.NoApiKey().message.orEmpty()) }
                return@launch
            }
            val sceneText = Document(_uiState.value.blocks).plainText()
            if (sceneText.isBlank()) {
                _uiState.update { it.copy(statusMessage = "Nothing to summarize yet") }
                return@launch
            }
            _uiState.update { it.copy(isSummarizing = true, statusMessage = "Summarizing…") }
            val entries = db.codexDao().observeEntries(bookId).first()
            val assembled = contextBuilder.build(
                entries,
                ContextBuildRequest(scanText = sceneText, userMessage = ""),
            )
            val renderCtx = buildPromptRenderContext(
                sceneText = sceneText,
                scene = scene,
                entries = entries,
                codexBlock = assembled.codexBlock,
            )
            val fresh = libraryPromptBundle("summarize", renderCtx)
            runCatching {
                aiGeneration.complete(
                    userMessage = fresh.finalUserMessage ?: "Summarize the scene above in a few sentences.",
                    assembled = AssembledPrompt(
                        systemBlocks = listOf(fresh.systemInstructions),
                        messages = fresh.historyMessages,
                        usedEntries = assembled.usedEntries,
                        tokenBreakdown = assembled.tokenBreakdown,
                    ),
                    maxTokens = 400,
                )
            }.onSuccess { result ->
                val summary = result.text.trim()
                val updated = scene.copy(summary = summary, updatedAt = System.currentTimeMillis())
                manuscriptRepository.saveScene(updated)
                loadedScene = updated
                _uiState.update { it.copy(isSummarizing = false, statusMessage = "Scene summarized") }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSummarizing = false,
                        statusMessage = when (err) {
                            is AIError.HttpFailure -> "HTTP ${err.statusCode}: ${err.message}"
                            is AIError -> err.message.orEmpty()
                            else -> err.message ?: err.toString()
                        },
                    )
                }
            }
        }
    }

    fun acceptAiResult() {
        val overlay = _uiState.value.aiOverlay ?: return
        val text = overlay.streamingText.trim()
        if (text.isBlank()) {
            dismissAiOverlay()
            return
        }
        flushTypingHistory()
        val replaceIndex = overlay.replaceBlockIndex
        val replaceStart = overlay.replaceStart
        val replaceEnd = overlay.replaceEnd
        if (replaceIndex != null && replaceStart != null && replaceEnd != null) {
            val block = _uiState.value.blocks.getOrNull(replaceIndex) as? Paragraph
            if (block != null) {
                updateBlocksSync(recordHistory = true) { blocks ->
                    val p = blocks[replaceIndex] as Paragraph
                    blocks[replaceIndex] = p.copy(
                        spans = p.spans.replaceRangeText(replaceStart, replaceEnd, text),
                    )
                }
                dismissAiOverlay()
                return
            }
        }
        updateBlocksSync(recordHistory = true) { blocks ->
            val next = blocks.insertGeneratedProseAfter(
                insertAfterIndex = overlay.insertAfterIndex,
                generatedText = text,
                beatPrompt = overlay.prompt.takeIf { overlay.commandId == "scene_beat" },
            )
            blocks.clear()
            blocks.addAll(next)
        }
        dismissAiOverlay()
    }

    fun discardAiResult() {
        _uiState.update {
            it.copy(aiOverlay = it.aiOverlay?.copy(streamingText = "", usageLog = "", errorMessage = ""))
        }
    }

    fun retryAiGeneration() = runAiGeneration()

    fun updateMediaWidth(index: Int, widthPercent: Float) {
        updateBlocks(recordHistory = true) { blocks ->
            val block = blocks[index]
            if (block is MediaBlock) blocks[index] = block.copy(widthPercent = widthPercent)
        }
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) {
            cancelImagePick()
            return
        }
        val blockIndex = _uiState.value.pickImageBlockIndex ?: return
        viewModelScope.launch {
            runCatching {
                val mediaList = mediaRepository.importFromUris(uris)
                if (blockIndex < 0) {
                    var index = (_uiState.value.blocks.size - 1).coerceAtLeast(0)
                    mediaList.forEach { media ->
                        val kind = MediaRepository.kindForType(media.type)
                        insertMediaBlock(index, media.id, kind)
                        index += 1
                    }
                } else {
                    var index = blockIndex
                    mediaList.forEach { media ->
                        val kind = MediaRepository.kindForType(media.type)
                        insertMediaBlock(index, media.id, kind)
                        index += 1
                    }
                }
            }.onFailure {
                _uiState.update { state -> state.copy(pickImageBlockIndex = null) }
            }
        }
    }

    fun cancelImagePick() {
        _uiState.update { it.copy(pickImageBlockIndex = null) }
    }

    fun requestAddMedia() {
        _uiState.update {
            it.copy(
                pickImageBlockIndex = -1,
                pickImageRequestId = it.pickImageRequestId + 1,
            )
        }
    }

    fun requestAddAudio() {
        _uiState.update {
            it.copy(
                pickImageBlockIndex = -1,
                pickAudioRequestId = it.pickAudioRequestId + 1,
            )
        }
    }

    private fun defaultPromptFor(commandId: String): String = when (commandId) {
        "scene_beat" -> "Write a pivotal scene beat where something important changes."
        "describe_image" -> "Describe the attached picture and turn it into scene-beat prose."
        "continue" -> "Continue writing from the current scene."
        "expand", "extend" -> "Expand the current passage with richer detail."
        "shorten" -> "Shorten the passage while preserving voice and meaning."
        "replace" -> "Rewrite the passage with improved clarity and flow."
        else -> "Continue the scene."
    }

    private suspend fun libraryPromptBundle(commandId: String, renderCtx: PromptRenderContext): LibraryPromptBundle {
        val type = when (commandId) {
            "extend" -> "expand"
            else -> commandId
        }
        val prompts = promptRepository.observeByType(type).first()
            .ifEmpty { promptRepository.observeByType(commandId).first() }
        val prompt = prompts.firstOrNull { it.isDefault } ?: prompts.firstOrNull()
        if (prompt == null) {
            return LibraryPromptBundle(
                promptId = null,
                systemInstructions = PromptTokens.apply(defaultPromptFor(commandId), tokenContext(renderCtx)),
            )
        }
        val rendered = PromptRenderer.render(prompt, renderCtx)
        val advanced = runCatching { json.parseToJsonElement(prompt.advancedJson).jsonObject }.getOrNull()
        val guidance = advanced?.get("guidance")?.jsonPrimitive?.contentOrNull.orEmpty()
        val bias = advanced?.get("bias")?.jsonPrimitive?.contentOrNull.orEmpty()
        val systemInstructions = buildString {
            append(rendered.systemText.ifBlank { prompt.description })
            if (guidance.isNotBlank()) append("\n\nGuidance: ").append(guidance)
            if (bias.isNotBlank()) append("\n\nBias: ").append(bias)
        }
        val lastTurn = rendered.messages.lastOrNull()
        val endsInUserTurn = lastTurn?.first == "user"
        return LibraryPromptBundle(
            promptId = prompt.id,
            systemInstructions = systemInstructions,
            historyMessages = if (endsInUserTurn) rendered.messages.dropLast(1) else rendered.messages,
            finalUserMessage = if (endsInUserTurn) lastTurn?.second else null,
        )
    }

    /** Builds the full templating context for [libraryPromptBundle] — book/series/POV/codex/components. */
    private suspend fun buildPromptRenderContext(
        sceneText: String,
        scene: SceneEntity?,
        entries: List<com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity>,
        codexBlock: String,
        message: String = "",
        outputWords: Int = 200,
    ): PromptRenderContext {
        val book = db.bookDao().getById(bookId)
        val series = book?.seriesId?.let { id -> db.seriesDao().observeById(id).first() }
        val povCharacter = scene?.povCharacterId
            ?.let { id -> entries.firstOrNull { it.id == id }?.name }
            .orEmpty()
        val componentBlocks = PromptComponents.build(promptRepository, codexBlock, book)
        return PromptRenderContext(
            novelTense = book?.tense?.ifBlank { "past tense" } ?: "past tense",
            novelTitle = book?.title.orEmpty(),
            seriesTitle = series?.title.orEmpty(),
            seriesDescription = listOfNotNull(
                series?.description?.takeIf { it.isNotBlank() },
                series?.premise?.takeIf { it.isNotBlank() },
            ).joinToString("\n"),
            pov = scene?.pov.orEmpty(),
            povType = scene?.pov.orEmpty(),
            povCharacter = povCharacter,
            sceneFullTextCurrent = sceneText,
            textBefore = sceneText,
            message = message,
            outputWords = outputWords,
            componentBlocks = componentBlocks,
        )
    }

    private fun tokenContext(ctx: PromptRenderContext): PromptTokenContext = PromptTokenContext(
        tense = ctx.novelTense,
        bookTitle = ctx.novelTitle,
        seriesTitle = ctx.seriesTitle,
        seriesDescription = ctx.seriesDescription,
    )

    private fun buildPovSystemBlock(
        scene: SceneEntity?,
        entries: List<com.ihy2ln.weaverse.data.db.entities.CodexEntryEntity>,
    ): String {
        if (scene == null) return ""
        val characterName = scene.povCharacterId
            ?.let { id -> entries.firstOrNull { it.id == id }?.name }
            .orEmpty()
        if (scene.pov.isBlank() && characterName.isBlank()) return ""
        return buildString {
            append("Point of view: ")
            append(scene.pov.ifBlank { "unspecified" })
            if (characterName.isNotBlank()) {
                append(" — focal character: ")
                append(characterName)
            }
            append(". Write consistently in this POV.")
        }
    }

    private fun buildUserMessage(
        overlay: AiOverlayState,
        sceneText: String,
        hasImage: Boolean = false,
    ): String = buildString {
        val userBeat = overlay.prompt.trim()
        if (hasImage) {
            append(
                if (userBeat.isNotBlank()) {
                    "Using the attached image, describe it and write scene-beat prose. Notes: $userBeat"
                } else {
                    defaultPromptFor("describe_image")
                },
            )
            append("\n\n")
        } else if (userBeat.isNotBlank()) {
            append(userBeat)
            append("\n\n")
        } else {
            append(defaultPromptFor(overlay.commandId))
            append("\n\n")
        }
        append("Target length: about ${overlay.outputWords} words.\n\n")
        append("Current scene:\n")
        append(sceneText.take(6000))
    }

    private fun loadImageAttachment(path: String): ImageAttachment? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        return ImageAttachment(
            mimeType = mime,
            base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
        )
    }

    private suspend fun insertMediaBlock(index: Int, mediaId: String, kind: MediaKind) {
        val media = mediaRepository.getById(mediaId) ?: return
        val path = mediaRepository.resolveFile(media).absolutePath
        val block = MediaBlock(
            id = UUID.randomUUID().toString(),
            mediaId = media.id,
            kind = kind,
        )
        updateBlocksSync(recordHistory = true) { blocks ->
            if (index in blocks.indices && blocks[index] is Paragraph) {
                blocks[index] = Paragraph(blocks[index].id, listOf(Span("")))
            }
            blocks.add(index + 1, block)
        }
        _uiState.update {
            it.copy(mediaPaths = it.mediaPaths + (media.id to path), pickImageBlockIndex = null)
        }
    }

    private fun beginTypingHistory() {
        if (applyingHistory) return
        if (typingBaseline == null) {
            typingBaseline = _uiState.value.blocks.toList()
            workspaceHistory.addPendingUndo()
        }
    }

    private fun flushTypingHistory() {
        val baseline = typingBaseline ?: return
        typingBaseline = null
        workspaceHistory.removePendingUndo()
        val current = _uiState.value.blocks
        if (baseline != current) {
            recordDocumentEdit(baseline, current)
        }
    }

    private fun recordDocumentEdit(before: List<Block>, after: List<Block>) {
        if (applyingHistory) return
        val sceneId = loadedScene?.id ?: return
        if (before == after) return
        val beforeCopy = before.toList()
        val afterCopy = after.toList()
        workspaceHistory.record(
            undo = { restoreSceneBlocks(sceneId, beforeCopy) },
            redo = { restoreSceneBlocks(sceneId, afterCopy) },
        )
    }

    private suspend fun restoreSceneBlocks(sceneId: String, blocks: List<Block>) {
        applyingHistory = true
        try {
            val doc = Document(blocks)
            val base = manuscriptRepository.getScene(sceneId) ?: return
            manuscriptRepository.saveScene(
                base.copy(
                    docJson = doc.toJson(),
                    plainText = doc.plainText(),
                    wordCount = doc.wordCount(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        } finally {
            applyingHistory = false
        }
    }

    private fun updateBlocks(recordHistory: Boolean = false, mutator: (MutableList<Block>) -> Unit) {
        if (recordHistory) flushTypingHistory()
        var before: List<Block> = emptyList()
        var after: List<Block> = emptyList()
        _uiState.update { state ->
            before = state.blocks.toList()
            val blocks = state.blocks.toMutableList()
            mutator(blocks)
            after = blocks.toList()
            val doc = Document(blocks)
            persistScene(doc)
            state.copy(
                blocks = blocks,
                wordCount = doc.wordCount(),
                canUndo = workspaceHistory.state.value.canUndo,
                canRedo = workspaceHistory.state.value.canRedo,
            )
        }
        if (recordHistory) recordDocumentEdit(before, after)
        _uiState.update {
            it.copy(
                canUndo = workspaceHistory.state.value.canUndo,
                canRedo = workspaceHistory.state.value.canRedo,
            )
        }
    }

    private fun updateBlocksSync(recordHistory: Boolean = false, mutator: (MutableList<Block>) -> Unit) {
        updateBlocks(recordHistory, mutator)
    }

    private fun persistScene(doc: Document) {
        viewModelScope.launch {
            val base = loadedScene ?: return@launch
            manuscriptRepository.saveScene(
                base.copy(
                    docJson = doc.toJson(),
                    plainText = doc.plainText(),
                    wordCount = doc.wordCount(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun onCleared() {
        if (typingBaseline != null) {
            typingBaseline = null
            workspaceHistory.removePendingUndo()
        }
        unregisterHistoryFlush()
        super.onCleared()
    }
}
