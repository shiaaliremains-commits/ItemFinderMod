package my.itemfinder.client

import com.mojang.blaze3d.platform.InputConstants
import my.itemfinder.ItemFinder
import my.itemfinder.SyncChestsPayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.Identifier

object ItemFinderClient : ClientModInitializer {
    private lateinit var findKey: KeyMapping

    override fun onInitializeClient() {
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ItemFinder.MOD_ID, "main"))
        findKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.itemfinder.find", InputConstants.KEY_Y, category)
        )

        ClientPlayNetworking.registerGlobalReceiver(SyncChestsPayload.TYPE) { payload, context ->
            val mc = context.client()
            Store.updateFromNetwork(mc, payload.boxes)
            val scr = getCurrentScreen(mc)
            if (scr is FinderScreen) {
                scr.refreshFromNetwork()
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (findKey.consumeClick()) {
                if (client.player != null) {
                    openScreen(FinderScreen())
                }
            }

            Highlights.tick(client)
            Highlights.render()
        }
    }

    fun getCurrentScreen(mc: Minecraft): Screen? {
        val method = mc.javaClass.methods.firstOrNull {
            (it.name == "screen" || it.name == "getScreen") && it.parameterCount == 0 && Screen::class.java.isAssignableFrom(it.returnType)
        }
        if (method != null) {
            return runCatching { method.invoke(mc) as? Screen }.getOrNull()
        }
        val field = mc.javaClass.fields.firstOrNull {
            Screen::class.java.isAssignableFrom(it.type)
        } ?: mc.javaClass.declaredFields.firstOrNull {
            Screen::class.java.isAssignableFrom(it.type)
        }
        field?.isAccessible = true
        return runCatching { field?.get(mc) as? Screen }.getOrNull()
    }

    fun openScreen(screen: Screen?) {
        val mc = Minecraft.getInstance()
        val gui = runCatching { mc.javaClass.getField("gui").get(mc) }.getOrNull()
        if (!invokeSetScreen(gui, screen)) invokeSetScreen(mc, screen)
    }

    private fun invokeSetScreen(target: Any?, screen: Screen?): Boolean {
        if (target == null) return false
        val method = target.javaClass.methods.firstOrNull {
            (it.name == "setScreen" || it.name == "setScreenAndShow") && it.parameterCount == 1
        } ?: return false
        method.invoke(target, screen)
        return true
    }
}
