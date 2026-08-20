package com.ihy2ln.weaverse.ai.prompt

import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity

/** Builds the system prompt the roleplay model actually receives. */
object RoleplayPromptBuilder {
    fun systemBlocks(
        character: RpCharacterEntity?,
        persona: RpPersonaEntity? = null,
        outputWords: Int,
        displayMode: String = "messenger",
    ): List<String> = buildList {
        addAll(DefaultAiGuides.systemBlocks(com.ihy2ln.weaverse.feature.shell.AppMode.Roleplay, outputWords))
        character?.let { add(characterBlock(it)) }
        persona?.takeIf { it.name.isNotBlank() || it.description.isNotBlank() }?.let { add(personaBlock(it)) }
        displayModeBlock(displayMode)?.let { add(it) }
    }

    private fun displayModeBlock(displayMode: String): String? = when (displayMode) {
        "dungeonMaster" -> "You are the Dungeon Master narrating a choose-your-own-adventure " +
            "scene. Write a short burst of narration (2-4 sentences) that sets or advances the " +
            "scene, then stop and wait for the player's response — never answer for the player " +
            "and never skip ahead multiple beats at once."
        "roleplay" -> "This is a manga/comic-style scene told in short panel beats. Keep each " +
            "reply tight and visual — a sentence or two of action or dialogue per beat, the way " +
            "a comic panel's caption or speech bubble would read, not prose paragraphs."
        else -> null
    }

    fun characterBlock(character: RpCharacterEntity): String {
        val system = character.systemPrompt.trim().takeIf { it.isNotBlank() }
            ?.takeUnless { DefaultAiGuides.isThinSystemPrompt(character.name, it) }
            ?: DefaultAiGuides.characterSystemPrompt(
                name = character.name,
                description = character.description,
                personality = character.personality,
                scenario = character.scenario,
            )
        return buildString {
            append(system)
            if (character.mesExample.isNotBlank()) {
                append("\n\nVoice examples:\n")
                append(character.mesExample.trim())
            }
            if (character.postHistoryInstructions.isNotBlank()) {
                append("\n\nAfter the history, remember:\n")
                append(character.postHistoryInstructions.trim())
            }
            if (
                character.description.isNotBlank() &&
                !system.contains(character.description.trim())
            ) {
                append("\n\nWho they are:\n")
                append(character.description.trim())
            }
            if (
                character.personality.isNotBlank() &&
                !system.contains(character.personality.trim())
            ) {
                append("\n\nHow they come across:\n")
                append(character.personality.trim())
            }
            if (
                character.scenario.isNotBlank() &&
                !system.contains(character.scenario.trim())
            ) {
                append("\n\nThe scene:\n")
                append(character.scenario.trim())
            }
        }
    }

    private fun personaBlock(persona: RpPersonaEntity): String = buildString {
        append("The other person is ")
        append(persona.name.ifBlank { "the writer" })
        append('.')
        if (persona.description.isNotBlank()) {
            append(' ')
            append(persona.description.trim())
        }
        append(" Do not write their actions, thoughts, or dialogue.")
    }
}
