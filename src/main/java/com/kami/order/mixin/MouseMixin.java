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
 * Chặn vanilla kéo cursor về giữa màn hình khi mở GUI ({@link Mouse#unlockCursor()}).
 * <p>
 * 1.21.11: unlockCursor gán x/y = width/2, height/2 rồi
 * {@link InputUtil#setCursorParameters(net.minecraft.client.util.Window, int, double, double)}.
 * Khi {@code disable-gui-cursor-center}: giữ / restore vị trí, chỉ đổi mode NORMAL.
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

    /**
     * Mở Screen/container → unlockCursor → không center nếu option bật.
     */
    @Inject(method = "unlockCursor", at = @At("HEAD"), cancellable = true)
    private void kamiOrder$unlockWithoutCenter(CallbackInfo ci) {
        if (!GuiCursorControl.shouldDisableCenter()) return;

        // Đã unlock rồi → không làm gì thêm (tránh double-mixin / race)
        if (!this.cursorLocked) {
            ci.cancel();
            return;
        }

        this.cursorLocked = false;

        // Ưu tiên vị trí đã lưu trước khi module mở GUI; không thì giữ x/y hiện tại
        double rx = GuiCursorControl.takeRestoreX(this.x);
        double ry = GuiCursorControl.takeRestoreY(this.y);
        this.x = rx;
        this.y = ry;

        // Chỉ hiện cursor thường — KHÔNG set về giữa cửa sổ
        InputUtil.setCursorParameters(
            this.client.getWindow(),
            InputUtil.GLFW_CURSOR_NORMAL,
            rx,
            ry
        );

        ci.cancel();
    }
}
