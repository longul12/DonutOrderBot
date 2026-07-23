package com.kami.order.mixin;

import com.kami.order.cursor.GuiCursorControl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Khi bot RUNNING: thay {@link Mouse#unlockCursor()} — không gán giữa màn hình.
 * Khi bot tắt: return ngay, vanilla chạy đủ 100%.
 * <p>
 * 1.21.11 vanilla: x=width/2, y=height/2 + glfwSetCursorPos → kéo về tâm.
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private boolean cursorLocked;

    @Shadow
    private double x;

    @Shadow
    private double y;

    @Inject(method = "unlockCursor", at = @At("HEAD"), cancellable = true)
    private void kamiOrder$unlockNoCenter(CallbackInfo ci) {
        // Module tắt / setting tắt → không đụng gì
        if (!GuiCursorControl.shouldInterceptUnlock()) return;

        // Đã unlock: để vanilla return sớm — KHÔNG cancel (tránh phá GUI khác)
        if (!this.cursorLocked) return;

        // Unlock nhưng không center
        this.cursorLocked = false;

        double[] pos = GuiCursorControl.resolveUnlockPos(this.client);
        this.x = pos[0];
        this.y = pos[1];

        // GLFW: hiện cursor tại pos, không phải tâm
        InputUtil.setCursorParameters(
            this.client.getWindow(),
            InputUtil.GLFW_CURSOR_NORMAL,
            this.x,
            this.y
        );

        ci.cancel();
    }
}
