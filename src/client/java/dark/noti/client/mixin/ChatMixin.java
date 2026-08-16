package dark.noti.client.mixin;

import dark.noti.client.features.modules.notifications.ChatMentionModule;
import dark.noti.client.features.modules.notifications.ChestSwapModule;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatMixin {
	@Inject(
		method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
		at = @At("HEAD")
	)
	private void darkNoti$onAddMessage(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
		if (message == null) {
			return;
		}
		ChestSwapModule.onChatMessage(message);
	}

	/**
	 * Style ChatMention without changing the addMessage signature under intermediary.
	 */
	@ModifyVariable(
		method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 0
	)
	private Component darkNoti$styleMention(Component message) {
		if (message == null) {
			return null;
		}
		Component styled = ChatMentionModule.process(message);
		return styled != null ? styled : message;
	}
}
