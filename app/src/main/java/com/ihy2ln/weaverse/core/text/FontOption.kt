package com.ihy2ln.weaverse.core.text

import androidx.compose.ui.text.font.FontFamily

/**
 * Font choices the editor can render. Compose ships only these generic families on
 * Android (no Arial/Times New Roman) without bundling font files, so the picker is
 * scoped to what actually renders correctly rather than promising fonts we don't have.
 */
enum class FontOption(val key: String, val label: String, val family: FontFamily) {
    Default("default", "Default", FontFamily.Default),
    Serif("serif", "Serif", FontFamily.Serif),
    SansSerif("sans", "Sans Serif", FontFamily.SansSerif),
    Monospace("mono", "Monospace", FontFamily.Monospace),
    Cursive("cursive", "Cursive", FontFamily.Cursive),
    ;

    companion object {
        val Manuscript = Serif

        fun fromKey(key: String?): FontOption = entries.firstOrNull { it.key == key } ?: Manuscript
    }
}

/** Selectable font sizes (sp) for the format toolbar / picker. */
val FontSizeOptions: List<Int> = listOf(10, 11, 12, 14, 16, 18, 20, 24, 28, 32)
