package com.starlevi.starnpcs;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Lo que se le puede hacer a un NPC desde el editor o por comando. */
public final class NpcActions {
    private NpcActions() {
    }

    public static void handle(ServerPlayer player, Net.Action m) {
        Entity e = player.serverLevel().getEntity(m.entity());
        if (!(e instanceof NpcEntity npc)) {
            return;
        }
        Profile p = Profile.fromJson(m.json());
        boolean reopen = true;
        switch (m.action()) {
            case "save" -> {
                npc.setProfile(p);
                reopen = false;
            }
            case "delete" -> {
                npc.discard();
                reopen = false;
            }
            case "sync" -> {
                npc.setProfile(p);
                int n = sync(npc);
                player.displayClientMessage(Component.translatable("starnpcs.msg.synced", n), true);
            }
            case "play" -> {
                npc.setProfile(p);
                play(npc, m.arg());
            }
            case "preset" -> Presets.applyTo(npc, p, m.arg());
            case "trade_add" -> {
                ItemStack sell = player.getMainHandItem();
                ItemStack buy = player.getOffhandItem();
                if (sell.isEmpty() || buy.isEmpty() || sell.is(StarNpcs.WAND.get())) {
                    player.displayClientMessage(Component.translatable("starnpcs.msg.trade_hands"), false);
                    npc.setProfile(p);
                } else {
                    Profile.Trade t = new Profile.Trade();
                    t.sell = NpcEntity.stackToText(sell);
                    t.buy = NpcEntity.stackToText(buy);
                    p.trades.add(t);
                    npc.setProfile(p);
                }
            }
            case "trade_pop" -> {
                if (!p.trades.isEmpty()) {
                    p.trades.remove(p.trades.size() - 1);
                }
                npc.setProfile(p);
            }
            case "trade_clear" -> {
                p.trades.clear();
                npc.setProfile(p);
            }
            case "equip" -> {
                npc.setProfile(p);
                equipFrom(npc, player);
            }
            case "unequip" -> {
                npc.setProfile(p);
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    npc.setItemSlot(slot, ItemStack.EMPTY);
                }
            }
            case "move" -> {
                npc.setProfile(p);
                move(npc, m.arg(), 0.1);
            }
            case "rotate" -> {
                npc.setProfile(p);
                try {
                    npc.place(npc.position(), npc.homeYaw() + Float.parseFloat(m.arg()));
                } catch (NumberFormatException ignored) {
                }
            }
            default -> npc.setProfile(p);
        }
        if (reopen && npc.isAlive()) {
            Net.openEditor(player, npc, m.tab());
        }
    }

    public static void equipFrom(NpcEntity npc, ServerPlayer player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            npc.setItemSlot(slot, stack.is(StarNpcs.WAND.get()) ? ItemStack.EMPTY : stack.copy());
        }
    }

    /** Mueve al NPC respecto de hacia donde mira: front, back, left, right, up, down. */
    public static boolean move(NpcEntity npc, String dir, double amount) {
        float rad = npc.homeYaw() * Mth.DEG_TO_RAD;
        Vec3 f = new Vec3(-Mth.sin(rad), 0, Mth.cos(rad));
        Vec3 r = new Vec3(-Mth.cos(rad), 0, -Mth.sin(rad));
        Vec3 d = switch (dir) {
            case "front" -> f;
            case "back" -> f.scale(-1);
            case "right" -> r;
            case "left" -> r.scale(-1);
            case "up" -> new Vec3(0, 1, 0);
            case "down" -> new Vec3(0, -1, 0);
            default -> null;
        };
        if (d == null) {
            return false;
        }
        npc.place(npc.position().add(d.scale(amount)), npc.homeYaw());
        return true;
    }

    /** El NPC y todos los de su grupo en ese mundo. */
    public static List<NpcEntity> group(NpcEntity npc) {
        List<NpcEntity> out = new ArrayList<>();
        String g = npc.profile().group;
        if (g.isBlank() || !(npc.level() instanceof ServerLevel level)) {
            out.add(npc);
            return out;
        }
        for (Entity e : level.getAllEntities()) {
            if (e instanceof NpcEntity other && g.equals(other.profile().group)) {
                out.add(other);
            }
        }
        return out;
    }

    /** Pone el mismo reloj a todo el grupo: sus animaciones en bucle arrancan juntas desde cero. */
    public static int sync(NpcEntity npc) {
        long now = npc.level().getGameTime();
        List<NpcEntity> all = group(npc);
        for (NpcEntity n : all) {
            Profile p = n.profile().copy();
            p.epoch = now;
            n.setProfile(p);
        }
        return all.size();
    }

    /** Dispara una animacion de una vez en todo el grupo, en el mismo tick. */
    public static int play(NpcEntity npc, String anim) {
        List<NpcEntity> all = group(npc);
        for (NpcEntity n : all) {
            // @see y @interact: la que cada uno tenga puesta en ese lugar
            Profile p = n.profile();
            n.trigger(anim.equals("@see") ? p.see : anim.equals("@interact") ? p.interact : anim);
        }
        return all.size();
    }
}
