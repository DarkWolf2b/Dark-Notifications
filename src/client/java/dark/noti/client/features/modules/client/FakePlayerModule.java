package dark.noti.client.features.modules.client;

import com.mojang.authlib.GameProfile;
import dark.noti.client.features.modules.notifications.TotemPopNotifierModule;
import dark.noti.client.features.settings.BoolSetting;
import dark.noti.client.manager.Category;
import dark.noti.client.manager.Module;
import dark.noti.client.manager.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FakePlayerModule extends Module {
	public static FakePlayerModule INSTANCE;

	private static final UUID FAKE_UUID = UUID.fromString("00000000-0000-4000-8000-0000000000fa");
	private static final int FAKE_ENTITY_ID = -100000;
	/** Low HP so punches / crystals kill quickly. */
	private static final float MAX_HEALTH = 6.0F;

	private final dark.noti.client.features.settings.StringSetting displayName = add(new dark.noti.client.features.settings.StringSetting("Name", "FakePlayer", 16));
	private final BoolSetting autoTotem = add(new BoolSetting("AutoTotem", true));

	private RemotePlayer fakePlayer;
	private double spawnX;
	private double spawnY;
	private double spawnZ;
	private float spawnYaw;
	private float spawnPitch;
	private float spawnHeadYaw;
	private float spawnBodyYaw;
	private boolean attackWasDown;
	private int hurtCooldown;
	private final Map<Integer, ExplosiveTrack> trackedExplosives = new HashMap<>();

	public FakePlayerModule() {
		super("FakePlayer", Category.CLIENT);
		INSTANCE = this;
	}

	public static boolean isFakePlayerName(String name) {
		FakePlayerModule module = INSTANCE;
		return module != null && module.isEnabled() && name != null
			&& module.getDisplayName().equalsIgnoreCase(name.trim());
	}

	public String getDisplayName() {
		return displayName.get();
	}

	public boolean setDisplayName(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		displayName.set(name);
		if (fakePlayer != null) {
			applyDisplayName(fakePlayer);
		}
		return true;
	}

	public RemotePlayer getFakePlayer() {
		return fakePlayer;
	}

	public boolean trySetEnabled(boolean enabled) {
		if (isEnabled() == enabled) {
			return true;
		}
		if (enabled) {
			spawnFakePlayer();
			if (fakePlayer == null) {
				return false;
			}
		}
		super.setEnabled(enabled);
		if (!enabled) {
			removeFakePlayer();
		}
		return true;
	}

	@Override
	public void setEnabled(boolean enabled) {
		trySetEnabled(enabled);
	}

	@Override
	protected void onEnable() {
		if (fakePlayer == null) {
			spawnFakePlayer();
		}
	}

	@Override
	protected void onDisable() {
		removeFakePlayer();
	}

	@Override
	public void onTick() {
		if (fakePlayer == null) {
			return;
		}

		if (hurtCooldown > 0) {
			hurtCooldown--;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player != null && client.level != null) {
			handleAttack(client, player);
			if (fakePlayer == null) {
				return;
			}
			handleExplosions(client.level);
			if (fakePlayer == null) {
				return;
			}
		}

		if (!fakePlayer.isAlive() || fakePlayer.getHealth() <= 0.0F) {
			killOrPop();
			return;
		}

		fakePlayer.setPos(spawnX, spawnY, spawnZ);
		fakePlayer.setDeltaMovement(Vec3.ZERO);
		fakePlayer.setYRot(spawnYaw);
		fakePlayer.setXRot(spawnPitch);
		fakePlayer.yHeadRot = spawnHeadYaw;
		fakePlayer.yBodyRot = spawnBodyYaw;
	}

	private void handleAttack(Minecraft client, LocalPlayer player) {
		long window = client.getWindow().handle();
		boolean attackDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean clicked = attackDown && !attackWasDown;
		attackWasDown = attackDown;
		if (!clicked || hurtCooldown > 0 || client.gui.screen() != null) {
			return;
		}
		HitResult hit = client.hitResult;
		if (!(hit instanceof EntityHitResult entityHit) || entityHit.getEntity() != fakePlayer) {
			return;
		}

		float damage = Math.max(2.0F, player.getAttackStrengthScale(0.5F) * 8.0F);
		applyDamage(damage);
	}

	private void handleExplosions(ClientLevel level) {
		Set<Integer> seen = new HashSet<>();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof EndCrystal) {
				seen.add(entity.getId());
				trackedExplosives.put(entity.getId(), new ExplosiveTrack(entity.position(), 6.0F));
			} else if (entity instanceof PrimedTnt) {
				seen.add(entity.getId());
				trackedExplosives.put(entity.getId(), new ExplosiveTrack(entity.position(), 4.0F));
			}
		}

		Iterator<Map.Entry<Integer, ExplosiveTrack>> it = trackedExplosives.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, ExplosiveTrack> entry = it.next();
			if (seen.contains(entry.getKey())) {
				continue;
			}
			ExplosiveTrack blast = entry.getValue();
			it.remove();
			applyExplosionDamage(blast.pos, blast.power);
		}
	}

	private void applyExplosionDamage(Vec3 blast, float power) {
		if (fakePlayer == null) {
			return;
		}
		double dist = fakePlayer.position().distanceTo(blast);
		double radius = power * 2.0;
		if (dist >= radius) {
			return;
		}
		float impact = (float) (1.0 - dist / radius);
		float damage = (impact * impact + impact) * power * 3.5F + 1.0F;
		applyDamage(Math.max(2.0F, damage));
	}

	private void applyDamage(float damage) {
		if (fakePlayer == null) {
			return;
		}
		fakePlayer.hurtTime = 10;
		fakePlayer.hurtDuration = 10;
		hurtCooldown = 5;

		float next = fakePlayer.getHealth() - damage;
		if (next <= 0.0F) {
			fakePlayer.setHealth(0.0F);
			killOrPop();
		} else {
			fakePlayer.setHealth(next);
		}
	}

	private void killOrPop() {
		if (fakePlayer == null) {
			return;
		}
		if (hasTotem(fakePlayer) || ensureTotem()) {
			doTotemPop();
			return;
		}
		removeFakePlayer();
		super.setEnabled(false);
	}

	private boolean ensureTotem() {
		if (!autoTotem.getValue() || fakePlayer == null) {
			return false;
		}
		if (hasTotem(fakePlayer)) {
			return true;
		}
		fakePlayer.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		return true;
	}

	private void doTotemPop() {
		if (fakePlayer == null) {
			return;
		}
		popTotem(fakePlayer);
		TotemPopNotifierModule notifier = ModuleManager.get().get(TotemPopNotifierModule.class);
		if (notifier != null && notifier.isEnabled()) {
			notifier.onTotemPop(fakePlayer.getUUID(), getDisplayName());
		}
		if (autoTotem.getValue()) {
			fakePlayer.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		}
		fakePlayer.setHealth(MAX_HEALTH);
	}

	private void spawnFakePlayer() {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null) {
			return;
		}
		removeFakePlayer();
		spawnX = player.getX();
		spawnY = player.getY();
		spawnZ = player.getZ();
		spawnYaw = player.getYRot();
		spawnPitch = player.getXRot();
		spawnHeadYaw = player.getYHeadRot();
		spawnBodyYaw = player.yBodyRot;

		GameProfile profile = new GameProfile(FAKE_UUID, getDisplayName());
		fakePlayer = new RemotePlayer(level, profile);
		fakePlayer.setId(FAKE_ENTITY_ID);
		fakePlayer.setUUID(FAKE_UUID);
		applyDisplayName(fakePlayer);
		fakePlayer.setPos(spawnX, spawnY, spawnZ);
		fakePlayer.setYRot(spawnYaw);
		fakePlayer.setXRot(spawnPitch);
		fakePlayer.setYHeadRot(spawnHeadYaw);
		fakePlayer.yBodyRot = spawnBodyYaw;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			fakePlayer.setItemSlot(slot, player.getItemBySlot(slot).copy());
		}
		if (!hasTotem(fakePlayer) && autoTotem.getValue()) {
			fakePlayer.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		}
		fakePlayer.setHealth(MAX_HEALTH);
		fakePlayer.setAbsorptionAmount(0.0F);
		fakePlayer.setInvulnerable(false);
		level.addEntity(fakePlayer);
		hurtCooldown = 0;
		attackWasDown = false;
		trackedExplosives.clear();
	}

	private void removeFakePlayer() {
		trackedExplosives.clear();
		if (fakePlayer == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level != null) {
			client.level.removeEntity(fakePlayer.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
		}
		fakePlayer = null;
	}

	private static boolean hasTotem(net.minecraft.world.entity.player.Player player) {
		return player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
			|| player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
	}

	private static void popTotem(net.minecraft.world.entity.player.Player player) {
		if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		} else if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
			player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		player.setHealth(MAX_HEALTH);
		player.setAbsorptionAmount(8.0F);
		player.hurtTime = 0;
		player.hurtDuration = 0;
		player.deathTime = 0;
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		// Same as ClientPacketListener entity event 35: tracking emitter for 30 ticks.
		client.particleEngine.createTrackingEmitter(player, ParticleTypes.TOTEM_OF_UNDYING, 30);
		client.level.playLocalSound(player.getX(), player.getY(), player.getZ(),
			SoundEvents.TOTEM_USE, player.getSoundSource(), 1.0F, 1.0F, false);
	}

	private void applyDisplayName(RemotePlayer player) {
		player.setCustomName(net.minecraft.network.chat.Component.literal(getDisplayName()));
		player.setCustomNameVisible(false);
	}

	private record ExplosiveTrack(Vec3 pos, float power) {
	}
}
