package com.kami.spawnersdrop.modules;

import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * KamiSpawnerDrop — Auto Drop Spawner.
 * <p>
 * Bình thường: mở GUI → VỨT HẾT (52) → ESC → lặp.<br>
 * Gặp item lạ (stop-items): Sell All <b>1 lần</b> (slot 50) → ESC → tự bật respawn delay.
 * Arrow &lt; 64 = nút sang trang (không tính junk).
 */
public class KamiSpawnerDrop extends Module {
    private static final String GUI_OWNER_SPAWNER = "SPAWNER_DROP";
    private static final double MAX_INTERACT_DISTANCE_SQUARED = 36.0;
    private static volatile boolean keepMouseFreeWhileRunning = false;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Safety Stop");
    private final SettingGroup sgSlot = settings.createGroup("Drop / Sell Slot");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    private final Setting<Boolean> autoDropSpawner = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-drop-spawner")
        .description("Bật/tắt auto drop khi module active.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoSpawnerProtect = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-spawner-protect")
        .description("Khi SpawnerDrop hoat dong thi tu bat Kami Spawner Protect neu chua bat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> spawnerProtectModule = sgGeneral.add(new StringSetting.Builder()
        .name("spawner-protect-module")
        .description("Ten module Spawner Protect. Mac dinh: kami-spawner-protect.")
        .defaultValue("kami-spawner-protect")
        .visible(autoSpawnerProtect::get)
        .build()
    );

    private final Setting<Integer> dropTimes = sgGeneral.add(new IntSetting.Builder()
        .name("drop-times")
        .description("Số lần lặp: mở → drop → (sell) → ESC. Có thể gõ số lớn trực tiếp.")
        .defaultValue(3)
        .range(1, Integer.MAX_VALUE)
        .sliderRange(1, 256)
        .build()
    );

    private final Setting<Boolean> repeatAfterWait = sgGeneral.add(new BoolSetting.Builder()
        .name("repeat-after-wait")
        .description("Hết drop-times thì nghỉ theo phút rồi lặp lại batch drop-times. Bật = lặp vô hạn tới khi tắt module.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> repeatWaitMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("repeat-wait-minutes")
        .description("Số phút nghỉ giữa các batch drop-times. 0 = lặp lại ngay. Có thể gõ số rất lớn trực tiếp.")
        .defaultValue(1)
        .range(0, Integer.MAX_VALUE)
        .sliderRange(0, 120)
        .visible(repeatAfterWait::get)
        .build()
    );

    private final Setting<Boolean> autoResumeOrder = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-resume-order")
        .description("Drop xong (không dừng an toàn) → bật lại Kami Order Bot nếu còn vòng.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> orderModuleName = sgGeneral.add(new StringSetting.Builder()
        .name("order-module")
        .description("Tên module Order. Mặc định: kami-order-bot.")
        .defaultValue("kami-order-bot")
        .visible(autoResumeOrder::get)
        .build()
    );

    private final Setting<Boolean> finalOrderDuringRespawn = sgGeneral.add(new BoolSetting.Builder()
        .name("final-order-during-respawn")
        .description("Khi chờ respawn vì gặp item lạ, bật Order một lần để dọn item đã drop/nhặt.")
        .defaultValue(true)
        .visible(autoResumeOrder::get)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay giữa mở GUI / click / ESC (tick) + random nhẹ.")
        .defaultValue(8)
        .range(1, 60)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> guiWaitTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("gui-wait-timeout")
        .description("Timeout chờ GUI Spawner load (tick).")
        .defaultValue(100)
        .range(20, 400)
        .sliderRange(20, 200)
        .build()
    );

    private final Setting<Boolean> escCancel = sgGeneral.add(new BoolSetting.Builder()
        .name("esc-cancel")
        .description("Nhan ESC nhieu lan lien tiep de tu tat KamiSpawnerDrop.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> escCancelPresses = sgGeneral.add(new IntSetting.Builder()
        .name("esc-cancel-presses")
        .description("So lan ESC can nhan lien tiep de tat module.")
        .defaultValue(3)
        .range(2, 10)
        .sliderRange(2, 5)
        .visible(escCancel::get)
        .build()
    );

    private final Setting<Integer> escCancelWindow = sgGeneral.add(new IntSetting.Builder()
        .name("esc-cancel-window")
        .description("Khoang tick toi da giua cac lan ESC de tinh la lien tiep.")
        .defaultValue(30)
        .range(5, 120)
        .sliderRange(10, 60)
        .visible(escCancel::get)
        .build()
    );

    // ── Safety Stop ──

    private final Setting<List<Item>> stopItems = sgSafety.add(new ItemListSetting.Builder()
        .name("stop-items")
        .description("Item lạ / không cần trong GUI. Thấy → Sell All 1 lần (sell-slot) rồi tự chờ respawn.")
        .defaultValue(List.of(
            Items.ARROW,
            Items.GLOWSTONE_DUST,
            Items.POPPY
        ))
        .build()
    );

    private final Setting<Integer> checkDelay = sgSafety.add(new IntSetting.Builder()
        .name("check-delay")
        .description("Delay (tick) sau drop/sell trước khi check GUI.")
        .defaultValue(5)
        .range(0, 40)
        .sliderRange(0, 20)
        .build()
    );

    /**
     * Arrow sang trang GUI thường chỉ 1 cái — chỉ coi là junk khi stack ≥ 64 (loot).
     */
    private final Setting<Integer> arrowStopMinCount = sgSafety.add(new IntSetting.Builder()
        .name("arrow-stop-min-count")
        .description("Arrow chỉ coi item lạ khi số lượng ≥ mức này (64 = full stack). Nút sang trang = 1 arrow.")
        .defaultValue(64)
        .range(1, 64)
        .sliderRange(1, 64)
        .build()
    );

    /**
     * Sau Sell All 1 lần (gặp item lạ) → tự chờ số phút này. 0 = đóng GUI xong tiếp tục ngay.
     */
    private final Setting<Integer> respawnDelayMin = sgSafety.add(new IntSetting.Builder()
        .name("respawn-delay-min")
        .description("Phút chờ respawn — tự bật sau khi Sell All 1 lần vì item lạ. 0 = không chờ.")
        .defaultValue(1)
        .range(0, 120)
        .sliderRange(0, 30)
        .build()
    );

    // ── Drop / Sell slot ──

    private final Setting<Integer> dropSlot = sgSlot.add(new IntSetting.Builder()
        .name("drop-slot")
        .description("Slot index nút VỨT HẾT (0-based). Mặc định 52.")
        .defaultValue(52)
        .range(0, 89)
        .sliderRange(0, 60)
        .build()
    );

    private final Setting<Boolean> forceFixedSlot = sgSlot.add(new BoolSetting.Builder()
        .name("force-fixed-slot")
        .description("true = luôn click đúng drop-slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> preferTooltipScan = sgSlot.add(new BoolSetting.Builder()
        .name("prefer-tooltip-scan")
        .description("Ưu tiên quét tooltip 'vứt hết' trước.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> enableSell = sgSlot.add(new BoolSetting.Builder()
        .name("enable-sell")
        .description("Gặp item lạ → Sell All 1 lần rồi auto respawn delay. Tắt = gặp item lạ thì dừng.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> sellSlot = sgSlot.add(new IntSetting.Builder()
        .name("sell-slot")
        .description("Slot index nút Sell All (0-based). Mặc định 50.")
        .defaultValue(50)
        .range(0, 89)
        .sliderRange(0, 60)
        .visible(enableSell::get)
        .build()
    );

    private final Setting<Boolean> forceSellSlot = sgSlot.add(new BoolSetting.Builder()
        .name("force-sell-slot")
        .description("true = luôn click sell-slot nếu không trống. false = chỉ click khi tooltip khớp bán/sell.")
        .defaultValue(true)
        .visible(enableSell::get)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgDebug.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("In thông báo chi tiết.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> debugScanSlots = sgDebug.add(new BoolSetting.Builder()
        .name("debug-scan-slots")
        .description("In list slot container khi mở GUI.")
        .defaultValue(false)
        .build()
    );

    private enum State {
        IDLE_WAIT_LOOK,
        OPEN_SPAWNER,
        WAIT_GUI,
        CHECK_BEFORE_DROP,
        CLICK_DROP,
        CHECK_AFTER_DROP,
        /** Sell All đúng 1 lần vì item lạ → rồi ESC + respawn delay */
        CLICK_SELL_JUNK,
        CLOSE_GUI,
        WAIT_RESPAWN,
        WAIT_LOOP_RESTART,
        DONE
    }

    private State state = State.IDLE_WAIT_LOOK;
    private int actionCooldown = 0;
    private int waitTicks = 0;
    private int remainingDrops = 0;
    private boolean scannedThisGui = false;
    private boolean stoppedByBannedItem = false;
    /**
     * Đã Sell All 1 lần vì item lạ trong vòng này → đóng GUI xong tự bật respawn delay.
     */
    private boolean soldThisCycle = false;
    private int respawnWaitTicks = 0;
    private int respawnTotalTicks = 0;
    private long loopRestartWaitTicks = 0;
    private long loopRestartTotalTicks = 0;
    private int escPressCount = 0;
    private int escWindowTicks = 0;
    private BlockPos targetSpawnerPos = null;
    private Direction targetSpawnerSide = null;
    private Vec3d targetHitPos = null;
    private RegistryKey<World> targetWorldKey = null;

    public KamiSpawnerDrop() {
        super(Categories.Misc, "kami-spawner-drop",
            "Auto Drop Spawner: item lạ → Sell All 1 lần + auto respawn delay.");
    }

    public static boolean shouldPreventMouseLock() {
        if (keepMouseFreeWhileRunning) return true;
        try {
            Class<?> cl = Class.forName("com.kami.order.modules.KamiOrderBot");
            Object value = cl.getMethod("shouldPreventMouseLock").invoke(null);
            return value instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onActivate() {
        if (!acquireGuiOwner()) {
            error("GUI đang được điều khiển bởi module khác — không bật SpawnerDrop.");
            toggle();
            return;
        }

        resetState();
        keepMouseFreeWhileRunning = true;
        if (!captureSpawnerTarget()) {
            error("Hãy nhìn thẳng vào block Spawner trước khi bật KamiSpawnerDrop.");
            keepMouseFreeWhileRunning = false;
            releaseGuiOwner();
            toggle();
            return;
        }

        tryActivateSpawnerProtect();

        remainingDrops = dropTimes.get();
        state = State.IDLE_WAIT_LOOK;
        log("Bật KamiSpawnerDrop — drop×" + remainingDrops
            + " | target=" + targetSpawnerPos.toShortString()
            + " | stop-items=" + stopItems.get().size()
            + " | drop=" + dropSlot.get()
            + (enableSell.get() ? " | sell-all×1 slot=" + sellSlot.get() : " | sell=off")
            + " | respawn=" + respawnDelayMin.get() + "p (Safety)");
    }

    @Override
    public void onDeactivate() {
        resetState();
        keepMouseFreeWhileRunning = false;
        if (mc.player != null && isContainerOpen() && ownsGui()) {
            mc.player.closeHandledScreen();
        }
        clearSpawnerTarget();
        releaseGuiOwner();
    }

    private void resetState() {
        state = State.IDLE_WAIT_LOOK;
        actionCooldown = 0;
        waitTicks = 0;
        remainingDrops = 0;
        scannedThisGui = false;
        stoppedByBannedItem = false;
        soldThisCycle = false;
        respawnWaitTicks = 0;
        respawnTotalTicks = 0;
        loopRestartWaitTicks = 0;
        loopRestartTotalTicks = 0;
        escPressCount = 0;
        escWindowTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            resetState();
            keepMouseFreeWhileRunning = false;
            clearSpawnerTarget();
            releaseGuiOwner();
            return;
        }
        if (!autoDropSpawner.get()) return;

        if (escWindowTicks > 0) escWindowTicks--;
        else escPressCount = 0;

        // Respawn countdown không bị chặn bởi actionCooldown ngắn
        if (state == State.WAIT_RESPAWN) {
            handleWaitRespawn();
            return;
        }

        if (state == State.WAIT_LOOP_RESTART) {
            handleWaitLoopRestart();
            return;
        }

        if (state == State.DONE) {
            finishDropRun();
            return;
        }

        if (!ownsGui()) return;

        if (actionCooldown > 0) {
            actionCooldown--;
            return;
        }

        switch (state) {
            case IDLE_WAIT_LOOK -> handleIdleWaitLook();
            case OPEN_SPAWNER -> handleOpenSpawner();
            case WAIT_GUI -> handleWaitGui();
            case CHECK_BEFORE_DROP -> handleCheckBeforeDrop();
            case CLICK_DROP -> handleClickDrop();
            case CHECK_AFTER_DROP -> handleCheckAfterDrop();
            case CLICK_SELL_JUNK -> handleClickSellJunk();
            case CLOSE_GUI -> handleCloseGui();
            case WAIT_RESPAWN -> handleWaitRespawn();
            case WAIT_LOOP_RESTART -> handleWaitLoopRestart();
            case DONE -> finishDropRun();
        }
    }

    // ───────────────────── Vòng drop ─────────────────────

    @EventHandler
    private void onKey(KeyEvent event) {
        if (!escCancel.get() || !isActive()) return;
        if (event.action != KeyAction.Press || event.key() != GLFW.GLFW_KEY_ESCAPE) return;

        escPressCount++;
        escWindowTicks = escCancelWindow.get();

        if (escPressCount >= escCancelPresses.get()) {
            log("Nhan ESC " + escPressCount + " lan - tu tat KamiSpawnerDrop.");
            escPressCount = 0;
            escWindowTicks = 0;
            if (isContainerOpen()) closeScreen();
            if (isActive()) toggle();
        }
    }

    private void handleIdleWaitLook() {
        if (remainingDrops <= 0) {
            state = State.DONE;
            return;
        }
        if (isContainerOpen()) {
            closeScreen();
            scheduleDelay();
            return;
        }
        if (!isSavedTargetValid(true)) {
            state = State.DONE;
            return;
        }
        if (targetSpawnerPos != null) {
            state = State.OPEN_SPAWNER;
        }
    }

    private void handleOpenSpawner() {
        if (!isSavedTargetValid(true)) {
            state = State.DONE;
            return;
        }
        BlockHitResult bhr = makeStoredBlockHitResult();
        if (bhr != null) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            waitTicks = 0;
            scannedThisGui = false;
            soldThisCycle = false;
            state = State.WAIT_GUI;
            scheduleDelay();
            log("Right-click Spawner — chờ GUI (còn " + remainingDrops + ").");
        } else {
            state = State.IDLE_WAIT_LOOK;
        }
    }

    private void handleWaitGui() {
        waitTicks++;
        if (isContainerOpen()) {
            if (debugScanSlots.get() && !scannedThisGui) {
                dumpContainerSlots();
                scannedThisGui = true;
            }
            state = State.CHECK_BEFORE_DROP;
            return;
        }
        if (waitTicks > guiWaitTimeout.get()) {
            warning("Timeout GUI Spawner — thử lại.");
            state = State.IDLE_WAIT_LOOK;
            scheduleDelay();
        }
    }

    private void handleCheckBeforeDrop() {
        if (!isContainerOpen()) {
            state = State.IDLE_WAIT_LOOK;
            return;
        }

        Item junk = findBannedInSpawnerGui();
        if (junk != null) {
            handleJunkFound(junk, "trước drop");
            return;
        }

        log("Check trước drop: sạch — bấm VỨT HẾT.");
        state = State.CLICK_DROP;
        scheduleDelay();
    }

    private void handleClickDrop() {
        if (!isContainerOpen()) {
            warning("GUI đóng sớm — mở lại.");
            state = State.IDLE_WAIT_LOOK;
            return;
        }

        int found = resolveDropSlot();
        if (found < 0) {
            error("Không tìm thấy nút VỨT HẾT. Chỉnh drop-slot / tooltip.");
            closeScreen();
            state = State.DONE;
            scheduleDelay();
            return;
        }

        clickSlot(found, 0, SlotActionType.PICKUP);
        log("Đã click VỨT HẾT RA ĐẤT → slot " + found + ".");

        waitTicks = 0;
        state = State.CHECK_AFTER_DROP;
        actionCooldown = 0;
    }

    private void handleCheckAfterDrop() {
        if (!isContainerOpen()) {
            warning("GUI đóng trước khi check sau drop.");
            remainingDrops--;
            if (remainingDrops <= 0) state = State.DONE;
            else state = State.IDLE_WAIT_LOOK;
            scheduleDelay();
            return;
        }

        waitTicks++;
        if (waitTicks < checkDelay.get()) return;

        Item junk = findBannedInSpawnerGui();
        if (junk != null) {
            handleJunkFound(junk, "sau drop");
            return;
        }

        log("Check sau drop: sạch — ESC.");
        state = State.CLOSE_GUI;
        scheduleDelay();
    }

    /**
     * Gặp item lạ (stop-items) → Sell All <b>đúng 1 lần</b> → ESC → tự bật respawn delay.
     */
    private void handleJunkFound(Item junk, String phase) {
        String id = Registries.ITEM.getId(junk).getPath();

        if (!enableSell.get()) {
            error("[SpawnerDrop] Item lạ " + id + " (" + phase + ") — enable-sell=off → dừng.");
            closeScreen();
            stoppedByBannedItem = true;
            remainingDrops = 0;
            state = State.DONE;
            return;
        }

        // Đã sell 1 lần trong vòng này rồi mà vẫn còn item lạ → không sell lại, đóng + respawn
        if (soldThisCycle) {
            log("[SpawnerDrop] Còn item lạ " + id + " sau Sell All ×1 — ESC + respawn delay.");
            state = State.CLOSE_GUI;
            scheduleDelay();
            return;
        }

        log("[SpawnerDrop] Item lạ " + id + " (" + phase + ") → Sell All ×1 slot " + sellSlot.get() + ".");
        state = State.CLICK_SELL_JUNK;
        scheduleDelay();
    }

    private void handleClickSellJunk() {
        if (!isContainerOpen()) {
            warning("GUI đóng trước khi Sell All — mở lại.");
            state = State.IDLE_WAIT_LOOK;
            scheduleDelay();
            return;
        }

        int slot = resolveSellSlot();
        if (slot < 0) {
            error("Không click được Sell All (slot " + sellSlot.get() + "). Chỉnh sell-slot.");
            closeScreen();
            stoppedByBannedItem = true;
            remainingDrops = 0;
            state = State.DONE;
            scheduleDelay();
            return;
        }

        clickSlot(slot, 0, SlotActionType.PICKUP);
        soldThisCycle = true; // đánh dấu → đóng GUI sẽ tự bật respawn delay
        log("Đã Sell All ×1 → slot " + slot + " — ESC rồi auto respawn delay.");
        state = State.CLOSE_GUI;
        scheduleDelay();
    }

    private void handleCloseGui() {
        if (isContainerOpen()) {
            closeScreen();
            log("ESC đóng GUI — xong 1 vòng.");
        }

        remainingDrops--;
        scannedThisGui = false;
        log("Còn " + remainingDrops + " lần.");

        // Sell All 1 lần vì item lạ → luôn tự bật respawn delay (nếu > 0)
        if (soldThisCycle && beginRespawnWait(remainingDrops <= 0 ? "sau sell (vòng cuối)" : "sau sell All ×1")) {
            return;
        }

        soldThisCycle = false;
        if (remainingDrops <= 0) {
            state = State.DONE;
        } else {
            state = State.IDLE_WAIT_LOOK;
        }
        scheduleDelay();
    }

    /**
     * Tự bật sau Sell All ×1. @return true nếu đã vào WAIT_RESPAWN
     */
    private boolean beginRespawnWait(String reason) {
        int min = respawnDelayMin.get();
        if (min <= 0) {
            log("respawn-delay-min=0 — bỏ qua chờ (" + reason + ").");
            return false;
        }
        respawnTotalTicks = min * 60 * 20; // phút → tick (20 TPS)
        respawnWaitTicks = 0;
        state = State.WAIT_RESPAWN;
        log("Auto respawn delay " + min + " phút (" + reason + ").");
        if (finalOrderDuringRespawn.get()) {
            log("Respawn delay vẫn chạy — bật Order cuối để dọn item vừa drop/nhặt.");
            tryResumeOrderBot();
        }
        return true;
    }

    private void handleWaitRespawn() {
        if (ownsGui() && isContainerOpen()) {
            closeScreen();
            return;
        }

        respawnWaitTicks++;
        if (respawnWaitTicks >= respawnTotalTicks) {
            soldThisCycle = false;
            if (remainingDrops <= 0) {
                log("Hết chờ respawn — hoàn tất.");
                state = State.DONE;
            } else {
                if (!ownsGui() && !acquireGuiOwner()) {
                    if (respawnWaitTicks % (10 * 20) == 0) {
                        log("Hết respawn nhưng Order đang dùng GUI — chờ lấy lại quyền để drop tiếp.");
                    }
                    return;
                }
                log("Hết chờ respawn — tiếp tục drop (còn " + remainingDrops + ").");
                state = State.IDLE_WAIT_LOOK;
            }
            scheduleDelay();
            return;
        }

        // Log mỗi ~30s
        if (respawnWaitTicks % (30 * 20) == 0) {
            int leftSec = Math.max(0, (respawnTotalTicks - respawnWaitTicks) / 20);
            log("Respawn... còn ~" + leftSec + "s");
        }
    }

    private void beginLoopRestartWait() {
        if (ownsGui() && isContainerOpen()) closeScreen();
        releaseGuiOwner();

        int minutes = Math.max(0, repeatWaitMinutes.get());
        loopRestartTotalTicks = minutes * 60L * 20L;
        loopRestartWaitTicks = 0;

        if (loopRestartTotalTicks <= 0) {
            restartDropBatchAfterWait();
            return;
        }

        state = State.WAIT_LOOP_RESTART;
        log("Hết drop-times — nghỉ " + minutes + " phút rồi lặp lại batch drop×" + dropTimes.get() + ".");
    }

    private void handleWaitLoopRestart() {
        if (ownsGui() && isContainerOpen()) {
            closeScreen();
            return;
        }

        loopRestartWaitTicks++;
        if (loopRestartWaitTicks < loopRestartTotalTicks) {
            if (loopRestartWaitTicks % (30L * 20L) == 0) {
                long leftSec = Math.max(0, (loopRestartTotalTicks - loopRestartWaitTicks) / 20L);
                log("Đang nghỉ giữa vòng drop... còn ~" + leftSec + "s");
            }
            return;
        }

        restartDropBatchAfterWait();
    }

    private void restartDropBatchAfterWait() {
        if (!acquireGuiOwner()) {
            if (loopRestartWaitTicks % (10L * 20L) == 0) {
                log("Hết nghỉ nhưng GUI đang được module khác dùng — chờ để lặp lại SpawnerDrop.");
            }
            state = State.WAIT_LOOP_RESTART;
            return;
        }

        if (!isSavedTargetValid(true)) {
            state = State.DONE;
            return;
        }

        remainingDrops = dropTimes.get();
        soldThisCycle = false;
        scannedThisGui = false;
        loopRestartWaitTicks = 0;
        loopRestartTotalTicks = 0;
        state = State.IDLE_WAIT_LOOK;
        log("Hết thời gian nghỉ — lặp lại SpawnerDrop drop×" + remainingDrops + ".");
        scheduleDelay();
    }

    // ───────────────────── Check item cấm trong GUI ─────────────────────

    /**
     * Duyệt slot container (không inventory dưới).
     * Arrow &lt; arrow-stop-min-count = nút sang trang → bỏ qua.
     */
    private Item findBannedInSpawnerGui() {
        List<Item> banned = stopItems.get();
        if (banned == null || banned.isEmpty()) return null;
        if (mc.player == null) return null;

        ScreenHandler menu = mc.player.currentScreenHandler;
        if (menu == null) return null;

        int containerSlots = Math.max(0, menu.slots.size() - 36);
        int arrowMin = arrowStopMinCount.get();

        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.slots.get(i).getStack();
            if (stack.isEmpty()) continue;

            if (isDropAllButton(stack) || isSellButton(stack) || isUiControlSlot(stack)) continue;

            Item it = stack.getItem();
            if (!banned.contains(it)) continue;

            if (it == Items.ARROW && stack.getCount() < arrowMin) continue;

            return it;
        }
        return null;
    }

    private boolean isUiControlSlot(ItemStack stack) {
        String all = collectText(stack).toLowerCase(Locale.ROOT);
        if (containsAny(all,
            "steal", "store", "next", "prev", "page", "trang", "close", "đóng",
            "back", "info", "thông tin", "settings", "cài đặt",
            "sang trang", "trang sau", "trang trước", "previous", "forward"
        )) {
            return true;
        }
        if (stack.isOf(Items.ARROW) && stack.getCount() < arrowStopMinCount.get()) {
            return true;
        }
        return false;
    }

    private static boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    // ───────────────────── Resume Order ─────────────────────

    private void tryResumeOrderBot() {
        if (!autoResumeOrder.get()) return;
        if (stoppedByBannedItem) return;

        boolean resume = true;
        try {
            Class<?> cl = Class.forName("com.kami.order.modules.KamiOrderBot");
            java.lang.reflect.Field fResume = cl.getField("resumeOrderAfterDrop");
            resume = fResume.getBoolean(null);
            if (resume) {
                cl.getField("nextActivateIsResume").setBoolean(null, true);
            }
        } catch (ClassNotFoundException e) {
            warning("Không thấy KamiOrderBot — cài kami-order-bot.jar?");
            return;
        } catch (Throwable t) {
            log("Không đọc flag resume (" + t.getMessage() + "), thử bật Order.");
        }

        if (!resume) {
            log("Hết vòng Order — không bật lại Order.");
            return;
        }

        String name = orderModuleName.get();
        if (name == null || name.isBlank()) name = "kami-order-bot";
        Module order = findModuleByName(name.trim());
        if (order == null) {
            warning("Không tìm thấy module \"" + name + "\".");
            return;
        }
        if (order.isActive()) {
            log("Order Bot đang bật sẵn — restart để resume sạch sau Drop.");
            order.toggle();
        }
        try {
            Class<?> cl = Class.forName("com.kami.order.modules.KamiOrderBot");
            cl.getField("nextActivateIsResume").setBoolean(null, true);
        } catch (Throwable ignored) {
        }
        releaseGuiOwner();
        order.toggle();
        if (order.isActive()) log("Đã bật lại " + order.title + " sau drop.");
        else warning("Không bật được Order.");
    }

    private boolean shouldResumeOrderBot() {
        if (!autoResumeOrder.get() || stoppedByBannedItem) return false;
        try {
            Class<?> cl = Class.forName("com.kami.order.modules.KamiOrderBot");
            java.lang.reflect.Field fResume = cl.getField("resumeOrderAfterDrop");
            return fResume.getBoolean(null);
        } catch (ClassNotFoundException e) {
            warning("Không thấy KamiOrderBot — cài kami-order-bot.jar?");
            return false;
        } catch (Throwable t) {
            log("Không đọc được flag resume (" + t.getMessage() + "), vẫn thử bật Order.");
            return true;
        }
    }

    private void tryActivateSpawnerProtect() {
        if (!autoSpawnerProtect.get()) return;

        String name = spawnerProtectModule.get();
        if (name == null || name.isBlank()) name = "kami-spawner-protect";
        name = name.trim();

        Module mod = findModuleByName(name);
        if (mod == null) {
            warning("Khong tim thay module \"" + name + "\" de tu bat Spawner Protect.");
            return;
        }

        if (mod.isActive()) return;

        mod.toggle();
        if (mod.isActive()) {
            log("Da tu bat " + mod.title + " cung voi SpawnerDrop.");
        } else {
            warning("Goi toggle " + mod.title + " nhung module van tat.");
        }
    }

    private Module findModuleByName(String name) {
        if (Modules.get() == null) return null;
        try {
            Module m = Modules.get().get(name);
            if (m != null) return m;
        } catch (Throwable ignored) {
        }
        String key = name.toLowerCase(Locale.ROOT).replace(' ', '-');
        for (Module m : Modules.get().getAll()) {
            if (m == null) continue;
            if (m.name != null && (m.name.equalsIgnoreCase(key) || m.name.equalsIgnoreCase(name))) {
                return m;
            }
            if (m.title != null && m.title.equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    // ───────────────────── Nút drop / sell ─────────────────────

    private int resolveDropSlot() {
        ScreenHandler menu = mc.player.currentScreenHandler;
        int configured = dropSlot.get();

        if (preferTooltipScan.get()) {
            int byTip = findByTooltipDrop();
            if (byTip >= 0) return byTip;
        }

        if (configured >= 0 && configured < menu.slots.size()) {
            ItemStack stack = menu.slots.get(configured).getStack();
            if (forceFixedSlot.get()) {
                if (stack.isEmpty()) {
                    warning("force-fixed-slot: slot " + configured + " trống!");
                    return -1;
                }
                return configured;
            }
            if (!stack.isEmpty() && isDropAllButton(stack)) return configured;
        }
        return findByTooltipDrop();
    }

    private int findByTooltipDrop() {
        ScreenHandler menu = mc.player.currentScreenHandler;
        int containerSlots = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.slots.get(i).getStack();
            if (!stack.isEmpty() && isDropAllButton(stack)) return i;
        }
        return -1;
    }

    private int resolveSellSlot() {
        ScreenHandler menu = mc.player.currentScreenHandler;
        if (menu == null) return -1;
        int configured = sellSlot.get();

        if (configured >= 0 && configured < menu.slots.size()) {
            ItemStack stack = menu.slots.get(configured).getStack();
            if (forceSellSlot.get()) {
                if (stack.isEmpty()) return -1;
                return configured;
            }
            if (!stack.isEmpty() && isSellButton(stack)) return configured;
        }

        // Fallback: quét tooltip bán/sell
        int containerSlots = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.slots.get(i).getStack();
            if (!stack.isEmpty() && isSellButton(stack)) return i;
        }
        return -1;
    }

    private boolean isDropAllButton(ItemStack stack) {
        String all = collectText(stack).toLowerCase(Locale.ROOT);
        return all.contains("vứt hết")
            || all.contains("vut het")
            || all.contains("item ra đất")
            || all.contains("item ra dat")
            || all.contains("vứt hết ra đất")
            || all.contains("bấm vào để vứt")
            || all.contains("bam vao de vut");
    }

    private boolean isSellButton(ItemStack stack) {
        String all = collectText(stack).toLowerCase(Locale.ROOT);
        // Tránh nhầm với drop
        if (isDropAllButton(stack)) return false;
        return all.contains("sell all")
            || all.contains("sellall")
            || all.contains("bán tất")
            || all.contains("ban tat")
            || all.contains("bán hết")
            || all.contains("ban het")
            || all.contains("bán rác")
            || all.contains("ban rac")
            || all.contains("bán all")
            || all.contains("ban all")
            || all.contains("bán")
            || all.contains("sell")
            || all.contains("shop");
    }

    private void dumpContainerSlots() {
        ScreenHandler menu = mc.player.currentScreenHandler;
        int containerSlots = Math.max(0, menu.slots.size() - 36);
        log("--- GUI Spawner " + containerSlots + " slot ---");
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.slots.get(i).getStack();
            if (stack.isEmpty()) continue;
            int row = i / 9 + 1;
            int col = i % 9 + 1;
            String mark = "";
            if (isDropAllButton(stack)) mark += " << DROP";
            if (isSellButton(stack)) mark += " << SELL";
            if (stopItems.get().contains(stack.getItem())) {
                if (!(stack.isOf(Items.ARROW) && stack.getCount() < arrowStopMinCount.get())) {
                    mark += " << BANNED";
                }
            }
            log(String.format(Locale.ROOT, "  slot %d (H%d C%d) %s x%d \"%s\"%s",
                i, row, col,
                Registries.ITEM.getId(stack.getItem()).getPath(),
                stack.getCount(),
                stack.getName().getString(), mark));
        }
    }

    private String collectText(ItemStack stack) {
        List<String> parts = new ArrayList<>();
        parts.add(stack.getName().getString());
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text t : lore.lines()) parts.add(t.getString());
            try {
                for (Text t : lore.styledLines()) parts.add(t.getString());
            } catch (Throwable ignored) {
            }
        }
        try {
            for (Text t : stack.getTooltip(
                Item.TooltipContext.create(mc.world),
                mc.player,
                TooltipType.BASIC
            )) {
                parts.add(t.getString());
            }
        } catch (Throwable ignored) {
        }
        return String.join(" ", parts);
    }

    private boolean captureSpawnerTarget() {
        if (mc.player == null || mc.world == null) return false;
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return false;
        if (!mc.world.getBlockState(bhr.getBlockPos()).isOf(Blocks.SPAWNER)) return false;

        targetSpawnerPos = bhr.getBlockPos().toImmutable();
        targetSpawnerSide = bhr.getSide();
        targetHitPos = bhr.getPos();
        targetWorldKey = mc.world.getRegistryKey();
        return isSavedTargetValid(false);
    }

    private void clearSpawnerTarget() {
        targetSpawnerPos = null;
        targetSpawnerSide = null;
        targetHitPos = null;
        targetWorldKey = null;
    }

    private boolean isSavedTargetValid(boolean notify) {
        if (mc.player == null || mc.world == null || targetSpawnerPos == null || targetWorldKey == null) {
            if (notify) error("Target spawner không còn hợp lệ — dừng module.");
            clearSpawnerTarget();
            return false;
        }
        if (!mc.world.getRegistryKey().equals(targetWorldKey)) {
            if (notify) error("Đã đổi world/dimension — xóa target spawner cũ.");
            clearSpawnerTarget();
            return false;
        }
        if (!mc.world.getBlockState(targetSpawnerPos).isOf(Blocks.SPAWNER)) {
            if (notify) error("Block target không còn là Spawner — dừng module.");
            clearSpawnerTarget();
            return false;
        }
        if (mc.player.squaredDistanceTo(Vec3d.ofCenter(targetSpawnerPos)) > MAX_INTERACT_DISTANCE_SQUARED) {
            if (notify) error("Bạn đã ở quá xa target spawner — dừng module.");
            return false;
        }
        return true;
    }

    private BlockHitResult makeStoredBlockHitResult() {
        if (targetSpawnerPos == null) return null;
        Direction side = targetSpawnerSide == null ? Direction.UP : targetSpawnerSide;
        Vec3d hitPos = targetHitPos == null ? Vec3d.ofCenter(targetSpawnerPos) : targetHitPos;
        return new BlockHitResult(hitPos, side, targetSpawnerPos, false);
    }

    /**
     * Click slot silent — packet clickSlot only, không GLFW di chuyển chuột tới ô.
     */
    private void clickSlot(int slotId, int button, SlotActionType type) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!isActive() || !ownsGui()) return;
        ScreenHandler menu = mc.player.currentScreenHandler;
        if (menu == null) return;
        if (slotId < 0 || slotId >= menu.slots.size()) return;
        mc.interactionManager.clickSlot(menu.syncId, slotId, button, type, mc.player);
    }

    private boolean isContainerOpen() {
        if (mc.player == null) return false;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return true;
        return mc.currentScreen instanceof HandledScreen;
    }

    private void closeScreen() {
        if (mc.player != null && ownsGui()) mc.player.closeHandledScreen();
    }

    private boolean acquireGuiOwner() {
        try {
            Class<?> cl = Class.forName("com.kami.order.modules.KamiOrderBot");
            return (Boolean) cl.getMethod("tryAcquireGuiOwner", String.class).invoke(null, GUI_OWNER_SPAWNER);
        } catch (ClassNotFoundException e) {
            return true;
        } catch (Throwable t) {
            warning("Không kiểm tra được GUI ownership (" + t.getMessage() + ").");
            return false;
        }
    }

    private boolean ownsGui() {
        try {
            Class<?> cl = Class.forName("com.kami.order.modules.KamiOrderBot");
            return (Boolean) cl.getMethod("isGuiOwner", String.class).invoke(null, GUI_OWNER_SPAWNER);
        } catch (ClassNotFoundException e) {
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void releaseGuiOwner() {
        try {
            Class<?> cl = Class.forName("com.kami.order.modules.KamiOrderBot");
            cl.getMethod("releaseGuiOwner", String.class).invoke(null, GUI_OWNER_SPAWNER);
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            warning("Không release được GUI ownership (" + t.getMessage() + ").");
        }
    }

    private void scheduleDelay() {
        int base = Math.max(1, delay.get());
        int jitter = ThreadLocalRandom.current().nextInt(0, Math.max(2, base / 3 + 1));
        actionCooldown = base + jitter + ThreadLocalRandom.current().nextInt(0, 4);
    }

    private void log(String msg) {
        if (chatFeedback.get()) info(msg);
    }

    @Override
    public String getInfoString() {
        if (!autoDropSpawner.get()) return "off";
        if (state == State.WAIT_RESPAWN && respawnTotalTicks > 0) {
            int leftSec = Math.max(0, (respawnTotalTicks - respawnWaitTicks) / 20);
            return "respawn " + leftSec + "s";
        }
        if (state == State.WAIT_LOOP_RESTART && loopRestartTotalTicks > 0) {
            long leftSec = Math.max(0, (loopRestartTotalTicks - loopRestartWaitTicks) / 20L);
            return "wait " + leftSec + "s";
        }
        return switch (state) {
            case CHECK_BEFORE_DROP -> "pre-check";
            case CHECK_AFTER_DROP -> "post-check";
            case CLICK_SELL_JUNK -> "sell×1";
            default -> "s" + dropSlot.get() + (remainingDrops > 0 ? " x" + remainingDrops : "");
        };
    }

    private void finishDropRun() {
        if (stoppedByBannedItem) {
            log("[SpawnerDrop] Dừng an toàn (item lạ, sell off/lỗi). Không resume Order.");
        } else if (repeatAfterWait.get()) {
            beginLoopRestartWait();
            return;
        } else {
            log("Hoàn tất drop-times — bật lại Order nếu còn vòng.");
            tryResumeOrderBot();
        }
        if (isActive()) toggle();
    }
}
