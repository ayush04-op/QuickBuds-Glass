package com.spizganed.quickbuds

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import com.spizganed.quickbuds.ui.ThemeRes

/**
 * The Liquid Glass style: translucent surfaces with a bright specular rim, a soft top sheen,
 * and a colored backdrop behind them. [ThemeRes] picks these drawables when the style is Glass.
 */
object LiquidGlassHelper {

    private const val LIQUID_LENS_SHADER = """
        uniform shader content;
        uniform float2 resolution;
        uniform float cornerRadius;
        uniform float bevelWidth;
        uniform float refractionStrength;
        uniform float aberration;

        float sdRoundBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 fragCoord) {
            float2 center = resolution * 0.5;
            float2 p = fragCoord - center;
            float2 halfSize = center - 1.0;

            float d = sdRoundBox(p, halfSize, cornerRadius);
            if (d > 0.0) return half4(0.0);

            float edgeFactor = smoothstep(-bevelWidth, 0.0, d);
            float2 normal = normalize(p + float2(0.0001));
            float2 offset = normal * edgeFactor * edgeFactor * refractionStrength;

            half r = content.eval(fragCoord - offset * (1.0 + aberration)).r;
            half g = content.eval(fragCoord - offset).g;
            half b = content.eval(fragCoord - offset * (1.0 - aberration)).b;
            half4 base = content.eval(fragCoord - offset);

            float rim = smoothstep(-2.5, 0.0, d) * 0.35;
            return half4(
                clamp(r + rim, 0.0, 1.0),
                clamp(g + rim, 0.0, 1.0),
                clamp(b + rim, 0.0, 1.0),
                base.a
            );
        }
    """

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha.coerceIn(0f, 1f) * 255f).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    /** [color] with its hue turned by [degrees]: the backdrop's second and third glow. */
    private fun shiftHue(color: Int, degrees: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + degrees + 360f) % 360f
        hsv[1] = hsv[1].coerceIn(0.45f, 1f)
        hsv[2] = hsv[2].coerceIn(0.6f, 1f)
        return Color.HSVToColor(hsv)
    }

    /**
     * One glass surface. [tint] colors the glass (a selected chip, a button). [base] is an opaque
     * color under the glass, for a sheet that overlaps other content. [light] selects the
     * light-theme values: a brighter, milkier fill and a darker lower edge.
     */
    class GlassDrawable(
        private val radiusPx: Float,
        private val strokePx: Float,
        private val light: Boolean,
        private val tint: Int?,
        private val tintAlpha: Float,
        private val base: Int?,
        private val topOnly: Boolean
    ) : Drawable() {

        private val rect = RectF()
        private val rimRect = RectF()
        private val path = Path()
        private val rimPath = Path()
        private val innerRect = RectF()
        private val innerPath = Path()
        private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = strokePx }
        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = strokePx }

        private fun radii(r: Float) =
            if (topOnly) floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) else floatArrayOf(r, r, r, r, r, r, r, r)

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            if (bounds.isEmpty) return
            rect.set(bounds)
            val w = rect.width()
            val h = rect.height()
            val r = radiusPx.coerceAtMost(minOf(w, h) / 2f)

            path.reset()
            path.addRoundRect(rect, radii(r), Path.Direction.CW)

            val half = strokePx / 2f
            rimRect.set(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half)
            rimPath.reset()
            rimPath.addRoundRect(rimRect, radii((r - half).coerceAtLeast(0f)), Path.Direction.CW)

            val inset = strokePx * 1.5f
            innerRect.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
            innerPath.reset()
            if (innerRect.width() > 0f && innerRect.height() > 0f) {
                innerPath.addRoundRect(innerRect, radii((r - inset).coerceAtLeast(0f)), Path.Direction.CW)
            }

            base?.let { basePaint.color = withAlpha(it, 0.82f) }
            tint?.let { tintPaint.color = withAlpha(it, tintAlpha) }

            // Body: a milky wash, brighter at the top.
            val topA = if (light) 0.62f else 0.15f
            val botA = if (light) 0.36f else 0.05f
            fillPaint.shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                withAlpha(Color.WHITE, topA), withAlpha(Color.WHITE, botA), Shader.TileMode.CLAMP
            )

            // Sheen: light pooling on the upper third, like light on a curved surface.
            sheenPaint.shader = LinearGradient(
                0f, rect.top, 0f, rect.top + h * 0.5f,
                withAlpha(Color.WHITE, if (light) 0.55f else 0.22f), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )

            // Rim: bright where light enters (top left), faint at the far corner.
            rimPaint.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    withAlpha(Color.WHITE, if (light) 0.95f else 0.75f),
                    withAlpha(Color.WHITE, if (light) 0.35f else 0.10f),
                    withAlpha(if (light) Color.BLACK else Color.WHITE, if (light) 0.10f else 0.32f)
                ),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            innerPaint.color = withAlpha(Color.WHITE, if (light) 0.30f else 0.06f)
        }

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            if (base != null) canvas.drawPath(path, basePaint)
            if (tint != null) canvas.drawPath(path, tintPaint)
            canvas.drawPath(path, fillPaint)
            val save = canvas.save()
            canvas.clipPath(path)
            canvas.drawRect(rect, sheenPaint)
            canvas.restoreToCount(save)
            canvas.drawPath(rimPath, rimPaint)
            // A second hairline just inside the rim gives the edge its thickness.
            canvas.drawPath(innerPath, innerPaint)
        }

        override fun getOutline(outline: Outline) {
            val b = bounds
            val r = radiusPx.coerceAtMost(minOf(b.width(), b.height()) / 2f)
            // A top-only sheet reaches below its bounds so the bottom corners stay square.
            outline.setRoundRect(b.left, b.top, b.right, b.bottom + if (topOnly) r.toInt() else 0, r)
        }

        override fun setAlpha(alpha: Int) {
            val a = alpha.coerceIn(0, 255)
            basePaint.alpha = a; tintPaint.alpha = a; fillPaint.alpha = a
            sheenPaint.alpha = a; rimPaint.alpha = a; innerPaint.alpha = a
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            basePaint.colorFilter = colorFilter; tintPaint.colorFilter = colorFilter; fillPaint.colorFilter = colorFilter
            sheenPaint.colorFilter = colorFilter; rimPaint.colorFilter = colorFilter; innerPaint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /**
     * The page behind the glass: the palette's background with three soft glows (the accent and two
     * turned hues). Glass needs something to sit on, or it reads as a flat gray card.
     */
    class Backdrop(
        private val background: Int,
        private val accent: Int,
        private val light: Boolean
    ) : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var glows: Array<RadialGradient> = emptyArray()

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            if (bounds.isEmpty) return
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val big = maxOf(w, h)
            val a = if (light) 0.30f else 0.34f
            val c1 = withAlpha(accent, a)
            val c2 = withAlpha(shiftHue(accent, 70f), a * 0.8f)
            val c3 = withAlpha(shiftHue(accent, -60f), a * 0.7f)
            glows = arrayOf(
                RadialGradient(w * 0.15f, h * 0.10f, big * 0.80f, c1, Color.TRANSPARENT, Shader.TileMode.CLAMP),
                RadialGradient(w * 0.98f, h * 0.48f, big * 0.70f, c2, Color.TRANSPARENT, Shader.TileMode.CLAMP),
                RadialGradient(w * 0.12f, h * 1.00f, big * 0.75f, c3, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            )
        }

        override fun draw(canvas: Canvas) {
            canvas.drawColor(background)
            val b = bounds
            for (g in glows) {
                paint.shader = g
                canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), paint)
            }
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.OPAQUE
    }

    /** A glass surface for the active palette: cards, chips, buttons, sheets. */
    fun drawable(
        context: Context,
        radiusDp: Float,
        tint: Int? = null,
        topOnly: Boolean = false,
        base: Int? = null,
        tintAlpha: Float = 0.45f
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val p = ThemeRes.palette(context)
        return GlassDrawable(
            radiusPx = radiusDp * density,
            strokePx = (1.2f * density).coerceAtLeast(1f),
            light = p.isLight,
            tint = tint,
            tintAlpha = tintAlpha,
            base = base,
            topOnly = topOnly
        )
    }

    /** The colored page behind the glass, for the window background. */
    fun backdrop(context: Context): Drawable {
        val p = ThemeRes.palette(context)
        return Backdrop(p.background, p.accent, p.isLight)
    }

    /** Styles any card, button, or pill bar with the liquid glass surface and specular rim. */
    fun applyGlassPill(view: View, cornerRadiusDp: Float = 36f) {
        val radiusPx = cornerRadiusDp * view.resources.displayMetrics.density

        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, radiusPx)
            }
        }
        view.background = drawable(view.context, cornerRadiusDp)
    }

    /** Walks a container and applies the glass pill style to its card-like children. */
    fun styleAllCardsInScreen(root: View) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child.background != null && child is ViewGroup && child.height > 80) {
                    applyGlassPill(child, 28f)
                }
                styleAllCardsInScreen(child)
            }
        }
    }
}
