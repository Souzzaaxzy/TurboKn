package com.turbokn.app.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.min

/**
 * Draws the in-game HUD: a centered banner at the top with the game name and
 * an FPS readout in the bottom-left corner.
 *
 * The window is added with FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCHABLE, so it
 * never steals input from the game.
 */
object GameOverlay {

    private const val BANNER_WIDTH_DP = 220
    private const val BANNER_HEIGHT_DP = 40
    private const val FPS_MARGIN_DP = 16

    private var windowManager: WindowManager? = null
    private var bannerView: BannerView? = null
    private var fpsView: FpsView? = null

    val isShowing: Boolean
        get() = bannerView != null

    private val handler = Handler(Looper.getMainLooper())

    fun show(context: Context, label: String) {
        if (bannerView != null) {
            bannerView?.label = label
            bannerView?.invalidate()
            return
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val banner = BannerView(context.applicationContext, label)
        val fps = FpsView(context.applicationContext)

        val bannerParams = WindowManager.LayoutParams(
            dp(context, BANNER_WIDTH_DP),
            dp(context, BANNER_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(context, 24)
        }

        val fpsParams = WindowManager.LayoutParams(
            dp(context, 190),
            dp(context, 90),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = dp(context, FPS_MARGIN_DP)
            y = dp(context, FPS_MARGIN_DP)
        }

        try {
            wm.addView(banner, bannerParams)
            wm.addView(fps, fpsParams)
        } catch (e: Exception) {
            bannerView = null
            fpsView = null
            return
        }

        bannerView = banner
        fpsView = fps
    }

    fun hide() {
        val wm = windowManager
        bannerView?.let { runCatching { wm?.removeView(it) } }
        fpsView?.let { runCatching { wm?.removeView(it) } }
        bannerView = null
        fpsView = null
        windowManager = null
    }

    /** Pushes a fresh FPS reading to the view; called by the sampler loop. */
    fun updateFps(value: Int?) {
        handler.post {
            fpsView?.fps = value
            fpsView?.invalidate()
        }
    }

    /** Updates the banner text without rebuilding the window. */
    fun updateLabel(label: String) {
        handler.post {
            bannerView?.let {
                if (it.label != label) {
                    it.label = label
                    it.invalidate()
                }
            }
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private const val OVERLAY_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    /** Banner plate: ink black at 80% and a snow-white hairline border. */
    private const val BANNER_FILL = 0xCC0A0A0B.toInt()
    private const val BANNER_STROKE = 0xFFF2F2F0.toInt()
    private const val FPS_CAPTION = 0xFFB9BDC4.toInt()

    /**
     * Banner shaped like a plate with cut corners: a wide top edge and a
     * narrower bottom edge, centered at the top of the screen.
     */
    private class BannerView(context: Context, var label: String) : View(context) {

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BANNER_FILL
            style = Paint.Style.FILL
        }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BANNER_STROKE
            style = Paint.Style.STROKE
            strokeWidth = 2f * resources.displayMetrics.density
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val notch = min(w * 0.14f, h * 0.6f)

            // Octagon: the top edge is full width, the sides cut inward toward
            // a narrower bottom edge, matching the requested shape.
            path.reset()
            path.moveTo(0f, 0f)
            path.lineTo(w, 0f)
            path.lineTo(w, h - notch)
            path.lineTo(w - notch, h)
            path.lineTo(notch, h)
            path.lineTo(0f, h - notch)
            path.close()

            canvas.drawPath(path, fill)
            canvas.drawPath(path, stroke)

            textPaint.textSize = h * 0.42f
            val metrics = textPaint.fontMetrics
            val baseline = h / 2f - (metrics.ascent + metrics.descent) / 2f
            val available = w - 4 * notch
            val fitted = fitText(label, available)
            canvas.drawText(fitted, w / 2f, baseline, textPaint)
        }

        /** Shrinks the label until it fits, then truncates if still too wide. */
        private fun fitText(text: String, maxWidth: Float): String {
            if (maxWidth <= 0f) return ""
            var size = textPaint.textSize
            while (size > 8f && textPaint.measureText(text) > maxWidth) {
                size -= 1f
                textPaint.textSize = size
            }
            if (textPaint.measureText(text) <= maxWidth) return text
            var cut = text
            while (cut.isNotEmpty() && textPaint.measureText("$cut…") > maxWidth) {
                cut = cut.dropLast(1)
            }
            return if (cut.isEmpty()) "" else "$cut…"
        }
    }

    /** Large FPS number with a small "FPS" caption, bottom-left. */
    private class FpsView(context: Context) : View(context) {

        var fps: Int? = null

        private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
        }
        private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FPS_CAPTION
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.25f
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
        }

        override fun onDraw(canvas: Canvas) {
            val density = resources.displayMetrics.density
            valuePaint.textSize = 34f * density
            captionPaint.textSize = 13f * density

            val text = fps?.toString() ?: "--"
            canvas.drawText(text, 0f, valuePaint.textSize, valuePaint)
            canvas.drawText("FPS", 0f, valuePaint.textSize + captionPaint.textSize + 6f * density, captionPaint)
        }
    }
}
