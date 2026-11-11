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
    private static final int BOX_HEIGHT = 92;
    private static final float CHARS_PER_SECOND = 42.0F;
    private static UUID talkingTo;

    // Dreams: menu nuevo
    private String tab = "talk";
    private net.minecraft.client.gui.widget.TextFieldWidget chatField;
    private String playerLine;

    private final VillagerVoice voice;
    private VillagerVoice.Speech speech;
    private String fullText = "";
    private float shown;
    private long lastFrame;
    private int pause;
    private final long openedAt = Util.getMeasuringTimeMs();
    private long lineStartedAt;

    // Dreams 3: historial, emocion, corazones que suben o bajan
    private record LogLine(boolean me, String who, String text) {
    }

    private final List<LogLine> history = new java.util.ArrayList<>();
    private boolean showHistory;
    private int historyScroll;
    private VillagerVoice.Emotion emotion = VillagerVoice.Emotion.NEUTRAL;
    private int lastHearts = Integer.MIN_VALUE;
    private int heartDelta;
    private long heartDeltaAt;

    private void log(boolean me, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        history.add(new LogLine(me, me ? player.getName().getString() : villager.asEntity().getName().getString(), text));
        if (history.size() > 60) {
            history.remove(0);
        }
        historyScroll = 0;
    }

    public InteractScreen(VillagerLike<?> villager) {
        super(Text.literal("Interact"));
        this.villager = villager;
        this.voice = VillagerVoice.of(villager);
        talkingTo = villager.asEntity().getUuid();
    }

    public boolean isVillager(UUID uuid) {
        return villager.asEntity().getUuid().equals(uuid);
    }

    /** Una respuesta del aldeano que llego por el chat (por ejemplo de la IA): va a la caja, con voz. */
    public void showVillagerLine(Text text) {
        dialogQuestionId = null;
        dialogAnswers = null;
        dialogQuestionText = textRenderer.wrapLines(text, textWidth());
        fullText = text.getString();
        emotion = VillagerVoice.emotionOf(villager, text);
        speech = voice.plan(fullText, emotion);
        log(false, fullText);
        net.mca.client.dreams.DreamsPoses.startSolo(villager.asEntity(), fullText.length() % 2 == 0 ? "talk" : "talk2");
        shown = 0;
        pause = 0;
        lastFrame = Util.getMeasuringTimeMs();
        lineStartedAt = lastFrame;
        playerLine = null;
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
        setLayout("main");
    }

    // ------------------------------------------------------------------ Dreams: menu de interaccion

    private static Map<String, Button> mcaButtons;

    /** Los botones originales de MCA, para saber si una orden esta permitida (por ejemplo "comerciar"). */
    private static Map<String, Button> mcaButtons() {
        if (mcaButtons == null) {
            mcaButtons = new HashMap<>();
            for (String key : List.of("main", "interact", "work", "professions", "locations")) {
                MCAScreens.getInstance().getScreen(key).ifPresent(list -> {
                    for (Button b : list) {
                        mcaButtons.putIfAbsent(b.identifier(), b);
                    }
                });
            }
        }
        return mcaButtons;
    }

    private int panelX() {
        return width - 196;
    }

    @Override
    public void setLayout(String guiKey) {
        clearChildren();
        if (inGiftMode) {
            return;
        }
        int px = panelX();
        int py = 10;

        // pestanas
        int tabs = net.mca.client.gui.dreams.DreamsActions.TABS.size();
        int tw = (184 - (tabs - 1)) / tabs;
        for (int i = 0; i < net.mca.client.gui.dreams.DreamsActions.TABS.size(); i++) {
            var t = net.mca.client.gui.dreams.DreamsActions.TABS.get(i);
            boolean selected = t.id().equals(tab);
            addDrawableChild(new net.mca.client.gui.dreams.VnButton(px + 4 + i * (tw + 1), py + 4, tw, 20,
                    Text.literal(t.icon()), selected, Text.translatable(t.label()), b -> {
                tab = t.id();
                setLayout("main");
            }));
        }

        // acciones de la pestana
        var current = net.mca.client.gui.dreams.DreamsActions.TABS.stream().filter(t -> t.id().equals(tab)).findFirst().orElseThrow();
        int col = 0;
        int row = 0;
        boolean adult = !villager.asEntity().isBaby() && villager.getAgeState() == net.mca.entity.ai.relationship.AgeState.ADULT;
        boolean attached = villager.asEntity() instanceof net.mca.entity.VillagerEntityMCA av && av.dreamsAttach != 0
                && MinecraftClient.getInstance().player != null && av.dreamsAttachTo == MinecraftClient.getInstance().player.getId();
        for (var a : current.actions()) {
            if (a.adultOnly() && !adult) {
                continue;
            }
            if (a.id().equals("touch") && (attached || !net.mca.client.gui.dreams.DreamsActions.touchAvailable())) {
                continue;
            }
            // enganchados: solo "Soltar"; sueltos: no hay nada que soltar
            if (a.id().equals("release") != attached && (a.id().equals("release") || a.id().startsWith("hold_") || a.id().startsWith("carry_"))) {
                continue;
            }
            boolean active = true;
            if (a.kind() == net.mca.client.gui.dreams.DreamsActions.Kind.COMMAND) {
                Button original = mcaButtons().get(a.target());
                if (original != null && !original.isValidForConstraint(getConstraints())) {
                    if (original.hideOnFail()) {
                        continue;
                    }
                    active = false;
                }
            }
            int bx = px + 4 + col * 94;
            int by = py + 30 + row * 21;
            var button = new net.mca.client.gui.dreams.VnButton(bx, by, 92, 19, Text.translatable(a.label()), false, null, b -> runAction(a));
            button.active = active;
            addDrawableChild(button);
            if (++col == 2) {
                col = 0;
                row++;
            }
        }

        // campo para escribirle
        chatField = new net.minecraft.client.gui.widget.TextFieldWidget(textRenderer, boxX() + 92, boxY() + BOX_HEIGHT - 19, textWidth(), 14, Text.translatable("gui.dreams.say"));
        chatField.setMaxLength(200);
        chatField.setDrawsBackground(false);
        chatField.setPlaceholder(Text.translatable("gui.dreams.say").formatted(Formatting.ITALIC, Formatting.DARK_GRAY));
        addDrawableChild(chatField);
    }

    private void runAction(net.mca.client.gui.dreams.DreamsActions.Action a) {
        UUID uuid = villager.asEntity().getUuid();
        switch (a.kind()) {
            case DIALOGUE -> NetworkHandler.sendToServer(new InteractionDialogueMessage(uuid, "main", a.target()));
            case COMMAND -> {
                if (a.target().equals("gui.button.inventory") || a.target().equals("gui.button.trade")) {
                    NetworkHandler.sendToServer(new InteractionVillagerMessage(a.target(), uuid));
                } else {
                    NetworkHandler.sendToServer(new InteractionVillagerMessage(a.target(), uuid));
                }
            }
            case CLIENT -> {
                switch (a.target()) {
                    case "say" -> {
                        setFocused(chatField);
                        chatField.setFocused(true);
                    }
                    case "gift" -> {
                        inGiftMode = true;
                        setLayout("main");
                    }
                    case "touch" -> {
                        int id = villager.asEntity().getId();
                        close();
                        net.mca.client.gui.dreams.DreamsActions.requestTouch(id);
                    }
                    case "family" -> MinecraftClient.getInstance().setScreen(new FamilyTreeScreen(uuid));
                    default -> {
                    }
                }
            }
        }
    }

    /** Fondo del menu de acciones (a la derecha) y titulo de la pestana. */
    private void drawActionPanel(DrawContext context) {
        if (inGiftMode) {
            Text hint = Text.translatable("gui.dreams.gift_hint");
            int w = textRenderer.getWidth(hint) + 20;
            VnStyle.panel(context, width - w - 8, 10, w, 20, VnStyle.PANEL, VnStyle.BORDER_HOVER);
            context.drawTextWithShadow(textRenderer, hint, width - w + 2, 16, VnStyle.ACCENT);
            return;
        }
        int count = 0;
        for (var t : net.mca.client.gui.dreams.DreamsActions.TABS) {
            if (t.id().equals(tab)) {
                count = t.actions().size();
            }
        }
        int rows = (count + 1) / 2;
        VnStyle.panel(context, panelX(), 10, 192, 32 + rows * 21, VnStyle.PANEL_SOFT, VnStyle.BORDER);
    }

    private void sendChat() {
        String text = chatField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        chatField.setText("");
        playerLine = text;
        log(true, text);
        NetworkHandler.sendToServer(new net.mca.network.c2s.DreamsChatMessage(villager.asEntity().getUuid(), text));
    }

    @Override
    public void tick() {
        timeSinceLastClick++;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        advanceText();
        drawBackdrop(context);
        drawPortrait(context, mouseX, mouseY);
        drawDialogueBox(context, mouseX, mouseY);
        drawActionPanel(context);
        super.render(context, mouseX, mouseY, tickDelta);

        drawTextPopups(context);
        drawHeartPopup(context);
        drawHints(context);
        if (showHistory) {
            drawHistory(context);
        }
    }

    /** "+5 ❤" que sube y se desvanece cuando cambian los corazones. */
    private void drawHeartPopup(DrawContext context) {
        int hearts = villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
        long now = Util.getMeasuringTimeMs();
        if (lastHearts != Integer.MIN_VALUE && hearts != lastHearts) {
            heartDelta = hearts - lastHearts;
            heartDeltaAt = now;
        }
        lastHearts = hearts;
        float t = (now - heartDeltaAt) / 1600.0F;
        if (heartDelta == 0 || t > 1) {
            return;
        }
        String txt = (heartDelta > 0 ? "+" : "") + heartDelta + " \u2764";
        int alpha = (int) (255 * (1 - t * t));
        int color = heartDelta > 0 ? 0xFF7FA8 : 0x9A9AA8;
        MatrixStack m = context.getMatrices();
        m.push();
        m.translate(boxX() + 40, boxY() - 60 - t * 26, 0);
        float sc = 1.6F + 0.3F * (1 - t);
        m.scale(sc, sc, 1);
        context.drawTextWithShadow(textRenderer, txt, -textRenderer.getWidth(txt) / 2, 0, (Math.max(8, alpha) << 24) | color);
        m.pop();
    }

    private void drawHints(DrawContext context) {
        if (inGiftMode) {
            return;
        }
        Text hint = Text.translatable(hasAnswers() ? "gui.dreams.hint.answers" : "gui.dreams.hint.idle");
        context.drawText(textRenderer, hint, boxX() + 6, height - 9, 0x90FFFFFF, false);
    }

    /** Historial de la conversacion (Tab): lo que dijo cada uno, como en una novela visual. */
    private void drawHistory(DrawContext context) {
        int w = Math.min(width - 40, 420);
        int h = Math.min(height - BOX_HEIGHT - 50, 190);
        int x = (width - w) / 2;
        int y = boxY() - h - 22;
        context.fill(0, 0, width, height, 0x70000000);
        VnStyle.panel(context, x, y, w, h, 0xF0140F1E, VnStyle.BORDER_HOVER);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.dreams.history"), x + 10, y + 7, VnStyle.ACCENT);
        context.fill(x + 8, y + 19, x + w - 8, y + 20, VnStyle.BORDER);
        List<OrderedText> lines = new java.util.ArrayList<>();
        List<Integer> colors = new java.util.ArrayList<>();
        for (LogLine l : history) {
            MutableText t = Text.literal(l.who() + ": ").formatted(l.me() ? Formatting.AQUA : Formatting.GOLD)
                    .append(Text.literal(l.text()).formatted(Formatting.WHITE));
            for (OrderedText o : textRenderer.wrapLines(t, w - 24)) {
                lines.add(o);
                colors.add(l.me() ? 0xFFCFE8FF : VnStyle.TEXT);
            }
        }
        int visible = (h - 30) / 10;
        historyScroll = Math.max(0, Math.min(historyScroll, Math.max(0, lines.size() - visible)));
        int start = Math.max(0, lines.size() - visible - historyScroll);
        int yy = y + 24;
        for (int i = start; i < Math.min(lines.size(), start + visible); i++) {
            context.drawTextWithShadow(textRenderer, lines.get(i), x + 12, yy, colors.get(i));
            yy += 10;
        }
        if (history.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.dreams.history.empty"), x + 12, y + 28, VnStyle.TEXT_DIM);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double d) {
        if (showHistory) {
            historyScroll += d > 0 ? 2 : -2;
            return true;
        }
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
        if (chatField != null && chatField.isFocused()) {
            if (keyChar == GLFW.GLFW_KEY_ENTER || keyChar == GLFW.GLFW_KEY_KP_ENTER) {
                sendChat();
                return true;
            }
            if (keyChar == GLFW.GLFW_KEY_ESCAPE) {
                chatField.setFocused(false);
                setFocused(null);
                return true;
            }
            return chatField.keyPressed(keyChar, keyCode, unknown);
        }

        // Dreams: Tab abre y cierra el historial
        if (keyChar == GLFW.GLFW_KEY_TAB) {
            showHistory = !showHistory;
            return true;
        }
        if (showHistory && keyChar == GLFW.GLFW_KEY_ESCAPE) {
            showHistory = false;
            return true;
        }
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
                setLayout("main");
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

        // relacion: nivel, barra de corazones y estado
        int hearts = brain.getMemoriesForPlayer(player).getHearts();
        String level = hearts < 0 ? "bad" : hearts < 10 ? "stranger" : hearts < 30 ? "known" : hearts < 60 ? "friend" : hearts < 100 ? "close" : "soulmate";
        int ry = cy + ch + 3;
        int rh = 30 + (marriageState != null && marriageState != RelationshipState.SINGLE ? 11 : 0) + (canDrawParentsIcon() ? 11 : 0) + (analysis != null ? 11 : 0);
        VnStyle.panel(context, cx, ry, cw, rh, VnStyle.PANEL_SOFT, VnStyle.BORDER);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.dreams.relation." + level), cx + 8, ry + 5,
                hearts < 0 ? 0xFF9A9AA8 : hearts >= 100 ? 0xFFFFD34D : 0xFFFF7FA8);
        String num = hearts + " \u2764";
        context.drawTextWithShadow(textRenderer, num, cx + cw - 8 - textRenderer.getWidth(num), ry + 5, VnStyle.TEXT_DIM);
        int bx0 = cx + 8, bx1 = cx + cw - 8, byy = ry + 17;
        context.fill(bx0, byy, bx1, byy + 5, 0xC0101018);
        float fill = Math.max(0, Math.min(1, hearts / 100.0F));
        if (hearts < 0) {
            context.fill(bx0, byy, bx0 + (int) ((bx1 - bx0) * Math.min(1, -hearts / 100.0F)), byy + 5, 0xFF6A6A78);
        } else {
            context.fillGradient(bx0, byy, bx0 + (int) ((bx1 - bx0) * fill), byy + 5, hearts >= 100 ? 0xFFFFE08A : 0xFFFF9FC0, hearts >= 100 ? 0xFFE0A020 : 0xFFE0508A);
        }
        for (int i = 1; i < 4; i++) {
            int tx = bx0 + (bx1 - bx0) * i / 4;
            context.fill(tx, byy, tx + 1, byy + 5, 0x60000000);
        }
        int ly = ry + 26;
        if (marriageState != null && marriageState != RelationshipState.SINGLE && villager instanceof CompassionateEntity<?>) {
            String ms = marriageState.base().getIcon().toLowerCase(Locale.ENGLISH);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(Text.translatable("gui.interact.label." + ms, spouse), cw - 16).getString(),
                    cx + 8, ly, 0xFFFFC9DC);
            ly += 11;
        }
        if (canDrawParentsIcon()) {
            Text parents = Text.translatable("gui.interact.label.parents",
                    father == null ? Text.translatable("gui.interact.label.parentUnknown") : father,
                    mother == null ? Text.translatable("gui.interact.label.parentUnknown") : mother);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(parents, cw - 16).getString(), cx + 8, ly, VnStyle.TEXT_DIM);
            ly += 11;
        }
        if (analysis != null) {
            Text last = Text.translatable("gui.dreams.last_chance", analysis.getTotalAsString());
            context.drawTextWithShadow(textRenderer, last, cx + 8, ly, VnStyle.TEXT_DIM);
            if (hoveringOver(cx, ly - 2, cw, 11)) {
                List<Text> lines = new LinkedList<>();
                lines.add(Text.translatable("analysis.title").formatted(Formatting.GRAY));
                for (Analysis.AnalysisElement d : analysis) {
                    lines.add(Text.translatable("analysis." + d.getKey())
                            .append(Text.literal(": " + (d.isPositive() ? "+" : "") + d.getValue()))
                            .formatted(d.isPositive() ? Formatting.GREEN : Formatting.RED));
                }
                lines.add(Text.translatable("analysis.total").append(": " + analysis.getTotalAsString()));
                context.drawTooltip(textRenderer, lines, cx + cw + 4, ly + 12);
            }
        }
        // genes: al pasar por el nombre
        if (hoveringOver(cx, cy, cw, 18) && !inGiftMode) {
            List<Text> lines = new LinkedList<>();
            lines.add(Text.translatable("gui.dreams.genes").formatted(Formatting.GRAY));
            for (Genetics.Gene gene : villager.getGenetics()) {
                lines.add(Text.translatable("gene.tooltip", Text.translatable(gene.getType().getTranslationKey()), (int) (gene.get() * 100)));
            }
            context.drawTooltip(textRenderer, lines, cx + cw + 4, cy + 12);
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
        emotion = VillagerVoice.emotionOf(villager, questionText);
        speech = voice.plan(fullText, emotion);
        log(false, fullText);
        // el aldeano gesticula mientras habla
        net.mca.client.dreams.DreamsPoses.startSolo(villager.asEntity(), fullText.length() % 2 == 0 ? "talk" : "talk2");
        shown = 0;
        pause = 0;
        lastFrame = Util.getMeasuringTimeMs();
        lineStartedAt = lastFrame;
    }

    // ------------------------------------------------------------------ Dreams: novela visual

    private void choose(String answer) {
        log(true, Text.translatable(Question.getTranslationKey(dialogQuestionId, answer)).getString());
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
        return Math.min(width - 30, 500);
    }

    private int boxX() {
        return (width - boxWidth()) / 2;
    }

    private int boxY() {
        return height - BOX_HEIGHT - 10;
    }

    private int textWidth() {
        return boxWidth() - 110;
    }

    private boolean insideBox(double x, double y) {
        return x >= boxX() && x <= boxX() + boxWidth() && y >= boxY() && y <= boxY() + BOX_HEIGHT;
    }

    /** Letra por letra, con pausas en la puntuacion y una silaba de voz por cada silaba del texto. */
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
        float speed = speech == null ? 40.0F : VillagerVoice.lettersPerSecond(speech.emotion);
        int before = (int) shown;
        shown = Math.min(fullText.length(), shown + elapsed * speed / 1000.0F);
        for (int i = before; i < (int) shown; i++) {
            char c = fullText.charAt(i);
            if (speech != null && speech.startsSyllable(i)) {
                speech.play(i);
            }
            if (c == ',' || c == ';' || c == ':') {
                pause = 120;
                shown = i + 1;
                break;
            }
            if (c == '.' || c == '!' || c == '?' || c == '\u2026') {
                pause = 280;
                shown = i + 1;
                break;
            }
        }
    }

    private int nameColor() {
        Gender gender = villager.getGenetics().getGender();
        return gender == Gender.FEMALE ? VnStyle.NAME_FEMALE : gender == Gender.MALE ? VnStyle.NAME_MALE : VnStyle.NAME_OTHER;
    }

    /** 0..1: animacion de entrada de la interfaz. */
    private float openProgress() {
        float t = Math.min(1.0F, (Util.getMeasuringTimeMs() - openedAt) / 260.0F);
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    /** Oscurece de a poco la parte de abajo de la pantalla, como en una novela visual. */
    private void drawBackdrop(DrawContext context) {
        float open = openProgress();
        int top = height - (int) (height * 0.55F);
        context.fillGradient(0, top, width, height, 0x00000000, VnStyle.alpha(0xC0080610, open));
        context.fillGradient(0, 0, width, 40, VnStyle.alpha(0x70080610, open), 0x00000000);
        // bandas de cine que entran al abrir
        int bar = (int) (12 * open);
        context.fill(0, 0, width, bar, 0xFF000000);
        context.fill(0, height - bar, width, height, 0xFF000000);
    }

    /**
     * El aldeano parado a la izquierda, detras de la caja, como el "sprite" del personaje.
     * Solo la cabeza sigue al cursor: el cuerpo queda derecho (el inventario de Minecraft inclina todo el cuerpo,
     * y en grande parecia que se hundia en la pantalla).
     */
    private void drawPortrait(DrawContext context, int mouseX, int mouseY) {
        net.minecraft.entity.LivingEntity living = villager.asEntity();
        float open = openProgress();
        int size = Math.max(48, Math.min(125, (int) (height / 2.7F)));
        int px = Math.max((int) (size * 0.42F) + 4, boxX() + 6) + (int) ((1.0F - open) * -40);
        int py = boxY() + 22;
        if (isTyping()) {
            // el "sprite" rebota un poquito mientras habla
            py -= (int) Math.round(Math.abs(Math.sin(Util.getMeasuringTimeMs() / 75.0)) * 1.6);
        }

        float dx = (float) Math.atan((px - mouseX) / 40.0F);
        float dy = (float) Math.atan((py - size * 1.6F - mouseY) / 40.0F);
        float bodyYaw = living.bodyYaw, yaw = living.getYaw(), pitch = living.getPitch();
        float prevHeadYaw = living.prevHeadYaw, headYaw = living.headYaw, prevBody = living.prevBodyYaw;
        living.bodyYaw = 180.0F + dx * 12.0F;
        living.prevBodyYaw = living.bodyYaw;
        living.setYaw(180.0F + dx * 40.0F);
        living.setPitch(-dy * 22.0F);
        living.headYaw = living.getYaw();
        living.prevHeadYaw = living.getYaw();
        net.mca.client.dreams.DreamsPoses.guiRender = true;
        try {
            org.joml.Quaternionf flip = new org.joml.Quaternionf().rotateZ((float) Math.PI);
            net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity(context, px, py, size, flip, null, living);
        } finally {
            net.mca.client.dreams.DreamsPoses.guiRender = false;
            living.bodyYaw = bodyYaw;
            living.prevBodyYaw = prevBody;
            living.setYaw(yaw);
            living.setPitch(pitch);
            living.prevHeadYaw = prevHeadYaw;
            living.headYaw = headYaw;
        }
    }

    private void drawDialogueBox(DrawContext context, int mouseX, int mouseY) {
        float open = openProgress();
        int x = boxX();
        int y = boxY() + (int) ((1.0F - open) * 30);
        int w = boxWidth();
        int textX = x + 92;

        // caja: degradado, doble borde y adornos en las esquinas
        VnStyle.box(context, x, y, w, BOX_HEIGHT, open);

        // cartel del nombre, con la cinta del color del aldeano
        Text name = villager.asEntity().getName();
        int nameW = textRenderer.getWidth(name) + 26;
        int nx = x + 84;
        int ny = y - 13;
        VnStyle.nameplate(context, nx, ny, nameW, 17, nameColor(), open);
        context.drawTextWithShadow(textRenderer, name, nx + 13, ny + 5, VnStyle.alpha(0xFFFFFFFF, open) | 0x01000000);
        // insignia de emocion de la linea actual
        String badge = fullText.trim().endsWith("?") ? "?" : switch (emotion) {
            case HAPPY -> "\u266a";
            case SAD -> "\u2026";
            case ANGRY -> "!!";
            default -> "";
        };
        if (!badge.isEmpty() && dialogQuestionText != null) {
            int bcol = switch (emotion) {
                case HAPPY -> 0xFFFFD34D;
                case SAD -> 0xFF8FB8FF;
                case ANGRY -> 0xFFFF6A5E;
                default -> 0xFFE8E2FF;
            };
            float pop = Math.min(1.0F, (Util.getMeasuringTimeMs() - lineStartedAt) / 220.0F);
            MatrixStack bm = context.getMatrices();
            bm.push();
            bm.translate(nx + nameW + 9, ny + 8 - 3 * (float) Math.sin(pop * Math.PI), 0);
            bm.scale(1.3F, 1.3F, 1);
            context.drawTextWithShadow(textRenderer, badge, -textRenderer.getWidth(badge) / 2, -4, bcol);
            bm.pop();
        }

        if (playerLine != null) {
            Text you = Text.translatable("gui.dreams.you", playerLine);
            int yw = Math.min(textRenderer.getWidth(you) + 16, w - 100);
            VnStyle.panel(context, x + w - yw - 6, y - 15, yw, 15, 0xE0221A2E, 0x60E2C98F);
            context.drawTextWithShadow(textRenderer, net.minecraft.util.Language.getInstance().reorder(textRenderer.trimToWidth(you, yw - 12)), x + w - yw + 2, y - 11, VnStyle.TEXT_DIM);
        }

        dialogAnswerHover = null;
        if (dialogQuestionText == null || fullText.isEmpty()) {
            Text hint = Text.translatable("gui.dreams.vn.choose");
            context.drawTextWithShadow(textRenderer, hint, textX, y + 16, VnStyle.alpha(VnStyle.TEXT_DIM, open) | 0x01000000);
            return;
        }

        String visible = fullText.substring(0, Math.min(fullText.length(), (int) shown));
        List<OrderedText> lines = textRenderer.wrapLines(StringVisitable.plain(visible), textWidth());
        int ty = y + 14;
        for (int i = 0; i < Math.min(lines.size(), 5); i++) {
            context.drawTextWithShadow(textRenderer, lines.get(i), textX, ty, VnStyle.TEXT);
            ty += 11;
        }

        if (!isTyping()) {
            if (!hasAnswers()) {
                // indicador de "listo" que sube y baja
                float bob = (float) Math.sin(Util.getMeasuringTimeMs() / 180.0) * 1.5F;
                context.getMatrices().push();
                context.getMatrices().translate(0, bob, 0);
                context.drawTextWithShadow(textRenderer, Text.literal("\u25BC"), x + w - 18, y + BOX_HEIGHT - 16, VnStyle.ACCENT);
                context.getMatrices().pop();
            }
            drawAnswers(context, mouseX, mouseY);
        }
    }

    /** Respuestas apiladas arriba de la caja, a la derecha, que entran deslizandose una por una. */
    private void drawAnswers(DrawContext context, int mouseX, int mouseY) {
        if (!hasAnswers()) {
            return;
        }
        long since = Util.getMeasuringTimeMs() - lineStartedAt;
        int bw = Math.min(250, boxWidth() - 100);
        int bx = boxX() + boxWidth() - bw - 6;
        int by = boxY() - 24 - dialogAnswers.size() * 21;
        for (int i = 0; i < dialogAnswers.size(); i++) {
            String a = dialogAnswers.get(i);
            float in = Math.max(0, Math.min(1, (since - fullText.length() * 1000.0F / 40.0F - i * 70) / 180.0F));
            in = 1.0F - (1.0F - in) * (1.0F - in);
            int yy = by + i * 21;
            int xx = bx + (int) ((1.0F - in) * 24);
            boolean hover = in > 0.9F && mouseX >= xx && mouseX <= xx + bw && mouseY >= yy && mouseY <= yy + 18;
            if (hover) {
                xx -= 4;
                dialogAnswerHover = a;
            }
            VnStyle.choice(context, xx, yy, bw, 18, hover, in);
            Text number = Text.literal(String.valueOf(i + 1));
            context.drawTextWithShadow(textRenderer, number, xx + 8, yy + 5, VnStyle.alpha(VnStyle.ACCENT, in) | 0x01000000);
            Text label = Text.translatable(Question.getTranslationKey(dialogQuestionId, a));
            context.drawTextWithShadow(textRenderer, label, xx + 20, yy + 5,
                    VnStyle.alpha(hover ? 0xFFFFFFFF : VnStyle.TEXT, in) | 0x01000000);
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
