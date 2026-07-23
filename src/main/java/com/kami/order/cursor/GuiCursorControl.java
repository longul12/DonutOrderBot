package com.kami.order.cursor;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;

/**
 * Điều khiển cursor khi mở GUI container.
 * <p>
 * Vanilla ({@code Mouse.unlockCursor}) gán x/y = giữa cửa sổ rồi
 * {@code InputUtil.setCursorParameters} → chuột hệ thống bị kéo về center.
 * Mixin đọc flag từ đây để bỏ bước center + (tuỳ chọn) khôi phục vị trí đã lưu.
 * <p>
 * Thao tác slot vẫn chỉ dùng {@code interactionManager.clickSlot} — không bay chuột.
 */
public final class GuiCursorControl {

    private static volatile boolean wantDisableCenter = false;

    private static volatile double savedX;
    private static volatile double savedY;
    private static volatile boolean hasSavedPos = false;

    private GuiCursorControl() {
    }

    /** Cập nhật từ module mỗi tick / activate (setting + active). */
    public static void setWantDisableCenter(boolean want) {
        wantDisableCenter = want;
    }

    /** Flag local của addon này (không đọc peer). */
    public static boolean wantsDisableLocal() {
        return wantDisableCenter;
    }

    /**
     * true nếu Order hoặc Spawner (peer) đang bật disable-gui-cursor-center.
     * Cả hai jar đều có mixin → cần OR để không bị jar kia “nhường” vanilla center.
     */
    public static boolean shouldDisableCenter() {
        if (wantDisableCenter) return true;
        return peerWantsDisable("com.kami.spawnersdrop.cursor.GuiCursorControl");
    }

    private static boolean peerWantsDisable(String className) {
        try {
            Class<?> cl = Class.forName(className);
            Object r = cl.getMethod("wantsDisableLocal").invoke(null);
            return r instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Lưu vị trí chuột hiện tại trước khi module mở container
     * (/order, right-click spawner, mở rương…).
     */
    public static void saveCursorBeforeGui() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.mouse == null) return;
        savedX = mc.mouse.getX();
        savedY = mc.mouse.getY();
        hasSavedPos = true;
    }

    /** Mixin: lấy X đã lưu (hoặc fallback). */
    public static double takeRestoreX(double fallback) {
        if (!hasSavedPos) return fallback;
        return savedX;
    }

    /** Mixin: lấy Y đã lưu (hoặc fallback). */
    public static double takeRestoreY(double fallback) {
        if (!hasSavedPos) return fallback;
        return savedY;
    }

    public static boolean hasSavedPos() {
        return hasSavedPos;
    }

    /** Xóa vị trí đã lưu (sau khi đã restore hoặc đóng GUI lâu). */
    public static void clearSavedPos() {
        hasSavedPos = false;
    }

    /**
     * Đồng bộ flag từ module Order đang load (kể cả khi tắt active
     * nhưng setting vẫn bật — chỉ khi module active mới chặn center).
     */
    public static void syncFromOrderModule(boolean moduleActive, boolean settingOn) {
        setWantDisableCenter(moduleActive && settingOn);
    }

    /** Tiện ích: tìm module theo tên Meteor. */
    public static Module findModule(String name) {
        if (Modules.get() == null || name == null) return null;
        try {
            Module m = Modules.get().get(name);
            if (m != null) return m;
        } catch (Throwable ignored) {
        }
        return null;
    }
}
