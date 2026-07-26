package com.kami.order.mixin;

import com.kami.order.modules.KamiOrderBot;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Khi bot đang chạy, không cho Minecraft grab chuột lại sau khi đóng GUI.
 * Không warp cursor, không click chuột thật, không đụng vị trí con trỏ hệ thống.
 */
@Mixin(Mouse.class)
public abstract class MouseLockMixin {
    @Inject(method = "lockCursor", at = @At("HEAD"), cancellable = true)
    private void kamiOrder$preventMouseGrabWhileRunning(CallbackInfo ci) {
        if (KamiOrderBot.shouldPreventMouseLock()) ci.cancel();
    }
}
