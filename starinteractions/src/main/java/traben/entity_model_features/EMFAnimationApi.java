package traben.entity_model_features;

/**
 * STUB solo para compilar. No va dentro del jar (build.gradle lo excluye):
 * en el juego se usa la clase real de Entity Model Features.
 * Solo declara lo que Star Interactions usa, con las mismas firmas que EMF 3.3.x.
 */
public interface EMFAnimationApi {

    static boolean registerAnimationHook(EMFAnimationHook hook) throws Exception {
        throw new UnsupportedOperationException("stub");
    }

    static boolean isModelCustomizedByEMF(net.minecraft.client.model.EntityModel<?> model) {
        throw new UnsupportedOperationException("stub");
    }

    abstract class EMFAnimationHook {
        public boolean onAnimationStart(AnimationContext context, boolean isCancelledByHook) {
            return true;
        }

        public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        }

        public static final class AnimationContext {
            private AnimationContext() {
            }
        }
    }
}
