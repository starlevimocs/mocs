package net.mca.network.s2c;

import net.mca.cobalt.network.Message;

import java.io.Serial;

/** Dreams: un aldeano va de la mano / en brazos / en la espalda de un jugador (o se solto: mode 0). */
public class DreamsAttachMessage implements Message {
    @Serial
    private static final long serialVersionUID = 4812290315514462674L;

    private final int villagerId;
    private final int mode;
    private final int playerId;

    public DreamsAttachMessage(int villagerId, int mode, int playerId) {
        this.villagerId = villagerId;
        this.mode = mode;
        this.playerId = playerId;
    }

    @Override
    public void receive() {
        net.mca.client.dreams.DreamsPoses.setAttach(villagerId, mode, playerId);
    }
}
