package com.ihy2ln.weaverse.core.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ihy2ln.weaverse.core.ui.LocalPromptShortcutHandler
import com.ihy2ln.weaverse.core.ui.consumePromptShortcut
import java.util.Locale

/**
 * Returns a lambda that starts system speech recognition and delivers spoken text to [onSpoken].
 */
@Composable
fun rememberSpeechToText(onSpoken: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pendingSpeech by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) onSpoken(spoken.trim())
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingSpeech) {
            pendingSpeech = false
            launchSpeech(context.packageManager, speechLauncher::launch)
        } else {
            pendingSpeech = false
        }
    }

    return {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchSpeech(context.packageManager, speechLauncher::launch)
        } else {
            pendingSpeech = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/**
 * Mic button that launches system speech recognition and returns spoken text.
 * Permission denials are handled gracefully (no crash).
 */
@Composable
fun VoiceInputButton(
    onSpoken: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = "Voice input",
    compact: Boolean = false,
) {
    val startSpeech = rememberSpeechToText(onSpoken)
    IconButton(
        onClick = { if (enabled) startSpeech() },
        enabled = enabled,
        modifier = if (compact) modifier.size(32.dp) else modifier,
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = contentDescription,
            modifier = if (compact) Modifier.size(18.dp) else Modifier,
        )
    }
}

/** Append [spoken] to [value] with a space when needed. */
fun mergeSpokenText(value: String, spoken: String): String {
    val trimmed = spoken.trim()
    if (trimmed.isEmpty()) return value
    return if (value.isBlank()) trimmed else "${value.trimEnd()} $trimmed"
}

/**
 * Outlined text field with a trailing mic that launches speech recognition.
 */
@Composable
fun VoiceToTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    extraTrailing: (@Composable RowScope.() -> Unit)? = null,
    compact: Boolean = false,
    showMic: Boolean = true,
) {
    val shortcutHandler = LocalPromptShortcutHandler.current
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            val shortcut = if (shortcutHandler != null) consumePromptShortcut(next) else null
            if (shortcut != null) {
                onValueChange(shortcut.remainder)
                shortcutHandler?.invoke(shortcut.kind)
            } else {
                onValueChange(next)
            }
        },
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        colors = colors,
        visualTransformation = visualTransformation,
        trailingIcon = if (extraTrailing == null && !showMic) {
            null
        } else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    extraTrailing?.invoke(this)
                    if (showMic) {
                        VoiceInputButton(
                            enabled = enabled,
                            compact = compact,
                            onSpoken = { spoken -> onValueChange(mergeSpokenText(value, spoken)) },
                        )
                    }
                }
            }
        },
    )
}

private fun launchSpeech(
    packageManager: PackageManager,
    launch: (Intent) -> Unit,
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
    }
    if (intent.resolveActivity(packageManager) != null) {
        launch(intent)
    }
}
