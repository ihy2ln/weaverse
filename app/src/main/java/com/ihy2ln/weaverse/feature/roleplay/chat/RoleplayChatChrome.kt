package com.ihy2ln.weaverse.feature.roleplay.chat

/**
 * Chrome lifted into [com.ihy2ln.weaverse.feature.shell.AppShell] WorkspaceChrome
 * plus the Roleplay display-mode bar (Messenger / DM / Roleplay).
 * so Roleplay does not render a duplicate full-width secondary header.
 */
data class RoleplayChatChrome(
    val title: String,
    val displayMode: String,
    val onDisplayMode: (String) -> Unit,
)

fun roleplayModeSubtitle(displayMode: String): String = when (displayMode) {
    "dungeonMaster" -> "DM · scene · picture, narration, response"
    "roleplay" -> "Storyboard · 3×3 adjustable panels"
    else -> "Messenger"
}

/** Short user-facing name for a displayMode value — used on the mode picker / list headers. */
fun roleplayModeLabel(displayMode: String): String = when (displayMode) {
    "dungeonMaster" -> "Dungeon Master"
    "roleplay" -> "Storyboard"
    else -> "Messenger"
}
