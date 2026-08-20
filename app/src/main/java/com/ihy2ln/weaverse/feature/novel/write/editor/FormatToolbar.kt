package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ihy2ln.weaverse.core.text.FontOption
import com.ihy2ln.weaverse.core.text.Mark
import com.ihy2ln.weaverse.core.ui.components.InkModeCapsule
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

/**
 * Google Docs-style format bar for the manuscript editor. The expand/collapse trigger lives
 * in the caller's header row next to Prompting/Media; this composable renders only the
 * options panel, and only while [expanded] is true. Every button here mirrors an
 * [com.ihy2ln.weaverse.core.ui.components.EditTextAction] so the toolbar and the long-press
 * Format menu stay in sync.
 */
@Composable
fun FormatToolbar(
    expanded: Boolean,
    hasSelection: Boolean,
    activeMarks: Set<Mark>,
    activeFontFamilyKey: String?,
    activeFontSizeSp: Float?,
    canUndo: Boolean,
    canRedo: Boolean,
    onToggleMark: (Mark) -> Unit,
    onOpenColorPicker: () -> Unit,
    onOpenHighlightPicker: () -> Unit,
    onOpenFontFamilyPicker: () -> Unit,
    onOpenFontSizePicker: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!expanded) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            InkTextButton(label = "Undo", onClick = onUndo, enabled = canUndo, compact = true)
            InkTextButton(label = "Redo", onClick = onRedo, enabled = canRedo, compact = true)
            InkModeCapsule(
                label = "B",
                onClick = { onToggleMark(Mark.Bold) },
                selected = Mark.Bold in activeMarks,
                enabled = hasSelection,
                compact = true,
            )
            InkModeCapsule(
                label = "I",
                onClick = { onToggleMark(Mark.Italic) },
                selected = Mark.Italic in activeMarks,
                enabled = hasSelection,
                compact = true,
            )
            InkModeCapsule(
                label = "U",
                onClick = { onToggleMark(Mark.Underline) },
                selected = Mark.Underline in activeMarks,
                enabled = hasSelection,
                compact = true,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = InkSpacing.xxs)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            InkTextButton(
                label = "Font: ${FontOption.fromKey(activeFontFamilyKey).label}",
                onClick = onOpenFontFamilyPicker,
                enabled = hasSelection,
                compact = true,
            )
            InkTextButton(
                label = "Size: ${activeFontSizeSp?.toInt() ?: "—"}",
                onClick = onOpenFontSizePicker,
                enabled = hasSelection,
                compact = true,
            )
            InkModeCapsule(
                label = "S",
                onClick = { onToggleMark(Mark.Strikethrough) },
                selected = Mark.Strikethrough in activeMarks,
                enabled = hasSelection,
                compact = true,
            )
            InkModeCapsule(
                label = "x²",
                onClick = { onToggleMark(Mark.Superscript) },
                selected = Mark.Superscript in activeMarks,
                enabled = hasSelection,
                compact = true,
            )
            InkModeCapsule(
                label = "x₂",
                onClick = { onToggleMark(Mark.Subscript) },
                selected = Mark.Subscript in activeMarks,
                enabled = hasSelection,
                compact = true,
            )
            InkTextButton(
                label = "Color",
                onClick = onOpenColorPicker,
                enabled = hasSelection,
                compact = true,
            )
            InkTextButton(
                label = "Highlight",
                onClick = onOpenHighlightPicker,
                enabled = hasSelection,
                compact = true,
            )
        }
    }
}
