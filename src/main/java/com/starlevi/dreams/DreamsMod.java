package com.starlevi.dreams;

import com.mojang.brigadier.CommandDispatcher;
import com.starlevi.dreams.cinematic.CinematicCommands;
import com.starlevi.dreams.item.ModItems;
import com.starlevi.dreams.network.DreamsNetwork;
import com.starlevi.dreams.network.SpawnNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.Collection;

@Mod(DreamsMod.MOD_ID)
public class DreamsMod {
    public static final String MOD_ID = "dreams";

    public DreamsMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        modBus.addListener(this::addCreativeItems);

        DreamsNetwork.register();
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.SPAWN_WAND);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> questCommand(
            String name, java.util.function.BiConsumer<ServerPlayer, String> action) {
        com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> ids = (c, b) ->
                net.minecraft.commands.SharedSuggestionProvider.suggest(com.starlevi.dreams.quest.Quests.defs().keySet(), b);
        return Commands.literal(name).then(Commands.argument("quest", com.mojang.brigadier.arguments.StringArgumentType.word()).suggests(ids)
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    action.accept(p, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "quest"));
                    com.starlevi.dreams.quest.Quests.sync(p);
                    return 1;
                })
                .then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> {
                    String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "quest");
                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                    for (ServerPlayer p : players) {
                        action.accept(p, id);
                        com.starlevi.dreams.quest.Quests.sync(p);
                    }
                    return players.size();
                })));
    }

    /**
     * /dreams quest reload|list|start|complete|reset   misiones
     * /dreams dialogue &lt;id&gt; [jugadores]   abre un dialogo
     * /dreams spawn                       abre el menu de control de spawn
     * /dreams spawn wand                  te da la varita de spawn
     * /dreams cinematic ...              crea y reproduce cinematicas (ver el README)
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
                                        }))))
                .then(Commands.literal("spawn")
                        .executes(ctx -> {
                            SpawnNet.sendSync(ctx.getSource().getPlayerOrException(), true);
                            return 1;
                        })
                        .then(Commands.literal("wand")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ItemStack wand = new ItemStack(ModItems.SPAWN_WAND.get());
                                    if (!player.getInventory().add(wand)) {
                                        player.drop(wand, false);
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("quest")
                        .then(Commands.literal("reload").executes(ctx -> {
                            com.starlevi.dreams.quest.Quests.reload();
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.dreams.quests_reloaded",
                                    com.starlevi.dreams.quest.Quests.defs().size()), true);
                            return 1;
                        }))
                        .then(Commands.literal("list").executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(String.join(", ", com.starlevi.dreams.quest.Quests.defs().keySet())), false);
                            return 1;
                        }))
                        .then(questCommand("start", (p, id) -> com.starlevi.dreams.quest.Quests.accept(p, id)))
                        .then(questCommand("complete", (p, id) -> com.starlevi.dreams.quest.Quests.forceComplete(p, id)))
                        .then(questCommand("reset", (p, id) -> com.starlevi.dreams.quest.Quests.reset(p, id))))
                .then(CinematicCommands.build()));
    }
}
