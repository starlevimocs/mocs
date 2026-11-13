package com.starlevi.starnpcs.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.starlevi.starnpcs.Profile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Resuelve la skin de un NPC: player:Nombre, url:https://..., file:archivo, o una textura del juego. */
final class Skins {
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    private Skins() {
    }

    static void clearFiles() {
        CACHE.keySet().removeIf(k -> k.startsWith("file:"));
    }

    static ResourceLocation get(Profile p) {
        String s = p.skin.trim();
        if (s.isEmpty()) {
            return DefaultPlayerSkin.getDefaultSkin();
        }
        ResourceLocation loc = CACHE.get(s);
        if (loc == null) {
            loc = resolve(s);
            CACHE.put(s, loc);
        }
        return loc;
    }

    private static ResourceLocation resolve(String s) {
        String lower = s.toLowerCase();
        if (lower.startsWith("file:")) {
            ResourceLocation loc = ClientAssets.skinFile(s.substring(5).trim().replaceAll("(?i)\\.png$", ""));
            return loc != null ? loc : DefaultPlayerSkin.getDefaultSkin();
        }
        if (lower.startsWith("url:") || lower.startsWith("http://") || lower.startsWith("https://")) {
            String url = lower.startsWith("url:") ? s.substring(4).trim() : s;
            if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
                return DefaultPlayerSkin.getDefaultSkin();
            }
            Minecraft mc = Minecraft.getInstance();
            String hash = Integer.toHexString(url.hashCode()) + "_" + url.length();
            ResourceLocation loc = new ResourceLocation("starnpcs", "url/" + hash);
            File cache = new File(mc.gameDirectory, "starnpcs_cache/skins/" + hash + ".png");
            cache.getParentFile().mkdirs();
            mc.getTextureManager().register(loc, new HttpTexture(cache, url, DefaultPlayerSkin.getDefaultSkin(), true, null));
            return loc;
        }
        if (lower.startsWith("player:") || !s.contains(":")) {
            String name = lower.startsWith("player:") ? s.substring(7).trim() : s;
            if (name.isEmpty() || name.length() > 16) {
                return DefaultPlayerSkin.getDefaultSkin();
            }
            final String key = s;
            Minecraft mc = Minecraft.getInstance();
            SkullBlockEntity.updateGameprofile(new GameProfile(null, name), prof -> mc.execute(() -> {
                if (prof != null) {
                    mc.getSkinManager().registerSkins(prof, (type, location, texture) -> {
                        if (type == MinecraftProfileTexture.Type.SKIN) {
                            CACHE.put(key, location);
                        }
                    }, true);
                }
            }));
            return DefaultPlayerSkin.getDefaultSkin();
        }
        ResourceLocation loc = ResourceLocation.tryParse(s);
        return loc != null ? loc : DefaultPlayerSkin.getDefaultSkin();
    }
}
