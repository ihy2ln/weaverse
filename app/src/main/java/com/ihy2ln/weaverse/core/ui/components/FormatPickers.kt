package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.ihy2ln.weaverse.core.text.FontOption
import com.ihy2ln.weaverse.core.text.FontSizeOptions
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

/** Font family choices for the selected text — limited to Compose's built-in families. */
@Composable
fun FontFamilyPickerDialog(
    current: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Font") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FontOption.entries.forEach { option ->
                    val selected = option.key == current
                    Text(
                        option.label,
                        fontFamily = option.family,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.key); onDismiss() }
                            .padding(vertical = InkSpacing.sm, horizontal = InkSpacing.xs),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/** Point-size choices for the selected text. */
@Composable
fun FontSizePickerDialog(
    current: Float?,
    onDismiss: () -> Unit,
    onSelect: (Float) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Size") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FontSizeOptions.forEach { size ->
                    val selected = current == size.toFloat()
                    Text(
                        "$size",
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(size.toFloat()); onDismiss() }
                            .padding(vertical = InkSpacing.sm, horizontal = InkSpacing.xs),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
