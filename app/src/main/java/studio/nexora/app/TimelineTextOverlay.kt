package studio.nexora.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect

@UnstableApi
private class TimelineTextCanvasOverlay(
    private val textClip: TimelineClip,
    private val videoClipStartMs: Long,
    private val playbackSpeed: Float
) : CanvasOverlay(true) {
    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        val timelineTimeMs = videoClipStartMs +
            (presentationTimeUs / 1_000.0 * playbackSpeed.coerceIn(0.25f, 3f)).toLong()
        if (timelineTimeMs < textClip.startMs || timelineTimeMs >= textClip.endMs) return

        val width = canvas.width.coerceAtLeast(1)
        val height = canvas.height.coerceAtLeast(1)
        val padding = (width * 0.025f).coerceAtLeast(12f)
        val maxTextWidth = (width * 0.88f).toInt().coerceAtLeast(1)
        val textSize = (height * textClip.textSizeScale.coerceIn(0.025f, 0.12f)).coerceAtLeast(18f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textClip.textColor
            this.textSize = textSize
            typeface = Typeface.create(
                Typeface.DEFAULT,
                if (textClip.textBold) Typeface.BOLD else Typeface.NORMAL
            )
            textAlign = Paint.Align.CENTER
            setShadowLayer(height * 0.004f, 0f, height * 0.002f, android.graphics.Color.BLACK)
        }
        val text = textClip.label.trim()
        if (text.isEmpty()) return
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setMaxLines(4)
            .build()

        val left = (width - maxTextWidth) / 2f
        val top = when (textClip.textPosition) {
            "TOP" -> height * 0.08f
            "CENTER" -> (height - layout.height) / 2f
            else -> height * 0.88f - layout.height
        }.coerceIn(0f, (height - layout.height).coerceAtLeast(0).toFloat())
        val rect = RectF(
            left - padding,
            top - padding * 0.55f,
            left + maxTextWidth + padding,
            top + layout.height + padding * 0.55f
        )
        if (android.graphics.Color.alpha(textClip.textBackgroundColor) > 0) {
            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textClip.textBackgroundColor
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(rect, padding * 0.55f, padding * 0.55f, background)
        }
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }
}

@UnstableApi
fun buildTimelineTextOverlayEffect(
    textClips: List<TimelineClip>,
    videoClipStartMs: Long,
    videoClipDurationMs: Long,
    playbackSpeed: Float
): Effect? {
    val videoClipEndMs = videoClipStartMs + videoClipDurationMs
    val overlays = textClips
        .filter { it.label.isNotBlank() && it.durationMs > 0L && it.startMs < videoClipEndMs && it.endMs > videoClipStartMs }
        .map { TimelineTextCanvasOverlay(it, videoClipStartMs, playbackSpeed) }
    return overlays.takeIf { it.isNotEmpty() }?.let { OverlayEffect(it) }
}
