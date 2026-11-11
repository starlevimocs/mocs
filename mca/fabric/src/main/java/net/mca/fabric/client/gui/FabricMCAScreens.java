package net.mca.fabric.client.gui;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.mca.client.gui.MCAScreens;
import net.minecraft.util.Identifier;

public class FabricMCAScreens extends MCAScreens implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
