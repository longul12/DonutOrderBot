package com.kami.order.cursor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

/**
 * Chống kéo chuột về tâm — chỉ khi bot RUNNING.
 * <p>
 * Bug “chỉ vòng 1 OK”:
 * <ul>
 *   <li>Đóng GUI → {@code lockCursor} ghi x/y = tâm</li>
 *   <li>Mở GUI lần 2 → unlock; GLFW hay snap tâm khi DISABLED→NORMAL
 *       (vanilla set pos <b>trước</b> set mode → pos bị nuốt)</li>
 *   <li>lastFree đôi khi bị overwrite bằng tọa độ tâm</li>
 * </ul>
 * Fix: snapshot trước lock; unlock = mode NORMAL rồi mới set pos (×2);
 * giữ ép lại pos vài tick sau mỗi lần mở GUI.
 */
public final class GuiCursorControl {

    private static volatile boolean orderRunningWant = false;

    /** Vị trí “an toàn” (không phải tâm) để đặt khi unlock. */
    private static volatile double safeX;
    private static volatile double safeY;
    private static volatile boolean hasSafe = false;

    /** Số tick còn lại phải ép lại vị trí cursor (sau unlock). */
    private static volatile int reapplyTicks = 0;

    private static final int REAPPLY_TICKS = 20; // 1 giây

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

    public static boolean shouldInterceptUnlock() {
        return orderRunningWant || peerWantsDisable();
    }

    public static void syncFromOrderModule(boolean running, boolean settingOn) {
        orderRunningWant = running && settingOn;
        if (!orderRunningWant) {
            reapplyTicks = 0;
        }
    }

    // ── Snapshot vị trí ─────────────────────────────────────────────

    /** Có phải tọa độ ~tâm cửa sổ không (pixel). */
    public static boolean isNearCenter(double x, double y, Window w) {
        if (w == null) return false;
        double cx = w.getWidth() / 2.0;
        double cy = w.getHeight() / 2.0;
        return Math.abs(x - cx) < 8.0 && Math.abs(y - cy) < 8.0;
    }

    /** Fallback lệch tâm — dùng khi chưa có safe pos. */
    public static double[] defaultOffCenter(Window w) {
        return new double[]{w.getWidth() * 0.33, w.getHeight() * 0.33};
    }

    /**
     * Lưu vị trí an toàn nếu không gần tâm.
     * Gọi: mỗi tick khi GUI mở; trước lockCursor; trước bot mở GUI.
     */
    public static void rememberSafePos(double x, double y, Window w) {
        if (w == null) return;
        if (isNearCenter(x, y, w)) return;
        safeX = x;
        safeY = y;
        hasSafe = true;
    }

    public static void tickTrackFreeCursor() {
        if (!orderRunningWant) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.mouse == null || mc.getWindow() == null) return;
        Mouse mouse = mc.mouse;
        if (mouse.isCursorLocked()) return;
        rememberSafePos(mouse.getX(), mouse.getY(), mc.getWindow());
        // Ép lại pos nếu vừa unlock (chống snap tâm vòng 2+)
        if (reapplyTicks > 0) {
            reapplyTicks--;
            double[] pos = resolveUnlockPos(mc);
            applyCursorPos(mc, pos[0], pos[1]);
        }
    }

    /** Bot sắp mở GUI — đảm bảo có safe pos + bật reapply sau unlock. */
    public static void saveCursorBeforeGui() {
        if (!orderRunningWant) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.mouse == null) return;
        if (!mc.mouse.isCursorLocked()) {
            rememberSafePos(mc.mouse.getX(), mc.mouse.getY(), mc.getWindow());
        }
        // Báo sắp unlock — reapply sẽ được arm trong onUnlockApplied
    }

    /**
     * Trước khi vanilla lockCursor (đóng GUI) — chụp pos hiện tại
     * để lần mở GUI sau không bị “tâm” từ lock.
     */
    public static void snapshotBeforeLock(double x, double y, Window w) {
        if (!shouldInterceptUnlock()) return;
        rememberSafePos(x, y, w);
    }

    public static double[] resolveUnlockPos(MinecraftClient client) {
        Window w = client.getWindow();
        if (hasSafe && !isNearCenter(safeX, safeY, w)) {
            return new double[]{safeX, safeY};
        }
        return defaultOffCenter(w);
    }

    /** Gọi sau khi mixin unlock xong — arm ép lại pos các tick sau. */
    public static void onUnlockApplied() {
        reapplyTicks = REAPPLY_TICKS;
    }

    // ── GLFW: mode trước, pos sau (và set pos 2 lần) ────────────────

    /**
     * Đặt cursor NORMAL tại (x,y) đúng thứ tự GLFW — tránh snap tâm vòng 2+.
     * Vanilla InputUtil.setCursorParameters làm <b>pos rồi mode</b> → lỗi lặp.
     */
    public static void applyCursorPos(MinecraftClient client, double x, double y) {
        if (client == null || client.getWindow() == null) return;
        Window w = client.getWindow();
        long handle = w.getHandle();
        try {
            // 1) Hiện cursor trước
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            // 2) Đặt vị trí sau khi đã NORMAL
            GLFW.glfwSetCursorPos(handle, x, y);
            // 3) Ép lần 2 (một số driver nuốt lần 1 khi rời DISABLED)
            GLFW.glfwSetCursorPos(handle, x, y);
        } catch (Throwable t) {
            // Fallback yarn helper
            try {
                InputUtil.setCursorParameters(w, InputUtil.GLFW_CURSOR_NORMAL, x, y);
                GLFW.glfwSetCursorPos(handle, x, y);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void clearAllHard() {
        orderRunningWant = false;
        hasSafe = false;
        reapplyTicks = 0;
    }
}
