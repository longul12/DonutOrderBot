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
 * Nhẹ: <b>không</b> hủy vanilla {@link Mouse#unlockCursor()}.
 * Chỉ sau khi vanilla chạy xong (RETURN): nếu bot RUNNING + pending mở GUI
 * → restore vị trí chuột đã lưu. Module tắt → early return, GUI 100% vanilla.
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private double x;

    @Shadow
    private double y;

    /**
     * Vanilla đã center x/y rồi — ghi đè lại vị trí free nếu bot vừa mở container.
     */
    @Inject(method = "unlockCursor", at = @At("RETURN"))
    private void kamiOrder$restoreCursorAfterUnlock(CallbackInfo ci) {
        // Không RUNNING / không pending → tuyệt đối không đụng chuột
        if (!GuiCursorControl.shouldRestoreOnUnlock()) return;
        if (!GuiCursorControl.hasPendingRestore()) return; // peer sẽ tự restore

        double rx = GuiCursorControl.consumeRestoreX(this.x);
        double ry = GuiCursorControl.consumeRestoreY(this.y);
        this.x = rx;
        this.y = ry;

        try {
            InputUtil.setCursorParameters(
                this.client.getWindow(),
                InputUtil.GLFW_CURSOR_NORMAL,
                rx,
                ry
            );
        } catch (Throwable ignored) {
        }

        GuiCursorControl.clearPendingAfterRestore();
    }
}
