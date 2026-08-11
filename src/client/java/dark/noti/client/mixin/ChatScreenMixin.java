package dark.noti.client.mixin;

import dark.noti.client.features.commands.ClientCommandHandler;
import dark.noti.client.features.commands.ClientCommandSuggestions;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	private static final int SUGGESTION_ROW = 12;
	private static final int SUGGESTION_FILL = 0xD0000000;
	private static final int SUGGESTION_SELECTED = 0xFFFFFF00;
	private static final int SUGGESTION_NORMAL = 0xFFAAAAAA;

	@Shadow
	protected EditBox input;

	@Unique
	private int darkNoti$boxX;
	@Unique
	private int darkNoti$boxY;
	@Unique
	private int darkNoti$boxW;
	@Unique
	private int darkNoti$boxH;
	@Unique
	private boolean darkNoti$boxActive;

	protected ChatScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void darkNoti$renderSuggestions(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		darkNoti$boxActive = false;
		if (this.input == null) {
			return;
		}
		String value = this.input.getValue();
		ClientCommandSuggestions.onInputChanged(value);
		ClientCommandSuggestions.SuggestionResult result = ClientCommandSuggestions.suggest(value);
		if (!result.active()) {
			return;
		}

		Font font = this.font;
		int tipY = this.height - 12;
		if (!result.ghost().isEmpty() && this.input.getCursorPosition() == value.length()) {
			// Align ghost with EditBox text (same coordinate system as suggestion box).
			int textX = this.input.getScreenX(value.length());
			graphics.drawString(font, result.ghost(), textX, tipY, 0xFF808080, false);
		}

		int maxTextW = 0;
		for (String option : result.options()) {
			maxTextW = Math.max(maxTextW, font.width(option));
		}

		int boxW = maxTextW + 1;
		int boxH = result.options().size() * SUGGESTION_ROW;
		int boxX = Mth.clamp(this.input.getScreenX(result.start()), 0, this.input.getScreenX(0) + this.input.getInnerWidth() - boxW);
		int boxY = this.height - 12 - 3 - boxH;

		darkNoti$boxX = boxX;
		darkNoti$boxY = boxY;
		darkNoti$boxW = boxW;
		darkNoti$boxH = boxH;
		darkNoti$boxActive = true;

		for (int i = 0; i < result.options().size(); i++) {
			String option = result.options().get(i);
			int rowY = boxY + i * SUGGESTION_ROW;
			graphics.fill(boxX, rowY, boxX + boxW, rowY + SUGGESTION_ROW, SUGGESTION_FILL);
			boolean selected = i == result.selected();
			// Same X as ghost text (no +1 offset) so previews line up.
			graphics.drawString(font, option, boxX, rowY + 2, selected ? SUGGESTION_SELECTED : SUGGESTION_NORMAL, false);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void darkNoti$clickSuggestions(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		if (this.input == null || !darkNoti$boxActive || event.button() != 0) {
			return;
		}
		String value = this.input.getValue();
		if (!value.startsWith(ClientCommandHandler.prefix())) {
			return;
		}
		ClientCommandSuggestions.SuggestionResult result = ClientCommandSuggestions.suggest(value);
		if (!result.active()) {
			return;
		}
		double mx = event.x();
		double my = event.y();
		if (mx < darkNoti$boxX || mx > darkNoti$boxX + darkNoti$boxW
			|| my < darkNoti$boxY || my > darkNoti$boxY + darkNoti$boxH) {
			return;
		}
		int index = (int) ((my - darkNoti$boxY) / SUGGESTION_ROW);
		if (index < 0 || index >= result.options().size()) {
			return;
		}
		ClientCommandSuggestions.select(index);
		String completed = ClientCommandSuggestions.complete(value);
		this.input.setValue(completed);
		this.input.setCursorPosition(completed.length());
		ClientCommandSuggestions.onInputChanged(completed);
		cir.setReturnValue(true);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void darkNoti$suggestionKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (this.input == null) {
			return;
		}
		String value = this.input.getValue();
		if (!value.startsWith(ClientCommandHandler.prefix())) {
			return;
		}
		ClientCommandSuggestions.onInputChanged(value);
		ClientCommandSuggestions.SuggestionResult result = ClientCommandSuggestions.suggest(value);
		if (!result.active()) {
			return;
		}

		int key = event.key();
		if (key == GLFW.GLFW_KEY_TAB) {
			// Cycle the suggestion list (Shift+Tab goes up); do not auto-fill.
			boolean up = event.hasShiftDown();
			ClientCommandSuggestions.cycle(up ? -1 : 1);
			cir.setReturnValue(true);
			return;
		}
		if (key == GLFW.GLFW_KEY_UP) {
			ClientCommandSuggestions.cycle(-1);
			cir.setReturnValue(true);
			return;
		}
		if (key == GLFW.GLFW_KEY_DOWN) {
			ClientCommandSuggestions.cycle(1);
			cir.setReturnValue(true);
		}
	}
}
