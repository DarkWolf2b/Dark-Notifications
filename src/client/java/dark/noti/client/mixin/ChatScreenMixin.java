package dark.noti.client.mixin;

import dark.noti.client.features.commands.ClientCommandHandler;
import dark.noti.client.features.commands.ClientCommandSuggestions;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
	private static final int SUGGESTION_FILL = 0xF0101010;
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
	@Unique
	private int darkNoti$optionCount;
	@Unique
	private int darkNoti$scrollOffset;

	protected ChatScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void darkNoti$renderSuggestions(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		darkNoti$boxActive = false;
		if (this.input == null) {
			return;
		}
		String value = this.input.getValue();
		if (!value.startsWith(ClientCommandHandler.prefix())) {
			this.input.setSuggestion(null);
			return;
		}

		ClientCommandSuggestions.onInputChanged(value);
		ClientCommandSuggestions.SuggestionResult result = ClientCommandSuggestions.suggest(value);
		if (!result.active()) {
			this.input.setSuggestion(null);
			return;
		}

		Font font = this.font;
		if (this.input.getCursorPosition() == value.length() && !result.ghost().isEmpty()) {
			this.input.setSuggestion(result.ghost());
		} else {
			this.input.setSuggestion(null);
		}

		int maxTextW = 0;
		for (String option : result.options()) {
			maxTextW = Math.max(maxTextW, font.width(option));
		}

		int visible = result.visibleCount();
		int rowH = ClientCommandSuggestions.ROW_HEIGHT;
		int boxW = maxTextW + 1;
		int boxH = visible * rowH;
		int boxX = Mth.clamp(this.input.getScreenX(result.start()), 0, this.input.getScreenX(0) + this.input.getInnerWidth() - boxW);
		int boxY = this.height - 12 - 3 - boxH;

		ClientCommandSuggestions.updateHover(mouseX, mouseY, boxX, boxY, boxW, visible, result.options().size());
		result = ClientCommandSuggestions.suggest(value);
		if (!result.active()) {
			this.input.setSuggestion(null);
			return;
		}
		if (this.input.getCursorPosition() == value.length() && !result.ghost().isEmpty()) {
			this.input.setSuggestion(result.ghost());
		} else {
			this.input.setSuggestion(null);
		}

		darkNoti$boxX = boxX;
		darkNoti$boxY = boxY;
		darkNoti$boxW = boxW;
		darkNoti$boxH = boxH;
		darkNoti$boxActive = true;
		darkNoti$optionCount = result.options().size();
		darkNoti$scrollOffset = result.offset();

		boolean canScrollUp = result.offset() > 0;
		boolean canScrollDown = result.options().size() > result.offset() + visible;
		if (canScrollUp || canScrollDown) {
			graphics.fill(boxX, boxY - 1, boxX + boxW, boxY, SUGGESTION_FILL);
			graphics.fill(boxX, boxY + boxH, boxX + boxW, boxY + boxH + 1, SUGGESTION_FILL);
			if (canScrollUp) {
				for (int m = 0; m < boxW; m += 2) {
					graphics.fill(boxX + m, boxY - 1, boxX + m + 1, boxY, 0xFFFFFFFF);
				}
			}
			if (canScrollDown) {
				for (int m = 0; m < boxW; m += 2) {
					graphics.fill(boxX + m, boxY + boxH, boxX + m + 1, boxY + boxH + 1, 0xFFFFFFFF);
				}
			}
		}

		for (int i = 0; i < visible; i++) {
			int optionIndex = result.offset() + i;
			String option = result.options().get(optionIndex);
			int rowY = boxY + i * rowH;
			graphics.fill(boxX, rowY, boxX + boxW, rowY + rowH, SUGGESTION_FILL);
			boolean selected = optionIndex == result.selected();
			graphics.text(font, option, boxX, rowY + 2, selected ? SUGGESTION_SELECTED : SUGGESTION_NORMAL, false);
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
		int relative = (int) ((my - darkNoti$boxY) / ClientCommandSuggestions.ROW_HEIGHT);
		int index = darkNoti$scrollOffset + relative;
		if (index < 0 || index >= result.options().size()) {
			return;
		}
		ClientCommandSuggestions.select(index);
		String completed = ClientCommandSuggestions.complete(value);
		this.input.setValue(completed);
		this.input.setCursorPosition(completed.length());
		this.input.setSuggestion(null);
		ClientCommandSuggestions.onInputChanged(completed);
		cir.setReturnValue(true);
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void darkNoti$scrollSuggestions(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
		if (this.input == null || !darkNoti$boxActive) {
			return;
		}
		String value = this.input.getValue();
		if (!value.startsWith(ClientCommandHandler.prefix())) {
			return;
		}
		if (mouseX < darkNoti$boxX || mouseX > darkNoti$boxX + darkNoti$boxW
			|| mouseY < darkNoti$boxY || mouseY > darkNoti$boxY + darkNoti$boxH) {
			return;
		}
		double amount = Mth.clamp(scrollY, -1.0, 1.0);
		ClientCommandSuggestions.mouseScrolled(amount, darkNoti$optionCount);
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
		if (key == GLFW.GLFW_KEY_TAB || event.isCycleFocus()) {
			ClientCommandSuggestions.tabCycle(event.hasShiftDown());
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
			return;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			this.input.setSuggestion(null);
		}
	}
}
