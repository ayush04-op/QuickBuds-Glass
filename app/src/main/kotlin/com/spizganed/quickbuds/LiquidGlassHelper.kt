package com.spizganed.quickbuds

import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider

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

    /**
     * Styles any card, button, or pill bar with the dark liquid glass surface & specular rim.
     */
    fun applyGlassPill(view: View, cornerRadiusDp: Float = 36f) {
        val density = view.resources.displayMetrics.density
        val radiusPx = cornerRadiusDp * density

        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, radiusPx)
            }
        }

        val glassDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(120, 42, 30, 24), // Warm top-left tint
                Color.argb(105, 18, 16, 16), // Deep smoked center
                Color.argb(125, 48, 34, 28)  // Warm bottom-right tint
            )
        ).apply {
            cornerRadius = radiusPx
            // 1dp bright white specular border
            setStroke((1.2f * density).toInt().coerceAtLeast(2), Color.argb(115, 255, 255, 255))
        }
        view.background = glassDrawable
    }

    /**
     * Automatically walks through a container and applies the glass pill style to child cards.
     */
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
