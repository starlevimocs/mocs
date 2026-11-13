package com.starlevi.starnpcs;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;

/** El NPC. El servidor decide con quien habla y que dispara; cada cliente lo anima por su cuenta con el reloj del mundo. */
public class NpcEntity extends PathfinderMob implements Merchant {
    private static final EntityDataAccessor<String> PROFILE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> TALK = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);

    /** lo pone el cliente: sonidos y particulas de las animaciones */
    public static Consumer<NpcEntity> clientTick = npc -> {
    };

    private static final Profile FALLBACK = new Profile();

    private Profile profile = new Profile();
    /** solo cliente: perfil en edicion, para ver los cambios antes de guardar */
    public Profile preview;
    /** solo cliente: estado de animacion */
    public Object clientState;

    private Vec3 home;
    private float homeYaw;
    private UUID talker;
    private int talkTicks;
    private boolean sawPlayer;
    private int lineTimer = 100;
    private Player lookTarget;
    private Player tradingPlayer;
    private MerchantOffers offers;

    public NpcEntity(EntityType<? extends NpcEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0F);
        }
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    /** Crea un NPC en ese punto mirando hacia yaw. */
    @Nullable
    public static NpcEntity spawn(ServerLevel level, Vec3 at, float yaw, Profile profile) {
        NpcEntity npc = StarNpcs.NPC.get().create(level);
        if (npc == null) {
            return null;
        }
        if (profile.epoch == 0L) {
            profile.epoch = level.getGameTime();
        }
        npc.place(at, yaw);
        npc.setProfile(profile);
        level.addFreshEntity(npc);
        return npc;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PROFILE, new Profile().toJson());
        entityData.define(TALK, 0);
        entityData.define(YAW, 0F);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6) {
            @Override
            public boolean canUse() {
                return wandering() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return wandering() && super.canContinueToUse();
            }
        });
    }

    // ------------------------------------------------------------------ perfil

    public Profile profile() {
        if (profile == null) {
            // llamado desde el constructor de la clase padre, antes de que existan los campos
            return FALLBACK;
        }
        return preview != null && level().isClientSide ? preview : profile;
    }

    public void setProfile(Profile p) {
        profile = p;
        offers = null;
        entityData.set(PROFILE, p.toJson());
        applyHome();
        refreshDimensions();
    }

    public int talkId() {
        return entityData.get(TALK);
    }

    public boolean staying() {
        return !"wander".equals(profile().behavior);
    }

    private boolean wandering() {
        return !staying() && talker == null;
    }

    /** Mueve al NPC y fija ahi su casa y su orientacion. */
    public void place(Vec3 at, float yaw) {
        yaw = Mth.wrapDegrees(yaw);
        moveTo(at.x, at.y, at.z, yaw, 0F);
        home = at;
        homeYaw = yaw;
        yBodyRot = yaw;
        yBodyRotO = yaw;
        yHeadRot = yaw;
        yHeadRotO = yaw;
        entityData.set(YAW, yaw);
        applyHome();
    }

    public float homeYaw() {
        return homeYaw;
    }

    private void applyHome() {
        if (level().isClientSide) {
            return;
        }
        if (home == null) {
            home = position();
        }
        if (staying()) {
            clearRestriction();
            getNavigation().stop();
        } else {
            restrictTo(BlockPos.containing(home), Math.max(1, (int) profile.wanderRadius));
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PROFILE.equals(key) && level().isClientSide) {
            profile = Profile.fromJson(entityData.get(PROFILE));
            refreshDimensions();
        }
    }

    @Override
    public float getScale() {
        return profile().scale;
    }

    @Override
    protected Component getTypeName() {
        String n = profile().name;
        return n.isBlank() ? super.getTypeName() : Component.literal(n);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick.accept(this);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        Profile p = profile;
        Player t = talker == null ? null : level().getPlayerByUUID(talker);
        if (talker != null) {
            talkTicks++;
            if (t == null || !t.isAlive() || t.distanceToSqr(this) > 36 || talkTicks > 2400) {
                endTalk();
                t = null;
            }
        }
        if (tickCount % 5 == 0) {
            Player near = level().getNearestPlayer(this, p.seeRadius);
            if (near != null && !sawPlayer) {
                sawPlayer = true;
                if (!p.see.isBlank() && talker == null) {
                    trigger(p.see);
                }
            } else if (near == null && sawPlayer && level().getNearestPlayer(this, p.seeRadius + 3) == null) {
                sawPlayer = false;
            }
            lookTarget = t != null ? t : p.lookAtPlayer ? near : null;
        }
        if (lookTarget != null && lookTarget.isAlive()) {
            getLookControl().setLookAt(lookTarget, 20F, 40F);
        }
        if (staying()) {
            float target = homeYaw;
            if (p.turnToPlayer && lookTarget != null && lookTarget.isAlive()) {
                target = (float) (Mth.atan2(lookTarget.getZ() - getZ(), lookTarget.getX() - getX()) * Mth.RAD_TO_DEG) - 90F;
            }
            float cur = entityData.get(YAW);
            float next = Mth.approachDegrees(cur, target, 9F);
            if (Math.abs(Mth.wrapDegrees(next - cur)) > 0.01F) {
                entityData.set(YAW, next);
            }
        }
        if (!p.lines.isEmpty() && talker == null && --lineTimer <= 0) {
            lineTimer = (int) (p.linesEvery * 20F * (0.7F + random.nextFloat() * 0.6F));
            if (level().getNearestPlayer(this, 14) != null) {
                say(p.lines.get(random.nextInt(p.lines.size())));
            }
        }
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                if (staying()) {
                    float y = entityData.get(YAW);
                    setYRot(y);
                    yBodyRot = y;
                    yHeadRot = y + Mth.clamp(Mth.wrapDegrees(yHeadRot - y), -70F, 70F);
                } else {
                    super.clientTick();
                }
            }
        };
    }

    @Override
    public void travel(Vec3 input) {
        if (staying()) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        super.travel(input);
    }

    // ------------------------------------------------------------------ interaccion

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ServerPlayer sp = (ServerPlayer) player;
        if (player.getItemInHand(hand).is(StarNpcs.WAND.get()) && player.hasPermissions(2)) {
            Net.openEditor(sp, this, 0);
            return InteractionResult.SUCCESS;
        }
        talkTo(sp);
        return InteractionResult.SUCCESS;
    }

    public void talkTo(ServerPlayer player) {
        Profile p = profile;
        talker = player.getUUID();
        talkTicks = 0;
        entityData.set(TALK, player.getId());
        if (!p.interact.isBlank()) {
            trigger(p.interact);
        }
        boolean did = false;
        if (!p.dialogue.isBlank()) {
            String id = p.dialogue.contains(":") ? p.dialogue : "dreams:" + p.dialogue;
            run("dreams dialogue " + id + " {player}", player);
            did = true;
        }
        for (String cmd : p.commands) {
            run(cmd, player);
            did = true;
        }
        if (!did && !p.trades.isEmpty()) {
            openShop(player);
            did = true;
        }
        if (!did && !p.lines.isEmpty()) {
            say(p.lines.get(random.nextInt(p.lines.size())));
        }
    }

    private void endTalk() {
        talker = null;
        entityData.set(TALK, 0);
    }

    private void run(String command, ServerPlayer player) {
        if (getServer() == null) {
            return;
        }
        String cmd = command.trim().replace("{player}", player.getGameProfile().getName());
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        try {
            CommandSourceStack source = createCommandSourceStack().withPermission(2).withSuppressedOutput();
            getServer().getCommands().performPrefixedCommand(source, cmd);
        } catch (RuntimeException e) {
            StarNpcs.LOG.warn("[Star NPCs] Fallo el comando '{}' del NPC {}", cmd, profile.name, e);
        }
    }

    /** Dispara una animacion de una vez en todos los que lo ven. */
    public void trigger(String anim) {
        if (!level().isClientSide && !anim.isBlank()) {
            Net.toWatchers(this, new Net.Trigger(getId(), anim, level().getGameTime()));
        }
    }

    /** Muestra un globo de texto sobre su cabeza. */
    public void say(String text) {
        if (!level().isClientSide && !text.isBlank()) {
            Net.toWatchers(this, new Net.Bubble(getId(), text));
        }
    }

    public void openShop(ServerPlayer player) {
        if (getOffers().isEmpty() || tradingPlayer != null) {
            return;
        }
        setTradingPlayer(player);
        openTradingScreen(player, getDisplayName(), 1);
    }

    public static String stackToText(ItemStack stack) {
        return stack.isEmpty() ? "" : stack.save(new CompoundTag()).toString();
    }

    public static ItemStack textToStack(String text) {
        if (text == null || text.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            return ItemStack.of(TagParser.parseTag(text));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------ comerciante

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        tradingPlayer = player;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        if (offers == null) {
            offers = new MerchantOffers();
            for (Profile.Trade t : profile.trades) {
                ItemStack sell = textToStack(t.sell);
                ItemStack buy = textToStack(t.buy);
                if (!sell.isEmpty() && !buy.isEmpty()) {
                    offers.add(new MerchantOffer(buy, textToStack(t.buy2), sell, 0, Integer.MAX_VALUE, 0, 0F));
                }
            }
        }
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers o) {
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide;
    }

    // ------------------------------------------------------------------ resistencias

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || profile().invulnerable && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean fireImmune() {
        return profile().invulnerable;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !staying();
    }

    @Override
    protected void doPush(Entity entity) {
        if (!staying()) {
            super.doPush(entity);
        }
    }

    @Override
    public void push(double x, double y, double z) {
        if (!staying()) {
            super.push(x, y, z);
        }
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (!staying()) {
            super.knockback(strength, x, z);
        }
    }

    @Override
    public boolean isPushedByFluid() {
        return !staying();
    }

    // ------------------------------------------------------------------ guardado

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("StarProfile", profile.toJson());
        Vec3 h = home == null ? position() : home;
        tag.putDouble("HomeX", h.x);
        tag.putDouble("HomeY", h.y);
        tag.putDouble("HomeZ", h.z);
        tag.putFloat("HomeYaw", homeYaw);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) {
            home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
            homeYaw = tag.getFloat("HomeYaw");
            entityData.set(YAW, homeYaw);
        }
        if (tag.contains("StarProfile")) {
            setProfile(Profile.fromJson(tag.getString("StarProfile")));
        }
    }
}
