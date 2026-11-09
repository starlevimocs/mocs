package com.starlevi.dreams.client.screen;

import com.starlevi.dreams.client.CinematicClient;
import com.starlevi.dreams.client.data.SpeakerManager;
import com.starlevi.dreams.dialogue.Dialogue;
import com.starlevi.dreams.dialogue.Speaker;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Pantalla de dialogo estilo novela visual: caja de texto abajo, retrato arriba a la izquierda,
 * texto que aparece letra por letra y un bip por personaje (como Undertale o Animal Crossing).
 */
public class DialogueScreen extends Screen {
    private static final int BOX_HEIGHT = 96;
    private static final int MARGIN = 16;
    private static final int PADDING = 12;

    private static final long BASE_DELAY_MS = 28;
    private static final long COMMA_DELAY_MS = 140;
    private static final long STOP_DELAY_MS = 300;
    private static final long BLIP_MIN_INTERVAL_MS = 45;
    private static final int BLIP_EVERY_N_LETTERS = 2;

    private final Dialogue dialogue;
    private final RandomSource random = RandomSource.create();

    private Dialogue.Node node;
    private Speaker speaker;
    private List<String> lines = List.of();
    private String flat = "";
    private int revealed;
    private long nextCharAt;
    private long lastBlipAt;
    private int letterCounter;
    private boolean choicesShown;
    private String pendingGoTo;

    public DialogueScreen(Dialogue dialogue) {
        super(Component.literal("Dreams"));
        this.dialogue = dialogue;
    }

    @Override
    protected void init() {
        if (node == null) {
            goTo(dialogue.start());
            if (dialogue.cinematic() != null) {
                CinematicClient.requestForDialogue(dialogue.cinematic());
            }
        } else {
            wrap();
            if (choicesShown) {
                layoutChoices();
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        if (dialogue.cinematic() != null) {
            CinematicClient.dialogueClosed();
        }
    }

    // ---------------------------------------------------------------- flujo

    private void goTo(String nodeId) {
        this.node = dialogue.nodes().get(nodeId);
        this.speaker = SpeakerManager.get(node.speaker());
        this.revealed = 0;
        this.nextCharAt = Util.getMillis();
        this.letterCounter = 0;
        this.choicesShown = false;
        this.pendingGoTo = null;
        clearWidgets();
        wrap();
    }

    private void wrap() {
        int textWidth = width - 2 * MARGIN - 2 * PADDING;
        this.lines = font.getSplitter().splitLines(node.text(), textWidth, Style.EMPTY).stream()
                .map(FormattedText::getString)
                .toList();
        this.flat = String.join("", lines);
        this.revealed = Math.min(revealed, flat.length());
    }

    private boolean textFinished() {
        return revealed >= flat.length();
    }

    private void advanceFromInput() {
        if (!textFinished()) {
            revealed = flat.length();
            return;
        }
        if (!node.choices().isEmpty()) {
            return; // el jugador tiene que elegir
        }
        if (node.next() == null) {
            onClose();
        } else {
            goTo(node.next());
        }
    }

    @Override
    public void tick() {
        if (pendingGoTo != null) {
            goTo(pendingGoTo);
            return;
        }
        if (textFinished() && !choicesShown && !node.choices().isEmpty()) {
            choicesShown = true;
            layoutChoices();
        }
    }

    private void layoutChoices() {
        int boxTop = height - BOX_HEIGHT - MARGIN;
        int buttonWidth = Math.min(width - 2 * MARGIN, 260);
        int x = width - MARGIN - buttonWidth;
        int y = boxTop - 8 - 20;
        for (Dialogue.Choice choice : node.choices()) {
            addRenderableWidget(Button.builder(Component.literal(choice.text()),
                            button -> pendingGoTo = choice.next())
                    .bounds(x, y, buttonWidth, 20)
                    .build());
            y -= 24;
        }
    }

    // ---------------------------------------------------------------- texto y voz

    private void advanceText() {
        long now = Util.getMillis();
        while (revealed < flat.length() && now >= nextCharAt) {
            char c = flat.charAt(revealed++);
            nextCharAt += delayFor(c);
            if (Character.isLetterOrDigit(c)) {
                letterCounter++;
                if (letterCounter % BLIP_EVERY_N_LETTERS == 0 && now - lastBlipAt >= BLIP_MIN_INTERVAL_MS) {
                    blip();
                    lastBlipAt = now;
                }
            }
        }
    }

    private static long delayFor(char c) {
        return switch (c) {
            case ',', ';', ':' -> COMMA_DELAY_MS;
            case '.', '!', '?', '…' -> STOP_DELAY_MS;
            default -> BASE_DELAY_MS;
        };
    }

    private void blip() {
        Speaker.Voice voice = speaker.voice();
        SoundEvent event = ForgeRegistries.SOUND_EVENTS.getValue(voice.sound());
        if (event == null) {
            event = SoundEvent.createVariableRangeEvent(voice.sound());
        }
        float pitch = Mth.clamp(voice.pitch() + (random.nextFloat() * 2.0F - 1.0F) * voice.variance(), 0.5F, 2.0F);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, voice.volume()));
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        advanceText();

        int boxLeft = MARGIN;
        int boxRight = width - MARGIN;
        int boxBottom = height - MARGIN;
        int boxTop = boxBottom - BOX_HEIGHT;
        int accent = 0xFF000000 | speaker.color();


        // caja de texto
        g.fill(boxLeft - 2, boxTop - 2, boxRight + 2, boxBottom + 2, accent);
        g.fill(boxLeft, boxTop, boxRight, boxBottom, 0xEE101018);

        // etiqueta con el nombre
        String name = speaker.displayName();
        int tagWidth = font.width(name) + 16;
        int tagX = boxLeft + PADDING;
        int tagY = boxTop - 16;
        g.fill(tagX - 2, tagY - 2, tagX + tagWidth + 2, boxTop, accent);
        g.fill(tagX, tagY, tagX + tagWidth, boxTop, 0xFF101018);
        g.drawString(font, name, tagX + 8, tagY + 4, accent, true);

        // texto revelado hasta ahora
        int remaining = revealed;
        int y = boxTop + PADDING;
        for (String line : lines) {
            if (remaining <= 0) {
                break;
            }
            int shown = Math.min(line.length(), remaining);
            g.drawString(font, line.substring(0, shown), boxLeft + PADDING, y, 0xFFFFFFFF, true);
            remaining -= line.length();
            y += font.lineHeight + 3;
        }

        // indicador de "sigue"
        if (textFinished() && node.choices().isEmpty() && (Util.getMillis() / 400) % 2 == 0) {
            g.drawString(font, "▼", boxRight - 20, boxBottom - 16, 0xFFFFFFFF, true);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    // ---------------------------------------------------------------- entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true; // se pulso una opcion
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            advanceFromInput();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            advanceFromInput();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
