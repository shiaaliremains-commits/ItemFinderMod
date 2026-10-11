package my.itemfinder.client

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ShulkerBoxMenu
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.state.properties.ChestType

class Box(val dim: String, val spots: List<BlockPos>, val items: Map<Item, Int>)

object Store {
    val boxes = ArrayList<Box>()
    var lastPos: BlockPos? = null
    var lastTick: Int = 0

    fun dimOf(mc: Minecraft): String {
        val lvl = mc.level ?: return ""
        return lvl.dimension().toString()
    }

    fun partners(level: Level, pos: BlockPos): List<BlockPos> {
        val st = level.getBlockState(pos)
        if (st.block is ChestBlock && st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return listOf(pos, pos.relative(ChestBlock.getConnectedDirection(st)))
        }
        return listOf(pos)
    }

    fun capture(mc: Minecraft, menu: AbstractContainerMenu, pos: BlockPos?) {
        if (pos == null) return
        if (Highlights.now - lastTick > 100) return
        val level = mc.level ?: return
        if (menu !is ChestMenu && menu !is ShulkerBoxMenu) return
        val be = level.getBlockEntity(pos)
        if (be !is Container) return
        val totals = LinkedHashMap<Item, Int>()
        for (slot in menu.slots) {
            if (slot.container is Inventory) continue
            val st = slot.item
            if (!st.isEmpty) {
                totals.merge(st.item, st.count) { a, b -> a + b }
            }
        }
        val spots = partners(level, pos)
        val dim = dimOf(mc)
        boxes.removeIf { b -> b.dim == dim && b.spots.any { s -> s in spots } }
        boxes.add(Box(dim, spots, totals))
    }
}
