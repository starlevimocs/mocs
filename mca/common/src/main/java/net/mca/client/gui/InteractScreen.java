package net.mca.client.gui;

import net.mca.MCA;
import net.mca.client.dreams.VillagerVoice;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Memories;
import net.mca.entity.ai.Traits;
import net.mca.entity.ai.brain.VillagerBrain;
import net.mca.entity.ai.relationship.CompassionateEntity;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.network.c2s.*;
import net.mca.resources.data.analysis.Analysis;
import net.mca.resources.data.dialogue.Question;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.util.Util;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class InteractScreen extends AbstractDynamicScreen {
    public static final Identifier ICON_TEXTURES = MCA.locate("textures/gui.png");

    private final VillagerLike<?> villager;
    private final PlayerEntity player = Objects.requireNonNull(MinecraftClient.getInstance().player);

    private boolean inGiftMode;
    private int timeSinceLastClick;

    private String father;
    private String mother;

    private RelationshipState marriageState;
    private Text spouse;

    private List<String> dialogAnswers;
    private String dialogAnswerHover;
    private List<OrderedText> dialogQuestionText;
    private String dialogQuestionId;

    private static Analysis<?> analysis;

    // Dreams: conversacion estilo novela visual
    private static final int BOX_HEIGHT = 74;
    private static final float CHARS_PER_SECOND = 42.0F;
    private static UUID talkingTo;

    private final VillagerVoice voice;
    private String fullText = "";
    private float shown;
    private long lastFrame;
    private int pause;
    private int lastBipAt = -1;

    public InteractScreen(VillagerLike<?> villager) {
        super(Text.literal("Interact"));
        this.villager = villager;
        this.voice = VillagerVoice.of(villager);
        talkingTo = villager.asEntity().getUuid();
    }

    /** true si la pantalla de conversacion con ese aldeano esta abierta (los bips los pone la pantalla). */
    public static boolean isTalkingTo(UUID uuid) {
        return talkingTo != null && talkingTo.equals(uuid)
                && MinecraftClient.getInstance().currentScreen instanceof InteractScreen;
    }

    public void setParents(String father, String mother) {
        this.father = father;
        this.mother = mother;
    }

    public void setSpouse(RelationshipState marriageState, String spouse) {
        this.marriageState = marriageState;
        this.spouse = spouse == null ? Text.translatable("gui.interact.label.parentUnknown") : Text.literal(spouse);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        talkingTo = null;
        Objects.requireNonNull(this.client).setScreen(null);
        NetworkHandler.sendToServer(new InteractionCloseRequest(villager.asEntity().getUuid()));
    }

    @Override
    public void init() {
        NetworkHandler.sendToServer(new GetInteractDataRequest(villager.asEntity().getUuid()));
    }

    @Override
    public void tick() {
        timeSinceLastClick++;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        advanceText();
        drawDialogueBox(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, tickDelta);

        drawIcons(context);
        drawTextPopups(context);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double d) {
        if (d < 0) {
            player.getInventory().selectedSlot = player.getInventory().selectedSlot == 8 ? 0 : player.getInventory().selectedSlot + 1;
        } else if (d > 0) {
            player.getInventory().selectedSlot = player.getInventory().selectedSlot == 0 ? 8 : player.getInventory().selectedSlot - 1;
        }

        return super.mouseScrolled(x, y, d);
    }

    @Override
    public boolean mouseClicked(double posX, double posY, int button) {
        super.mouseClicked(posX, posY, button);

        // Dialog
        if (button == 0 && isTyping() && insideBox(posX, posY)) {
            skipTyping();
            return true;
        }
        if (button == 0 && dialogAnswerHover != null && dialogQuestionText != null && !isTyping()) {
            //todo double click (Likely fixable via using a different event -- 7.4.0)
            choose(dialogAnswerHover);
            return true;
        }

        // Right mouse button
        if (inGiftMode && button == 1) {
            NetworkHandler.sendToServer(new InteractionVillagerMessage("gui.button.gift", villager.asEntity().getUuid()));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean keyPressed(int keyChar, int keyCode, int unknown) {
        // Dreams: espacio / enter completan el texto; 1-9 eligen respuesta
        if ((keyChar == GLFW.GLFW_KEY_SPACE || keyChar == GLFW.GLFW_KEY_ENTER) && isTyping()) {
            skipTyping();
            return true;
        }
        if (keyChar >= GLFW.GLFW_KEY_1 && keyChar <= GLFW.GLFW_KEY_9 && hasAnswers() && !isTyping()) {
            int index = keyChar - GLFW.GLFW_KEY_1;
            if (index < dialogAnswers.size()) {
                choose(dialogAnswers.get(index));
                return true;
            }
        }

        // Hotkey to leave gift mode
        if (keyChar == GLFW.GLFW_KEY_ESCAPE) {
            if (inGiftMode) {
                inGiftMode = false;
                setLayout("interact");
            } else {
                close();
            }
            return true;
        }
        return false;
    }

    private void drawIcons(DrawContext context) {
        final MatrixStack matrices = context.getMatrices();
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);

        matrices.push();
        matrices.scale(iconScale, iconScale, iconScale);

        if (marriageState != null) {
            drawIcon(context, ICON_TEXTURES, marriageState.getIcon());
        }

        drawIcon(context, ICON_TEXTURES, memory.getHearts() < 0 ? "blackHeart" : memory.getHearts() >= 100 ? "goldHeart" : "redHeart");
        // drawIcon(transform, "neutralEmerald");
        drawIcon(context, ICON_TEXTURES, "genes");

        if (canDrawParentsIcon()) {
            drawIcon(context, ICON_TEXTURES, "parents");
        }
        if (canDrawGiftIcon()) {
            drawIcon(context, ICON_TEXTURES, "gift");
        }

        if (analysis != null) {
            drawIcon(context, ICON_TEXTURES, "analysis");
        }

        matrices.pop();
    }

    private void drawTextPopups(DrawContext context) {
        // Dreams: ficha del aldeano en un panel, en lugar de los carteles sueltos
        VillagerBrain<?> brain = villager.getVillagerBrain();
        int cx = 8;
        int cy = 8;
        int cw = 168;
        Set<Traits.Trait> traits = villager.getTraits().getTraits();
        int ch = traits.isEmpty() ? 64 : 76;
        VnStyle.panel(context, cx, cy, cw, ch, VnStyle.PANEL_SOFT, VnStyle.BORDER);

        Text title = inGiftMode ? Text.translatable("gui.interact.label.giveGift") : villager.asEntity().getName();
        MatrixStack m = context.getMatrices();
        m.push();
        m.translate(cx + 8, cy + 7, 0);
        m.scale(1.25F, 1.25F, 1.0F);
        context.drawTextWithShadow(textRenderer, title, 0, 0, inGiftMode ? VnStyle.ACCENT : nameColor());
        m.pop();

        Text sub = villager.asEntity().isBaby() ? villager.getAgeState().getName() : villager.getProfessionText();
        context.drawTextWithShadow(textRenderer, sub, cx + 8, cy + 21, VnStyle.TEXT_DIM);
        context.fill(cx + 8, cy + 32, cx + cw - 8, cy + 33, VnStyle.BORDER);

        context.drawTextWithShadow(textRenderer,
                Text.translatable("gui.interact.label.mood", brain.getMood().getText()).formatted(brain.getMood().getColor()),
                cx + 8, cy + 37, VnStyle.TEXT);
        context.drawTextWithShadow(textRenderer,
                Text.translatable("gui.interact.label.personality", brain.getPersonality().getName()),
                cx + 8, cy + 49, VnStyle.TEXT);
        if (hoveringOver(cx, cy + 46, cw, 12)) {
            context.drawTooltip(textRenderer, brain.getPersonality().getDescription(), cx + cw + 4, cy + 58);
        }

        if (!traits.isEmpty()) {
            MutableText traitText = Text.translatable("traits.title");
            traits.stream().map(Traits.Trait::getName).forEach(t -> {
                if (traitText.getSiblings().size() > 0) {
                    traitText.append(Text.literal(", "));
                }
                traitText.append(t);
            });
            List<OrderedText> tl = textRenderer.wrapLines(traitText, cw - 16);
            if (!tl.isEmpty()) {
                context.drawTextWithShadow(textRenderer, tl.get(0), cx + 8, cy + 61, VnStyle.TEXT_DIM);
            }
            if (hoveringOver(cx, cy + 58, cw, 12)) {
                List<Text> details = traits.stream().map(Traits.Trait::getDescription).collect(Collectors.toList());
                details.add(0, Text.translatable("traits.title"));
                context.drawTooltip(textRenderer, details, cx + cw + 4, cy + 70);
            }
        }

        //hearts
        if (hoveringOverIcon("redHeart")) {
            int hearts = brain.getMemoriesForPlayer(player).getHearts();
            drawHoveringIconText(context, Text.literal(hearts + " hearts"), "redHeart");
        }

        //marriage status
        if (marriageState != null && hoveringOverIcon("married") && villager instanceof CompassionateEntity<?>) {
            String ms = marriageState.base().getIcon().toLowerCase(Locale.ENGLISH);
            drawHoveringIconText(context, Text.translatable("gui.interact.label." + ms, spouse), "married");
        }

        //parents
        if (canDrawParentsIcon() && hoveringOverIcon("parents")) {
            drawHoveringIconText(context, Text.translatable("gui.interact.label.parents",
                    father == null ? Text.translatable("gui.interact.label.parentUnknown") : father,
                    mother == null ? Text.translatable("gui.interact.label.parentUnknown") : mother
            ), "parents");
        }

        //gift
        if (canDrawGiftIcon() && hoveringOverIcon("gift")) {
            drawHoveringIconText(context, Text.translatable("gui.interact.label.gift"), "gift");
        }

        //genes
        if (hoveringOverIcon("genes")) {
            List<Text> lines = new LinkedList<>();
            lines.add(Text.literal("Genes"));

            for (Genetics.Gene gene : villager.getGenetics()) {
                String key = gene.getType().getTranslationKey();
                int value = (int)(gene.get() * 100);
                lines.add(Text.translatable("gene.tooltip", Text.translatable(key), value));
            }

            drawHoveringIconText(context, lines, "genes");
        }

        //analysis
        if (hoveringOverIcon("analysis") && analysis != null) {
            List<Text> lines = new LinkedList<>();
            lines.add(Text.translatable("analysis.title").formatted(Formatting.GRAY));

            //summands
            for (Analysis.AnalysisElement d : analysis) {
                lines.add(Text.translatable("analysis." + d.getKey())
                        .append(Text.literal(": " + (d.isPositive() ? "+" : "") + d.getValue()))
                        .formatted(d.isPositive() ? Formatting.GREEN : Formatting.RED));
            }

            //total
            String chance = analysis.getTotalAsString();
            lines.add(Text.translatable("analysis.total").append(": " + chance));

            drawHoveringIconText(context, lines, "analysis");
        }

    }

    //checks if the mouse hovers over a tooltip
    //tooltips are not rendered on the given coordinates, so we need an offset
    private boolean hoveringOverText(int x, int y, int w) {
        return hoveringOver(x + 8, y - 16, w, 16);
    }

    private boolean canDrawParentsIcon() {
        return father != null || mother != null;
    }

    private boolean canDrawGiftIcon() {
        return false;//villager.getVillagerBrain().getMemoriesForPlayer(player).isGiftPresent();
    }

    public void setDialogue(String dialogue, List<String> answers) {
        dialogQuestionId = dialogue;
        dialogAnswers = answers;
    }

    public void setLastPhrase(MutableText questionText, boolean silent) {
        MutableText text;
        if (!silent) {
            text = villager.sendChatMessage(questionText, player);
        } else {
            text = villager.transformMessage(questionText);
        }
        dialogQuestionText = textRenderer.wrapLines(text, textWidth());
        fullText = text.getString();
        shown = 0;
        pause = 0;
        lastBipAt = -1;
        lastFrame = Util.getMeasuringTimeMs();
    }

    // ------------------------------------------------------------------ Dreams: novela visual

    private void choose(String answer) {
        NetworkHandler.sendToServer(new InteractionDialogueMessage(villager.asEntity().getUuid(), dialogQuestionId, answer));
        dialogAnswers = null;
        dialogAnswerHover = null;
    }

    private boolean hasAnswers() {
        return dialogAnswers != null && !dialogAnswers.isEmpty();
    }

    private boolean isTyping() {
        return shown < fullText.length();
    }

    private void skipTyping() {
        shown = fullText.length();
    }

    private int boxWidth() {
        return Math.min(width - 40, 440);
    }

    private int boxX() {
        return (width - boxWidth()) / 2;
    }

    private int boxY() {
        return height - BOX_HEIGHT - 10;
    }

    private int textWidth() {
        return boxWidth() - 28;
    }

    private boolean insideBox(double x, double y) {
        return x >= boxX() && x <= boxX() + boxWidth() && y >= boxY() && y <= boxY() + BOX_HEIGHT;
    }

    /** Letra por letra, con pausas en la puntuacion y un bip cada dos letras. */
    private void advanceText() {
        long now = Util.getMeasuringTimeMs();
        long elapsed = Math.min(100, now - lastFrame);
        lastFrame = now;
        if (!isTyping()) {
            return;
        }
        if (pause > 0) {
            pause -= (int) elapsed;
            return;
        }
        int before = (int) shown;
        shown = Math.min(fullText.length(), shown + elapsed * CHARS_PER_SECOND / 1000.0F);
        for (int i = before; i < (int) shown; i++) {
            char c = fullText.charAt(i);
            if (VillagerVoice.voiced(c) && i - lastBipAt >= 2) {
                voice.bip();
                lastBipAt = i;
            }
            if (c == ',' || c == ';' || c == ':') {
                pause = 110;
                shown = i + 1;
                break;
            }
            if (c == '.' || c == '!' || c == '?' || c == '\u2026') {
                pause = 260;
                shown = i + 1;
                break;
            }
        }
    }

    private int nameColor() {
        Gender gender = villager.getGenetics().getGender();
        return gender == Gender.FEMALE ? VnStyle.NAME_FEMALE : gender == Gender.MALE ? VnStyle.NAME_MALE : VnStyle.NAME_OTHER;
    }

    private void drawDialogueBox(DrawContext context, int mouseX, int mouseY) {
        int x = boxX();
        int y = boxY();
        int w = boxWidth();

        // nombre
        Text name = villager.asEntity().getName();
        int nameW = textRenderer.getWidth(name) + 20;
        VnStyle.panel(context, x + 10, y - 15, nameW, 17, VnStyle.PANEL, VnStyle.BORDER);
        context.fill(x + 14, y - 10, x + 16, y - 3, nameColor());
        context.drawTextWithShadow(textRenderer, name, x + 20, y - 10, nameColor());

        // caja
        VnStyle.panel(context, x, y, w, BOX_HEIGHT, VnStyle.PANEL, VnStyle.BORDER);

        dialogAnswerHover = null;
        if (dialogQuestionText == null || fullText.isEmpty()) {
            Text hint = Text.translatable("gui.dreams.vn.choose");
            context.drawTextWithShadow(textRenderer, hint, x + 14, y + 12, VnStyle.TEXT_DIM);
            return;
        }

        String visible = fullText.substring(0, Math.min(fullText.length(), (int) shown));
        List<OrderedText> lines = textRenderer.wrapLines(StringVisitable.plain(visible), textWidth());
        int ty = y + 12;
        for (int i = 0; i < Math.min(lines.size(), 5); i++) {
            context.drawTextWithShadow(textRenderer, lines.get(i), x + 14, ty, VnStyle.TEXT);
            ty += 11;
        }

        if (!isTyping()) {
            // indicador de "listo"
            if (!hasAnswers() && (Util.getMeasuringTimeMs() / 400) % 2 == 0) {
                context.drawTextWithShadow(textRenderer, Text.literal("\u25BC"), x + w - 16, y + BOX_HEIGHT - 14, VnStyle.ACCENT);
            }
            drawAnswers(context, mouseX, mouseY);
        }
    }

    /** Respuestas apiladas arriba de la caja, a la derecha, como en una novela visual. */
    private void drawAnswers(DrawContext context, int mouseX, int mouseY) {
        if (!hasAnswers()) {
            return;
        }
        int bw = Math.min(240, boxWidth() - 20);
        int bx = boxX() + boxWidth() - bw - 10;
        int by = boxY() - 22 - dialogAnswers.size() * 18;
        for (int i = 0; i < dialogAnswers.size(); i++) {
            String a = dialogAnswers.get(i);
            int yy = by + i * 18;
            boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= yy && mouseY <= yy + 16;
            VnStyle.panel(context, bx, yy, bw, 16, hover ? 0xF0262033 : VnStyle.PANEL_SOFT, hover ? VnStyle.BORDER_HOVER : VnStyle.BORDER);
            if (hover) {
                context.fill(bx + 2, yy + 3, bx + 4, yy + 13, VnStyle.ACCENT);
                dialogAnswerHover = a;
            }
            Text label = Text.literal((i + 1) + ". ").formatted(Formatting.GOLD)
                    .append(Text.translatable(Question.getTranslationKey(dialogQuestionId, a)));
            context.drawTextWithShadow(textRenderer, label, bx + 8, yy + 4, hover ? VnStyle.ACCENT : VnStyle.TEXT);
        }
    }

    @Override
    protected void buttonPressed(Button button) {
        String id = button.identifier();

        if (timeSinceLastClick <= 2) {
            return; /* Prevents click-through on Mojang's button system */
        }
        timeSinceLastClick = 0;

        /* Progression to different GUIs */
        if (id.equals("gui.button.interact")) {
            setLayout("interact");
        } else if (id.equals("gui.button.command")) {
            setLayout("command");
            disableButton("gui.button." + villager.getVillagerBrain().getMoveState().name().toLowerCase(Locale.ENGLISH));
        } else if (id.equals("gui.button.clothing")) {
            setLayout("clothing");
        } else if (id.equals("gui.button.familyTree")) {
            MinecraftClient.getInstance().setScreen(new FamilyTreeScreen(villager.asEntity().getUuid()));
        } else if (id.equals("gui.button.talk")) {
            clearChildren();
            NetworkHandler.sendToServer(new InteractionDialogueInitMessage(villager.asEntity().getUuid()));
        } else if (id.equals("gui.button.work")) {
            setLayout("work");
            disableButton("gui.button." + villager.getVillagerBrain().getCurrentJob().name().toLowerCase(Locale.ENGLISH));
        } else if (id.equals("gui.button.professions")) {
            setLayout("professions");
        } else if (id.equals("gui.button.backarrow")) {
            if (inGiftMode) {
                inGiftMode = false;
                setLayout("interact");
            } else if (getActiveScreen().equals("locations")) {
                setLayout("interact");
            } else {
                setLayout("main");
            }
        } else if (id.equals("gui.button.locations")) {
            setLayout("locations");
        } else if (button.notifyServer()) {
            /* Anything that should notify the server is handled here */

            if (!button.targetServer()) {
                NetworkHandler.sendToServer(new InteractionVillagerMessage(id, villager.asEntity().getUuid()));
            }
        } else if (id.equals("gui.button.gift")) {
            this.inGiftMode = true;
            disableAllButtons();
        }
    }

    public static void setAnalysis(Analysis<?> analysis) {
        InteractScreen.analysis = analysis;
    }
}
