package org.akanework.gramophone.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.util.AttributeSet
import android.view.Choreographer
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.PathParser
import androidx.core.view.doOnLayout

class TransformableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), Choreographer.FrameCallback {

    private val originalPath: Path = PathParser.createPathFromPathData(FLOWER_SHAPE_PATH)
    private val path = Path()
    private val matrix = Matrix()

    private var shouldClip = false
    private var rotationAngle = 0f
    private var isAnimating = false
    private var isWindowVisible = false

    private var vw = 0f
    private var vh = 0f

    init {
        doOnLayout {
            vw = width.toFloat()
            vh = height.toFloat()

            val vbw = 370f
            val vbh = 370f
            val scaleX = vw / vbw
            val scaleY = vh / vbh

            matrix.reset()
            matrix.setScale(scaleX, scaleY)

            path.reset()
            originalPath.transform(matrix, path)
        }
    }

    fun setClip(state: Boolean) {
        shouldClip = state
        invalidate()
    }

    fun startRotation() {
        isAnimating = true
        updateFrameCallback()
    }

    fun stopRotation() {
        isAnimating = false
        updateFrameCallback()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        isWindowVisible = visibility == VISIBLE
        updateFrameCallback()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isWindowVisible = false
        updateFrameCallback()
    }

    private fun updateFrameCallback() {
        val choreographer = Choreographer.getInstance()
        choreographer.removeFrameCallback(this)
        if (isAnimating && isWindowVisible) {
            choreographer.postFrameCallback(this)
        }
    }

    private var lastFrameTimeNanos: Long = 0L
    private var rotationSpeedDegPerFrame = 0.15f

    private val frameIntervalNanos = 16_666_667L

    override fun doFrame(frameTimeNanos: Long) {
        if (isAnimating) {
            if (lastFrameTimeNanos == 0L) {
                lastFrameTimeNanos = frameTimeNanos
            }

            val deltaNanos = frameTimeNanos - lastFrameTimeNanos
            if (deltaNanos >= frameIntervalNanos) {
                rotationAngle += rotationSpeedDegPerFrame
                if (rotationAngle >= 360f) rotationAngle -= 360f
                lastFrameTimeNanos = frameTimeNanos

                val vbw = 370f
                val vbh = 370f
                val scaleX = vw / vbw
                val scaleY = vh / vbh
                val centerX = vw / 2f
                val centerY = vh / 2f

                matrix.reset()
                matrix.setScale(scaleX, scaleY)
                matrix.postRotate(rotationAngle, centerX, centerY)

                path.reset()
                originalPath.transform(matrix, path)

                invalidate()
            }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (shouldClip) {
            canvas.clipPath(path)
        }
        super.onDraw(canvas)
    }

    companion object {
        const val FLOWER_SHAPE_PATH =
            "M158.142 11.6242C173.013 -3.11741 196.987 -3.11741 211.858 11.6242V11.6242C221.453 21.1358 235.363 24.863 248.428 21.4233V21.4233C268.678 16.0921 289.44 28.079 294.947 48.281V48.281C298.501 61.3158 308.684 71.4988 321.719 75.0526V75.0526C341.921 80.5604 353.908 101.322 348.577 121.572V121.572C345.137 134.637 348.864 148.547 358.376 158.142V158.142C373.117 173.013 373.117 196.987 358.376 211.858V211.858C348.864 221.453 345.137 235.363 348.577 248.428V248.428C353.908 268.678 341.921 289.44 321.719 294.947V294.947C308.684 298.501 298.501 308.684 294.947 321.719V321.719C289.44 341.921 268.678 353.908 248.428 348.577V348.577C235.363 345.137 221.453 348.864 211.858 358.376V358.376C196.987 373.117 173.013 373.117 158.142 358.376V358.376C148.547 348.864 134.637 345.137 121.572 348.577V348.577C101.322 353.908 80.5604 341.921 75.0526 321.719V321.719C71.4988 308.684 61.3158 298.501 48.281 294.947V294.947C28.079 289.44 16.0921 268.678 21.4233 248.428V248.428C24.863 235.363 21.1358 221.453 11.6242 211.858V211.858C-3.11741 196.987 -3.11741 173.013 11.6242 158.142V158.142C21.1358 148.547 24.863 134.637 21.4233 121.572V121.572C16.0921 101.322 28.079 80.5604 48.281 75.0526V75.0526C61.3158 71.4988 71.4988 61.3158 75.0526 48.281V48.281C80.5604 28.079 101.322 16.0921 121.572 21.4233V21.4233C134.637 24.863 148.547 21.1358 158.142 11.6242V11.6242Z"
    }
}