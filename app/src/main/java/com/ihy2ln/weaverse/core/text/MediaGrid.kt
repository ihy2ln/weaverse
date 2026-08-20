package com.ihy2ln.weaverse.core.text

/** Shared snap grid for Roleplay manga / Write media placement (default 6×6). */
object MediaGrid {
    const val SIZE = 6
    /** Compact grid — used by the Roleplay Storyboard panel canvas. */
    const val DM_SIZE = 3

    fun clampCell(value: Int, gridSize: Int = SIZE): Int = value.coerceIn(0, gridSize - 1)

    fun clampSpan(value: Int, gridSize: Int = SIZE): Int = value.coerceIn(1, gridSize)

    fun snapFraction(fraction: Float, gridSize: Int = SIZE): Int =
        clampCell((fraction.coerceIn(0f, 0.999f) * gridSize).toInt(), gridSize)

    fun isPlaced(col: Int, row: Int, gridSize: Int = SIZE): Boolean =
        col in 0 until gridSize && row in 0 until gridSize

    fun clampSpanAt(
        col: Int,
        row: Int,
        colSpan: Int,
        rowSpan: Int,
        gridSize: Int = SIZE,
    ): Pair<Int, Int> {
        val c = clampCell(col, gridSize)
        val r = clampCell(row, gridSize)
        val cs = clampSpan(colSpan, gridSize).coerceAtMost(gridSize - c)
        val rs = clampSpan(rowSpan, gridSize).coerceAtMost(gridSize - r)
        return cs to rs
    }

    fun cellsCovered(
        col: Int,
        row: Int,
        colSpan: Int,
        rowSpan: Int,
        gridSize: Int = SIZE,
    ): Set<Pair<Int, Int>> {
        if (!isPlaced(col, row, gridSize)) return emptySet()
        val (cs, rs) = clampSpanAt(col, row, colSpan, rowSpan, gridSize)
        val out = mutableSetOf<Pair<Int, Int>>()
        for (r in row until row + rs) {
            for (c in col until col + cs) {
                out += c to r
            }
        }
        return out
    }

    /** Next free top-left for a 1×1 cell in row-major order, or (0,0) if full. */
    fun nextFreeCell(occupied: Set<Pair<Int, Int>>, gridSize: Int = SIZE): Pair<Int, Int> {
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val cell = col to row
                if (cell !in occupied) return cell
            }
        }
        return 0 to 0
    }

    /**
     * Next free top-left in row-major order where a [colSpan]×[rowSpan] block fits
     * without overlapping [occupied], or null if no such slot exists on this grid.
     */
    fun nextFreeSlot(
        occupied: Set<Pair<Int, Int>>,
        gridSize: Int,
        colSpan: Int,
        rowSpan: Int,
    ): Pair<Int, Int>? {
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                if (canPlace(col, row, colSpan, rowSpan, occupied, gridSize = gridSize)) {
                    return col to row
                }
            }
        }
        return null
    }

    fun canPlace(
        col: Int,
        row: Int,
        colSpan: Int,
        rowSpan: Int,
        occupied: Set<Pair<Int, Int>>,
        ignore: Set<Pair<Int, Int>> = emptySet(),
        gridSize: Int = SIZE,
    ): Boolean {
        if (!isPlaced(col, row, gridSize)) return false
        val (cs, rs) = clampSpanAt(col, row, colSpan, rowSpan, gridSize)
        if (col + cs > gridSize || row + rs > gridSize) return false
        for (r in row until row + rs) {
            for (c in col until col + cs) {
                val cell = c to r
                if (cell in occupied && cell !in ignore) return false
            }
        }
        return true
    }
}

fun Block.gridColOrUnset(): Int = when (this) {
    is MediaBlock -> gridCol
    is MediaStackBlock -> gridCol
    else -> -1
}

fun Block.gridRowOrUnset(): Int = when (this) {
    is MediaBlock -> gridRow
    is MediaStackBlock -> gridRow
    else -> -1
}

/** Storyboard: which separate board this panel lives on. 0 for block types without pages. */
fun Block.gridPageOrZero(): Int = when (this) {
    is MediaBlock -> gridPage
    is MediaStackBlock -> gridPage
    else -> 0
}

fun Block.gridColSpanOrOne(gridSize: Int = MediaGrid.SIZE): Int = when (this) {
    is MediaBlock -> MediaGrid.clampSpan(gridColSpan, gridSize)
    is MediaStackBlock -> MediaGrid.clampSpan(gridColSpan, gridSize)
    else -> 1
}

fun Block.gridRowSpanOrOne(gridSize: Int = MediaGrid.SIZE): Int = when (this) {
    is MediaBlock -> MediaGrid.clampSpan(gridRowSpan, gridSize)
    is MediaStackBlock -> MediaGrid.clampSpan(gridRowSpan, gridSize)
    else -> 1
}

fun Block.withGridCell(col: Int, row: Int, gridSize: Int = MediaGrid.SIZE): Block = when (this) {
    is MediaBlock -> copy(
        gridCol = MediaGrid.clampCell(col, gridSize),
        gridRow = MediaGrid.clampCell(row, gridSize),
    )
    is MediaStackBlock -> copy(
        gridCol = MediaGrid.clampCell(col, gridSize),
        gridRow = MediaGrid.clampCell(row, gridSize),
    )
    else -> this
}

fun Block.withGridPlacement(
    col: Int,
    row: Int,
    colSpan: Int,
    rowSpan: Int,
    gridSize: Int = MediaGrid.SIZE,
    page: Int? = null,
): Block {
    val (cs, rs) = MediaGrid.clampSpanAt(col, row, colSpan, rowSpan, gridSize)
    return when (this) {
        is MediaBlock -> copy(
            gridCol = MediaGrid.clampCell(col, gridSize),
            gridRow = MediaGrid.clampCell(row, gridSize),
            gridColSpan = cs,
            gridRowSpan = rs,
            gridPage = page ?: gridPage,
        )
        is MediaStackBlock -> copy(
            gridCol = MediaGrid.clampCell(col, gridSize),
            gridRow = MediaGrid.clampCell(row, gridSize),
            gridColSpan = cs,
            gridRowSpan = rs,
            gridPage = page ?: gridPage,
        )
        else -> this
    }
}

/** Marks a panel unplaced (grid col/row = -1) without touching its span or page. */
fun Block.withGridUnplaced(): Block = when (this) {
    is MediaBlock -> copy(gridCol = -1, gridRow = -1)
    is MediaStackBlock -> copy(gridCol = -1, gridRow = -1)
    else -> this
}
