package net.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.mca.client.resources.ColorPaletteLoader;
import net.minecraft.util.Identifier;

public class FabricColorPaletteLoader extends ColorPaletteLoader implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
