package com.ihy2ln.weaverse.core.text

import kotlinx.serialization.Serializable

@Serializable
enum class Align { Start, Center, End, Justify }

@Serializable
enum class DividerStyle { SceneBreak, HorizontalRule }

@Serializable
enum class MediaKind { Image, Video, Audio }

@Serializable
enum class Mark {
    Bold, Italic, Underline, Strikethrough, Code, Superscript, Subscript,
}

@Serializable
data class Span(
    val text: String,
    val marks: Set<Mark> = emptySet(),
    val colorHex: String? = null,
    val highlightHex: String? = null,
    val codexEntryId: String? = null,
    /** Key into [com.ihy2ln.weaverse.core.text.FontOption]; null = inherit the editor default. */
    val fontFamilyKey: String? = null,
    /** null = inherit the editor default size. */
    val fontSizeSp: Float? = null,
)

@Serializable
sealed interface Block {
    val id: String
}

@Serializable
data class Paragraph(
    override val id: String,
    val spans: List<Span>,
    val align: Align = Align.Start,
    val indentLevel: Int = 0,
) : Block

@Serializable
data class Heading(
    override val id: String,
    val level: Int,
    val spans: List<Span>,
) : Block

@Serializable
data class Quote(
    override val id: String,
    val spans: List<Span>,
) : Block

@Serializable
data class ListItem(
    override val id: String,
    val ordered: Boolean,
    val depth: Int,
    val spans: List<Span>,
) : Block

@Serializable
data class Divider(
    override val id: String,
    val style: DividerStyle,
) : Block

@Serializable
data class MediaBlock(
    override val id: String,
    val mediaId: String,
    val kind: MediaKind,
    val widthPercent: Float = 100f,
    val align: Align = Align.Center,
    val caption: List<Span> = emptyList(),
    val autoplay: Boolean = false,
    val loop: Boolean = false,
    val muted: Boolean = true,
    /** 6×6 snap cell (0–5). -1 = auto / unset. */
    val gridCol: Int = -1,
    val gridRow: Int = -1,
    /** How many grid cells wide/tall (1–6). */
    val gridColSpan: Int = 1,
    val gridRowSpan: Int = 1,
    /** When true, show a compact bar instead of full media. */
    val collapsed: Boolean = false,
    /** Storyboard: which separate 3×3 board this panel lives on. 0 = first page. */
    val gridPage: Int = 0,
) : Block

@Serializable
data class SceneBeatBlock(
    override val id: String,
    val prompt: String,
    val collapsed: Boolean = false,
    val generatedMessageId: String? = null,
) : Block

@Serializable
data class CodeBlock(
    override val id: String,
    val text: String,
    val language: String? = null,
) : Block

@Serializable
data class MediaStackBlock(
    override val id: String,
    val mediaIds: List<String>,
    val currentIndex: Int = 0,
    val caption: List<Span> = emptyList(),
    /** 6×6 snap cell (0–5). -1 = auto / unset. */
    val gridCol: Int = -1,
    val gridRow: Int = -1,
    val gridColSpan: Int = 1,
    val gridRowSpan: Int = 1,
    val collapsed: Boolean = false,
    /** Storyboard: which separate 3×3 board this panel lives on. 0 = first page. */
    val gridPage: Int = 0,
) : Block

@Serializable
data class MediaGridBlock(
    override val id: String,
    val mediaIds: List<String>,
    val template: String = "2-up",
    val gutterDp: Int = 8,
) : Block

@Serializable
data class Document(
    val blocks: List<Block> = emptyList(),
) {
    companion object {
        fun empty() = Document()
        fun fromPlainText(text: String, blockId: String = "p-1"): Document {
            if (text.isBlank()) return empty()
            return Document(listOf(Paragraph(blockId, listOf(Span(text)))))
        }
    }
}

fun Document.plainText(): String = buildString {
    blocks.forEach { block ->
        when (block) {
            is Paragraph -> append(block.spans.joinToString("") { it.text })
            is Heading -> append(block.spans.joinToString("") { it.text })
            is Quote -> append(block.spans.joinToString("") { it.text })
            is ListItem -> append(block.spans.joinToString("") { it.text })
            is CodeBlock -> append(block.text)
            else -> Unit
        }
        append('\n')
    }
}.trim()

fun Document.wordCount(): Int {
    val words = plainText().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return words.size
}
