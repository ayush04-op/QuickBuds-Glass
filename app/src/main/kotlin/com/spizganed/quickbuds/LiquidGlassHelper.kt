package com.spizganed.quickbuds

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import com.spizganed.quickbuds.ui.ThemeRes

/**
 * The Liquid Glass style.
 *
 * The page is a [Scene]: a colored background with soft glows and crisp shapes. Every glass surface
 * shows a blurred, saturated copy of the scene behind it (see [BackdropCache]), then a faint wash,
 * a top sheen, a bottom shade and a bright specular rim. The blur is real: the scene is rendered
 * small, box blurred, and sampled at the surface's position in the window. Scrolling redraws it.
 * [ThemeRes] picks these drawables when the style is Glass.
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

    /** [color] with its hue turned by [degrees]. */
    private fun shiftHue(color: Int, degrees: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + degrees + 360f) % 360f
        hsv[1] = hsv[1].coerceIn(0.45f, 1f)
        hsv[2] = hsv[2].coerceIn(0.6f, 1f)
        return Color.HSVToColor(hsv)
    }

    // ---------------------------------------------------------------------------------------------
    // The scene behind the glass
    // ---------------------------------------------------------------------------------------------

    /**
     * Background color, three soft glows, and four crisp, translucent orbs. The crisp shapes are what
     * makes the blur visible: the page shows them sharp, a glass surface shows them dissolved.
     */
    private class Scene(private val background: Int, private val accent: Int, private val light: Boolean) {

        private class Orb(val cx: Float, val cy: Float, val r: Float, val shader: LinearGradient)

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var glows: Array<RadialGradient> = emptyArray()
        private var orbs: Array<Orb> = emptyArray()

        fun layout(w: Float, h: Float) {
            val big = maxOf(w, h)
            val glowA = if (light) 0.26f else 0.30f
            glows = arrayOf(
                RadialGradient(w * 0.15f, h * 0.08f, big * 0.80f, withAlpha(accent, glowA), Color.TRANSPARENT, Shader.TileMode.CLAMP),
                RadialGradient(w * 0.98f, h * 0.50f, big * 0.70f, withAlpha(shiftHue(accent, 70f), glowA * 0.8f), Color.TRANSPARENT, Shader.TileMode.CLAMP),
                RadialGradient(w * 0.10f, h * 1.00f, big * 0.75f, withAlpha(shiftHue(accent, -60f), glowA * 0.7f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            )
            // (x, y, radius as a fraction of the width, hue turn, opacity)
            val spec = arrayOf(
                floatArrayOf(0.86f, 0.15f, 0.26f, 0f, 0.55f),
                floatArrayOf(0.08f, 0.38f, 0.20f, 70f, 0.45f),
                floatArrayOf(0.80f, 0.64f, 0.34f, -60f, 0.45f),
                floatArrayOf(0.20f, 0.88f, 0.27f, 30f, 0.50f)
            )
            val k = if (light) 0.65f else 1f
            orbs = Array(spec.size) { i ->
                val s = spec[i]
                val cx = w * s[0]
                val cy = h * s[1]
                val r = w * s[2]
                val c = shiftHue(accent, s[3])
                Orb(cx, cy, r, LinearGradient(
                    cx - r, cy - r, cx + r, cy + r,
                    withAlpha(c, s[4] * k), withAlpha(c, s[4] * k * 0.25f), Shader.TileMode.CLAMP
                ))
            }
        }

        fun draw(canvas: Canvas, w: Float, h: Float) {
            canvas.drawColor(background)
            for (g in glows) {
                paint.shader = g
                canvas.drawRect(0f, 0f, w, h, paint)
            }
            for (o in orbs) {
                paint.shader = o.shader
                canvas.drawCircle(o.cx, o.cy, o.r, paint)
            }
            paint.shader = null
        }
    }

    /**
     * The scene rendered at 1/6 size, once as is and once blurred, for a window of a given size and
     * palette. One entry is kept: every glass surface on screen shares it.
     */
    private object BackdropCache {

        class Entry(val key: Long, val blur: Bitmap)

        private var entry: Entry? = null
        private const val SCALE = 6
        private const val BLUR_RADIUS = 7

        fun get(context: Context, width: Int, height: Int): Entry {
            val p = ThemeRes.palette(context)
            var key = p.background.toLong() * 31 + p.accent
            key = key * 31 + (if (p.isLight) 1 else 0)
            key = key * 31 + width
            key = key * 31 + height
            entry?.let { if (it.key == key) return it }

            val sw = (width / SCALE).coerceAtLeast(8)
            val sh = (height / SCALE).coerceAtLeast(8)
            val bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            val scene = Scene(p.background, p.accent, p.isLight)
            scene.layout(sw.toFloat(), sh.toFloat())
            scene.draw(Canvas(bmp), sw.toFloat(), sh.toFloat())

            val px = IntArray(sw * sh)
            bmp.getPixels(px, 0, sw, 0, 0, sw, sh)
            val tmp = IntArray(px.size)
            repeat(3) {
                pass(px, tmp, sw, sh, BLUR_RADIUS, true)
                pass(tmp, px, sw, sh, BLUR_RADIUS, false)
            }
            bmp.setPixels(px, 0, sw, 0, 0, sw, sh)
            return Entry(key, bmp).also { entry = it }
        }

        /** One box blur pass along rows ([horizontal]) or columns, edge pixels repeated. The scene is opaque. */
        private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
            val len = if (horizontal) w else h
            val lines = if (horizontal) h else w
            val stride = if (horizontal) 1 else w
            val div = 2 * r + 1
            for (line in 0 until lines) {
                val base = if (horizontal) line * w else line
                var rs = 0
                var gs = 0
                var bs = 0
                for (i in -r..r) {
                    val c = src[base + i.coerceIn(0, len - 1) * stride]
                    rs += (c shr 16) and 255
                    gs += (c shr 8) and 255
                    bs += c and 255
                }
                for (i in 0 until len) {
                    dst[base + i * stride] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                    val add = src[base + (i + r + 1).coerceAtMost(len - 1) * stride]
                    val rem = src[base + (i - r).coerceAtLeast(0) * stride]
                    rs += ((add shr 16) and 255) - ((rem shr 16) and 255)
                    gs += ((add shr 8) and 255) - ((rem shr 8) and 255)
                    bs += (add and 255) - (rem and 255)
                }
            }
        }
    }

    /** The scene as the window background. Crisp at any size: it is drawn as vectors. */
    class Backdrop(background: Int, accent: Int, light: Boolean) : Drawable() {

        private val scene = Scene(background, accent, light)

        override fun onBoundsChange(bounds: Rect) {
            if (!bounds.isEmpty) scene.layout(bounds.width().toFloat(), bounds.height().toFloat())
        }

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            scene.draw(canvas, bounds.width().toFloat(), bounds.height().toFloat())
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.OPAQUE
    }

    // ---------------------------------------------------------------------------------------------
    // One glass surface
    // ---------------------------------------------------------------------------------------------

    /**
     * A glass surface. [tint] colors the glass (a selected chip, a button). [base] is an opaque color
     * under the glass, for a sheet that overlaps other content; a sheet does not sample the scene,
     * because its window is not the activity's. [light] selects the light-theme values.
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
        private val innerRect = RectF()
        private val path = Path()
        private val rimPath = Path()
        private val innerPath = Path()
        private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val samplePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            // Glass drinks the colors behind it: the blurred copy is more saturated than the page.
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(if (light) 1.35f else 1.6f) })
        }
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = strokePx }
        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = strokePx }

        private val sample = base == null && !topOnly
        private val loc = IntArray(2)
        private val src = Rect()
        private val dst = RectF()
        private var host: View? = null
        private val scrollListener = ViewTreeObserver.OnScrollChangedListener { host?.invalidate() }
        private val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { release(v) }
        }

        private fun radii(r: Float) =
            if (topOnly) floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) else floatArrayOf(r, r, r, r, r, r, r, r)

        override fun onBoundsChange(bounds: Rect) {
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

            base?.let { basePaint.color = withAlpha(it, 0.78f) }
            tint?.let { tintPaint.color = withAlpha(it, tintAlpha) }

            // A faint wash only: the blurred scene is the body of the glass. Without a sampled scene
            // (a sheet) the wash is stronger, so the surface still reads.
            val topA = if (light) (if (sample) 0.34f else 0.50f) else (if (sample) 0.07f else 0.14f)
            val botA = if (light) (if (sample) 0.16f else 0.30f) else (if (sample) 0.02f else 0.05f)
            fillPaint.shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                withAlpha(Color.WHITE, topA), withAlpha(Color.WHITE, botA), Shader.TileMode.CLAMP
            )

            // Sheen: light pooling on the upper part of the surface.
            sheenPaint.shader = LinearGradient(
                0f, rect.top, 0f, rect.top + h * 0.45f,
                withAlpha(Color.WHITE, if (light) 0.40f else 0.16f), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )

            // Shade: the lower edge darkens a little, which gives the surface thickness.
            shadePaint.shader = LinearGradient(
                0f, rect.bottom - h * 0.35f, 0f, rect.bottom,
                Color.TRANSPARENT, withAlpha(Color.BLACK, if (light) 0.07f else 0.16f), Shader.TileMode.CLAMP
            )

            // Rim: bright where light enters (top left), faint across, a soft edge at the far corner.
            rimPaint.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    withAlpha(Color.WHITE, if (light) 0.95f else 0.85f),
                    withAlpha(Color.WHITE, if (light) 0.30f else 0.14f),
                    withAlpha(Color.WHITE, if (light) 0.55f else 0.40f)
                ),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            innerPaint.color = withAlpha(Color.WHITE, if (light) 0.30f else 0.07f)
        }

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            if (base != null) canvas.drawPath(path, basePaint)

            val save = canvas.save()
            canvas.clipPath(path)
            if (sample) drawBlurredScene(canvas)
            if (tint != null) canvas.drawPath(path, tintPaint)
            canvas.drawPath(path, fillPaint)
            canvas.drawRect(rect, sheenPaint)
            canvas.drawRect(rect, shadePaint)
            canvas.restoreToCount(save)

            canvas.drawPath(rimPath, rimPaint)
            canvas.drawPath(innerPath, innerPaint)
        }

        /** Draws the blurred scene at this surface's position in its window. */
        private fun drawBlurredScene(canvas: Canvas) {
            val v = hostView() ?: return
            if (!v.isAttachedToWindow) return
            val root = v.rootView
            val rw = root.width
            val rh = root.height
            if (rw <= 0 || rh <= 0) return

            if (v !== host) {
                host?.let { release(it) }
                host = v
                v.viewTreeObserver.addOnScrollChangedListener(scrollListener)
                v.addOnAttachStateChangeListener(attachListener)
            }

            val bmp = BackdropCache.get(v.context, rw, rh).blur
            v.getLocationInWindow(loc)
            val sx = bmp.width.toFloat() / rw
            val sy = bmp.height.toFloat() / rh
            src.set(
                ((loc[0] + bounds.left) * sx).toInt(), ((loc[1] + bounds.top) * sy).toInt(),
                ((loc[0] + bounds.right) * sx).toInt(), ((loc[1] + bounds.bottom) * sy).toInt()
            )
            if (src.width() <= 0 || src.height() <= 0) return
            dst.set(rect)
            canvas.drawBitmap(bmp, src, dst, samplePaint)
        }

        /** The View this drawable is the background of, through any wrapping drawable (a ripple). */
        private fun hostView(): View? {
            var cb: Callback? = callback
            while (cb is Drawable) cb = cb.callback
            return cb as? View
        }

        private fun release(v: View) {
            val vto = v.viewTreeObserver
            if (vto.isAlive) vto.removeOnScrollChangedListener(scrollListener)
            v.removeOnAttachStateChangeListener(attachListener)
            if (host === v) host = null
        }

        override fun getOutline(outline: Outline) {
            val b = bounds
            val r = radiusPx.coerceAtMost(minOf(b.width(), b.height()) / 2f)
            // A top-only sheet reaches below its bounds so the bottom corners stay square.
            outline.setRoundRect(b.left, b.top, b.right, b.bottom + if (topOnly) r.toInt() else 0, r)
        }

        override fun setAlpha(alpha: Int) {
            val a = alpha.coerceIn(0, 255)
            basePaint.alpha = a; tintPaint.alpha = a; fillPaint.alpha = a; samplePaint.alpha = a
            sheenPaint.alpha = a; shadePaint.alpha = a; rimPaint.alpha = a; innerPaint.alpha = a
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            basePaint.colorFilter = colorFilter; tintPaint.colorFilter = colorFilter; fillPaint.colorFilter = colorFilter
            sheenPaint.colorFilter = colorFilter; shadePaint.colorFilter = colorFilter
            rimPaint.colorFilter = colorFilter; innerPaint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
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

    /** The scene for the window background. */
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
