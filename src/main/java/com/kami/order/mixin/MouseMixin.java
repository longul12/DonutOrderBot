package com.kami.order.mixin;

import com.kami.order.cursor.GuiCursorControl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * unlockCursor: bot RUNNING → không center, GLFW mode→pos.
 * lockCursor: chụp vị trí free trước khi vanilla ghi tâm (fix vòng lặp 2+).
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
        if (!GuiCursorControl.shouldInterceptUnlock()) return;

        if (!this.cursorLocked) {
            // Đã unlock (đổi GUI liên tiếp): vẫn ép pos nếu đang bị tâm
            if (this.client.getWindow() != null
                && GuiCursorControl.isNearCenter(this.x, this.y, this.client.getWindow())) {
                double[] pos = GuiCursorControl.resolveUnlockPos(this.client);
                this.x = pos[0];
                this.y = pos[1];
                GuiCursorControl.applyCursorPos(this.client, this.x, this.y);
                GuiCursorControl.onUnlockApplied();
            }
            return; // không cancel — vanilla return sớm
        }

        this.cursorLocked = false;

        double[] pos = GuiCursorControl.resolveUnlockPos(this.client);
        this.x = pos[0];
        this.y = pos[1];

        // Không dùng InputUtil.setCursorParameters (pos trước mode → snap tâm lần 2+)
        GuiCursorControl.applyCursorPos(this.client, this.x, this.y);
        GuiCursorControl.onUnlockApplied();
        // Nhớ pos vừa đặt là safe (trừ khi lỡ là tâm)
        GuiCursorControl.rememberSafePos(this.x, this.y, this.client.getWindow());

        ci.cancel();
    }

    /**
     * Trước khi đóng GUI / lock FPS: lưu chỗ chuột hiện tại (không phải tâm)
     * để lần mở GUI sau vẫn restore đúng — đây là fix chính cho vòng lặp 2+.
     */
    @Inject(method = "lockCursor", at = @At("HEAD"))
    private void kamiOrder$snapshotBeforeLock(CallbackInfo ci) {
        if (!GuiCursorControl.shouldInterceptUnlock()) return;
        if (this.cursorLocked) return; // đã lock
        GuiCursorControl.snapshotBeforeLock(this.x, this.y, this.client.getWindow());
    }
}
