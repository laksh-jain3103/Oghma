package com.reader.oghma.reader

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

/**
 * A FrameLayout that watches for pinch gestures without getting in the way of its children.
 *
 * The EPUB reader re-flows text, so "zoom" means changing the text size. Readium's web view does
 * not do that on pinch by itself, so this layout reports the gesture and the reader resizes the
 * text. Touches are never consumed here: scrolling, taps and text selection keep working.
 *
 * Nothing happens until a [Listener] is set (the PDF reader leaves it unset and uses the web
 * view's own zoom).
 */
class PinchZoomLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface Listener {

        /** While pinching. [factor] is the total change since the pinch started (1.0 = none). */
        fun onScale(factor: Float)

        /** Fingers lifted. Apply [factor] now. */
        fun onScaleEnd(factor: Float)
    }

    var listener: Listener? = null

    private var total = 1f

    private val detector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                total = 1f
                return listener != null
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                total *= detector.scaleFactor
                listener?.onScale(total)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                listener?.onScaleEnd(total)
            }
        }
    )

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {

        if (listener != null) {
            detector.onTouchEvent(ev)
        }

        return super.dispatchTouchEvent(ev)
    }
}
