package com.ihy2ln.weaverse.feature.prompt

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Base64
import com.ihy2ln.weaverse.ai.AIChunk
import com.ihy2ln.weaverse.ai.AIError
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.ai.ImageAttachment
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.ai.context.AssembledPrompt
import com.ihy2ln.weaverse.ai.openrouter.OpenRouterModelCache
import com.ihy2ln.weaverse.ai.prompt.DefaultAiGuides
import com.ihy2ln.weaverse.ai.prompt.PromptTokenContext
import com.ihy2ln.weaverse.ai.prompt.RoleplayPromptBuilder
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.text.Document
import com.ihy2ln.weaverse.core.text.appendParagraphs
import com.ihy2ln.weaverse.core.text.documentFromJson
import com.ihy2ln.weaverse.core.text.plainText
import com.ihy2ln.weaverse.core.text.toJson
import com.ihy2ln.weaverse.core.text.wordCount
import com.ihy2ln.weaverse.core.ui.util.UsageFormat
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.ChatMessageEntity
import com.ihy2ln.weaverse.data.db.entities.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entities.SnippetEntity
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.notes.NotesViewModel
import com.ihy2ln.weaverse.feature.shell.AppMode
import com.ihy2ln.weaverse.feature.shell.NovelDestination
import com.ihy2ln.weaverse.feature.shell.WorkspaceHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class GlobalPromptUiState(
    val kind: PromptEntryKind? = null,
    val text: String = "",
    val outputWords: Int = 750,
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val errorMessage: String = "",
    val usageText: String = "",
    val imageMediaId: String? = null,
    val imagePath: String? = null,
    val pickImageRequestId: Long = 0L,
    val statusMessage: String = "",
    /** Empty follows Settings default for this generation. */
    val selectedModelRef: String = "",
    val defaultModelRef: String = "",
    val writingModels: List<ModelInfo> = emptyList(),
)

data class PromptInsertContext(
    val mode: AppMode = AppMode.Novel,
    val sceneId: String? = null,
    val rpChatId: String? = null,
    val noteId: String? = null,
    val bookId: String = "",
    val workshopThreadId: String? = null,
    val novelDest: String? = null,
)

@HiltViewModel
class GlobalPromptViewModel @Inject constructor(
    private val bus: PromptEntryBus,
    private val aiGeneration: AiGenerationService,
    private val db: WeaverseDatabase,
    private val mediaRepository: MediaRepository,
    private val settings: SettingsRepository,
    private val modelCache: OpenRouterModelCache,
    private val workspaceHistory: WorkspaceHistory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GlobalPromptUiState())
    val uiState: StateFlow<GlobalPromptUiState> = _uiState.asStateFlow()

    private var context = PromptInsertContext()

    init {
        viewModelScope.launch {
            bus.openRequests.collect { kind -> open(kind) }
        }
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                _uiState.update { it.copy(defaultModelRef = prefs.defaultModelRef) }
            }
        }
        viewModelScope.launch {
            modelCache.models.collect { dtos ->
                _uiState.update { it.copy(writingModels = modelCache.writingModels(dtos)) }
            }
        }
    }

    fun updateContext(ctx: PromptInsertContext) {
        context = ctx
    }

    fun open(kind: PromptEntryKind) {
        _uiState.update {
            it.copy(
                kind = kind,
                text = "",
                streamingText = "",
                errorMessage = "",
                usageText = "",
                statusMessage = "",
                isStreaming = false,
                imageMediaId = null,
                imagePath = null,
                outputWords = if (kind == PromptEntryKind.Ai) 750 else it.outputWords,
            )
        }
    }

    fun dismiss() {
        _uiState.update { it.copy(kind = null, isStreaming = false) }
    }

    /**
     * Selects Manual/Generative mode. Opens the prompt window fresh if it wasn't
     * already expanded; otherwise switches mode in place, keeping the typed text.
     */
    fun setKind(kind: PromptEntryKind) {
        val current = _uiState.value.kind
        if (current == kind) return
        if (current == null) {
            open(kind)
        } else {
            _uiState.update { it.copy(kind = kind) }
        }
    }

    fun onTextChange(value: String) {
        _uiState.update { it.copy(text = value, errorMessage = "", statusMessage = "") }
    }

    fun clearText() {
        _uiState.update {
            it.copy(text = "", streamingText = "", errorMessage = "", statusMessage = "")
        }
    }

    fun updateOutputWords(words: Int) {
        _uiState.update { it.copy(outputWords = words.coerceIn(50, 4000)) }
    }

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedModelRef = PromptModelSelection.modelRef(modelId)) }
    }

    fun useDefaultModel() {
        _uiState.update { it.copy(selectedModelRef = "") }
    }

    fun requestImage() {
        _uiState.update { it.copy(pickImageRequestId = it.pickImageRequestId + 1) }
    }

    fun clearImage() {
        _uiState.update { it.copy(imageMediaId = null, imagePath = null) }
    }

    fun importImage(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val entity = mediaRepository.importFromUri(uri)
                _uiState.update {
                    it.copy(
                        imageMediaId = entity.id,
                        imagePath = mediaRepository.resolveFile(entity).absolutePath,
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Could not import image") }
            }
        }
    }

    fun submit() {
        val state = _uiState.value
        val kind = state.kind ?: PromptEntryKind.Manual
        if (state.text.isBlank() && state.imageMediaId == null) return
        when (kind) {
            PromptEntryKind.Manual -> submitManual(state.text)
            PromptEntryKind.Ai -> generateAi(state)
        }
    }

    private fun submitManual(text: String) {
        viewModelScope.launch {
            runCatching { insertText(text, asUserInRoleplay = true) }
                .onSuccess {
                    _uiState.update { it.copy(statusMessage = "Added", text = "") }
                    dismiss()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Could not add text") }
                }
        }
    }

    private fun generateAi(state: GlobalPromptUiState) {
        viewModelScope.launch {
            if (!aiGeneration.hasApiKey()) {
                _uiState.update { it.copy(errorMessage = AIError.NoApiKey().message.orEmpty()) }
                return@launch
            }
            _uiState.update {
                it.copy(isStreaming = true, streamingText = "", errorMessage = "", usageText = "")
            }
            val maxTokens = (state.outputWords * 1.5).toInt().coerceIn(64, 8192)
            val userMessage = buildString {
                append(state.text.ifBlank { DefaultAiGuides.draftFor(context.mode) })
                if (state.imageMediaId != null) {
                    append("\n\n(Use the attached image; turn it into vivid scene text.)")
                }
            }
            val imageAttachments = state.imagePath?.let { path ->
                val file = File(path)
                if (!file.exists()) return@let null
                val bytes = file.readBytes()
                val mime = when (file.extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    else -> "image/jpeg"
                }
                listOf(ImageAttachment(mimeType = mime, base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)))
            }.orEmpty()
            val builder = StringBuilder()
            var usage = ""
            runCatching {
                aiGeneration.stream(
                    userMessage = userMessage,
                    assembled = AssembledPrompt(
                        systemBlocks = assembleSystemBlocks(state.outputWords),
                        messages = emptyList(),
                        usedEntries = emptyList(),
                        tokenBreakdown = emptyList(),
                    ),
                    modelRef = state.selectedModelRef.ifBlank { null },
                    maxTokens = maxTokens,
                    temperature = 0.85,
                    imageAttachments = imageAttachments,
                ).collect { chunk ->
                    when (chunk) {
                        is AIChunk.Delta -> {
                            builder.append(chunk.text)
                            _uiState.update { it.copy(streamingText = builder.toString()) }
                        }
                        is AIChunk.Usage -> {
                            usage = UsageFormat.formatUsage(
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
                    it.copy(isStreaming = false, errorMessage = err.message ?: "Generation failed")
                }
                return@launch
            }
            val result = builder.toString()
            runCatching {
                // Roleplay: keep user prompt + character reply; others get AI text only.
                if (context.mode == AppMode.Roleplay && !context.rpChatId.isNullOrBlank()) {
                    insertRoleplayExchange(state.text, result)
                } else if (isWorkshopChat()) {
                    val added = buildList {
                        insertWorkshop(state.text, role = "user")?.let { add(it) }
                        insertWorkshop(result, role = "assistant")?.let { add(it) }
                    }
                    recordChatMessages(added)
                } else if (context.mode == AppMode.Novel) {
                    insertNovelAi(result)
                } else {
                    insertText(result, asUserInRoleplay = false)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        usageText = usage,
                        errorMessage = err.message ?: "Could not insert result",
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isStreaming = false,
                    streamingText = result,
                    usageText = usage,
                    statusMessage = "Inserted",
                    text = "",
                )
            }
            dismiss()
        }
    }

    private suspend fun assembleSystemBlocks(outputWords: Int): List<String> {
        val chatId = context.rpChatId
        if (context.mode != AppMode.Roleplay || chatId.isNullOrBlank()) {
            val book = context.bookId.takeIf { it.isNotBlank() }?.let { db.bookDao().getById(it) }
            val series = book?.seriesId?.let { id -> db.seriesDao().observeById(id).first() }
            return DefaultAiGuides.systemBlocks(
                context.mode,
                outputWords,
                PromptTokenContext(
                    tense = book?.tense?.ifBlank { "past tense" } ?: "past tense",
                    bookTitle = book?.title.orEmpty(),
                    seriesTitle = series?.title.orEmpty(),
                    seriesDescription = series?.description.orEmpty(),
                ),
            )
        }
        val chat = db.roleplayDao().getChat(chatId)
        val character = chat?.characterId?.let { db.roleplayDao().getCharacter(it) }
        val persona = chat?.personaId?.let { db.roleplayDao().getPersona(it) }
        return RoleplayPromptBuilder.systemBlocks(
            character,
            persona,
            outputWords,
            displayMode = chat?.displayMode.orEmpty().ifBlank { "messenger" },
        )
    }

    private suspend fun activeRpDisplayMode(chatId: String): String =
        db.roleplayDao().getChat(chatId)?.displayMode?.ifBlank { "messenger" } ?: "messenger"

    private fun isWorkshopChat(): Boolean {
        val dest = context.novelDest?.let { runCatching { NovelDestination.valueOf(it) }.getOrNull() }
        return context.mode == AppMode.Novel && dest == NovelDestination.Chat
    }

    private suspend fun insertWorkshop(text: String, role: String): ChatMessageEntity? {
        val threadId = context.workshopThreadId ?: error("Open Chat first")
        if (text.isBlank()) return null
        val entity = ChatMessageEntity(
            id = "msg-${UUID.randomUUID()}",
            threadId = threadId,
            role = role,
            contentJson = Document.fromPlainText(text).toJson(),
            createdAt = System.currentTimeMillis(),
        )
        db.workshopChatDao().upsertMessage(entity)
        return entity
    }

    private fun recordChatMessages(entities: List<ChatMessageEntity>) {
        if (entities.isEmpty()) return
        workspaceHistory.record(
            undo = { entities.forEach { db.workshopChatDao().deleteMessage(it.id) } },
            redo = { entities.forEach { db.workshopChatDao().upsertMessage(it) } },
        )
    }

    private suspend fun insertNovelAi(generated: String) {
        val sceneId = context.sceneId ?: error("Open a scene in Write first")
        val scene = db.manuscriptDao().getScene(sceneId) ?: error("Scene not found")
        persistSceneWithHistory(scene, documentFromJson(scene.docJson).appendParagraphs(generated))
    }

    private suspend fun persistSceneWithHistory(scene: com.ihy2ln.weaverse.data.db.entities.SceneEntity, next: Document) {
        val after = scene.copy(
            docJson = next.toJson(),
            plainText = next.plainText(),
            wordCount = next.wordCount(),
            updatedAt = System.currentTimeMillis(),
        )
        db.manuscriptDao().upsertScene(after)
        workspaceHistory.record(
            undo = { db.manuscriptDao().upsertScene(scene) },
            redo = { db.manuscriptDao().upsertScene(after) },
        )
    }

    private suspend fun insertRoleplayExchange(userPrompt: String, aiText: String) {
        val chatId = context.rpChatId ?: return
        val mode = activeRpDisplayMode(chatId)
        val now = System.currentTimeMillis()
        val groupId = "sw-$now"
        val added = mutableListOf<RpMessageEntity>()
        if (userPrompt.isNotBlank()) {
            val user = RpMessageEntity(
                id = "rpm-$now",
                chatId = chatId,
                swipeGroupId = groupId,
                swipeIndex = 0,
                isActiveSwipe = true,
                role = "user",
                contentJson = Document.fromPlainText(userPrompt).toJson(),
                createdAt = now,
                displayMode = mode,
            )
            db.roleplayDao().upsertMessage(user)
            added += user
        }
        val reply = RpMessageEntity(
            id = "rpm-${now + 1}",
            chatId = chatId,
            swipeGroupId = groupId,
            swipeIndex = 0,
            isActiveSwipe = true,
            role = "char",
            contentJson = Document.fromPlainText(aiText).toJson(),
            createdAt = now + 1,
            displayMode = mode,
        )
        db.roleplayDao().upsertMessage(reply)
        added += reply
        recordRpMessages(added)
    }

    private fun recordRpMessages(entities: List<RpMessageEntity>) {
        if (entities.isEmpty()) return
        workspaceHistory.record(
            undo = { entities.forEach { db.roleplayDao().deleteMessage(it.id) } },
            redo = { entities.forEach { db.roleplayDao().upsertMessage(it) } },
        )
    }

    private suspend fun insertText(text: String, asUserInRoleplay: Boolean) {
        when {
            isWorkshopChat() -> {
                val entity = insertWorkshop(text, role = if (asUserInRoleplay) "user" else "assistant")
                if (entity != null) recordChatMessages(listOf(entity))
            }
            context.mode == AppMode.Roleplay -> {
                val chatId = context.rpChatId ?: error("Open a roleplay chat first")
                val mode = activeRpDisplayMode(chatId)
                val now = System.currentTimeMillis()
                val entity = RpMessageEntity(
                    id = "rpm-$now",
                    chatId = chatId,
                    swipeGroupId = "sw-$now",
                    swipeIndex = 0,
                    isActiveSwipe = true,
                    role = if (asUserInRoleplay) "user" else "char",
                    contentJson = Document.fromPlainText(text).toJson(),
                    createdAt = now,
                    displayMode = mode,
                )
                db.roleplayDao().upsertMessage(entity)
                recordRpMessages(listOf(entity))
            }
            context.mode == AppMode.Novel -> {
                val sceneId = context.sceneId ?: error("Open a scene in Write first")
                val scene = db.manuscriptDao().getScene(sceneId) ?: error("Scene not found")
                persistSceneWithHistory(scene, documentFromJson(scene.docJson).appendParagraphs(text))
            }
            context.mode == AppMode.Notes -> {
                val noteId = context.noteId ?: bus.activeNoteId
                if (noteId != null) {
                    val existing = db.snippetDao().getById(noteId)
                        ?: error("Select a note first")
                    val next = documentFromJson(existing.body).appendParagraphs(text)
                    persistNoteWithHistory(
                        existing,
                        existing.copy(
                            body = next.toJson(),
                            scopeType = NotesViewModel.SCOPE_TYPE,
                            scopeId = NotesViewModel.SCOPE_ID,
                            category = NotesViewModel.CATEGORY,
                        ),
                    )
                } else {
                    val now = System.currentTimeMillis()
                    val id = "note-${UUID.randomUUID()}"
                    val created = SnippetEntity(
                        id = id,
                        scopeType = NotesViewModel.SCOPE_TYPE,
                        scopeId = NotesViewModel.SCOPE_ID,
                        title = "Prompt note",
                        body = Document.fromPlainText(text).toJson(),
                        category = NotesViewModel.CATEGORY,
                        pinned = false,
                        createdAt = now,
                    )
                    persistNoteWithHistory(before = null, after = created)
                    bus.activeNoteId = id
                }
            }
        }
    }

    private suspend fun persistNoteWithHistory(before: SnippetEntity?, after: SnippetEntity) {
        db.snippetDao().upsert(after)
        bus.notifyNoteChanged(after.id)
        workspaceHistory.record(
            undo = {
                if (before == null) db.snippetDao().deleteById(after.id)
                else db.snippetDao().upsert(before)
                bus.notifyNoteChanged(after.id)
            },
            redo = {
                db.snippetDao().upsert(after)
                bus.notifyNoteChanged(after.id)
            },
        )
    }
}
