package com.starlevi.dreams.cinematic;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * /dreams cinematic ...
 *
 * <pre>
 * point add &lt;nombre&gt; [segundos]     agrega un punto donde estas parado y mirando
 * point undo|clear &lt;nombre&gt;         quita el ultimo punto / todos
 * orbit &lt;nombre&gt; &lt;radio&gt; &lt;segundos&gt; [altura] [grados] [alturaMirada]
 * play &lt;nombre&gt; [jugadores]         la ven todos (o los que elijas) al mismo tiempo
 * preview &lt;nombre&gt;                  solo para vos
 * stop [jugadores]
 * onend &lt;nombre&gt; &lt;comando...&gt;|clear   comando que corre el servidor al terminar
 * hideplayers &lt;nombre&gt; true|false
 * list | info &lt;nombre&gt; | remove &lt;nombre&gt;
 * </pre>
 */
public final class CinematicCommands {
    private static final double DEFAULT_SECONDS_PER_POINT = 3.0;

    private static final SuggestionProvider<CommandSourceStack> NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(CinematicSavedData.get(ctx.getSource().getServer()).names(), builder);

    private CinematicCommands() {
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> name() {
        return Commands.argument("name", StringArgumentType.word()).suggests(NAMES);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("cinematic")
                .then(Commands.literal("point")
                        .then(Commands.literal("add")
                                .then(name()
                                        .executes(ctx -> addPoint(ctx, DEFAULT_SECONDS_PER_POINT))
                                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.05, 600))
                                                .executes(ctx -> addPoint(ctx, DoubleArgumentType.getDouble(ctx, "seconds"))))))
                        .then(Commands.literal("undo").then(name().executes(CinematicCommands::undoPoint)))
                        .then(Commands.literal("clear").then(name().executes(CinematicCommands::clearPoints))))
                .then(Commands.literal("orbit")
                        .then(name()
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1, 512))
                                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.5, 3600))
                                                .executes(ctx -> orbit(ctx, 4, 360, 2))
                                                .then(Commands.argument("height", DoubleArgumentType.doubleArg(-256, 256))
                                                        .executes(ctx -> orbit(ctx, DoubleArgumentType.getDouble(ctx, "height"), 360, 2))
                                                        .then(Commands.argument("degrees", DoubleArgumentType.doubleArg(-7200, 7200))
                                                                .executes(ctx -> orbit(ctx,
                                                                        DoubleArgumentType.getDouble(ctx, "height"),
                                                                        DoubleArgumentType.getDouble(ctx, "degrees"), 2))
                                                                .then(Commands.argument("lookheight", DoubleArgumentType.doubleArg(-256, 256))
                                                                        .executes(ctx -> orbit(ctx,
                                                                                DoubleArgumentType.getDouble(ctx, "height"),
                                                                                DoubleArgumentType.getDouble(ctx, "degrees"),
                                                                                DoubleArgumentType.getDouble(ctx, "lookheight"))))))))))
                .then(Commands.literal("play")
                        .then(name()
                                .executes(ctx -> play(ctx, allPlayers(ctx)))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> play(ctx, EntityArgument.getPlayers(ctx, "targets"))))))
                .then(Commands.literal("preview")
                        .then(name().executes(ctx -> play(ctx, List.of(ctx.getSource().getPlayerOrException())))))
                .then(Commands.literal("stop")
                        .executes(ctx -> stop(ctx, allPlayers(ctx)))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> stop(ctx, EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("onend")
                        .then(name()
                                .then(Commands.literal("clear").executes(ctx -> setOnEnd(ctx, null)))
                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                        .executes(ctx -> setOnEnd(ctx, StringArgumentType.getString(ctx, "command"))))))
                .then(Commands.literal("hideplayers")
                        .then(name()
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(CinematicCommands::setHidePlayers))))
                .then(Commands.literal("list").executes(CinematicCommands::list))
                .then(Commands.literal("info").then(name().executes(CinematicCommands::info)))
                .then(Commands.literal("remove").then(name().executes(CinematicCommands::remove)));
    }

    // ---------------------------------------------------------------- utilidades

    private static Collection<ServerPlayer> allPlayers(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getServer().getPlayerList().getPlayers();
    }

    private static int ok(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendFailure(Component.literal(message));
        return 0;
    }

    private static String nameArg(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "name");
    }

    private static String seconds(int ticks) {
        return String.format("%.1f", ticks / 20.0) + " s";
    }

    // ---------------------------------------------------------------- camino por puntos

    private static int addPoint(CommandContext<CommandSourceStack> ctx, double seconds) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        CinematicSavedData data = CinematicSavedData.get(server);
        String name = nameArg(ctx);

        Cinematic cinematic = data.find(name);
        if (cinematic == null) {
            cinematic = Cinematic.emptyPath(name);
        } else if (cinematic.kind() != Cinematic.Kind.PATH) {
            return fail(ctx, "'" + name + "' es una orbita. Usa otro nombre o borrala con /dreams cinematic remove " + name);
        }

        int ticks = cinematic.frames().isEmpty() ? 0 : Math.max(1, (int) Math.round(seconds * 20));
        Vec3 eye = player.getEyePosition();
        cinematic = cinematic.withFrame(new Cinematic.Keyframe(eye.x, eye.y, eye.z, player.getYRot(), player.getXRot(), ticks));
        data.put(cinematic);

        int count = cinematic.frames().size();
        String detail = count == 1
                ? "Punto 1 marcado. Muevete y agrega el siguiente."
                : "Punto " + count + " agregado (llega en " + seconds(ticks) + "). Duracion total: " + seconds(cinematic.durationTicks()) + ".";
        return ok(ctx, "[" + name + "] " + detail);
    }

    private static int undoPoint(CommandContext<CommandSourceStack> ctx) {
        CinematicSavedData data = CinematicSavedData.get(ctx.getSource().getServer());
        Cinematic cinematic = data.find(nameArg(ctx));
        if (cinematic == null || cinematic.kind() != Cinematic.Kind.PATH || cinematic.frames().isEmpty()) {
            return fail(ctx, "No hay puntos para quitar.");
        }
        cinematic = cinematic.withoutLastFrame();
        data.put(cinematic);
        return ok(ctx, "Ultimo punto quitado. Quedan " + cinematic.frames().size() + ".");
    }

    private static int clearPoints(CommandContext<CommandSourceStack> ctx) {
        CinematicSavedData data = CinematicSavedData.get(ctx.getSource().getServer());
        Cinematic cinematic = data.find(nameArg(ctx));
        if (cinematic == null) {
            return fail(ctx, "No existe esa cinematica.");
        }
        data.put(cinematic.withoutFrames());
        return ok(ctx, "Puntos borrados de '" + cinematic.name() + "'.");
    }

    // ---------------------------------------------------------------- orbita

    private static int orbit(CommandContext<CommandSourceStack> ctx, double height, double degrees, double lookHeight)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CinematicSavedData data = CinematicSavedData.get(ctx.getSource().getServer());
        String name = nameArg(ctx);
        double radius = DoubleArgumentType.getDouble(ctx, "radius");
        int ticks = Math.max(1, (int) Math.round(DoubleArgumentType.getDouble(ctx, "seconds") * 20));

        // el centro es el bloque al que miras; si no miras ninguno, donde estas parado
        HitResult hit = player.pick(128.0, 1.0F, false);
        Vec3 center = hit.getType() == HitResult.Type.BLOCK
                ? Vec3.atBottomCenterOf(((BlockHitResult) hit).getBlockPos())
                : player.position();

        Cinematic previous = data.find(name);
        boolean hide = previous != null && previous.hidePlayers();
        String onEnd = previous == null ? null : previous.onEnd();
        Cinematic.Orbit orbit = new Cinematic.Orbit(center.x, center.y, center.z, radius, height, lookHeight, 0, degrees, ticks);
        data.put(Cinematic.orbit(name, orbit, hide, onEnd));

        return ok(ctx, "[" + name + "] Orbita de " + seconds(ticks) + " alrededor de "
                + Math.round(center.x) + ", " + Math.round(center.y) + ", " + Math.round(center.z)
                + " (radio " + radius + ", altura " + height + ", " + Math.round(degrees) + " grados).");
    }

    // ---------------------------------------------------------------- reproducir

    private static int play(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        Cinematic cinematic = CinematicSavedData.get(ctx.getSource().getServer()).find(nameArg(ctx));
        if (cinematic == null) {
            return fail(ctx, "No existe la cinematica '" + nameArg(ctx) + "'.");
        }
        if (cinematic.durationTicks() <= 0) {
            return fail(ctx, "'" + cinematic.name() + "' necesita al menos 2 puntos.");
        }
        if (targets.isEmpty()) {
            return fail(ctx, "No hay jugadores para mostrarsela.");
        }
        CinematicServer.play(ctx.getSource().getServer(), targets, cinematic, false);
        return ok(ctx, "Reproduciendo '" + cinematic.name() + "' (" + seconds(cinematic.durationTicks()) + ") a "
                + targets.size() + " jugador(es).");
    }

    private static int stop(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        CinematicServer.stop(targets);
        return ok(ctx, "Cinematica detenida para " + targets.size() + " jugador(es).");
    }

    // ---------------------------------------------------------------- opciones

    private static int setOnEnd(CommandContext<CommandSourceStack> ctx, String command) {
        CinematicSavedData data = CinematicSavedData.get(ctx.getSource().getServer());
        Cinematic cinematic = data.find(nameArg(ctx));
        if (cinematic == null) {
            return fail(ctx, "No existe esa cinematica.");
        }
        String clean = command == null ? null : (command.startsWith("/") ? command.substring(1) : command);
        data.put(cinematic.withOnEnd(clean));
        return ok(ctx, clean == null
                ? "Ya no ejecuta nada al terminar."
                : "Al terminar '" + cinematic.name() + "' se ejecutara: /" + clean);
    }

    private static int setHidePlayers(CommandContext<CommandSourceStack> ctx) {
        CinematicSavedData data = CinematicSavedData.get(ctx.getSource().getServer());
        Cinematic cinematic = data.find(nameArg(ctx));
        if (cinematic == null) {
            return fail(ctx, "No existe esa cinematica.");
        }
        boolean value = BoolArgumentType.getBool(ctx, "value");
        data.put(cinematic.withHidePlayers(value));
        return ok(ctx, "Los jugadores " + (value ? "se ocultan" : "se ven") + " durante '" + cinematic.name() + "'.");
    }

    // ---------------------------------------------------------------- consultas

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CinematicSavedData data = CinematicSavedData.get(ctx.getSource().getServer());
        List<String> names = data.names();
        if (names.isEmpty()) {
            return ok(ctx, "No hay cinematicas todavia. Crea una con /dreams cinematic point add <nombre>.");
        }
        StringBuilder text = new StringBuilder("Cinematicas (" + names.size() + "):");
        for (String name : names) {
            Cinematic c = data.find(name);
            text.append("\n- ").append(name).append(" (")
                    .append(c.kind() == Cinematic.Kind.ORBIT ? "orbita" : c.frames().size() + " puntos")
                    .append(", ").append(seconds(c.durationTicks())).append(")");
        }
        return ok(ctx, text.toString());
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        Cinematic c = CinematicSavedData.get(ctx.getSource().getServer()).find(nameArg(ctx));
        if (c == null) {
            return fail(ctx, "No existe esa cinematica.");
        }
        return ok(ctx, c.name() + ": " + (c.kind() == Cinematic.Kind.ORBIT ? "orbita" : c.frames().size() + " puntos")
                + ", duracion " + seconds(c.durationTicks())
                + ", jugadores " + (c.hidePlayers() ? "ocultos" : "visibles")
                + ", al terminar: " + (c.onEnd() == null ? "nada" : "/" + c.onEnd()));
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        String name = nameArg(ctx);
        if (!CinematicSavedData.get(ctx.getSource().getServer()).remove(name)) {
            return fail(ctx, "No existe esa cinematica.");
        }
        return ok(ctx, "Cinematica '" + name + "' borrada.");
    }
}
