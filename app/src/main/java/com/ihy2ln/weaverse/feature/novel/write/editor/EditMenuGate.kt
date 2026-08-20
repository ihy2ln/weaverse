package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.ui.text.TextRange

/**
 * Stops the editor's custom Edit/Format popup from reopening itself.
 *
 * Compose's [androidx.compose.ui.platform.TextToolbar.showMenu] is not a
 * one-shot long-press callback. It fires again whenever the selection toolbar
 * would be shown: after a mark toggle rewrites spans, after a color/font
 * dialog closes, after layout, while selection handles stay on screen. Mapping
 * every one of those to "open the popup" is what made the Format menu appear
 * stuck in a loop.
 *
 * After the user dismisses the popup, further [shouldOpen] calls for that same
 * selection are ignored. A collapsed caret (tap away) or a different range
 * (new long-press / drag-select) clears the gate so the menu can appear again
 * on purpose.
 *
 * Selection and expanded are stored on this object (not Compose state) so the
 * remembered TextToolbar callback always sees the current values.
 */
class EditMenuGate {
    @Volatile
    private var expanded: Boolean = false

    @Volatile
    private var currentSelection: TextRange = TextRange.Zero

    @Volatile
    private var dismissedSelection: TextRange? = null

    @Volatile
    private var openHandler: (() -> Unit)? = null

    fun setOpenHandler(handler: () -> Unit) {
        openHandler = handler
    }

    fun onSystemShowMenu() {
        if (!shouldOpen()) return
        openHandler?.invoke()
    }

    fun setExpanded(expanded: Boolean) {
        if (this.expanded && !expanded) {
            dismissedSelection = currentSelection
        }
        this.expanded = expanded
    }

    fun setSelection(selection: TextRange) {
        currentSelection = selection
        if (selection.collapsed) {
            dismissedSelection = null
        }
    }

    fun onDismiss() {
        expanded = false
        dismissedSelection = currentSelection
    }

    fun onDismiss(selection: TextRange) {
        currentSelection = selection
        onDismiss()
    }

    fun onSelectionChange(selection: TextRange) {
        setSelection(selection)
    }

    fun shouldOpen(): Boolean = shouldOpen(currentSelection)

    fun shouldOpen(selection: TextRange): Boolean {
        if (expanded) return false
        val dismissed = dismissedSelection
        if (dismissed != null && dismissed == selection) return false
        dismissedSelection = null
        return true
    }
}
