package com.starlevi.starcompat.client;

import com.starlevi.starcompat.StarCompat;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * Puente con Cinematic Interactions sin depender de el para compilar.
 * <p>
 * Cinematic pone la pose de las estaciones (anvil, mesa, horno...) al final de {@code HumanoidModel.setupAnim}.
 * Con EMF, la animacion de Fresh Animations corre despues y la pisa. Aca se guarda que jugador se esta
 * dibujando y, cuando EMF termina de animar, se llama otra vez al mismo codigo de Cinematic para que su pose
 * quede arriba. Solo pasa mientras ese jugador esta en una estacion; el resto del tiempo no se toca nada.
 */
@Mod.EventBusSubscriber(modid = StarCompat.MOD_ID, value = Dist.CLIENT)
public final class CinematicPose {
    private static boolean resolved;
    private static Method atStation;   // ClientPayloads.atStation(int) -> StationKind | null
    private static Method stationPose; // handler del mixin de Cinematic dentro de HumanoidModel

    private static LivingEntity entity;
    private static HumanoidModel<?> model;
    private static float partialTick;
    private static boolean applying;

    private CinematicPose() {
    }

    static boolean available() {
        resolve();
        return atStation != null && stationPose != null;
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> payloads = Class.forName("dev.dyslexic.cinematic.client.ClientPayloads");
            atStation = payloads.getMethod("atStation", int.class);
        } catch (Throwable t) {
            atStation = null;
            return;
        }
        // Mixin puede renombrar el metodo (handler$xxx$cinematic$stationPose), por eso se busca por sufijo
        for (Method method : HumanoidModel.class.getDeclaredMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (method.getName().endsWith("stationPose") && params.length == 7
                    && LivingEntity.class.isAssignableFrom(params[0])
                    && params[6] == CallbackInfo.class) {
                method.setAccessible(true);
                stationPose = method;
                break;
            }
        }
    }

    /** Llamado por el hook de EMF al terminar cada animacion. */
    static void afterEmfAnimation() {
        reapply();
        if (entity != null && model != null) {
            ArtifactsPose.apply(entity, model);
        }
        if (model != null) {
            PoseKeeper.capture(model);
        }
    }

    private static void reapply() {
        if (applying || entity == null || model == null || stationPose == null) {
            return;
        }
        applying = true;
        try {
            if (atStation.invoke(null, entity.getId()) == null) {
                return;
            }
            float ageInTicks = entity.tickCount + partialTick;
            stationPose.invoke(model, entity, 0.0F, 0.0F, ageInTicks, 0.0F, 0.0F,
                    new CallbackInfo("setupAnim", false));
            syncSecondLayer(model);
        } catch (Throwable t) {
            StarCompat.LOGGER.warn("[Starcompat] Error al reaplicar la pose de Cinematic; se desactiva", t);
            stationPose = null;
        } finally {
            applying = false;
        }
    }

    /**
     * La segunda capa de la skin (sombrero, chaqueta, mangas y pantalon) la mueve Fresh Animations por separado,
     * copiando la pose que EMF le dio al cuerpo. Como Cinematic cambia el cuerpo despues, la segunda capa quedaba
     * en la pose vieja (por ejemplo, adelantada al sentarse en la fogata). Aca se la vuelve a pegar al cuerpo,
     * igual que hace Minecraft sin EMF.
     */
    private static void syncSecondLayer(HumanoidModel<?> humanoid) {
        humanoid.hat.copyFrom(humanoid.head);
        if (humanoid instanceof PlayerModel<?> player) {
            player.jacket.copyFrom(player.body);
            player.leftSleeve.copyFrom(player.leftArm);
            player.rightSleeve.copyFrom(player.rightArm);
            player.leftPants.copyFrom(player.leftLeg);
            player.rightPants.copyFrom(player.rightLeg);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        EntityModel<?> current = event.getRenderer().getModel();
        if (!event.isCanceled() && event.getEntity() instanceof Player && current instanceof HumanoidModel<?> humanoid) {
            entity = event.getEntity();
            model = humanoid;
            partialTick = event.getPartialTick();
        } else {
            clear();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        clear();
    }

    private static void clear() {
        entity = null;
        model = null;
        PoseKeeper.clear();
    }

}
