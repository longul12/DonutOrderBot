package com.kami.order.cursor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.Window;

/**
 * Chống kéo chuột về giữa — <b>chỉ khi bot Order/Spawner đang RUNNING</b>.
 * <p>
 * Lỗi thực tế:
 * <ul>
 *   <li>Restore ở RETURN quá muộn / pending không set (FPS lock không có lastFree)</li>
 *   <li>Vanilla {@code unlockCursor} luôn gán x/y = giữa + {@code glfwSetCursorPos}</li>
 * </ul>
 * Cách fix: khi bot RUNNING + setting, <b>thay</b> unlockCursor (HEAD cancel) —
 * unlock bình thường nhưng đặt cursor ở vị trí free / lệch giữa, không center.
 * Module tắt → flag false → mixin no-op → GUI server vanilla.
 */
public final class GuiCursorControl {

    private static volatile boolean orderRunningWant = false;

    private static volatile double lastFreeX;
    private static volatile double lastFreeY;
    private static volatile boolean hasLastFree = false;

    private GuiCursorControl() {
    }

    public static boolean wantsDisableLocal() {
        return orderRunningWant;
    }

    private static boolean peerWantsDisable() {
        try {
            Class<?> cl = Class.forName("com.kami.spawnersdrop.cursor.GuiCursorControl");
            Object r = cl.getMethod("wantsDisableLocal").invoke(null);
            return r instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** true = được phép thay unlockCursor (bot RUNNING + setting). */
    public static boolean shouldInterceptUnlock() {
        return orderRunningWant || peerWantsDisable();
    }

    public static void syncFromOrderModule(boolean running, boolean settingOn) {
        orderRunningWant = running && settingOn;
        if (!orderRunningWant && !peerWantsDisable()) {
            // cả hai bot off — có thể giữ lastFree cho lần sau, không bắt buộc xóa
        }
    }

    /** Mỗi tick khi RUNNING: nhớ chỗ chuột lúc GUI đang mở (không lock). */
    public static void tickTrackFreeCursor() {
        if (!orderRunningWant) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.mouse == null) return;
        Mouse mouse = mc.mouse;
        if (mouse.isCursorLocked()) return;
        lastFreeX = mouse.getX();
        lastFreeY = mouse.getY();
        hasLastFree = true;
    }

    /**
     * Gọi trước khi bot mở GUI — cập nhật lastFree nếu đang không lock.
     * (Không còn phụ thuộc pending; intercept theo RUNNING.)
     */
    public static void saveCursorBeforeGui() {
        if (!orderRunningWant) return;
        tickTrackFreeCursor();
    }

    /**
     * Toạ độ đặt cursor khi unlock (pixel cửa sổ, cùng hệ Mouse.x/y).
     * Ưu tiên last free; không có thì lệch khỏi tâm (tránh snap giữa).
     */
    public static double[] resolveUnlockPos(MinecraftClient client) {
        if (hasLastFree) {
            return new double[]{lastFreeX, lastFreeY};
        }
        Window w = client.getWindow();
        // ~1/3 kích thước — không phải tâm, GUI vẫn dùng được nếu user click tay
        double x = w.getWidth() * 0.33;
        double y = w.getHeight() * 0.33;
        return new double[]{x, y};
    }

    public static void clearAll() {
        orderRunningWant = false;
        // giữ lastFree để lần bật sau mượt hơn; clear hẳn nếu muốn:
        // hasLastFree = false;
    }

    public static void clearAllHard() {
        orderRunningWant = false;
        hasLastFree = false;
    }
}
