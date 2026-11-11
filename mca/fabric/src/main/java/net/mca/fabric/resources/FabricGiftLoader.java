package net.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.mca.entity.interaction.gifts.GiftLoader;
import net.minecraft.util.Identifier;

public class FabricGiftLoader extends GiftLoader implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
