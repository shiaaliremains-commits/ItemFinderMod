package my.itemfinder

import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

class SyncBoxData(val pos: BlockPos, val items: Map<String, Int>)

class RequestChestsPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<RequestChestsPayload> =
            CustomPacketPayload.Type(ItemFinder.id("request_chests"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestChestsPayload> =
            StreamCodec.unit(RequestChestsPayload())
    }
}

class SyncChestsPayload(val boxes: List<SyncBoxData>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<SyncChestsPayload> =
            CustomPacketPayload.Type(ItemFinder.id("sync_chests"))

        val BOX_CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncBoxData> = StreamCodec.composite(
            BlockPos.STREAM_CODEC, { p: SyncBoxData -> p.pos },
            ByteBufCodecs.map(::HashMap, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), { p: SyncBoxData -> p.items },
            { pos, items -> SyncBoxData(pos, items) }
        )

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncChestsPayload> = StreamCodec.composite(
            ByteBufCodecs.collection(::ArrayList, BOX_CODEC), { p: SyncChestsPayload -> p.boxes },
            { boxes -> SyncChestsPayload(boxes) }
        )
    }
}
