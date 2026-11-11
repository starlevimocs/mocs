package net.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.mca.resources.BuildingTypes;
import net.minecraft.util.Identifier;

public class FabricBuildingTypes extends BuildingTypes implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
