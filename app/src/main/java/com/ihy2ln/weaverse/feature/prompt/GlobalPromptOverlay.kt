package com.ihy2ln.weaverse.feature.prompt

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.core.ui.components.InkCheckIconButton
import com.ihy2ln.weaverse.core.ui.components.InkClearIconButton
import com.ihy2ln.weaverse.core.ui.components.InkModeCapsule
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.VoiceInputButton
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.components.mergeSpokenText
import com.ihy2ln.weaverse.core.ui.theme.InkAccentBlue
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

private const val PromptMaxHeightDp = 290f

@Composable
fun GlobalPromptOverlay(
    context: PromptInsertContext,
    novelDest: String? = null,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    viewModel: GlobalPromptViewModel = hiltViewModel(),
) {
    if (!active) return
    if (!PromptSurface.usesGlobalOverlay(context.mode, novelDest)) return
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    LaunchedEffect(context) { viewModel.updateContext(context) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.importImage(uri) }
    LaunchedEffect(state.pickImageRequestId) {
        if (state.pickImageRequestId > 0) {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    val kind = state.kind
    val expanded = kind != null
    val label = when (kind) {
        PromptEntryKind.Manual -> "MANUAL (\\)"
        PromptEntryKind.Ai -> "SCENE BEAT (/)"
        null -> "PROMPT"
    }
    val placeholder = when (kind) {
        PromptEntryKind.Manual -> "Write ideas / brainstorm (no AI)…"
        PromptEntryKind.Ai -> "Describe the beat…"
        null -> "Insert text"
    }
    val acceptDescription = if (kind == PromptEntryKind.Ai) "Generate" else "Accept"
    val canSubmit = state.text.isNotBlank() || state.imagePath != null
    val canClear = !state.isStreaming && (state.text.isNotBlank() || state.streamingText.isNotBlank())
    var modelsOpen by remember { mutableStateOf(false) }
    var modelSearch by rememberSaveable { mutableStateOf("") }
    val activeModelRef = PromptModelSelection.effectiveModelRef(
        state.selectedModelRef,
        state.defaultModelRef,
    )
    val shape = RoundedCornerShape(InkSpacing.radiusMd)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        focusedTextColor = tokens.primaryText,
        unfocusedTextColor = tokens.primaryText,
        disabledTextColor = tokens.primaryText.copy(alpha = 0.7f),
        cursorColor = tokens.primaryText,
        focusedPlaceholderColor = tokens.secondaryText,
        unfocusedPlaceholderColor = tokens.secondaryText,
        disabledPlaceholderColor = tokens.secondaryText,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xxs)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .border(1.dp, InkAccentBlue, shape)
            .heightIn(max = PromptMaxHeightDp.dp)
            .padding(horizontal = InkSpacing.xs, vertical = InkSpacing.xxs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xxs),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = InkAccentBlue,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (expanded) {
                InkTextButton(label = "−", onClick = viewModel::dismiss, compact = true)
            }
        }
        VoiceToTextField(
            value = state.text,
            onValueChange = viewModel::onTextChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
            enabled = !state.isStreaming,
            minLines = PromptBoxSizing.MinLines,
            maxLines = PromptBoxSizing.MaxLines,
            compact = true,
            colors = fieldColors,
            showMic = false,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = InkSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xxs),
        ) {
            InkModeCapsule(
                label = "Man",
                onClick = { viewModel.setKind(PromptEntryKind.Manual) },
                selected = kind == PromptEntryKind.Manual,
                enabled = !state.isStreaming,
                compact = true,
            )
            InkModeCapsule(
                label = "Gen",
                onClick = { viewModel.setKind(PromptEntryKind.Ai) },
                selected = kind == PromptEntryKind.Ai,
                enabled = !state.isStreaming,
                compact = true,
            )
            InkTextButton(
                label = "Models",
                onClick = { modelsOpen = true },
                compact = true,
                enabled = !state.isStreaming,
            )
            Text(
                PromptModelSelection.shortLabel(activeModelRef, state.writingModels),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 1_200),
            )
            if (state.isStreaming) {
                Text(
                    "…",
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(end = InkSpacing.xxs),
                )
            } else {
                InkCheckIconButton(
                    onClick = viewModel::submit,
                    enabled = canSubmit,
                    contentDescription = acceptDescription,
                )
            }
            InkClearIconButton(
                onClick = viewModel::clearText,
                enabled = canClear,
            )
            VoiceInputButton(
                enabled = !state.isStreaming,
                compact = true,
                onSpoken = { spoken -> viewModel.onTextChange(mergeSpokenText(state.text, spoken)) },
            )
        }
        if (kind == PromptEntryKind.Ai) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = InkSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            ) {
                Text("Words", style = MaterialTheme.typography.labelSmall, color = tokens.primaryText)
                OutlinedTextField(
                    value = state.outputWords.toString(),
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(4)
                        viewModel.updateOutputWords(digits.toIntOrNull() ?: 750)
                    },
                    modifier = Modifier
                        .width(60.dp)
                        .heightIn(max = 36.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.primaryText),
                    colors = fieldColors,
                )
                InkTextButton(
                    label = if (state.imagePath != null) "Image ✓" else "Add pic",
                    onClick = viewModel::requestImage,
                    compact = true,
                    enabled = !state.isStreaming,
                )
            }
        }
        if (state.isStreaming) {
            Text(
                "Generating…",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                modifier = Modifier.padding(top = InkSpacing.xxs),
            )
        }
        if (state.usageText.isNotBlank()) {
            Text(
                state.usageText,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                modifier = Modifier.padding(top = InkSpacing.xxs),
            )
        }
        if (state.errorMessage.isNotBlank()) {
            Text(
                state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = InkSpacing.xxs),
            )
        }
    }
    if (modelsOpen) {
        PromptModelPickerDialog(
            models = state.writingModels,
            search = modelSearch,
            onSearchChange = { modelSearch = it },
            selectedRef = state.selectedModelRef,
            defaultRef = state.defaultModelRef,
            onSelect = { id ->
                viewModel.selectModel(id)
                modelsOpen = false
            },
            onUseDefault = {
                viewModel.useDefaultModel()
                modelsOpen = false
            },
            onDismiss = { modelsOpen = false },
        )
    }
}

@Composable
private fun PromptModelPickerDialog(
    models: List<ModelInfo>,
    search: String,
    onSearchChange: (String) -> Unit,
    selectedRef: String,
    defaultRef: String,
    onSelect: (String) -> Unit,
    onUseDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    val filtered = PromptModelSelection.filter(models, search)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Models") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search models") },
                )
                Text(
                    "Per generation · Settings default stays unless you change it there",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = InkSpacing.xs, bottom = InkSpacing.xs),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onUseDefault)
                        .padding(vertical = InkSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val usingDefault = PromptModelSelection.followsDefault(selectedRef)
                    Text(
                        "Settings default",
                        modifier = Modifier.weight(1f),
                        fontWeight = if (usingDefault) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        PromptModelSelection.shortLabel(defaultRef, models),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (filtered.isEmpty()) {
                    Text(
                        "Refresh models in Settings → Writing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = InkSpacing.sm),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .padding(top = InkSpacing.xs),
                    ) {
                        items(filtered, key = { it.id }) { model ->
                            val selected = PromptModelSelection.isSelected(
                                model,
                                selectedRef,
                                defaultRef,
                            )
                            val muted = !model.available
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = model.available) { onSelect(model.id) }
                                    .padding(vertical = InkSpacing.xs),
                            ) {
                                Text(
                                    model.displayName,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (muted) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildString {
                                        append(model.id)
                                        if (muted) append(" · unavailable")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if (muted) 0.4f else 1f,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
