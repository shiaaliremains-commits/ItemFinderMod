package my.itemfinder.client

import kotlin.math.sin
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object Highlights {
    class H(
        val dim: String,
        val pos: BlockPos,
        val start: Int,
        val end: Int,
        var isFading: Boolean = false,
        var fadeStart: Int = 0,
        var fadeEnd: Int = 0
    )

    val list = ArrayList<H>()
    var now = 0

    fun add(mc: Minecraft, boxes: List<Box>) {
        list.clear()
        for (b in boxes) {
            for (s in b.spots) {
                list.add(H(b.dim, s, now, now + 1200))
            }
        }
    }

    fun fadeOut(spots: List<BlockPos>) {
        for (h in list) {
            if (h.pos in spots && !h.isFading) {
                h.isFading = true
                h.fadeStart = now
                h.fadeEnd = now + 25
            }
        }
    }

    fun removeAt(spots: List<BlockPos>) {
        fadeOut(spots)
    }

    private fun argb(alpha: Int, rgb: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    private fun rainbow(t: Float): Int {
        val h = (t * 6f) % 6f
        val i = h.toInt()
        val f = h - i
        val q = (255 * (1f - f)).toInt()
        val u = (255 * f).toInt()
        val r: Int; val g: Int; val b: Int
        when (i % 6) {
            0 -> { r = 255; g = u; b = 0 }
            1 -> { r = q; g = 255; b = 0 }
            2 -> { r = 0; g = 255; b = u }
            3 -> { r = 0; g = q; b = 255 }
            4 -> { r = u; g = 0; b = 255 }
            else -> { r = 255; g = 0; b = q }
        }
        return (r shl 16) or (g shl 8) or b
    }

    private fun lerp(a: Vec3, b: Vec3, t: Double): Vec3 =
        Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)

    fun render() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (mc.level == null || list.isEmpty()) return

        val dim = Store.dimOf(mc)
        val eyePos = player.eyePosition
        val timeMs = System.currentTimeMillis()

        val rainbowTime = (timeMs % 3500L) / 3500f
        val rgb = rainbow(rainbowTime)
        val pulse = 0.5 + 0.5 * sin(timeMs / 180.0)

        val beamCycle = ((timeMs % 1500L) / 1500.0)
        val pingPong = if (beamCycle < 0.5) beamCycle * 2.0 else (1.0 - beamCycle) * 2.0

        for (h in list) {
            if (h.dim != dim) continue

            val masterAlpha = if (h.isFading) {
                ((h.fadeEnd - now).toFloat() / 25f).coerceIn(0f, 1f)
            } else 1.0f

            if (masterAlpha <= 0f) continue

            val box = AABB(h.pos).inflate(0.015 + 0.02 * pulse)
            val center = box.center

            val strokeAlpha = ((150 + 105 * pulse) * masterAlpha).toInt()
            val fillAlpha = ((35 + 40 * pulse) * masterAlpha).toInt()
            val strokeColor = argb(strokeAlpha, rgb)
            val fillColor = argb(fillAlpha, rgb)

            // 1. صندوق نيون نابض بقوس قزح من وراء الجدران
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(strokeColor, (2.2 + 1.2 * pulse).toFloat(), fillColor)).setAlwaysOnTop()
            Gizmos.point(center, strokeColor, (5.0 + 2.0 * pulse).toFloat()).setAlwaysOnTop()

            // 2. خط نيون ناعم
            val lineBaseAlpha = (50 * masterAlpha).toInt()
            Gizmos.line(eyePos, center, argb(lineBaseAlpha, rgb), 1.2f).setAlwaysOnTop()

            // 3. نبضة ليزر تسافر ذهاباً وإياباً
            val pulseHead = lerp(eyePos, center, pingPong)
            val trailDist = 0.12
            val trailT = (pingPong - (if (beamCycle < 0.5) trailDist else -trailDist)).coerceIn(0.0, 1.0)
            val pulseTail = lerp(eyePos, center, trailT)

            val pulseColor = argb((235 * masterAlpha).toInt(), rgb)
            Gizmos.line(pulseHead, pulseTail, pulseColor, 3.0f).setAlwaysOnTop()
            Gizmos.point(pulseHead, pulseColor, 5.5f).setAlwaysOnTop()
        }
    }

    fun tick(mc: Minecraft) {
        now++
        val player = mc.player
        val level = mc.level
        if (level == null || player == null) {
            list.clear()
            return
        }

        // تشغيل الـ Fade-out فور فتح الصندوق بأمان وبدون استخدام mc.screen المفقودة
        if (player.containerMenu != player.inventoryMenu) {
            val pPos = player.blockPosition()
            for (h in list) {
                if (!h.isFading && h.pos.closerThan(pPos, 4.5)) {
                    fadeOut(listOf(h.pos))
                }
            }
        }

        list.removeIf { h -> (h.isFading && now >= h.fadeEnd) || (!h.isFading && now > h.end) }
    }
}
