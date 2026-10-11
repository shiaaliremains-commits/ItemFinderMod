package my.itemfinder.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionResult

object ItemFinderClient : ClientModInitializer {

    private fun show(mc: Minecraft, s: Screen) {
        val methods = Minecraft::class.java.methods
        val m = methods.firstOrNull { it.name == "setScreenAndShow" && it.parameterCount == 1 }
            ?: methods.first { it.name == "setScreen" && it.parameterCount == 1 }
        try { m.invoke(mc, s) } catch (t: Throwable) { DebugLog.error("open screen", t) }
    }

    override fun onInitializeClient() {
        val cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("itemfinder", "main"))
        val key = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.itemfinder.open", InputConstants.KEY_K, cat)
        )

        UseBlockCallback.EVENT.register(UseBlockCallback { _, world, _, hit ->
            if (world.isClientSide) {
                Store.lastPos = hit.blockPos
                Store.lastTick = Highlights.now
            }
            InteractionResult.PASS
        })

        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { client, screen, _, _ ->
            if (screen is AbstractContainerScreen<*>) {
                val pos = Store.lastPos
                Store.lastPos = null
                val level = client.level
                if (pos != null && level != null) {
                    Highlights.removeAt(Store.partners(level, pos))
                }
                val menu = screen.menu
                ScreenEvents.remove(screen).register(ScreenEvents.Remove {
                    Store.capture(client, menu, pos)
                })
            }
        })

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            Highlights.tick(mc)
            val fs = FinderPending.screen
            if (fs != null) {
                FinderPending.screen = null
                fs.commit()
            }
            while (key.consumeClick()) {
                if (mc.level != null) {
                    show(mc, FinderScreen())
                }
            }
        })
    }
}
