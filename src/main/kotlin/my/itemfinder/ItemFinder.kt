package my.itemfinder

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import org.slf4j.LoggerFactory

object ItemFinder : ModInitializer {
    const val MOD_ID: String = "itemfinder"
    private val LOGGER = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        // تسجيل حزم النتورك
        PayloadTypeRegistry.serverboundPlay().register(RequestChestsPayload.TYPE, RequestChestsPayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(SyncChestsPayload.TYPE, SyncChestsPayload.CODEC)

        // فحص كل الصناديق المحملة حول اللاعب فور طلب البحث
        ServerPlayNetworking.registerGlobalReceiver(RequestChestsPayload.TYPE) { _, context ->
            val player = context.player()
            scanAllLoadedChests(player)
        }

        LOGGER.info("ItemFinder Server Scanner initialized.")
    }

    private fun scanAllLoadedChests(player: ServerPlayer) {
        val level = player.serverLevel()
        val chunkX = player.blockX shr 4
        val chunkZ = player.blockZ shr 4
        val radius = 8 // فحص دائرة 8 شنكات حول اللاعب = 256 بلوكة!

        val results = ArrayList<SyncBoxData>()

        for (cx in (chunkX - radius)..(chunkX + radius)) {
            for (cz in (chunkZ - radius)..(chunkZ + radius)) {
                val chunk = level.chunkSource.getChunkNow(cx, cz) ?: continue
                for ((pos, blockEntity) in chunk.blockEntities) {
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

        // إرسال كل الصناديق ومحتوياتها للكلاينت فوراً
        ServerPlayNetworking.send(player, SyncChestsPayload(results))
    }

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
