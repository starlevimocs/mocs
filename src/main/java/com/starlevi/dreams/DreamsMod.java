package com.starlevi.dreams;

import com.mojang.brigadier.CommandDispatcher;
import com.starlevi.dreams.network.DreamsNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;

@Mod(DreamsMod.MOD_ID)
public class DreamsMod {
    public static final String MOD_ID = "dreams";

    public DreamsMod() {
        DreamsNetwork.register();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }

    /**
     * /dreams dialogue <id>              abre el dialogo al que ejecuta el comando
     * /dreams dialogue <id> <jugadores>  abre el dialogo a los jugadores indicados
     */
    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dreams")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("dialogue")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(ctx -> {
                                    ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    DreamsNetwork.openDialogue(player, id);
                                    return 1;
                                })
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> {
                                            ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                            for (ServerPlayer player : players) {
                                                DreamsNetwork.openDialogue(player, id);
                                            }
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Dialogo " + id + " abierto a " + players.size() + " jugador(es)."),
                                                    true);
                                            return players.size();
                                        })))));
    }
}
