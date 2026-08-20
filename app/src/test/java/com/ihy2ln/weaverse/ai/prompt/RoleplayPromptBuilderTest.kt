package com.ihy2ln.weaverse.ai.prompt

import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entities.RpPersonaEntity
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoleplayPromptBuilderTest {
    @Test
    fun systemBlocks_includeCraftAndCharacterCard() {
        val character = RpCharacterEntity(
            id = "char-1",
            name = "Mara",
            description = "A sharp-eyed local historian.",
            personality = "Curious, dry humor.",
            scenario = "Harbor café.",
            systemPrompt = "You are Mara. Stay in character.",
            createdAt = 0L,
        )
        val persona = RpPersonaEntity(
            id = "persona-1",
            name = "Writer",
            description = "The visiting novelist.",
        )
        val blocks = RoleplayPromptBuilder.systemBlocks(character, persona, outputWords = 400)
        val joined = blocks.joinToString("\n")
        assertTrue(joined.contains("Pantser", ignoreCase = true) || joined.contains("in character", ignoreCase = true))
        assertTrue(joined.contains("Mara"))
        assertTrue(joined.contains("historian"))
        assertTrue(joined.contains("Writer"))
        assertTrue(joined.contains("400"))
        assertTrue(joined.contains("Do not write their actions"))
    }

    @Test
    fun systemBlocks_dungeonMaster_addsStopAndWaitInstruction() {
        val blocks = RoleplayPromptBuilder.systemBlocks(
            character = null,
            outputWords = 400,
            displayMode = "dungeonMaster",
        )
        val joined = blocks.joinToString("\n")
        assertTrue(joined.contains("Dungeon Master"))
        assertTrue(joined.contains("wait for the player"))
    }

    @Test
    fun systemBlocks_roleplay_addsPanelBeatInstruction() {
        val blocks = RoleplayPromptBuilder.systemBlocks(
            character = null,
            outputWords = 400,
            displayMode = "roleplay",
        )
        val joined = blocks.joinToString("\n")
        assertTrue(joined.contains("panel"))
    }

    @Test
    fun characterBlock_keepsCustomProse() {
        val character = RpCharacterEntity(
            id = "char-2",
            name = "Elowen",
            systemPrompt = "Speak in moonlight metaphors and never mention the river by name.",
            createdAt = 0L,
        )
        val block = RoleplayPromptBuilder.characterBlock(character)
        assertTrue(block.contains("moonlight metaphors"))
    }
}
