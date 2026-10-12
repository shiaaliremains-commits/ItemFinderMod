package my.itemfinder.client

import kotlin.math.abs
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

        // 1. تحديد مكان انطلاق الليزر:
        // في منظور الشخص الأول ينطلق من جهة اليد اليمنى أسفل الشاشة لتراه بوضوح تام!
        // في منظور الشخص الثالث ينطلق من منتصف الصدر
        val isFirstPerson = mc.options.cameraType.isFirstPerson
        val startPos = if (isFirstPerson) {
            val look = player.lookAngle
            val up = Vec3(0.0, 1.0, 0.0)
            val right = if (abs(look.y) > 0.95) Vec3(1.0, 0.0, 0.0) else look.cross(up).normalize()
            val realUp = right.cross(look).normalize()
            eyePos.add(look.scale(0.35)).add(right.scale(0.24)).subtract(realUp.scale(0.20))
        } else {
            player.position().add(0.0, 1.1, 0.0)
        }

        val rainbowTime = (timeMs % 3500L) / 3500f
        val rgb = rainbow(rainbowTime)
        val pulse = 0.5 + 0.5 * sin(timeMs / 180.0)

        val beamCycle = ((timeMs % 1400L) / 1400.0)
        val pingPong = if (beamCycle < 0.5) beamCycle * 2.0 else (1.0 - beamCycle) * 2.0

        for (h in list) {
            if (h.dim != dim) continue

            val masterAlpha = if (h.isFading) {
                ((h.fadeEnd - now).toFloat() / 25f).coerceIn(0f, 1f)
            } else 1.0f

            if (masterAlpha <= 0f) continue

            val box = AABB(h.pos).inflate(0.015 + 0.025 * pulse)
            val center = box.center

            val strokeAlpha = ((160 + 95 * pulse) * masterAlpha).toInt()
            val fillAlpha = ((35 + 45 * pulse) * masterAlpha).toInt()
            val strokeColor = argb(strokeAlpha, rgb)
            val fillColor = argb(fillAlpha, rgb)

            // 1. صندوق نيون نابض بقوس قزح من وراء الجدران
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(strokeColor, (2.2 + 1.2 * pulse).toFloat(), fillColor)).setAlwaysOnTop()
            Gizmos.point(center, strokeColor, (5.0 + 2.0 * pulse).toFloat()).setAlwaysOnTop()

            // 2. منارة عمودية ناعمة ترتفع من الصندوق مترين للأعلى لتدلك على مكانه
            val beaconTop = center.add(0.0, 2.2, 0.0)
            Gizmos.line(center, beaconTop, argb((130 * masterAlpha).toInt(), rgb), 2.0f).setAlwaysOnTop()
            Gizmos.point(beaconTop, strokeColor, 4.5f).setAlwaysOnTop()

            // 3. خط الليزر الأساسي (مزدوج الطبقات: غلاف ملون + قلب أبيض مشع)
            val outerColor = argb((70 * masterAlpha).toInt(), rgb)
            val coreColor = argb((180 * masterAlpha).toInt(), 0xFFFFFF)
            Gizmos.line(startPos, center, outerColor, 3.2f).setAlwaysOnTop()
            Gizmos.line(startPos, center, coreColor, 1.2f).setAlwaysOnTop()

            // 4. نبضة الليزر المنطلقة ذهاباً وإياباً (Ping-Pong)
            val pulseHead = lerp(startPos, center, pingPong)
            val trailDist = 0.15
            val trailT = (pingPong - (if (beamCycle < 0.5) trailDist else -trailDist)).coerceIn(0.0, 1.0)
            val pulseTail = lerp(startPos, center, trailT)

            val pulseOuter = argb((240 * masterAlpha).toInt(), rgb)
            val pulseCore = argb((255 * masterAlpha).toInt(), 0xFFFFFF)
            Gizmos.line(pulseHead, pulseTail, pulseOuter, 4.5f).setAlwaysOnTop()
            Gizmos.line(pulseHead, pulseTail, pulseCore, 2.0f).setAlwaysOnTop()
            Gizmos.point(pulseHead, pulseOuter, 6.5f).setAlwaysOnTop()
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
