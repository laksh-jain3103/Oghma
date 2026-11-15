package com.reader.oghma.reader

/**
 * Decides where the dictionary card goes so the selected word stays visible.
 *
 * The card takes whichever side of the word (above or below) has more room.
 * This is plain arithmetic with no Android code, so it can be tested on its own.
 */
object PopupPlacement {

    class Result(
        /** true: card sits at the bottom of the reader; false: at the top. */
        val atBottom: Boolean,
        /** The tallest the card may be, in pixels. */
        val maxCardPx: Float,
        /** true when there is not enough room on either side and the card may cover the word. */
        val mayCoverWord: Boolean
    )

    /**
     * @param parentHeight height of the reader area in pixels
     * @param anchorTop top of the selected text in pixels (NaN if unknown)
     * @param anchorBottom bottom of the selected text in pixels (NaN if unknown)
     * @param density screen density, to turn dp into pixels
     */
    fun compute(
        parentHeight: Float,
        anchorTop: Float,
        anchorBottom: Float,
        density: Float
    ): Result {

        val minCard = 190f * density

        val gap = 16f * density

        if (anchorTop.isNaN() || anchorBottom.isNaN() || parentHeight <= 0f) {

            return Result(
                atBottom = true,
                maxCardPx = maxOf(minCard, parentHeight * 0.5f),
                mayCoverWord = false
            )
        }

        val below = parentHeight - anchorBottom

        val above = anchorTop

        val atBottom = below >= above

        val room = if (atBottom) below else above

        val wanted = minOf(room - gap, parentHeight * 0.62f)

        val maxCard = maxOf(wanted, minCard)

        return Result(
            atBottom = atBottom,
            maxCardPx = maxCard,
            mayCoverWord = wanted < minCard
        )
    }
}
