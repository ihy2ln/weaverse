package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

@Composable
fun InkTextTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    val underline = tokens.activePill
    Text(
        text = label,
        modifier = modifier
            .clickable(onClick = onClick)
            .drawBehind {
                if (selected) {
                    val y = size.height - 2.dp.toPx()
                    drawLine(underline, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                }
            }
            .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
        color = if (selected) tokens.activePill else tokens.secondaryText,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}

@Composable
fun InkMenuChip(
    label: String,
    options: List<SegmentedOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val tokens = inkTokens()
    val selectedLabel = options.firstOrNull { it.id == selectedId }?.label ?: label
    Box(modifier = modifier) {
        Text(
            text = "$selectedLabel ▾",
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(tokens.hover)
                .border(InkSpacing.hairline, tokens.hairline, RoundedCornerShape(999.dp))
                .clickable { open = true }
                .padding(horizontal = InkSpacing.md, vertical = InkSpacing.sm),
            color = tokens.primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            fontWeight = if (option.id == selectedId) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    onClick = {
                        onSelect(option.id)
                        open = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkspaceChrome(
    bookTitle: String,
    seriesTitle: String,
    workspaceOptions: List<SegmentedOption>,
    workspaceId: String,
    modeOptions: List<SegmentedOption>,
    modeId: String,
    focusOptions: List<SegmentedOption>,
    focusId: String,
    toolOptions: List<SegmentedOption>,
    activeToolId: String?,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onTool: (String?) -> Unit,
    onWorkspace: (String) -> Unit,
    onMode: (String) -> Unit,
    onFocus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(tokens.panel)
            .padding(top = InkSpacing.sm)
            .border(width = InkSpacing.hairline, color = tokens.hairline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xxs),
        ) {
            IconButton(onClick = onLibrary) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Library")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
            InkMenuChip(
                label = "Workspace",
                options = workspaceOptions,
                selectedId = workspaceId,
                onSelect = onWorkspace,
            )
            InkMenuChip(
                label = "Mode",
                options = modeOptions,
                selectedId = modeId,
                onSelect = onMode,
            )
            InkMenuChip(
                label = "Focus",
                options = focusOptions,
                selectedId = focusId,
                onSelect = onFocus,
            )
            toolOptions.forEach { tab ->
                InkTextTab(
                    label = tab.label,
                    selected = activeToolId == tab.id,
                    onClick = { onTool(if (activeToolId == tab.id) null else tab.id) },
                )
            }
            InkTextButton(label = "Import", onClick = onImport)
            InkTextButton(label = "Export", onClick = onExport)
            InkTextButton(label = "Undo", onClick = onUndo, enabled = canUndo)
            InkTextButton(label = "Redo", onClick = onRedo, enabled = canRedo)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = InkSpacing.md, end = InkSpacing.md, bottom = InkSpacing.sm),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = listOf(bookTitle, seriesTitle)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" · "),
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    repeatDelayMillis = 1_200,
                ),
                color = tokens.primaryText,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
            )
        }
    }
}

/** Dark capsule matching the Novel / Write / Story chrome — use for Clear Text and mode chips. */
@Composable
fun InkModeCapsule(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val tokens = inkTokens()
    val shape = RoundedCornerShape(999.dp)
    Text(
        text = label,
        modifier = modifier
            .clip(shape)
            .background(tokens.hover)
            .border(
                InkSpacing.hairline,
                if (selected) tokens.activePill else tokens.hairline,
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (compact) InkSpacing.sm else InkSpacing.lg,
                vertical = if (compact) InkSpacing.xxs else InkSpacing.sm,
            ),
        color = if (enabled) tokens.primaryText else tokens.secondaryText,
        fontSize = if (compact) 12.sp else 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}
