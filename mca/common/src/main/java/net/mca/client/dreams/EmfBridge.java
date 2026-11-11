package net.mca.client.dreams;

import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.Entity;

import java.lang.reflect.Method;

/**
 * Dreams: puente opcional con Entity Model Features (EMF), sin depender de EMF para compilar.
 * <p>
 * MCA dibuja las partes del aldeano de a una (cabeza, cuerpo, pechos...), y ademas calcula los pechos y la
 * segunda capa de ropa a partir del cuerpo antes de dibujar. Para que eso siga a la animacion de EMF, MCA le
 * pide a EMF que anime el modelo primero y despues recalcula esas partes.
 */
public final class EmfBridge {
    private static boolean resolved;
    private static Method animate; // EMFAnimationApi.animateModelForEntity(EMFEntity, EntityModel, boolean)

    private EmfBridge() {
    }

    private static Method method() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
                Class<?> emfEntity = Class.forName("traben.entity_model_features.utils.EMFEntity");
                animate = api.getMethod("animateModelForEntity", emfEntity, EntityModel.class, boolean.class);
            } catch (Throwable t) {
                animate = null; // EMF no esta instalado
            }
        }
        return animate;
    }

    /** @return true si EMF animo el modelo (o sea, si hay que recalcular lo que depende del cuerpo). */
    public static boolean animate(Entity entity, EntityModel<?> model) {
        Method m = method();
        if (m == null || entity == null) {
            return false;
        }
        try {
            return (boolean) m.invoke(null, entity, model, false);
        } catch (Throwable t) {
            return false;
        }
    }
}
