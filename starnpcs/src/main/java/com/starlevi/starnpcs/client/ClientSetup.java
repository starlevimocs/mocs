package com.starlevi.starnpcs.client;

import com.starlevi.starnpcs.NpcEntity;
import com.starlevi.starnpcs.StarNpcs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StarNpcs.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(StarNpcs.NPC.get(), NpcRenderer::new);
        NpcEntity.clientTick = ClientSetup::tick;
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientAssets.reset());
    }

    private static void tick(NpcEntity npc) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(npc.level() instanceof ClientLevel level) || mc.isPaused()) {
            return;
        }
        if (npc.distanceToSqr(mc.player) > 40 * 40) {
            return;
        }
        var p = npc.profile();
        if (!p.starBones) {
            return;
        }
        BbModel m = ClientAssets.model(NpcRenderer.modelId(p));
        if (m != null) {
            NpcAnim.of(npc).tickFx(npc, m, level);
        }
    }
}
