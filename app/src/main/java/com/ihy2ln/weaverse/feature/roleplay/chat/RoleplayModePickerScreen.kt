package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

private data class RoleplayModeOption(
    val id: String,
    val label: String,
    val description: String,
)

private val roleplayModeOptions = listOf(
    RoleplayModeOption(
        id = "messenger",
        label = "Messenger",
        description = "Chat-app style — a scrolling back-and-forth conversation.",
    ),
    RoleplayModeOption(
        id = "dungeonMaster",
        label = "Dungeon Master",
        description = "One scene picture, the DM's narration, and your reply — choose your own adventure.",
    ),
    RoleplayModeOption(
        id = "roleplay",
        label = "Storyboard",
        description = "An adjustable 3×3 grid of picture panels, each with its own caption — manga/comic style.",
    ),
)

/** Top of the Roleplay flow: pick a mode, then pick which chat to continue in it. */
@Composable
fun RoleplayModePickerScreen(
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Column(modifier = modifier.fillMaxSize().padding(InkSpacing.lg)) {
        Text("Roleplay", style = MaterialTheme.typography.titleLarge)
        Text(
            "Choose a mode, then pick which chat to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.secondaryText,
            modifier = Modifier.padding(top = InkSpacing.xs, bottom = InkSpacing.lg),
        )
        roleplayModeOptions.forEach { option ->
            InkCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = InkSpacing.md)
                    .clickable { onModeSelected(option.id) },
            ) {
                Text(option.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    option.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(top = InkSpacing.xs),
                )
            }
        }
    }
}
