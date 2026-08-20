package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.text.Mark
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.toHexString

enum class EditTextAction {
    Copy,
    Cut,
    Paste,
    SelectAll,
    Delete,
    Edit,
    Bold,
    Italic,
    Underline,
    Strikethrough,
    Superscript,
    Subscript,
    Color,
    Highlight,
    FontFamily,
    FontSize,
    AddToCodex,
    Shorten,
    Extend,
    Replace,
    Undo,
    Redo,
    Speak,
    /** Speech-to-text: insert dictated words at the caret / selection. */
    Dictate,
}

data class EditTextPopupConfig(
    val showFormatting: Boolean = true,
    val showWritingAi: Boolean = true,
    val showHistory: Boolean = true,
    val showMessageEdit: Boolean = false,
    val showSpeak: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasSelection: Boolean = false,
    /** Marks shared by the whole selection — shown as a ✓ next to their format item. */
    val activeMarks: Set<Mark> = emptySet(),
)

private enum class EditPopupPage { Main, Format }

@Composable
fun EditTextPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (EditTextAction) -> Unit,
    config: EditTextPopupConfig = EditTextPopupConfig(),
    anchorOffset: Offset = Offset.Zero,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val offset = with(density) {
        DpOffset(anchorOffset.x.toDp(), anchorOffset.y.toDp())
    }
    val labelColor = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // Selecting text (drag-select) re-fires showMenu with a non-empty selection each time,
    // so jumping straight to Format there means highlighting text always surfaces format
    // options directly, while a plain long-press (no selection) opens the full Edit menu.
    var page by remember { mutableStateOf(EditPopupPage.Main) }
    LaunchedEffect(expanded) {
        if (expanded) {
            page = if (config.hasSelection && config.showFormatting) {
                EditPopupPage.Format
            } else {
                EditPopupPage.Main
            }
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = modifier,
    ) {
        when (page) {
            EditPopupPage.Main -> MainMenuItems(
                config = config,
                labelColor = labelColor,
                muted = muted,
                onAction = onAction,
                onDismiss = onDismiss,
                onOpenFormat = { page = EditPopupPage.Format },
            )
            EditPopupPage.Format -> FormatMenuItems(
                config = config,
                labelColor = labelColor,
                muted = muted,
                onAction = onAction,
                onDismiss = onDismiss,
                onBack = { page = EditPopupPage.Main },
            )
        }
    }
}

@Composable
private fun MainMenuItems(
    config: EditTextPopupConfig,
    labelColor: Color,
    muted: Color,
    onAction: (EditTextAction) -> Unit,
    onDismiss: () -> Unit,
    onOpenFormat: () -> Unit,
) {
    MenuHeader("Edit", muted)
    Item("Copy", labelColor) { onAction(EditTextAction.Copy); onDismiss() }
    Item("Cut", labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Cut); onDismiss()
    }
    Item("Paste", labelColor) { onAction(EditTextAction.Paste); onDismiss() }
    Item("Select all", labelColor) { onAction(EditTextAction.SelectAll); onDismiss() }
    Item("Delete", labelColor, enabled = config.hasSelection || config.showMessageEdit) {
        onAction(EditTextAction.Delete); onDismiss()
    }
    if (config.showMessageEdit) {
        Item("Edit…", labelColor) { onAction(EditTextAction.Edit); onDismiss() }
    }
    Item("Dictate (voice)", labelColor) {
        onAction(EditTextAction.Dictate); onDismiss()
    }
    if (config.showSpeak) {
        Item("Speak", labelColor, enabled = config.hasSelection) {
            onAction(EditTextAction.Speak); onDismiss()
        }
    }

    if (config.showFormatting) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        MenuHeader("Format", muted)
        Item("Format…", labelColor, enabled = config.hasSelection, onClick = onOpenFormat)
    }

    if (config.showWritingAi) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        MenuHeader("Writing AI", muted)
        Item("Add to Codex", labelColor, enabled = config.hasSelection) {
            onAction(EditTextAction.AddToCodex); onDismiss()
        }
        Item("Shorten", labelColor) { onAction(EditTextAction.Shorten); onDismiss() }
        Item("Extend", labelColor) { onAction(EditTextAction.Extend); onDismiss() }
        Item("Replace", labelColor) { onAction(EditTextAction.Replace); onDismiss() }
    }

    if (config.showHistory) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        MenuHeader("History", muted)
        Item("Undo", labelColor, enabled = config.canUndo) {
            onAction(EditTextAction.Undo); onDismiss()
        }
        Item("Redo", labelColor, enabled = config.canRedo) {
            onAction(EditTextAction.Redo); onDismiss()
        }
    }
}

@Composable
private fun FormatMenuItems(
    config: EditTextPopupConfig,
    labelColor: Color,
    muted: Color,
    onAction: (EditTextAction) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
) {
    fun markLabel(base: String, mark: Mark) = if (mark in config.activeMarks) "$base  ✓" else base

    MenuHeader("Format", muted)
    Item("← Back", labelColor, onClick = onBack)
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    // Close after each toggle. Span changes make Compose call showMenu() again;
    // EditMenuGate keeps the popup from coming back for this same selection.
    Item(markLabel("Bold", Mark.Bold), labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Bold); onDismiss()
    }
    Item(markLabel("Italic", Mark.Italic), labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Italic); onDismiss()
    }
    Item(markLabel("Underline", Mark.Underline), labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Underline); onDismiss()
    }
    Item(markLabel("Strikethrough", Mark.Strikethrough), labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Strikethrough); onDismiss()
    }
    Item(markLabel("Superscript", Mark.Superscript), labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Superscript); onDismiss()
    }
    Item(markLabel("Subscript", Mark.Subscript), labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Subscript); onDismiss()
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Item("Text color…", labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Color); onDismiss()
    }
    Item("Highlight…", labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.Highlight); onDismiss()
    }
    Item("Font…", labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.FontFamily); onDismiss()
    }
    Item("Size…", labelColor, enabled = config.hasSelection) {
        onAction(EditTextAction.FontSize); onDismiss()
    }
}

@Composable
fun TextColorPickerDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
    title: String = "Text color",
) {
    var color by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                InkHsvColorWheel(
                    selected = color,
                    onSelect = { color = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(color); onDismiss() }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun MenuHeader(label: String, color: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
    )
}

@Composable
private fun Item(
    label: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = if (enabled) color else color.copy(alpha = 0.38f),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        onClick = onClick,
        enabled = enabled,
    )
}

fun Color.toSpanHex(): String = toHexString()
