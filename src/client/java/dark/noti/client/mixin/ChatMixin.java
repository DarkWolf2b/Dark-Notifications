package dark.noti.client.mixin;

import dark.noti.client.features.modules.notifications.CCMNotifierModule;
import dark.noti.client.features.modules.notifications.ChatMentionModule;
import dark.noti.client.features.modules.notifications.ChestSwapModule;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ChatMixin {
	/**
	 * Only capture the modified argument — including other method params breaks
	 * ModifyVariable under intermediary (production) mappings.
	 */
	@ModifyVariable(
		method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
		at = @At("HEAD"),
		argsOnly = true,
		ordinal = 0
	)
	private Component onAddMessage(Component message) {
		if (message == null) {
			return null;
		}
		CCMNotifierModule.onChatMessage(message);
		ChestSwapModule.onChatMessage(message);
		Component styled = ChatMentionModule.process(message);
		return styled != null ? styled : message;
	}
}
