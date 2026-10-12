package my.itemfinder.client

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
        val end: Int
    )

    val list = ArrayList<H>()
    var now = 0

    fun add(mc: Minecraft, boxes: List<Box>) {
        list.clear()
        for (b in boxes) {
            for (s in b.spots) {
                list.add(H(b.dim, s, now, now + 600)) // يستمر التحديد لـ 30 ثانية
            }
        }
    }

    fun removeAt(spots: List<BlockPos>) {
        list.removeIf { h -> h.pos in spots }
    }

    // لون نيون ذهبي واضح جداً من وراء الجدران
    private const val NEON_GOLD = 0xFFFFD700.toInt()
    private const val FILL_COLOR = 0x44FFD700.toInt()
    private const val LASER_COLOR = 0xAA00E5FF.toInt()

    fun render() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (mc.level == null || list.isEmpty()) return

        val dim = Store.dimOf(mc)
        val eyePos = player.eyePosition

        for (h in list) {
            if (h.dim != dim) continue

            val box = AABB(h.pos)
            val center = box.center

            // 1. إطار صندوق نيون متوهج ناعم يحيط بالصندوق ويظهر من وراء الجدران
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(NEON_GOLD, 2.5f, FILL_COLOR)).setAlwaysOnTop()

            // 2. خط ليزر نيون ناعم ومستقيم يصل بين نظرك والصندوق بدال رذاذ البارتكلز المشوه
            Gizmos.line(eyePos, center, LASER_COLOR, 1.8f).setAlwaysOnTop()

            // 3. لمعة متوهجة بمنتصف الصندوق
            Gizmos.point(center, NEON_GOLD, 6.0f).setAlwaysOnTop()
        }
    }

    fun tick(mc: Minecraft) {
        now++
        if (mc.level == null || mc.player == null) {
            list.clear()
            return
        }
        list.removeIf { h -> now > h.end }
    }
}
