package my.itemfinder.client

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.level.Level

object Highlights {
    class H(
        val dim: String, val pos: BlockPos,
        val sx: Double, val sy: Double, val sz: Double,
        val start: Int, val end: Int
    )

    val list = ArrayList<H>()
    var now = 0

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
            if (now % 6 == 0) {
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
            if (now % 3 == 0) {
                for (k in 0..14) {
                    p(level, cx, y0 + 1.1 + k * 0.5, cz)
                }
            }
        }
    }
}
