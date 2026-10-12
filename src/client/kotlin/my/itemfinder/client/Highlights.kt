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
                list.add(H(b.dim, s, now, now + 1200)) // 60 ثانية
            }
        }
    }

    fun fadeOut(spots: List<BlockPos>) {
        for (h in list) {
            if (h.pos in spots && !h.isFading) {
                h.isFading = true
                h.fadeStart = now
                h.fadeEnd = now + 25 // تلاشي ناعم لمدة ثانية ونصف
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

        // مكان انطلاق المكعب المرشد (أمام اللاعب مباشرة ليكون مرئياً بالكامل بمنظور الشخص الأول)
        val isFirstPerson = mc.options.cameraType.isFirstPerson
        val startPos = if (isFirstPerson) {
            val look = player.lookAngle
            val up = Vec3(0.0, 1.0, 0.0)
            val right = if (abs(look.y) > 0.95) Vec3(1.0, 0.0, 0.0) else look.cross(up).normalize()
            val realUp = right.cross(look).normalize()
            eyePos.add(look.scale(0.5)).add(right.scale(0.18)).subtract(realUp.scale(0.15))
        } else {
            player.position().add(0.0, 1.1, 0.0)
        }

        val rainbowTime = (timeMs % 3500L) / 3500f
        val rgb = rainbow(rainbowTime)
        val pulse = 0.5 + 0.5 * sin(timeMs / 180.0)

        // أنيميشن طيران وسفر المكعب المرشد (دورة كاملة كل ثانيتين)
        val travelDuration = 1800.0
        val rawProgress = (timeMs % travelDuration) / travelDuration
        // حركة Smoothstep لانطلاق ووصول ناعم جداً
        val progress = rawProgress * rawProgress * (3.0 - 2.0 * rawProgress)

        for (h in list) {
            if (h.dim != dim) continue

            val masterAlpha = if (h.isFading) {
                ((h.fadeEnd - now).toFloat() / 25f).coerceIn(0f, 1f)
            } else 1.0f

            if (masterAlpha <= 0f) continue

            val box = AABB(h.pos).inflate(0.015 + 0.02 * pulse)
            val center = box.center

            val strokeAlpha = ((160 + 95 * pulse) * masterAlpha).toInt()
            val fillAlpha = ((35 + 40 * pulse) * masterAlpha).toInt()
            val strokeColor = argb(strokeAlpha, rgb)
            val fillColor = argb(fillAlpha, rgb)

            // 1. صندوق نيون نابض بقوس قزح على الصندوق الهدف نفسه يظهر من وراء الجدران
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(strokeColor, (2.2 + 1.2 * pulse).toFloat(), fillColor)).setAlwaysOnTop()
            Gizmos.point(center, strokeColor, (5.0 + 2.0 * pulse).toFloat()).setAlwaysOnTop()

            // 2. المكعب المرشد المنطلق (The Guiding Cube) الذي يسافر من عندك للصندوق
            val currentGuidePos = lerp(startPos, center, progress)

            // حجم المكعب المرشد وهو يطير
            val guideSize = 0.22 + 0.06 * sin(timeMs / 100.0)
            val guideBox = AABB(
                currentGuidePos.x - guideSize, currentGuidePos.y - guideSize, currentGuidePos.z - guideSize,
                currentGuidePos.x + guideSize, currentGuidePos.y + guideSize, currentGuidePos.z + guideSize
            )

            val guideAlpha = (220 * masterAlpha).toInt()
            val guideColor = argb(guideAlpha, rgb)
            val guideFill = argb((60 * masterAlpha).toInt(), rgb)

            // رسم المكعب المرشد المنطلق بالهواء باتجاه الصندوق
            Gizmos.cuboid(guideBox, GizmoStyle.strokeAndFill(guideColor, 2.5f, guideFill)).setAlwaysOnTop()
            Gizmos.point(currentGuidePos, guideColor, 6.0f).setAlwaysOnTop()

            // 3. ذيل ناعم وخافت يوضح المسار خلف المكعب المرشد
            val tailProgress = (progress - 0.12).coerceAtLeast(0.0)
            val tailPos = lerp(startPos, center, tailProgress)
            val trailAlpha = (75 * masterAlpha).toInt()
            Gizmos.line(tailPos, currentGuidePos, argb(trailAlpha, rgb), 2.2f).setAlwaysOnTop()
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

        // تشغيل الـ Fade-out التلقائي فور فتح الصندوق
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
