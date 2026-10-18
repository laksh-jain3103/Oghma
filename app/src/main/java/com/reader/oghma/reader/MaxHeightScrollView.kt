package com.reader.oghma.reader

import android.content.Context
import android.widget.ScrollView

/** A ScrollView that stops growing at [maxHeightPx] and scrolls instead. */
class MaxHeightScrollView(context: Context) : ScrollView(context) {

    var maxHeightPx: Int = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
        )
    }
}
