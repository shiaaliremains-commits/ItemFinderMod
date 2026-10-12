package my.itemfinder

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.level.chunk.LevelChunk
import org.slf4j.LoggerFactory

object ItemFinder : ModInitializer {
    const val MOD_ID: String = "itemfinder"
    private val LOGGER = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(RequestChestsPayload.TYPE, RequestChestsPayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(SyncChestsPayload.TYPE, SyncChestsPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(RequestChestsPayload.TYPE) { _, context ->
            val player = context.player()
            scanLocalLoadedChests(player)
        }

        LOGGER.info("ItemFinder Local Scanner initialized.")
    }

    // فحص الشنكات القريبة فقط (نطاق قاعدتك 70 إلى 80 بلوكة)
    private fun scanLocalLoadedChests(player: ServerPlayer) {
        val level = player.level() as? ServerLevel ?: return
        val chunkX = player.blockX shr 4
        val chunkZ = player.blockZ shr 4
        val radius = 4 // 4 شنكات فقط = نطاق محلي مريح وسريع 0ms

        val results = ArrayList<SyncBoxData>()

        for (cx in (chunkX - radius)..(chunkX + radius)) {
            for (cz in (chunkZ - radius)..(chunkZ + radius)) {
                val chunk = level.chunkSource.getChunkNow(cx, cz) as? LevelChunk ?: continue
                for (pos in chunk.blockEntitiesPos) {
                    val blockEntity = level.getBlockEntity(pos) ?: continue
                    if (blockEntity is Container) {
                        val itemsMap = HashMap<String, Int>()
                        for (i in 0 until blockEntity.containerSize) {
                            val stack = blockEntity.getItem(i)
                            if (!stack.isEmpty) {
                                val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
                                itemsMap[id] = itemsMap.getOrDefault(id, 0) + stack.count
                            }
                        }
                        if (itemsMap.isNotEmpty()) {
                            results.add(SyncBoxData(pos, itemsMap))
                        }
                    }
                }
            }
        }

        ServerPlayNetworking.send(player, SyncChestsPayload(results))
    }

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
