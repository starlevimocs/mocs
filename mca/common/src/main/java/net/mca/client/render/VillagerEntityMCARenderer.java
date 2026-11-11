package net.mca.client.render;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.EntityModelLayers;

import net.mca.client.model.VillagerEntityModelMCA;
import net.mca.client.render.layer.ClothingLayer;
import net.mca.client.render.layer.FaceLayer;
import net.mca.client.render.layer.HairLayer;
import net.mca.client.render.layer.SkinLayer;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.EntityRendererFactory;

public class VillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<VillagerEntityMCA> {
    public VillagerEntityMCARenderer(EntityRendererFactory.Context ctx) {
        super(ctx, createMainModel(ctx).hideWears());

        addFeature(new SkinLayer<>(this, model));
        addFeature(new FaceLayer<>(this, createModel(VillagerEntityModelMCA.bodyData(new Dilation(0.01F))).hideWears(), "normal"));
        addFeature(new ClothingLayer<>(this, createModel(VillagerEntityModelMCA.bodyData(new Dilation(0.0625F))), "normal"));
        addFeature(new HairLayer<>(this, createModel(VillagerEntityModelMCA.hairData(new Dilation(0.125F)))));
    }

    /**
     * Dreams: el cuerpo sale de la capa de modelo del jugador, asi EMF le aplica las animaciones del jugador
     * (Fresh Animations Player). Los pechos siguen saliendo del modelo propio de MCA.
     */
    private static VillagerEntityModelMCA<VillagerEntityMCA> createMainModel(EntityRendererFactory.Context ctx) {
        ModelPart own = TexturedModelData.of(VillagerEntityModelMCA.bodyData(Dilation.NONE), 64, 64).createModel();
        if (net.mca.Config.getInstance().useAnimatedPlayerModel) {
            try {
                ModelPart player = ctx.getPart(EntityModelLayers.PLAYER);
                return new VillagerEntityModelMCA<>(player, own);
            } catch (Exception e) {
                net.mca.MCA.LOGGER.warn("Dreams: no se pudo usar el modelo del jugador para los aldeanos", e);
            }
        }
        return new VillagerEntityModelMCA<>(own);
    }

    private static VillagerEntityModelMCA<VillagerEntityMCA> createModel(ModelData data) {
        return new VillagerEntityModelMCA<>(TexturedModelData.of(data, 64, 64).createModel());
    }
}
