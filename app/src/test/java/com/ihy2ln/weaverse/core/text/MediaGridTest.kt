package com.ihy2ln.weaverse.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaGridTest {
    @Test
    fun clampSpanAt_respectsEdges() {
        val (cs, rs) = MediaGrid.clampSpanAt(4, 4, 4, 4)
        assertEquals(2, cs)
        assertEquals(2, rs)
    }

    @Test
    fun clampSpanAt_dmSize3() {
        val (cs, rs) = MediaGrid.clampSpanAt(2, 2, 4, 4, gridSize = MediaGrid.DM_SIZE)
        assertEquals(1, cs)
        assertEquals(1, rs)
    }

    @Test
    fun cellsCovered_includesFootprint() {
        val cells = MediaGrid.cellsCovered(1, 1, 2, 2)
        assertEquals(setOf(1 to 1, 2 to 1, 1 to 2, 2 to 2), cells)
    }

    @Test
    fun canPlace_detectsOverlap() {
        val occupied = MediaGrid.cellsCovered(0, 0, 2, 2)
        assertFalse(MediaGrid.canPlace(1, 1, 1, 1, occupied))
        assertTrue(MediaGrid.canPlace(2, 2, 1, 1, occupied))
    }

    @Test
    fun canPlace_dmGrid() {
        val occupied = MediaGrid.cellsCovered(0, 0, 1, 1, gridSize = MediaGrid.DM_SIZE)
        assertFalse(MediaGrid.canPlace(0, 0, 1, 1, occupied, gridSize = MediaGrid.DM_SIZE))
        assertTrue(MediaGrid.canPlace(2, 2, 1, 1, occupied, gridSize = MediaGrid.DM_SIZE))
        assertFalse(MediaGrid.isPlaced(3, 0, gridSize = MediaGrid.DM_SIZE))
    }

    @Test
    fun snapFraction_dmSize() {
        assertEquals(0, MediaGrid.snapFraction(0f, gridSize = MediaGrid.DM_SIZE))
        assertEquals(2, MediaGrid.snapFraction(0.99f, gridSize = MediaGrid.DM_SIZE))
        assertEquals(1, MediaGrid.snapFraction(0.5f, gridSize = MediaGrid.DM_SIZE))
    }

    @Test
    fun nextFreeCell_dmSize() {
        val free = MediaGrid.nextFreeCell(setOf(0 to 0, 1 to 0), gridSize = MediaGrid.DM_SIZE)
        assertEquals(2 to 0, free)
    }

    @Test
    fun nextFreeSlot_findsRoomForSpan() {
        val occupied = MediaGrid.cellsCovered(0, 0, 1, 1, gridSize = MediaGrid.DM_SIZE)
        val slot = MediaGrid.nextFreeSlot(occupied, MediaGrid.DM_SIZE, colSpan = 2, rowSpan = 1)
        assertEquals(1 to 0, slot)
    }

    @Test
    fun nextFreeSlot_nullWhenNoRoom() {
        val occupied = MediaGrid.cellsCovered(0, 0, MediaGrid.DM_SIZE, MediaGrid.DM_SIZE, gridSize = MediaGrid.DM_SIZE)
        val slot = MediaGrid.nextFreeSlot(occupied, MediaGrid.DM_SIZE, colSpan = 1, rowSpan = 1)
        assertEquals(null, slot)
    }

    @Test
    fun withGridPlacement_preservesPageByDefault() {
        val block = MediaBlock(id = "m1", mediaId = "x", kind = MediaKind.Image, gridPage = 2)
        val placed = block.withGridPlacement(1, 1, 1, 1) as MediaBlock
        assertEquals(2, placed.gridPage)
    }

    @Test
    fun withGridPlacement_setsPageWhenGiven() {
        val block = MediaBlock(id = "m1", mediaId = "x", kind = MediaKind.Image, gridPage = 0)
        val placed = block.withGridPlacement(1, 1, 1, 1, page = 3) as MediaBlock
        assertEquals(3, placed.gridPage)
    }

    @Test
    fun withGridUnplaced_clearsCellButKeepsSpanAndPage() {
        val block = MediaBlock(id = "m1", mediaId = "x", kind = MediaKind.Image)
            .withGridPlacement(2, 2, 2, 2, page = 1) as MediaBlock
        val unplaced = block.withGridUnplaced() as MediaBlock
        assertEquals(-1, unplaced.gridCol)
        assertEquals(-1, unplaced.gridRow)
        assertEquals(2, unplaced.gridColSpan)
        assertEquals(1, unplaced.gridPage)
    }

    @Test
    fun withGridPlacement_persistsSpans() {
        val block = MediaBlock(id = "m1", mediaId = "x", kind = MediaKind.Image)
        val placed = block.withGridPlacement(2, 3, 2, 3) as MediaBlock
        assertEquals(2, placed.gridCol)
        assertEquals(3, placed.gridRow)
        assertEquals(2, placed.gridColSpan)
        assertEquals(3, placed.gridRowSpan)
    }

    @Test
    fun withGridPlacement_dmSizeClamps() {
        val block = MediaBlock(id = "m1", mediaId = "x", kind = MediaKind.Image)
        val placed = block.withGridPlacement(1, 1, 3, 3, gridSize = MediaGrid.DM_SIZE) as MediaBlock
        assertEquals(1, placed.gridCol)
        assertEquals(1, placed.gridRow)
        assertEquals(2, placed.gridColSpan)
        assertEquals(2, placed.gridRowSpan)
    }
}
