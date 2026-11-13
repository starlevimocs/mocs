package com.starlevi.starnpcs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /starnpcs (o /snpc): todo lo que hace el editor, y algo mas, por comando. Actua sobre el NPC mas cercano. */
public final class NpcCommands {
    private static final SimpleCommandExceptionType NO_NPC = new SimpleCommandExceptionType(Component.translatable("starnpcs.msg.no_npc"));

    private NpcCommands() {
    }

    private interface NpcAction {
        int run(CommandContext<CommandSourceStack> ctx, NpcEntity npc) throws CommandSyntaxException;
    }

    private static com.mojang.brigadier.Command<CommandSourceStack> on(NpcAction action) {
        return ctx -> action.run(ctx, nearest(ctx.getSource()));
    }

    private static NpcEntity nearest(CommandSourceStack source) throws CommandSyntaxException {
        if (source.getEntity() instanceof NpcEntity self) {
            return self;
        }
        Vec3 at = source.getPosition();
        List<NpcEntity> list = source.getLevel().getEntitiesOfClass(NpcEntity.class, AABB.ofSize(at, 24, 24, 24));
        return list.stream().min(Comparator.comparingDouble(n -> n.distanceToSqr(at))).orElseThrow(NO_NPC::create);
    }

    private static void ok(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
        ctx.getSource().sendSuccess(() -> Component.translatable(key, args), false);
    }

    private static int edit(NpcEntity npc, java.util.function.Consumer<Profile> change) {
        Profile p = npc.profile().copy();
        change.accept(p);
        npc.setProfile(p);
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(build("starnpcs"));
        dispatcher.register(build("snpc"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return literal(name).requires(s -> s.hasPermission(2))
                .then(literal("wand").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ItemStack wand = new ItemStack(StarNpcs.WAND.get());
                    if (!player.getInventory().add(wand)) {
                        player.drop(wand, false);
                    }
                    return 1;
                }))
                .then(literal("spawn")
                        .executes(ctx -> spawn(ctx, ""))
                        .then(argument("preset", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Presets.ids(), b))
                                .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "preset")))))
                .then(literal("presets").executes(ctx -> {
                    ok(ctx, "starnpcs.msg.presets", String.join(", ", Presets.ids()));
                    return 1;
                }))
                .then(literal("preset").then(literal("save").then(argument("name", StringArgumentType.word()).executes(on((ctx, npc) -> {
                    String id = StringArgumentType.getString(ctx, "name");
                    try {
                        Files.createDirectories(Assets.dir().resolve("presets"));
                        Files.writeString(Assets.dir().resolve("presets").resolve(id + ".json"), npc.profile().toPrettyJson(), StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        throw new SimpleCommandExceptionType(Component.literal(String.valueOf(e.getMessage()))).create();
                    }
                    Presets.reload();
                    ok(ctx, "starnpcs.msg.preset_saved", id);
                    return 1;
                })))))
                .then(literal("edit").executes(on((ctx, npc) -> {
                    Net.openEditor(ctx.getSource().getPlayerOrException(), npc, 0);
                    return 1;
                })))
                .then(literal("remove").executes(on((ctx, npc) -> {
                    String n = npc.profile().name;
                    npc.discard();
                    ok(ctx, "starnpcs.msg.removed", n);
                    return 1;
                })))
                .then(literal("list").executes(ctx -> {
                    int n = 0;
                    for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
                        for (Entity e : level.getAllEntities()) {
                            if (e instanceof NpcEntity npc) {
                                n++;
                                String line = npc.profile().name + "  " + level.dimension().location().getPath() + "  "
                                        + npc.blockPosition().toShortString()
                                        + (npc.profile().group.isBlank() ? "" : "  [" + npc.profile().group + "]");
                                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                            }
                        }
                    }
                    ok(ctx, "starnpcs.msg.count", n);
                    return n;
                }))
                .then(literal("info").executes(on((ctx, npc) -> {
                    String json = npc.profile().toPrettyJson();
                    ctx.getSource().sendSuccess(() -> Component.literal(json), false);
                    return 1;
                })))
                .then(literal("move").then(argument("dir", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("front", "back", "left", "right", "up", "down"), b))
                        .executes(on((ctx, npc) -> NpcActions.move(npc, StringArgumentType.getString(ctx, "dir"), 0.1) ? 1 : 0))
                        .then(argument("blocks", DoubleArgumentType.doubleArg(-64, 64))
                                .executes(on((ctx, npc) -> NpcActions.move(npc, StringArgumentType.getString(ctx, "dir"), DoubleArgumentType.getDouble(ctx, "blocks")) ? 1 : 0)))))
                .then(literal("rotate").then(argument("degrees", FloatArgumentType.floatArg(-360, 360)).executes(on((ctx, npc) -> {
                    npc.place(npc.position(), npc.homeYaw() + FloatArgumentType.getFloat(ctx, "degrees"));
                    return 1;
                }))))
                .then(literal("here").executes(on((ctx, npc) -> {
                    npc.place(ctx.getSource().getPosition(), ctx.getSource().getRotation().y);
                    return 1;
                })))
                .then(literal("set").then(argument("field", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(JsonParser.parseString(new Profile().toJson()).getAsJsonObject().keySet(), b))
                        .then(argument("value", StringArgumentType.greedyString()).executes(on((ctx, npc) -> {
                            String field = StringArgumentType.getString(ctx, "field");
                            String value = StringArgumentType.getString(ctx, "value");
                            JsonObject o = JsonParser.parseString(npc.profile().toJson()).getAsJsonObject();
                            JsonElement old = o.get(field);
                            if (old == null || !old.isJsonPrimitive()) {
                                throw new SimpleCommandExceptionType(Component.translatable("starnpcs.msg.bad_field", field)).create();
                            }
                            JsonPrimitive prim = old.getAsJsonPrimitive();
                            try {
                                if (prim.isBoolean()) {
                                    o.addProperty(field, value.equalsIgnoreCase("true") || value.equals("1") || value.equalsIgnoreCase("si"));
                                } else if (prim.isNumber()) {
                                    o.addProperty(field, Double.parseDouble(value));
                                } else {
                                    o.addProperty(field, value.equals("-") ? "" : value);
                                }
                            } catch (NumberFormatException e) {
                                throw new SimpleCommandExceptionType(Component.translatable("starnpcs.msg.bad_number", value)).create();
                            }
                            npc.setProfile(Profile.fromJson(o.toString()));
                            ok(ctx, "starnpcs.msg.set", field, value);
                            return 1;
                        })))))
                .then(literal("skin").then(argument("skin", StringArgumentType.greedyString()).executes(on((ctx, npc) ->
                        edit(npc, p -> p.skin = StringArgumentType.getString(ctx, "skin"))))))
                .then(literal("scale").then(argument("scale", FloatArgumentType.floatArg(0.1F, 6F)).executes(on((ctx, npc) ->
                        edit(npc, p -> p.scale = FloatArgumentType.getFloat(ctx, "scale"))))))
                .then(literal("play").then(argument("anim", StringArgumentType.greedyString()).executes(on((ctx, npc) -> {
                    int n = NpcActions.play(npc, StringArgumentType.getString(ctx, "anim").trim());
                    ok(ctx, "starnpcs.msg.played", n);
                    return n;
                }))))
                .then(literal("sync").executes(on((ctx, npc) -> {
                    int n = NpcActions.sync(npc);
                    ok(ctx, "starnpcs.msg.synced", n);
                    return n;
                })))
                .then(literal("say").then(argument("text", StringArgumentType.greedyString()).executes(on((ctx, npc) -> {
                    npc.say(StringArgumentType.getString(ctx, "text"));
                    return 1;
                }))))
                .then(literal("equip").executes(on((ctx, npc) -> {
                    NpcActions.equipFrom(npc, ctx.getSource().getPlayerOrException());
                    return 1;
                })))
                .then(literal("trade")
                        .then(literal("add").executes(on((ctx, npc) -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ItemStack sell = player.getMainHandItem();
                            ItemStack buy = player.getOffhandItem();
                            if (sell.isEmpty() || buy.isEmpty()) {
                                throw new SimpleCommandExceptionType(Component.translatable("starnpcs.msg.trade_hands")).create();
                            }
                            edit(npc, p -> {
                                Profile.Trade t = new Profile.Trade();
                                t.sell = NpcEntity.stackToText(sell);
                                t.buy = NpcEntity.stackToText(buy);
                                p.trades.add(t);
                            });
                            ok(ctx, "starnpcs.msg.trade_added", npc.profile().trades.size());
                            return 1;
                        })))
                        .then(literal("clear").executes(on((ctx, npc) -> edit(npc, p -> p.trades.clear()))))
                        .then(literal("open").then(argument("targets", EntityArgument.players()).executes(on((ctx, npc) -> {
                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                            for (ServerPlayer p : players) {
                                npc.openShop(p);
                                break;
                            }
                            return 1;
                        })))))
                .then(literal("line")
                        .then(literal("add").then(argument("text", StringArgumentType.greedyString()).executes(on((ctx, npc) ->
                                edit(npc, p -> p.lines.add(StringArgumentType.getString(ctx, "text")))))))
                        .then(literal("clear").executes(on((ctx, npc) -> edit(npc, p -> p.lines.clear())))))
                .then(literal("command")
                        .then(literal("add").then(argument("text", StringArgumentType.greedyString()).executes(on((ctx, npc) ->
                                edit(npc, p -> p.commands.add(StringArgumentType.getString(ctx, "text")))))))
                        .then(literal("clear").executes(on((ctx, npc) -> edit(npc, p -> p.commands.clear())))))
                .then(literal("sound")
                        .then(literal("add").then(argument("model", StringArgumentType.word()).then(argument("anim", StringArgumentType.word())
                                .then(argument("seconds", FloatArgumentType.floatArg(0, 600)).then(argument("sound", ResourceLocationArgument.id())
                                        .executes(ctx -> sound(ctx, 1F, 1F))
                                        .then(argument("volume", FloatArgumentType.floatArg(0, 4))
                                                .executes(ctx -> sound(ctx, FloatArgumentType.getFloat(ctx, "volume"), 1F))
                                                .then(argument("pitch", FloatArgumentType.floatArg(0.5F, 2F))
                                                        .executes(ctx -> sound(ctx, FloatArgumentType.getFloat(ctx, "volume"), FloatArgumentType.getFloat(ctx, "pitch"))))))))))
                        .then(literal("clear").then(argument("model", StringArgumentType.word()).then(argument("anim", StringArgumentType.word()).executes(ctx -> {
                            try {
                                Assets.clearSounds(StringArgumentType.getString(ctx, "model"), StringArgumentType.getString(ctx, "anim"));
                            } catch (Exception e) {
                                throw new SimpleCommandExceptionType(Component.literal(String.valueOf(e.getMessage()))).create();
                            }
                            resync(ctx);
                            return 1;
                        })))))
                .then(literal("reload").executes(ctx -> {
                    resync(ctx);
                    Presets.reload();
                    ok(ctx, "starnpcs.msg.reloaded");
                    return 1;
                }));
    }

    private static void resync(CommandContext<CommandSourceStack> ctx) {
        Assets.reload();
        Assets.sendAll(ctx.getSource().getServer());
    }

    private static int sound(CommandContext<CommandSourceStack> ctx, float volume, float pitch) throws CommandSyntaxException {
        String model = StringArgumentType.getString(ctx, "model");
        String anim = StringArgumentType.getString(ctx, "anim");
        try {
            Assets.addSound(model, anim, FloatArgumentType.getFloat(ctx, "seconds"), ResourceLocationArgument.getId(ctx, "sound").toString(), volume, pitch);
        } catch (Exception e) {
            throw new SimpleCommandExceptionType(Component.literal(String.valueOf(e.getMessage()))).create();
        }
        resync(ctx);
        ok(ctx, "starnpcs.msg.sound_added", model, anim);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, String preset) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        float yaw = src.getRotation().y;
        if (preset.isEmpty()) {
            NpcEntity npc = NpcEntity.spawn(src.getLevel(), src.getPosition(), yaw, new Profile());
            if (npc != null && src.getEntity() instanceof ServerPlayer player) {
                Net.openEditor(player, npc, 0);
            }
            return 1;
        }
        if (!Presets.exists(preset)) {
            throw new SimpleCommandExceptionType(Component.translatable("starnpcs.msg.no_preset", preset)).create();
        }
        int n = Presets.spawn(preset, src.getLevel(), src.getPosition(), yaw).size();
        ok(ctx, "starnpcs.msg.spawned", n, preset);
        return n;
    }
}
