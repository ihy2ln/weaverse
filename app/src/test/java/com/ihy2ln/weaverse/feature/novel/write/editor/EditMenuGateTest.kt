package com.ihy2ln.weaverse.feature.novel.write.editor

import androidx.compose.ui.text.TextRange
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditMenuGateTest {
    private val word = TextRange(12, 21)

    @Test
    fun firstShowMenuForASelectionOpens() {
        val gate = EditMenuGate()
        assertTrue(gate.shouldOpen(word))
    }

    @Test
    fun showMenuWhileAlreadyOpenIsIgnored() {
        val gate = EditMenuGate()
        assertTrue(gate.shouldOpen(word))
        gate.setExpanded(true)
        assertFalse(gate.shouldOpen(word))
        assertFalse(gate.shouldOpen(TextRange(0, 4)))
    }

    @Test
    fun dismissKeepsTheMenuClosedForTheSameSelection() {
        val gate = EditMenuGate()
        assertTrue(gate.shouldOpen(word))
        gate.setExpanded(true)
        gate.onDismiss(word)
        // Span resync, color-dialog cancel, layout — all call showMenu again
        // with the same range while the handles are still on screen.
        repeat(5) {
            assertFalse(gate.shouldOpen(word), "reopen #$it")
        }
    }

    @Test
    fun collapsingTheCaretAllowsALaterLongPress() {
        val gate = EditMenuGate()
        gate.onDismiss(word)
        assertFalse(gate.shouldOpen(word))
        gate.onSelectionChange(TextRange(21))
        assertTrue(gate.shouldOpen(word))
    }

    @Test
    fun draggingHandlesToANewRangeAllowsReopen() {
        val gate = EditMenuGate()
        gate.onDismiss(word)
        assertFalse(gate.shouldOpen(word))
        assertTrue(gate.shouldOpen(TextRange(12, 30)))
    }

    @Test
    fun closingExpandedViaSetExpandedArmsTheGate() {
        val gate = EditMenuGate()
        gate.setSelection(word)
        assertTrue(gate.shouldOpen())
        gate.setExpanded(true)
        gate.setExpanded(false)
        assertFalse(gate.shouldOpen(word))
    }

    @Test
    fun systemShowMenuInvokesHandlerOnlyWhenAllowed() {
        val gate = EditMenuGate()
        var opened = 0
        gate.setOpenHandler { opened += 1 }
        gate.setSelection(word)
        gate.onSystemShowMenu()
        gate.setExpanded(true)
        gate.onSystemShowMenu()
        gate.onDismiss()
        gate.onSystemShowMenu()
        gate.onSystemShowMenu()
        assertTrue(opened == 1)
    }
}
