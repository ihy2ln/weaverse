package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer

@Composable
fun RoleplayChatsScreen(
    displayMode: String,
    onChatClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RoleplayChatsViewModel = hiltViewModel(),
) {
    val chats by viewModel.chats.collectAsState()
    val filtered = chats.filter { it.displayMode.ifBlank { "messenger" } == displayMode }
    Column(modifier = Modifier.fillMaxSize().padding(InkSpacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkTextButton(label = "← Modes", onClick = onBack)
        }
        Text(
            "${roleplayModeLabel(displayMode)} chats",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = InkSpacing.sm, bottom = InkSpacing.md),
        )
        if (filtered.isEmpty()) {
            Text(
                "No chats in this mode yet. Open a chat in another mode and switch its mode to move it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = inkTokens().secondaryText,
            )
        }
        LazyColumn {
            items(filtered, key = { it.id }) { chat ->
                InkCard(
                    modifier = Modifier
                        .padding(vertical = InkSpacing.sm)
                        .clickable { onChatClick(chat.id) },
                ) {
                    Text(chat.title, style = MaterialTheme.typography.titleMedium)
                }
            }
            alwaysScrollEndSpacer()
        }
    }
}
