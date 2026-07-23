package com.kami.order.cursor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;

/**
 * Chống kéo chuột về giữa — <b>chỉ khi bot đang RUNNING</b>.
 * <p>
 * Lỗi cũ: mixin rewrite toàn bộ {@code unlockCursor} (cancellabe HEAD) →
 * ảnh hưởng mọi GUI; lưu X/Y lúc cursor locked (= giữa màn hình) nên restore vô dụng.
 * <p>
 * Cách mới (nhẹ):
 * <ul>
 *   <li>Theo dõi vị trí chuột khi <b>chưa</b> lock (FPS)</li>
 *   <li>Bot sắp mở GUI → đánh dấu {@code pendingRestore}</li>
 *   <li>Mixin chỉ ở RETURN của unlockCursor: nếu RUNNING + pending → restore</li>
 *   <li>Tắt module / hết pending → không đụng chuột</li>
 * </ul>
 * Thao tác slot: chỉ {@code clickSlot}, không bay chuột.
 */
public final class GuiCursorControl {

    /** Module Order đang active + setting bật. */
    private static volatile boolean orderRunningWant = false;

    /** Vị trí chuột tự do gần nhất (khi không lock). */
    private static volatile double lastFreeX;
    private static volatile double lastFreeY;
    private static volatile boolean hasLastFree = false;

    /** Bot vừa gọi save trước khi mở container — chỉ restore 1 lần unlock tiếp theo. */
    private static volatile boolean pendingRestore = false;
    private static volatile double savedX;
    private static volatile double savedY;

    private GuiCursorControl() {
    }

    // ── Peer (Spawner jar) ──────────────────────────────────────────

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

    private static boolean peerHasPendingRestore() {
        try {
            Class<?> cl = Class.forName("com.kami.spawnersdrop.cursor.GuiCursorControl");
            Object r = cl.getMethod("hasPendingRestore").invoke(null);
            return r instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ── Điều kiện RUNNING ───────────────────────────────────────────

    /**
     * true chỉ khi ít nhất 1 bot đang RUNNING <b>và</b> bật disable-gui-cursor-center.
     * Flag do onTick/onActivate/onDeactivate ghi — tắt module → false ngay.
     */
    public static boolean isAnyBotRunningForCursor() {
        return orderRunningWant || peerWantsDisable();
    }

    /**
     * Mixin được phép restore cursor?
     * — bot RUNNING (local hoặc peer)
     * — và có pending restore (bot chủ động mở GUI)
     */
    public static boolean shouldRestoreOnUnlock() {
        if (!isAnyBotRunningForCursor()) return false;
        return pendingRestore || peerHasPendingRestore();
    }

    public static boolean hasPendingRestore() {
        return pendingRestore;
    }

    // ── Sync từ module ──────────────────────────────────────────────

    /**
     * Gọi mỗi tick / activate / deactivate.
     * {@code running} = module.isActive(); {@code settingOn} = disable-gui-cursor-center.
     */
    public static void syncFromOrderModule(boolean running, boolean settingOn) {
        boolean want = running && settingOn;
        orderRunningWant = want;
        if (!want) {
            // Tắt module → trả control chuột ngay, xóa pending
            pendingRestore = false;
            // Không xóa lastFree — vô hại khi không RUNNING
        }
    }

    // ── Theo dõi / lưu / restore ────────────────────────────────────

    /**
     * Gọi mỗi tick khi module RUNNING: nhớ vị trí chuột lúc không lock
     * (tránh lưu nhầm center khi đang FPS lock).
     */
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
     * Bot sắp mở GUI (/order, …). Đánh dấu pending restore cho unlock kế tiếp.
     */
    public static void saveCursorBeforeGui() {
        if (!orderRunningWant) return; // module không chạy → không đánh dấu

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.mouse == null) return;

        Mouse mouse = mc.mouse;
        if (mouse.isCursorLocked() && hasLastFree) {
            // Đang FPS: x/y vanilla = giữa — dùng vị trí free đã track
            savedX = lastFreeX;
            savedY = lastFreeY;
        } else if (!mouse.isCursorLocked()) {
            savedX = mouse.getX();
            savedY = mouse.getY();
            lastFreeX = savedX;
            lastFreeY = savedY;
            hasLastFree = true;
        } else if (hasLastFree) {
            savedX = lastFreeX;
            savedY = lastFreeY;
        } else {
            // Không có gì đáng tin — không bật pending (để vanilla center)
            return;
        }
        pendingRestore = true;
    }

    /** Mixin RETURN unlock: lấy toạ độ restore (local pending). */
    public static double consumeRestoreX(double fallback) {
        if (pendingRestore) return savedX;
        return fallback;
    }

    public static double consumeRestoreY(double fallback) {
        if (pendingRestore) return savedY;
        return fallback;
    }

    /** Hết 1 lần restore (chỉ clear local; peer tự clear). */
    public static void clearPendingAfterRestore() {
        pendingRestore = false;
    }

    public static void clearAll() {
        orderRunningWant = false;
        pendingRestore = false;
        hasLastFree = false;
    }
}
