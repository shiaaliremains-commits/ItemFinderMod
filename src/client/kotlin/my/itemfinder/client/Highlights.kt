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
        val startPos: Vec3,
        val startTimeMs: Long,
        val start: Int,
        val travelDurationMs: Long = 2800L,
        var isFading: Boolean = false,
        var fadeStart: Int = 0,
        var fadeEnd: Int = 0
    )

    val list = ArrayList<H>()
    var now = 0

    fun add(mc: Minecraft, boxes: List<Box>) {
        list.clear()
        val player = mc.player ?: return
        val eyePos = player.eyePosition
        val look = player.lookAngle
        val launchPos = eyePos.add(look.scale(0.65)).subtract(0.0, 0.2, 0.0)
        val nowMs = System.currentTimeMillis()

        for (b in boxes) {
            for (s in b.spots) {
                list.add(H(b.dim, s, launchPos, nowMs, now))
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
        if (mc.level == null || list.isEmpty()) return

        val dim = Store.dimOf(mc)
        val timeMs = System.currentTimeMillis()

        val rainbowTime = (timeMs % 3500L) / 3500f
        val rgb = rainbow(rainbowTime)
        val pulse = 0.5 + 0.5 * sin(timeMs / 180.0)

        for (h in list) {
            if (h.dim != dim) continue

            val masterAlpha = if (h.isFading) {
                ((h.fadeEnd - now).toFloat() / 25f).coerceIn(0f, 1f)
            } else 1.0f

            if (masterAlpha <= 0f) continue

            val destAABB = AABB(h.pos)
            val destCenter = destAABB.center

            val elapsed = timeMs - h.startTimeMs
            val rawProgress = (elapsed.toDouble() / h.travelDurationMs.toDouble()).coerceIn(0.0, 1.0)

            val t = 1.0 - Math.pow(1.0 - rawProgress, 3.0)

            if (rawProgress < 1.0) {
                val curCenter = lerp(h.startPos, destCenter, t)

                val currentSize = 0.40 + (0.62 * t)
                val half = currentSize / 2.0
                val travelBox = AABB(
                    curCenter.x - half, curCenter.y - half, curCenter.z - half,
                    curCenter.x + half, curCenter.y + half, curCenter.z + half
                )

                val strokeAlpha = ((190 + 65 * pulse) * masterAlpha).toInt()
                val fillAlpha = ((40 + 30 * pulse) * masterAlpha).toInt()
                val strokeColor = argb(strokeAlpha, rgb)
                val fillColor = argb(fillAlpha, rgb)

                Gizmos.cuboid(travelBox, GizmoStyle.strokeAndFill(strokeColor, 2.5f, fillColor)).setAlwaysOnTop()
                Gizmos.point(curCenter, strokeColor, 6.0f).setAlwaysOnTop()

                val trailT = (t - 0.08).coerceAtLeast(0.0)
                val trailCenter = lerp(h.startPos, destCenter, trailT)
                Gizmos.line(trailCenter, curCenter, argb((85 * masterAlpha).toInt(), rgb), 2.2f).setAlwaysOnTop()
            } else {
                val timeSinceArrival = elapsed - h.travelDurationMs

                val arrivalShockwave = if (timeSinceArrival < 500) {
                    (1.0 - (timeSinceArrival / 500.0)) * 0.12
                } else 0.0

                val settledBox = destAABB.inflate(0.02 + 0.025 * pulse + arrivalShockwave)

                val strokeAlpha = ((160 + 95 * pulse) * masterAlpha).toInt()
                val fillAlpha = ((35 + 40 * pulse) * masterAlpha).toInt()
                val strokeColor = argb(strokeAlpha, rgb)
                val fillColor = argb(fillAlpha, rgb)

                Gizmos.cuboid(settledBox, GizmoStyle.strokeAndFill(strokeColor, (2.2 + 1.2 * pulse).toFloat(), fillColor)).setAlwaysOnTop()
                Gizmos.point(destCenter, strokeColor, (5.5 + 2.0 * pulse).toFloat()).setAlwaysOnTop()
            }
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

        list.removeIf { h -> (h.isFading && now >= h.fadeEnd) || (!h.isFading && now > (h.start + 1200)) }
    }
}
