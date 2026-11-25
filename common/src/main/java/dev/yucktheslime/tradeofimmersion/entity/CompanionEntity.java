package dev.yucktheslime.tradeofimmersion.entity;

import dev.yucktheslime.tradeofimmersion.ai.CompanionBrain;
import dev.yucktheslime.tradeofimmersion.budget.AiBudget;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.entity.goal.CompanionGoals;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * The companion NPC: a {@link PathfinderMob} with a player-sized inventory (36 + 3 extra slots, plus armor, main hand
 * and offhand equipment), an owner, a skin, an MCA body and an AI brain. Item use goes through a loader fake player
 * (see {@link dev.yucktheslime.tradeofimmersion.action.ItemUse}) so items from other mods behave as for a player.
 */
public class CompanionEntity extends PathfinderMob {
    public static final int INVENTORY_SIZE = 39;
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> GENDER = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> MODE = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SLIM = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);

    public final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private String ownerName = "";
    private String persona = "";
    @Nullable
    private CompanionTask task;
    @Nullable
    private LivingEntity commandedTarget;
    /** Active MCA villager conversation. */
    @Nullable
    public UUID talkingVillager;
    public int villagerExchanges;
    public long lastVillagerLineTick;
    private long lastDamageEventTick;
    private int autoEatCooldown;

    public CompanionEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 2.0f);
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanFloat(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.ATTACK_SPEED, 4.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        CompanionGoals.register(this, goalSelector, targetSelector);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(SKIN, "");
        builder.define(GENDER, "");
        builder.define(MODE, "follow");
        builder.define(SLIM, false);
    }

    // ---------------------------------------------------------------- accessors

    public Optional<UUID> getOwnerId() {
        return entityData.get(OWNER);
    }

    public void setOwner(@Nullable Player player) {
        entityData.set(OWNER, Optional.ofNullable(player == null ? null : player.getUUID()));
        ownerName = player == null ? "" : player.getGameProfile().getName();
    }

    @Nullable
    public Player getOwner() {
        return getOwnerId().map(id -> level().getPlayerByUUID(id)).orElse(null);
    }

    public boolean isOwner(Entity e) {
        return e instanceof Player p && getOwnerId().map(id -> id.equals(p.getUUID())).orElse(false);
    }

    public String getOwnerName() {
        Player p = getOwner();
        return p != null ? p.getGameProfile().getName() : ownerName;
    }

    public String getSkin() {
        return entityData.get(SKIN);
    }

    public void setSkin(String spec) {
        entityData.set(SKIN, spec == null ? "" : spec.trim());
    }

    public boolean isSlim() {
        return entityData.get(SLIM);
    }

    public void setSlim(boolean slim) {
        entityData.set(SLIM, slim);
    }

    /** {@code female}, {@code male}, {@code neutral} or empty (config default). */
    public String getGender() {
        return entityData.get(GENDER);
    }

    public void setGender(String g) {
        entityData.set(GENDER, g == null ? "" : g);
    }

    /** {@code follow}, {@code stay} or {@code wander}. */
    public String getMode() {
        return entityData.get(MODE);
    }

    public void setMode(String mode) {
        entityData.set(MODE, mode);
        if (!"follow".equals(mode)) getNavigation().stop();
    }

    public String getPersona() {
        return persona;
    }

    public void setPersona(String persona) {
        this.persona = persona == null ? "" : persona;
    }

    public String name() {
        return getName().getString();
    }

    @Nullable
    public CompanionTask getTask() {
        return task;
    }

    public void setTask(@Nullable CompanionTask newTask) {
        if (task != null) task.stop(this);
        task = newTask;
    }

    @Nullable
    public LivingEntity getCommandedTarget() {
        return commandedTarget != null && commandedTarget.isAlive() ? commandedTarget : null;
    }

    public void setCommandedTarget(@Nullable LivingEntity target) {
        commandedTarget = target;
        setTarget(target);
    }

    // ---------------------------------------------------------------- speaking

    /** Says a line out loud to players within npcChatRadius (and its owner within npcChatOwnerRadius) and remembers it. */
    public void say(String text) {
        if (text == null || text.isBlank() || !(level() instanceof ServerLevel sl)) return;
        String line = text.length() > 400 ? text.substring(0, 400) + "..." : text;
        Component msg = Component.literal("<" + name() + "> ").withStyle(s -> s.withColor(0x7FD4FF)).append(Component.literal(line).withStyle(s -> s.withColor(0xFFFFFF)));
        var cfg = dev.yucktheslime.tradeofimmersion.config.ToiConfig.get();
        double r = Math.max(1, cfg.npcChatRadius), ro = Math.max(r, cfg.npcChatOwnerRadius);
        for (ServerPlayer p : sl.players()) {
            double d = p.distanceToSqr(this);
            if (d < r * r || isOwner(p) && d < ro * ro) dev.yucktheslime.tradeofimmersion.dialogue.DialogueNet.deliver(p, this, name(), line,
                    dev.yucktheslime.tradeofimmersion.dialogue.DialogueNet.KIND_COMPANION, "", msg);
        }
        NpcMemoryStore.get().chat(getUUID(), name(), line);
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        boolean owner = isOwner(player);
        ItemStack held = player.getItemInHand(hand);
        // MCA Reborn's villager editor, needle and thread and comb work on companions (compat.McaEditorBridge).
        String item = dev.yucktheslime.tradeofimmersion.compat.McaEditorBridge.itemId(held);
        if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND) {
            String which = switch (item) {
                case dev.yucktheslime.tradeofimmersion.compat.McaEditorBridge.EDITOR -> "edit";
                case dev.yucktheslime.tradeofimmersion.compat.McaEditorBridge.NEEDLE -> "needle";
                case dev.yucktheslime.tradeofimmersion.compat.McaEditorBridge.COMB -> "comb";
                default -> null;
            };
            if (which != null) {
                if (which.equals("edit") && player.isShiftKeyDown() && (owner || player.hasPermissions(2))) openInventory(player);
                else dev.yucktheslime.tradeofimmersion.compat.McaEditorBridge.open(sp, this, which);
                return InteractionResult.CONSUME;
            }
        }
        if (getOwnerId().isEmpty()) {
            setOwner(player);
            say("Hello " + player.getGameProfile().getName() + ", I'll come with you.");
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && (owner || player.hasPermissions(2))) {
            openInventory(player);
            return InteractionResult.CONSUME;
        }
        if (ToiConfig.get().companionMenuOnRightClick && player instanceof ServerPlayer sp) {
            if (hand == InteractionHand.MAIN_HAND) dev.yucktheslime.tradeofimmersion.menu.CompanionMenu.open(sp, this);
            return InteractionResult.CONSUME;
        }
        if (!held.isEmpty() && owner) {
            giveFrom(player, hand);
            return InteractionResult.CONSUME;
        }
        if (owner) {
            setMode("follow".equals(getMode()) ? "stay" : "follow");
            say("follow".equals(getMode()) ? "Following you." : "I'll wait here.");
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Moves the item in the player's hand into this companion's inventory (the owner's gift). */
    public void giveFrom(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            say("Hold what you want to give me.");
            return;
        }
        ItemStack given = held.copy();
        ItemStack rest = inventory.addItem(given);
        int moved = given.getCount() - rest.getCount();
        if (moved > 0) {
            held.shrink(moved);
            String what = moved + "x " + given.getHoverName().getString();
            NpcMemoryStore.get().event(getUUID(), "item_given", player.getGameProfile().getName() + " gave you " + what);
            CompanionBrain.onEvent(this, "item_given", player.getGameProfile().getName() + " just gave you " + what + ".");
        } else {
            say("My pockets are full.");
        }
    }

    public void openInventory(Player player) {
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x5, id, inv, new CompanionContainer(this), 5),
                    Component.literal(name())));
        }
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (tickCount == 2 || (tickCount + getId()) % 40 == 0) dev.yucktheslime.tradeofimmersion.body.CompanionLooks.tick(this);
        if (task != null && task.tick(this)) {
            CompanionTask done = task;
            task = null;
            done.stop(this);
        }
        if (isPassenger()) rideTick2();
        if (getCommandedTarget() == null && commandedTarget != null) commandedTarget = null;
        if (--autoEatCooldown <= 0) {
            autoEatCooldown = 40;
            if (getHealth() < getMaxHealth() * 0.6f && getTarget() == null && !isUsingItem()) eatSomething();
        }
    }

    private void rideTick2() {
        Vec3 dest = task != null ? task.destination(this) : null;
        if (dest == null && "follow".equals(getMode())) {
            Player owner = getOwner();
            if (owner != null && owner.getVehicle() != getVehicle() && owner.distanceToSqr(this) > 36) dest = owner.position();
        }
        if (dest == null) {
            stopVehicle();
            return;
        }
        steerVehicleTo(dest, 1.4);
    }

    /** Steers the vehicle this companion rides toward {@code dest}. Boats via their inputs, mobs (horses, pigs, modded mounts) via navigation. */
    public void steerVehicleTo(Vec3 dest, double speed) {
        Entity v = getVehicle();
        if (v == null) return;
        double dx = dest.x - v.getX(), dz = dest.z - v.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (v instanceof Boat boat) {
            float desired = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            float diff = Mth.wrapDegrees(desired - boat.getYRot());
            boolean forward = dist > 2.0 && Math.abs(diff) < 70;
            boat.setInput(diff < -8, diff > 8, forward, false); // paddle animation on clients
            // Boat.controlBoat() only runs on the client for the controlling player, so drive it here on the server
            float turn = Mth.clamp(diff, -4f, 4f);
            boat.setYRot(boat.getYRot() + turn);
            if (forward) {
                float rad = boat.getYRot() * Mth.DEG_TO_RAD;
                double f = 0.04 * Math.min(1.0, dist / 4.0);
                boat.setDeltaMovement(boat.getDeltaMovement().add(Mth.sin(-rad) * f, 0, Mth.cos(rad) * f));
            }
        } else if (v instanceof Mob m) {
            if (tickCount % 10 == 0 || m.getNavigation().isDone()) m.getNavigation().moveTo(dest.x, dest.y, dest.z, speed);
        }
        setYRot(v.getYRot());
    }

    public void stopVehicle() {
        Entity v = getVehicle();
        if (v instanceof Boat boat) boat.setInput(false, false, false, false);
        else if (v instanceof Mob m && tickCount % 20 == 0) m.getNavigation().stop();
    }

    /** Eats the best food in the inventory (vanilla or modded food component). */
    public boolean eatSomething() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && s.has(DataComponents.FOOD)) {
                ItemStack one = s.split(1);
                inventory.setChanged();
                eatNow(one);
                return true;
            }
        }
        return false;
    }

    public void eatNow(ItemStack food) {
        var props = food.get(DataComponents.FOOD);
        if (props == null) return;
        heal(Math.max(1, props.nutrition()));
        ItemStack rest = food.finishUsingItem(level(), this);
        if (!rest.isEmpty() && rest != food) {
            ItemStack left = inventory.addItem(rest);
            if (!left.isEmpty()) spawnAtLocation(left);
        }
        playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, 0.6f, 1.0f);
    }

    // ---------------------------------------------------------------- combat / damage

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        boolean result = super.hurt(source, amount);
        if (result && !level().isClientSide && attacker != null && level().getGameTime() - lastDamageEventTick > 60) {
            lastDamageEventTick = level().getGameTime();
            String who = attacker.getName().getString();
            NpcMemoryStore.get().event(getUUID(), "damage", who + " hit you (" + Math.round(amount) + " damage, health " + Math.round(getHealth()) + "/" + Math.round(getMaxHealth()) + ")");
            dev.yucktheslime.tradeofimmersion.ai.KagrenacWorld.event("npc_hurt", this, who + " hit " + name() + " (" + Math.round(amount)
                    + " damage, health " + Math.round(getHealth()) + "/" + Math.round(getMaxHealth()) + ")");
            if (attacker instanceof Player && ToiConfig.get().reactToEvents) {
                CompanionBrain.onEvent(this, "combat_started", who + " just hit you.");
            }
        }
        return result;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (isOwner(target)) return false;
        if (target instanceof CompanionEntity other && other.getOwnerId().equals(getOwnerId())) return false;
        return super.canAttack(target);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (isOwner(other)) return true;
        if (other instanceof CompanionEntity c && c.getOwnerId().isPresent() && c.getOwnerId().equals(getOwnerId())) return true;
        return super.isAlliedTo(other);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.removeItemNoUpdate(i);
            if (!s.isEmpty()) spawnAtLocation(s);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            Player owner = getOwner();
            if (owner instanceof ServerPlayer sp && dev.yucktheslime.tradeofimmersion.config.ToiConfig.get().chatCompanionDeathNotice) sp.sendSystemMessage(Component.literal(name() + " died: ").append(source.getLocalizedDeathMessage(this)));
            CompanionBrain.onCompanionGone(this);
        }
        super.die(source);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide) {
            if (reason != null && reason.shouldDestroy()) CompanionBrain.onCompanionGone(this);
            else AiBudget.onNpcUnloaded(getUUID());
        }
        super.remove(reason);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    // ---------------------------------------------------------------- persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        getOwnerId().ifPresent(id -> tag.putUUID("Owner", id));
        tag.putString("OwnerName", ownerName);
        tag.putString("Skin", getSkin());
        tag.putBoolean("Slim", isSlim());
        tag.putString("Gender", getGender());
        tag.putString("Mode", getMode());
        tag.putString("Persona", persona);
        tag.put("Inventory", saveInventory(level().registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        ownerName = tag.getString("OwnerName");
        setSkin(tag.getString("Skin"));
        setSlim(tag.getBoolean("Slim"));
        setGender(tag.getString("Gender"));
        if (tag.contains("Mode")) setMode(tag.getString("Mode"));
        persona = tag.getString("Persona");
        loadInventory(tag.getList("Inventory", Tag.TAG_COMPOUND), level().registryAccess());
    }

    private ListTag saveInventory(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putByte("Slot", (byte) i);
            list.add(s.save(registries, t));
        }
        return list;
    }

    private void loadInventory(ListTag list, HolderLookup.Provider registries) {
        inventory.clearContent();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int slot = t.getByte("Slot") & 255;
            if (slot < inventory.getContainerSize()) inventory.setItem(slot, ItemStack.parse(registries, t).orElse(ItemStack.EMPTY));
        }
    }
}
