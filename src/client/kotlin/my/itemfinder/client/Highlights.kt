package my.itemfinder.client

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.level.Level

object Highlights {
    class H(
        val dim: String, val pos: BlockPos,
        val sx: Double, val sy: Double, val sz: Double,
        val start: Int, val end: Int
    )

    val list = ArrayList<H>()
    var now = 0
    private var gizmoFailed = false

    fun add(mc: Minecraft, boxes: List<Box>) {
        val player = mc.player ?: return
        val e = player.getEyePosition()
        list.clear()
        for (b in boxes) {
            for (s in b.spots) {
                list.add(H(b.dim, s, e.x, e.y - 0.4, e.z, now, now + 600))
            }
        }
    }

    fun removeAt(spots: List<BlockPos>) {
        list.removeIf { h -> h.pos in spots }
    }

    private fun p(level: Level, x: Double, y: Double, z: Double) {
        level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.0, 0.0)
    }

    private fun rainbow(t: Float): Int {
        val h = t * 6f
        val i = h.toInt()
        val f = h - i
        val q = (255 * (1f - f)).toInt()
        val u = (255 * f).toInt()
        val r: Int
        val g: Int
        val b: Int
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

    fun render() {
        if (gizmoFailed) return
        val mc = Minecraft.getInstance()
        if (mc.level == null || list.isEmpty()) return
        try {
            val dim = Store.dimOf(mc)
            val t = (System.currentTimeMillis() % 3000L) / 3000f
            val rgb = rainbow(t)
            val stroke = (0xFF shl 24) or rgb
            val fill = (0x40 shl 24) or rgb
            for (h in list) {
                if (h.dim != dim) continue
                Gizmos.cuboid(h.pos, GizmoStyle.strokeAndFill(stroke, 3.0f, fill)).setAlwaysOnTop()
            }
        } catch (t: Throwable) {
            gizmoFailed = true
            DebugLog.error("gizmo render", t)
        }
    }

    fun tick(mc: Minecraft) {
        now++
        val level = mc.level
        if (level == null || mc.player == null) {
            list.clear()
            return
        }
        list.removeIf { h -> now > h.end }
        val dim = Store.dimOf(mc)
        for (h in list) {
            if (h.dim != dim) continue
            val x0 = h.pos.x.toDouble()
            val y0 = h.pos.y.toDouble()
            val z0 = h.pos.z.toDouble()
            val cx = x0 + 0.5
            val cy = y0 + 0.5
            val cz = z0 + 0.5
            val age = now - h.start
            if (age <= 30) {
                val t = age / 30.0
                val e = t * t * (3.0 - 2.0 * t)
                for (i in 0..4) {
                    val tt = (e - i * 0.02).coerceAtLeast(0.0)
                    val x = h.sx + (cx - h.sx) * tt + (Math.random() - 0.5) * 0.12
                    val y = h.sy + (cy - h.sy) * tt + (Math.random() - 0.5) * 0.12
                    val z = h.sz + (cz - h.sz) * tt + (Math.random() - 0.5) * 0.12
                    p(level, x, y, z)
                }
            }
            if (gizmoFailed && now % 6 == 0) {
                for (i in 0..4) {
                    val t = i / 4.0
                    for (a in 0..1) {
                        for (b in 0..1) {
                            p(level, x0 + t, y0 + a, z0 + b)
                            p(level, x0 + a, y0 + t, z0 + b)
                            p(level, x0 + a, y0 + b, z0 + t)
                        }
                    }
                }
            }
        }
    }
}
