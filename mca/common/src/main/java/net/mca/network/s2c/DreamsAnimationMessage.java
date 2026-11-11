package net.mca.network.s2c;

import net.mca.cobalt.network.Message;

import java.io.Serial;

/**
 * Dreams: avisa a los clientes cercanos que un aldeano y un jugador hacen una animacion juntos
 * (beso, abrazo, cachetada...), para que todos la vean.
 */
public class DreamsAnimationMessage implements Message {
    @Serial
    private static final long serialVersionUID = 6021190815514462671L;

    private final String type;
    private final int villagerId;
    private final int playerId;
    private final int duration;

    public DreamsAnimationMessage(String type, int villagerId, int playerId, int duration) {
        this.type = type;
        this.villagerId = villagerId;
        this.playerId = playerId;
        this.duration = duration;
    }

    @Override
    public void receive() {
        net.mca.client.dreams.DreamsPoses.start(type, villagerId, playerId, duration);
    }
}
