package punchy.api;

/**
 * STUB solo para compilar. No va dentro del jar (build.gradle lo excluye):
 * en el juego se usa la clase real de Punchy. Solo declara lo que Star Combat usa,
 * con las mismas firmas que Punchy 3.2.
 */
public final class PunchyApi {
    private PunchyApi() {
    }

    public static void attack(boolean mainHand, boolean strong) {
        throw new UnsupportedOperationException("stub");
    }

    public static void heldItemLook(String owner, HeldItemLook look) {
        throw new UnsupportedOperationException("stub");
    }

    public interface HeldItemLook {
        Object stack(Object player, boolean mainHand, Object stack);
    }
}
