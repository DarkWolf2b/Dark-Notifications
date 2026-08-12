package dark.noti.client.mixin;

import dark.noti.client.features.modules.notifications.ChestSwapModule;
import dark.noti.client.features.modules.notifications.DeathNotifierModule;
import dark.noti.client.features.modules.notifications.TotemPopNotifierModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleEntityEvent", at = @At("HEAD"))
	private void darkNoti$totemPop(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		Entity entity = packet.getEntity(client.level);
		if (!(entity instanceof Player player)) {
			return;
		}
		byte eventId = packet.getEventId();
		if (eventId == EntityEvent.PROTECTED_FROM_DEATH) {
			TotemPopNotifierModule.onPlayerTotemPop(player);
			DeathNotifierModule.onPlayerTotemPop(player);
		} else if (eventId == EntityEvent.DEATH) {
			// Reset pop streak as soon as the death animation packet arrives.
			TotemPopNotifierModule.onPlayerDeath(player);
		}
	}

	@Inject(method = "handleSetEquipment", at = @At("TAIL"))
	private void darkNoti$chestSwap(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
		ChestSwapModule.onEquipmentPacket(packet);
	}
}
