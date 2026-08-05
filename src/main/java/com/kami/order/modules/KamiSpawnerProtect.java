package com.kami.order.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * KamiSpawnerProtect - bao ve Spawner da nhin vao khi bat module.
 *
 * Module chi thao tac qua tick/client API: khong dung chuot that, ban phim that,
 * GLFW, Robot, cursor warp hay toa do man hinh.
 */
public class KamiSpawnerProtect extends Module {
    private static final String GUI_OWNER_PROTECT = "SPAWNER_PROTECT";
    private static final double MAX_ENDER_CHEST_RANGE = 10.0;
    private static final double MAX_ENDER_CHEST_RANGE_SQ = MAX_ENDER_CHEST_RANGE * MAX_ENDER_CHEST_RANGE;
    private static final double MAX_INTERACT_RANGE_SQ = 36.0;
    private static final int FORCED_GUI_CLOSE_ATTEMPTS = 3;
    private static final int FINAL_STORE_CHECK_MAX_ATTEMPTS = 3;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgThreat = settings.createGroup("Threat");
    private final SettingGroup sgChest = settings.createGroup("Ender Chest");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    private final Setting<ToolMode> toolMode = sgGeneral.add(new EnumSetting.Builder<ToolMode>()
        .name("tool-mode")
        .description("Cach chon cuoc de pha Spawner.")
        .defaultValue(ToolMode.Best_Hotbar_Tool)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Xoay ve Spawner / Ender Chest bang rotation packet cua Meteor.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay tick giua cac hanh dong.")
        .defaultValue(1)
        .range(0, 40)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> protectRadius = sgGeneral.add(new IntSetting.Builder()
        .name("protect-radius")
        .description("Ban kinh don/cat cac Spawner quanh target da chon.")
        .defaultValue(5)
        .range(1, 10)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> locateRadius = sgGeneral.add(new IntSetting.Builder()
        .name("locate-radius")
        .description("Ban kinh dinh vi Spawner xa trong vung client da load, giong cach ESP thay block entity.")
        .defaultValue(32)
        .range(5, 128)
        .sliderRange(5, 96)
        .build()
    );

    private final Setting<Boolean> keepRunning = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-running")
        .description("Cat xong mot Spawner thi tiep tuc tim Spawner ke tiep thay vi tu tat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disconnectAfterClear = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-after-clear")
        .description("Sau khi cat het Spawner quanh khu vuc va khong con target ke tiep thi tu dong out khoi server.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoRunWithoutThreat = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-run-without-threat")
        .description("Tu dinh vi, di toi, dap va cat Spawner ma khong can nguoi la lai gan.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> minEmptySlotsBeforeBreak = sgGeneral.add(new IntSetting.Builder()
        .name("min-empty-slots-before-break")
        .description("Khong dap Spawner neu inventory con it hon so slot trong nay.")
        .defaultValue(9)
        .range(0, 36)
        .sliderRange(0, 18)
        .build()
    );

    private final Setting<Boolean> autoSellBeforeBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-sell-before-break")
        .description("Neu chua an toan thi mo /sell va shift-click tat ca stack ban duoc vao GUI.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> sellCommand = sgGeneral.add(new meteordevelopment.meteorclient.settings.StringSetting.Builder()
        .name("sell-command")
        .description("Lenh mo GUI sell, khong can dau /. Mac dinh: sell.")
        .defaultValue("sell")
        .visible(autoSellBeforeBreak::get)
        .build()
    );

    private final Setting<SellCleanupMode> sellCleanupMode = sgGeneral.add(new EnumSetting.Builder<SellCleanupMode>()
        .name("sell-cleanup-mode")
        .description("Safe_Whitelist chi shift-click item trong whitelist de tranh item server khong ban duoc.")
        .defaultValue(SellCleanupMode.Safe_Whitelist)
        .visible(autoSellBeforeBreak::get)
        .build()
    );

    private final Setting<List<Item>> sellWhitelistItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("sell-whitelist-items")
        .description("Chi cac item nay moi duoc shift-click vao GUI sell trong SpawnerProtect.")
        .defaultValue(List.of(Items.BONE, Items.ARROW, Items.ROTTEN_FLESH, Items.STRING, Items.SPIDER_EYE, Items.GUNPOWDER))
        .visible(() -> autoSellBeforeBreak.get() && sellCleanupMode.get() == SellCleanupMode.Safe_Whitelist)
        .build()
    );

    private final Setting<Integer> sellCleanupAttemptsMax = sgGeneral.add(new IntSetting.Builder()
        .name("sell-cleanup-attempts")
        .description("So lan toi da mo /sell de don do. Dat cao de ban den khi het item duoi dat.")
        .defaultValue(60)
        .range(1, 999)
        .sliderRange(1, 120)
        .visible(autoSellBeforeBreak::get)
        .build()
    );

    private final Setting<Integer> sellOpenDelay = sgGeneral.add(new IntSetting.Builder()
        .name("sell-open-delay")
        .description("So tick doi giua cac lan gui/mo GUI sell trong cleanup.")
        .defaultValue(1)
        .range(0, 20)
        .sliderRange(0, 10)
        .visible(autoSellBeforeBreak::get)
        .build()
    );

    private final Setting<Integer> sellCloseDelay = sgGeneral.add(new IntSetting.Builder()
        .name("sell-close-delay")
        .description("So tick doi sau khi shift-click tat ca item vao GUI sell 2 lan truoc khi dong.")
        .defaultValue(1)
        .range(0, 20)
        .sliderRange(0, 10)
        .visible(autoSellBeforeBreak::get)
        .build()
    );

    private final Setting<Integer> sellPickupWait = sgGeneral.add(new IntSetting.Builder()
        .name("sell-pickup-wait")
        .description("So tick doi sau khi dong GUI sell de server cap nhat slot/item duoi dat.")
        .defaultValue(3)
        .range(0, 40)
        .sliderRange(0, 20)
        .visible(autoSellBeforeBreak::get)
        .build()
    );

    private final Setting<Integer> maxGroundItemsBeforeBreak = sgGeneral.add(new IntSetting.Builder()
        .name("max-ground-items-before-break")
        .description("Khong dap Spawner neu quanh chan co nhieu item entity hon muc nay.")
        .defaultValue(0)
        .range(0, 256)
        .sliderRange(0, 64)
        .build()
    );

    private final Setting<Double> groundItemCheckRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("ground-item-check-radius")
        .description("Ban kinh quanh player de dem item entity duoi chan truoc khi dap Spawner.")
        .defaultValue(2.5)
        .min(0.5)
        .sliderRange(0.5, 8.0)
        .build()
    );

    private final Setting<Boolean> autoWalkToSpawner = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-walk-to-spawner")
        .description("Cho phep dung Meteor/Baritone path manager de di toi Spawner ngoai tam interact.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> walkTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("walk-timeout")
        .description("So tick toi da cho path manager di toi Spawner.")
        .defaultValue(200)
        .range(20, 1200)
        .sliderRange(20, 400)
        .visible(autoWalkToSpawner::get)
        .build()
    );

    private final Setting<Integer> waitPickupTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("wait-pickup-timeout")
        .description("So tick toi da cho Spawner vao inventory sau khi block bi pha.")
        .defaultValue(160)
        .range(20, 600)
        .sliderRange(20, 240)
        .build()
    );

    private final Setting<Integer> guiWaitTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("gui-wait-timeout")
        .description("So tick toi da cho Ender Chest GUI mo.")
        .defaultValue(100)
        .range(20, 400)
        .sliderRange(20, 160)
        .build()
    );

    private final Setting<Double> detectionRange = sgThreat.add(new DoubleSetting.Builder()
        .name("detection-range")
        .description("Ban kinh phat hien nguoi la quanh Spawner da luu. Toi da slider 256 neu server gui entity.")
        .defaultValue(64.0)
        .min(1.0)
        .sliderRange(1.0, 256.0)
        .build()
    );

    private final Setting<Integer> scanInterval = sgThreat.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("Moi bao nhieu tick thi quet player mot lan.")
        .defaultValue(1)
        .range(1, 40)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> confirmTicks = sgThreat.add(new IntSetting.Builder()
        .name("threat-confirm-ticks")
        .description("So tick xac nhan nguoi la van o trong vung bao ve.")
        .defaultValue(1)
        .range(1, 80)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<List<String>> whitelist = sgThreat.add(new StringListSetting.Builder()
        .name("whitelist")
        .description("Ten player bo qua ngoai Friends cua Meteor.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<Boolean> placeEnderChestIfMissing = sgChest.add(new BoolSetting.Builder()
        .name("place-if-missing")
        .description("Khong co Ender Chest trong ban kinh 10 block thi dat Ender Chest tu inventory.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> placeSearchRadius = sgChest.add(new IntSetting.Builder()
        .name("place-search-radius")
        .description("Ban kinh quet vi tri dat Ender Chest quanh player khi cac o sat canh bi chan.")
        .defaultValue(3)
        .range(1, 6)
        .sliderRange(1, 5)
        .visible(placeEnderChestIfMissing::get)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgDebug.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("In log trang thai ra chat.")
        .defaultValue(true)
        .build()
    );

    private State state = State.IDLE;
    private BlockPos targetSpawnerPos;
    private Direction targetSpawnerSide;
    private Vec3d targetHitPos;
    private RegistryKey<World> targetWorldKey;
    private PlayerEntity confirmedThreat;
    private BlockPos enderChestPos;
    private BlockPos placedEnderChestPos;
    private int originalSlot = -1;
    private boolean wasSneaking;
    private boolean sneakStarted;
    private int cooldown;
    private int waitTicks;
    private int threatTicks;
    private int scanTicks;
    private int movedEnderChestSourceSlotId = -1;
    private int movedEnderChestHotbarSlot = -1;
    private int movedToolSourceSlotId = -1;
    private int movedToolHotbarSlot = -1;
    private int spawnerCountBeforeStore;
    private int storeVerifyTicks;
    private int storeRetryCount;
    private int spawnerCountBeforeBreak;
    private boolean pathingToSpawner;
    private int pathTicks;
    private boolean protectGuiOwnerAcquired;
    private int sellCleanupAttempts;
    private int sellVerifyTicks;
    private int forcedGuiCloseAttempts;
    private int finalStoreCheckTicks;
    private int finalStoreCheckAttempts;

    public KamiSpawnerProtect() {
        super(Categories.Misc, "kami-spawner-protect",
            "Bao ve Spawner da luu: gap nguoi la thi pha va cat vao Ender Chest.");
    }

    public static boolean shouldPreventMouseLock() {
        return false;
    }

    @Override
    public void onActivate() {
        resetRuntime();
        if (!captureSpawnerTarget()) {
            targetWorldKey = mc.world.getRegistryKey();
            log("Chua co Spawner target khi bat - se tu dinh vi Spawner gan nhat khi co threat.");
        }

        originalSlot = mc.player.getInventory().getSelectedSlot();
        wasSneaking = mc.player.isSneaking();
        state = State.ARMED;
        if (targetSpawnerPos != null) {
            log("Da luu Spawner " + targetSpawnerPos.toShortString() + " - dang bao ve.");
        } else {
            log("KamiSpawnerProtect dang canh gac - khong can nhin thang vao Spawner luc bat.");
        }
    }

    @Override
    public void onDeactivate() {
        restoreState();
        if (mc.player != null && isContainerOpen() && ownsGui()) mc.player.closeHandledScreen();
        clearTarget();
        resetRuntime();
        releaseProtectGuiOwner();
    }

    private void resetRuntime() {
        state = State.IDLE;
        confirmedThreat = null;
        enderChestPos = null;
        placedEnderChestPos = null;
        cooldown = 0;
        waitTicks = 0;
        threatTicks = 0;
        scanTicks = 0;
        movedEnderChestSourceSlotId = -1;
        movedEnderChestHotbarSlot = -1;
        movedToolSourceSlotId = -1;
        movedToolHotbarSlot = -1;
        spawnerCountBeforeStore = 0;
        storeVerifyTicks = 0;
        storeRetryCount = 0;
        spawnerCountBeforeBreak = 0;
        sneakStarted = false;
        pathingToSpawner = false;
        pathTicks = 0;
        protectGuiOwnerAcquired = false;
        sellCleanupAttempts = 0;
        sellVerifyTicks = 0;
        forcedGuiCloseAttempts = 0;
        finalStoreCheckTicks = 0;
        finalStoreCheckAttempts = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            state = State.ERROR;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        switch (state) {
            case IDLE -> state = State.ARMED;
            case ARMED -> handleArmed();
            case SCAN_PLAYERS -> handleScanPlayers();
            case THREAT_CONFIRM -> handleThreatConfirm();
            case MOVE_TO_SPAWNER -> handleMoveToSpawner();
            case SWAP_PICKAXE -> handleSwapPickaxe();
            case START_SNEAK -> handleStartSneak();
            case ROTATE -> handleRotate();
            case ENSURE_INVENTORY_SPACE -> handleEnsureInventorySpace();
            case OPEN_SELL_GUI -> handleOpenSellGui();
            case SELL_ITEMS -> handleSellItems();
            case SELL_ITEMS_SECOND_PASS -> handleSellItemsSecondPass();
            case VERIFY_SELL_SPACE -> handleVerifySellSpace();
            case BREAK_SPAWNER -> handleBreakSpawner();
            case WAIT_PICKUP -> handleWaitPickup();
            case FIND_ENDER_CHEST -> handleFindEnderChest();
            case PLACE_ENDER_CHEST -> handlePlaceEnderChest();
            case OPEN_ENDER_CHEST -> handleOpenEnderChest();
            case STORE_SPAWNER -> handleStoreSpawner();
            case VERIFY_STORE -> handleVerifyStore();
            case FINAL_STORE_CHECK -> handleFinalStoreCheck();
            case RESTORE_STATE -> handleRestoreState();
            case COMPLETED -> finish("Hoan tat bao ve Spawner - module tu tat.");
            case ERROR -> finish("Dung SpawnerProtect do loi/trang thai khong hop le.");
        }
    }

    private void handleArmed() {
        if (!refreshTargetSpawner(false)) {
            log("Chua thay Spawner trong ban kinh " + protectRadius.get() + " block - tiep tuc canh gac.");
            state = State.SCAN_PLAYERS;
            return;
        }
        if (!isTargetStillValid(true)) {
            state = State.ERROR;
            return;
        }
        state = State.SCAN_PLAYERS;
    }

    private void handleScanPlayers() {
        if (targetSpawnerPos != null && !isTargetStillValid(true)) {
            state = State.ERROR;
            return;
        }

        scanTicks++;
        if (scanTicks < scanInterval.get()) return;
        scanTicks = 0;

        PlayerEntity threat = findThreat();
        if (threat == null) {
            if (autoRunWithoutThreat.get()) {
                if (!refreshTargetSpawner(true)) {
                    log("Auto-run: chua tim thay Spawner trong locate-radius " + locateRadius.get() + " block.");
                    return;
                }
                if (!ensureProtectGuiOwner(false)) return;
                log("Auto-run: cat Spawner " + targetSpawnerPos.toShortString() + ".");
                state = shouldWalkToTarget() ? State.MOVE_TO_SPAWNER : State.SWAP_PICKAXE;
            }
            return;
        }

        stopOrderAndDropImmediately();
        confirmedThreat = threat;
        threatTicks = 0;
        state = State.THREAT_CONFIRM;
        log("Phat hien nguoi la: " + threat.getName().getString() + " - dang xac nhan.");
    }

    private void handleThreatConfirm() {
        if (targetSpawnerPos != null && !isTargetStillValid(true)) {
            state = State.ERROR;
            return;
        }

        PlayerEntity threat = findThreat();
        if (threat == null) {
            if (autoRunWithoutThreat.get()) {
                if (!refreshTargetSpawner(true)) {
                    state = State.SCAN_PLAYERS;
                    return;
                }
                if (!ensureProtectGuiOwner(false)) return;
                log("Auto-run tiep tuc: cat Spawner " + targetSpawnerPos.toShortString() + ".");
                state = shouldWalkToTarget() ? State.MOVE_TO_SPAWNER : State.SWAP_PICKAXE;
                return;
            }
            confirmedThreat = null;
            threatTicks = 0;
            state = State.SCAN_PLAYERS;
            log("Nguoi la da roi vung bao ve - quay lai canh gac.");
            return;
        }

        stopOrderAndDropImmediately();
        confirmedThreat = threat;
        threatTicks++;
        if (threatTicks >= confirmTicks.get()) {
            if (!refreshTargetSpawner(true)) {
                warning("Co threat nhung khong tim thay Spawner trong locate-radius " + locateRadius.get() + " block.");
                state = State.SCAN_PLAYERS;
                return;
            }
            if (!ensureProtectGuiOwner(true)) return;
            log("Xac nhan threat: " + threat.getName().getString() + " - cat Spawner " + targetSpawnerPos.toShortString() + ".");
            state = shouldWalkToTarget() ? State.MOVE_TO_SPAWNER : State.SWAP_PICKAXE;
        }
    }

    private void handleMoveToSpawner() {
        if (targetSpawnerPos == null || !isTargetStillValid(true)) {
            state = State.ERROR;
            return;
        }

        if (isTargetInInteractRange()) {
            stopPathingToSpawner();
            state = State.SWAP_PICKAXE;
            scheduleDelay();
            return;
        }

        if (!autoWalkToSpawner.get()) {
            error("Spawner ngoai tam interact va auto-walk-to-spawner dang tat.");
            state = State.ERROR;
            return;
        }

        if ("none".equalsIgnoreCase(PathManagers.get().getName())) {
            error("Khong co Meteor path manager/Baritone de auto walk toi Spawner.");
            state = State.ERROR;
            return;
        }

        if (!pathingToSpawner) {
            PathManagers.get().moveTo(targetSpawnerPos);
            pathingToSpawner = true;
            pathTicks = 0;
            log("Dang auto-walk toi Spawner " + targetSpawnerPos.toShortString() + ".");
        }

        pathTicks++;
        if (pathTicks > walkTimeout.get()) {
            stopPathingToSpawner();
            error("Timeout auto-walk toi Spawner - dung de tranh ket path.");
            state = State.ERROR;
        }
    }

    private void handleSwapPickaxe() {
        if (!isTargetStillValid(true)) {
            state = State.ERROR;
            return;
        }

        originalSlot = mc.player.getInventory().getSelectedSlot();
        if (toolMode.get() == ToolMode.Current_Hotbar) {
            if (!selectCurrentOrHotbarPickaxe()) {
                warning("Current_Hotbar: slot hien tai/hotbar khong co cuoc - hay de cuoc len hotbar hoac dung Best_Hotbar_Tool.");
            }
        } else if (!selectBestPickaxe()) {
            warning("Khong tim thay cuoc trong hotbar/inventory - se khong dap bang tay neu server yeu cau Silk Touch.");
        }
        state = State.START_SNEAK;
        scheduleDelay();
    }

    private void handleStartSneak() {
        if (!sneakStarted) {
            wasSneaking = mc.player.isSneaking();
            sendSneak(true);
            sneakStarted = true;
        }
        state = rotate.get() ? State.ROTATE : State.ENSURE_INVENTORY_SPACE;
    }

    private void handleRotate() {
        Vec3d hit = targetHitPos == null ? Vec3d.ofCenter(targetSpawnerPos) : targetHitPos;
        Rotations.rotate(Rotations.getYaw(hit), Rotations.getPitch(hit), 50);
        state = State.ENSURE_INVENTORY_SPACE;
    }

    private void handleEnsureInventorySpace() {
        if (!isTargetStillValid(true)) {
            state = State.ERROR;
            return;
        }

        if (!isSafeToBreakSpawner()) return;

        spawnerCountBeforeBreak = countSpawnersInPlayerInventory();
        state = State.BREAK_SPAWNER;
    }

    private void handleBreakSpawner() {
        if (targetSpawnerPos == null || !mc.world.getBlockState(targetSpawnerPos).isOf(Blocks.SPAWNER)) {
            stopTemporarySneak();
            waitTicks = 0;
            state = State.WAIT_PICKUP;
            scheduleDelay();
            return;
        }

        if (!isTargetStillValid(true)) {
            state = State.ERROR;
            return;
        }

        if (!isSafeToBreakSpawner()) return;

        if (!isTargetInInteractRange()) {
            state = shouldWalkToTarget() ? State.MOVE_TO_SPAWNER : State.ERROR;
            if (state == State.ERROR) error("Spawner ngoai tam interact va auto-walk-to-spawner dang tat.");
            return;
        }

        BlockUtils.breakBlock(targetSpawnerPos, true);
    }

    private void beginSellCleanup(String reason) {
        if (!autoSellBeforeBreak.get()) {
            error(reason + " Auto sell dang tat - khong dap Spawner.");
            state = State.ERROR;
            return;
        }

        if (sellCleanupAttempts >= sellCleanupAttemptsMax.get()) {
            error(reason + " Da thu /sell " + sellCleanupAttempts + " lan nhung van chua an toan.");
            state = State.ERROR;
            return;
        }

        sellCleanupAttempts++;
        sellVerifyTicks = 0;
        waitTicks = 0;
        stopTemporarySneak();
        if (isContainerOpen()) closeScreen();

        log(reason + " Mo /sell de don do truoc khi dap Spawner (lan "
            + sellCleanupAttempts + "/" + sellCleanupAttemptsMax.get() + ").");
        state = State.OPEN_SELL_GUI;
        scheduleFixedDelay(sellOpenDelay.get());
    }

    private void handleOpenSellGui() {
        if (isContainerOpen()) {
            state = State.SELL_ITEMS;
            return;
        }

        if (waitTicks == 0) {
            String cmd = sellCommand.get();
            if (cmd == null || cmd.isBlank()) cmd = "sell";
            cmd = cmd.trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);

            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendChatCommand(cmd);
            } else if (mc.player.networkHandler != null) {
                mc.player.networkHandler.sendChatMessage("/" + cmd);
            }
            log("Da gui /" + cmd + " - cho GUI sell.");
        }

        waitTicks++;
        if (waitTicks > guiWaitTimeout.get()) {
            error("Timeout mo GUI /sell - khong dap Spawner.");
            state = State.ERROR;
            return;
        }
        scheduleFixedDelay(sellOpenDelay.get());
    }

    private void handleSellItems() {
        if (!isContainerOpen()) {
            waitTicks = 0;
            state = State.OPEN_SELL_GUI;
            return;
        }

        int moved = quickMoveSellableItems(1);
        log("Da shift-click item ban duoc vao GUI sell lan 1, tong " + moved + " stack.");
        state = State.SELL_ITEMS_SECOND_PASS;
        scheduleFixedDelay(1);
    }

    private void handleSellItemsSecondPass() {
        if (!isContainerOpen()) {
            waitTicks = 0;
            state = State.OPEN_SELL_GUI;
            return;
        }

        int moved = quickMoveSellableItems(1);
        log("Da shift-click item ban duoc vao GUI sell lan 2, tong " + moved + " stack.");
        sellVerifyTicks = 0;
        state = State.VERIFY_SELL_SPACE;
        scheduleFixedDelay(sellCloseDelay.get());
    }

    private void handleVerifySellSpace() {
        sellVerifyTicks++;
        int closeAfterTicks = Math.max(0, sellCloseDelay.get());
        int verifyAfterTicks = closeAfterTicks + Math.max(0, sellPickupWait.get());

        if (isContainerOpen()) {
            if (sellVerifyTicks >= closeAfterTicks) closeScreen();
            scheduleFixedDelay(1);
            return;
        }

        if (sellVerifyTicks < verifyAfterTicks) {
            scheduleFixedDelay(1);
            return;
        }

        if (isBreakPickupSafeNow()) {
            log("Da don do xong - tiep tuc dap Spawner.");
            sellCleanupAttempts = 0;
            state = State.START_SNEAK;
            scheduleDelay();
            return;
        }

        beginSellCleanup(getBreakSafetyReason());
    }

    private void handleWaitPickup() {
        waitTicks++;

        if (hasNewSpawnerPickedUp()) {
            log("Spawner da vao inventory - tim Ender Chest gan nhat.");
            waitTicks = 0;
            state = State.FIND_ENDER_CHEST;
            scheduleDelay();
            return;
        }

        if (waitTicks > 5 && hasSpawnerInInventory()) {
            log("Phat hien Spawner trong inventory sau khi dap - di cat Ender Chest.");
            waitTicks = 0;
            state = State.FIND_ENDER_CHEST;
            scheduleDelay();
            return;
        }

        if (waitTicks > waitPickupTimeout.get()) {
            if (hasSpawnerInInventory()) {
                warning("Timeout dem pickup nhung inventory co Spawner - van di cat Ender Chest.");
                waitTicks = 0;
                state = State.FIND_ENDER_CHEST;
                scheduleDelay();
                return;
            }

            warning("Timeout cho Spawner vao inventory - tim Spawner tiep theo neu con.");
            afterOneSpawnerCycle();
        }
    }

    private void handleFindEnderChest() {
        enderChestPos = findNearestEnderChest();
        if (enderChestPos != null) {
            log("Tim thay Ender Chest gan nhat: " + enderChestPos.toShortString() + ".");
            state = State.OPEN_ENDER_CHEST;
            scheduleDelay();
            return;
        }

        if (!placeEnderChestIfMissing.get()) {
            error("Khong co Ender Chest trong ban kinh 10 block - giu Spawner trong inventory.");
            state = State.ERROR;
            return;
        }

        if (!hasEnderChestAvailable()) {
            error("Khong co Ender Chest gan do va inventory/hotbar cung khong co - giu Spawner trong inventory.");
            state = State.ERROR;
            return;
        }

        state = State.PLACE_ENDER_CHEST;
        scheduleDelay();
    }

    private void handlePlaceEnderChest() {
        BlockPos placePos = findPlacePosNearPlayer();
        if (placePos == null) {
            error("Khong tim duoc vi tri dat Ender Chest gan player - giu Spawner trong inventory.");
            state = State.ERROR;
            return;
        }

        FindItemResult hotbarChest = InvUtils.findInHotbar(Items.ENDER_CHEST);
        if (!hotbarChest.found()) {
            int movedSlot = moveEnderChestToHotbar();
            if (movedSlot < 0) {
                error("Khong the dua Ender Chest len hotbar - giu Spawner trong inventory.");
                state = State.ERROR;
                return;
            }
            log("Da gui swap Ender Chest len hotbar slot " + movedSlot + " - cho sync roi dat.");
            scheduleDelay();
            return;
        }

        if (!BlockUtils.place(placePos, hotbarChest, rotate.get(), 50, true, true, false)) {
            error("Dat Ender Chest that bai - giu Spawner trong inventory.");
            state = State.ERROR;
            return;
        }

        placedEnderChestPos = placePos.toImmutable();
        enderChestPos = placedEnderChestPos;
        log("Da dat Ender Chest tai " + placePos.toShortString() + ".");
        state = State.OPEN_ENDER_CHEST;
        scheduleDelay();
    }

    private void handleOpenEnderChest() {
        if (enderChestPos == null) {
            state = State.FIND_ENDER_CHEST;
            return;
        }

        if (isContainerOpen()) {
            waitTicks = 0;
            state = State.STORE_SPAWNER;
            return;
        }

        if (!mc.world.getBlockState(enderChestPos).isOf(Blocks.ENDER_CHEST)) {
            warning("Ender Chest da mat - tim lai trong ban kinh 10 block.");
            enderChestPos = null;
            state = State.FIND_ENDER_CHEST;
            return;
        }

        if (mc.player.squaredDistanceTo(Vec3d.ofCenter(enderChestPos)) > MAX_INTERACT_RANGE_SQ) {
            error("Ender Chest ngoai tam interact - khong tu di bo/pathfind, giu Spawner trong inventory.");
            state = State.ERROR;
            return;
        }

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(enderChestPos), Direction.UP, enderChestPos, false);
        if (rotate.get()) Rotations.rotate(Rotations.getYaw(Vec3d.ofCenter(enderChestPos)), Rotations.getPitch(Vec3d.ofCenter(enderChestPos)), 50);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        waitTicks++;
        if (waitTicks > guiWaitTimeout.get()) {
            error("Timeout mo Ender Chest GUI - giu Spawner trong inventory.");
            state = State.ERROR;
        }
        scheduleDelay();
    }

    private void handleStoreSpawner() {
        if (!isContainerOpen()) {
            state = State.OPEN_ENDER_CHEST;
            return;
        }

        if (!hasSpaceForSpawner()) {
            error("Ender Chest day - khong ghi de/khong nem do, giu Spawner trong inventory.");
            state = State.ERROR;
            return;
        }

        int slot = findSpawnerSlotInOpenHandler();
        if (slot < 0) {
            error("Khong tim thay stack Spawner trong player inventory khi GUI mo.");
            state = State.ERROR;
            return;
        }

        spawnerCountBeforeStore = countSpawnersInPlayerInventory();
        storeVerifyTicks = 0;
        clickSlot(slot, 0, SlotActionType.QUICK_MOVE);
        log("Da QUICK_MOVE Spawner vao Ender Chest tu slot " + slot + ".");
        state = State.VERIFY_STORE;
        scheduleDelay();
    }

    private void handleVerifyStore() {
        if (!isContainerOpen()) {
            error("GUI dong truoc khi xac minh cat Spawner.");
            state = State.ERROR;
            return;
        }

        int now = countSpawnersInPlayerInventory();
        storeVerifyTicks++;
        if (now < spawnerCountBeforeStore) {
            storeRetryCount = 0;
            if (hasSpawnerInInventory()) {
                log("Da cat 1 stack Spawner - tiep tuc cat cac stack Spawner con lai.");
                state = State.STORE_SPAWNER;
                scheduleDelay();
                return;
            }

            log("Da cat het cac stack Spawner theo GUI - kiem tra lan cuoi.");
            beginFinalStoreCheck();
            scheduleDelay();
            return;
        }

        if (storeVerifyTicks < 6) {
            scheduleDelay();
            return;
        }

        if (!hasSpawnerInInventory()) {
            log("Da cat het Spawner - kiem tra lan cuoi.");
            beginFinalStoreCheck();
            scheduleDelay();
            return;
        }

        if (hasSpaceForSpawner() && storeRetryCount < 4) {
            storeRetryCount++;
            warning("QUICK_MOVE chua giam Spawner sau sync - thu lai lan " + storeRetryCount + "/4.");
            state = State.STORE_SPAWNER;
            scheduleDelay();
            return;
        }

        error("Spawner van con trong inventory sau QUICK_MOVE - co the Ender Chest day hoac stack khong the gop.");
        state = State.ERROR;
    }

    private void beginFinalStoreCheck() {
        finalStoreCheckTicks = 0;
        state = State.FINAL_STORE_CHECK;
    }

    private void handleFinalStoreCheck() {
        finalStoreCheckTicks++;
        if (finalStoreCheckTicks < 2) {
            scheduleFixedDelay(1);
            return;
        }

        if (!hasSpawnerInInventory()) {
            if (isContainerOpen()) closeScreen();
            finalStoreCheckAttempts = 0;
            log("Kiem tra lan cuoi: inventory khong con Spawner.");
            afterOneSpawnerCycle();
            scheduleDelay();
            return;
        }

        if (isContainerOpen()) {
            warning("Kiem tra lan cuoi: van con Spawner trong inventory - dong GUI de mo lai Ender Chest.");
            closeScreen();
            scheduleFixedDelay(1);
            return;
        }

        if (finalStoreCheckAttempts >= FINAL_STORE_CHECK_MAX_ATTEMPTS) {
            error("Kiem tra lan cuoi van con Spawner trong inventory sau " + FINAL_STORE_CHECK_MAX_ATTEMPTS + " lan thu - giu lai de tranh thao tac sai.");
            state = State.ERROR;
            return;
        }

        finalStoreCheckAttempts++;
        warning("Kiem tra lan cuoi: con Spawner chua cat - tat sneak va mo lai Ender Chest lan " + finalStoreCheckAttempts + "/" + FINAL_STORE_CHECK_MAX_ATTEMPTS + ".");
        sendSneak(false);
        sneakStarted = false;
        waitTicks = 0;
        storeRetryCount = 0;
        state = State.FIND_ENDER_CHEST;
        scheduleFixedDelay(1);
    }

    private void handleRestoreState() {
        restoreState();
        state = keepRunning.get() ? State.ARMED : State.COMPLETED;
        if (state != State.COMPLETED) releaseProtectGuiOwner();
        scheduleDelay();
    }

    private void afterOneSpawnerCycle() {
        restoreState();
        if (!keepRunning.get()) {
            if (disconnectAfterClear.get()) {
                if (refreshTargetSpawner(true)) {
                    PlayerEntity threat = findThreat();
                    if (!ensureProtectGuiOwner(threat != null)) {
                        state = State.SCAN_PLAYERS;
                        return;
                    }
                    confirmedThreat = threat;
                    log("Kiem tra truoc khi out: van con Spawner " + targetSpawnerPos.toShortString() + " - tiep tuc dap va cat.");
                    state = shouldWalkToTarget() ? State.MOVE_TO_SPAWNER : State.SWAP_PICKAXE;
                    return;
                }
                disconnectAfterSpawnerClear();
                return;
            }
            state = State.COMPLETED;
            return;
        }

        if (refreshTargetSpawner(true)) {
            PlayerEntity threat = findThreat();
            if (!ensureProtectGuiOwner(threat != null)) {
                state = State.SCAN_PLAYERS;
                return;
            }
            confirmedThreat = threat;
            log("Keep-running: tim thay Spawner tiep theo " + targetSpawnerPos.toShortString() + " - tiep tuc dap va cat.");
            state = shouldWalkToTarget() ? State.MOVE_TO_SPAWNER : State.SWAP_PICKAXE;
        } else {
            releaseProtectGuiOwner();
            confirmedThreat = null;
            threatTicks = 0;
            if (disconnectAfterClear.get()) {
                disconnectAfterSpawnerClear();
                return;
            }
            state = State.SCAN_PLAYERS;
        }
    }

    private void disconnectAfterSpawnerClear() {
        releaseProtectGuiOwner();
        if (mc.player != null && isContainerOpen()) mc.player.closeHandledScreen();
        log("Da cat het Spawner quanh khu vuc - disconnect khoi server de dam bao an toan.");
        try {
            mc.disconnect(Text.literal("KamiSpawnerProtect: Spawner secured"));
        } catch (Throwable t) {
            warning("Khong disconnect duoc: " + t.getMessage());
        }
        state = State.COMPLETED;
        if (isActive()) toggle();
    }

    private void restoreState() {
        if (mc.player == null || mc.interactionManager == null) return;
        stopPathingToSpawner();
        stopTemporarySneak();
        restoreMovedTool();
        restoreMovedEnderChest();
        if (originalSlot >= 0 && originalSlot <= 8) InvUtils.swap(originalSlot, false);
    }

    private void stopTemporarySneak() {
        if (sneakStarted && !wasSneaking) sendSneak(false);
        sneakStarted = false;
    }

    private void finish(String message) {
        if (State.ERROR.equals(state)) {
            restoreState();
            if (isContainerOpen() && ownsGui()) closeScreen();
            warning(message);
        } else {
            log(message);
        }
        if (isActive()) toggle();
    }

    private boolean ensureProtectGuiOwner(boolean stopPeersFirst) {
        if (stopPeersFirst) stopOrderAndDropImmediately();

        if (forcedGuiCloseAttempts > 0 && forcedGuiCloseAttempts < FORCED_GUI_CLOSE_ATTEMPTS) {
            forceCloseGuiBeforeProtect();
            return false;
        }

        if (isContainerOpen()) {
            releaseInactivePeerGuiOwners();
            if (stopPeersFirst) {
                forceCloseGuiBeforeProtect();
                return false;
            }

            cleanupStaleGuiBeforeProtect();
            if (isContainerOpen()) {
                forceCloseGuiBeforeProtect();
                return false;
            }
        }
        if (ownsGui()) {
            protectGuiOwnerAcquired = true;
            forcedGuiCloseAttempts = 0;
            return true;
        }

        cleanupStaleGuiBeforeProtect();
        if (isContainerOpen()) {
            forceCloseGuiBeforeProtect();
            return false;
        }
        if (KamiOrderBot.tryAcquireGuiOwner(GUI_OWNER_PROTECT)) {
            protectGuiOwnerAcquired = true;
            forcedGuiCloseAttempts = 0;
            return true;
        }
        log("Dang cho GUI owner ranh (hien tai: " + KamiOrderBot.currentGuiOwner() + ").");
        return false;
    }

    private void cleanupStaleGuiBeforeProtect() {
        releaseInactivePeerGuiOwners();

        Module order = getModuleByName("kami-order-bot");
        Module drop = getModuleByName("kami-spawner-drop");
        boolean orderActive = order != null && order.isActive();
        boolean dropActive = drop != null && drop.isActive();

        if (!orderActive && !dropActive && isContainerOpen() && mc.player != null) {
            mc.player.closeHandledScreen();
            log("Da dong GUI cu bi treo truoc khi Protect thao tac.");
        }
    }

    private void releaseInactivePeerGuiOwners() {
        Module order = getModuleByName("kami-order-bot");
        Module drop = getModuleByName("kami-spawner-drop");
        boolean orderActive = order != null && order.isActive();
        boolean dropActive = drop != null && drop.isActive();

        if (!orderActive) KamiOrderBot.releaseGuiOwner(KamiOrderBot.GUI_OWNER_ORDER);
        if (!dropActive) KamiOrderBot.releaseGuiOwner(KamiOrderBot.GUI_OWNER_SPAWNER);
    }

    private void forceCloseGuiBeforeProtect() {
        if (mc.player == null) return;
        if (forcedGuiCloseAttempts < FORCED_GUI_CLOSE_ATTEMPTS) forcedGuiCloseAttempts++;
        mc.player.closeHandledScreen();
        log("Threat detected - ESC dong GUI truoc khi Protect thao tac ("
            + forcedGuiCloseAttempts + "/" + FORCED_GUI_CLOSE_ATTEMPTS + ").");
        scheduleFixedDelay(1);
    }

    private void releaseProtectGuiOwner() {
        if (!protectGuiOwnerAcquired && !ownsGui()) return;
        KamiOrderBot.releaseGuiOwner(GUI_OWNER_PROTECT);
        protectGuiOwnerAcquired = false;
        forcedGuiCloseAttempts = 0;
    }

    private boolean shouldWalkToTarget() {
        return autoWalkToSpawner.get() && targetSpawnerPos != null && !isTargetInInteractRange();
    }

    private boolean isTargetInInteractRange() {
        return mc.player != null
            && targetSpawnerPos != null
            && mc.player.squaredDistanceTo(Vec3d.ofCenter(targetSpawnerPos)) <= MAX_INTERACT_RANGE_SQ;
    }

    private void stopPathingToSpawner() {
        if (!pathingToSpawner) return;
        try {
            PathManagers.get().stop();
        } catch (Throwable ignored) {
        }
        pathingToSpawner = false;
        pathTicks = 0;
    }

    private boolean captureSpawnerTarget() {
        if (mc.player == null || mc.world == null) return false;
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            return refreshTargetSpawner(false);
        }
        if (!mc.world.getBlockState(bhr.getBlockPos()).isOf(Blocks.SPAWNER)) {
            return refreshTargetSpawner(false);
        }

        targetSpawnerPos = bhr.getBlockPos().toImmutable();
        targetSpawnerSide = bhr.getSide();
        targetHitPos = bhr.getPos();
        targetWorldKey = mc.world.getRegistryKey();
        return true;
    }

    private boolean refreshTargetSpawner(boolean preferNearestToPlayer) {
        if (mc.player == null || mc.world == null) return false;
        BlockPos anchor = targetSpawnerPos == null ? mc.player.getBlockPos() : targetSpawnerPos;
        int radius = targetSpawnerPos == null ? locateRadius.get() : protectRadius.get();

        BlockPos found = findSpawnerAround(anchor, radius, preferNearestToPlayer);
        if (found == null && !anchor.equals(mc.player.getBlockPos())) {
            found = findSpawnerAround(mc.player.getBlockPos(), locateRadius.get(), true);
        }
        if (found == null) return false;

        targetSpawnerPos = found;
        targetSpawnerSide = Direction.UP;
        targetHitPos = Vec3d.ofCenter(found);
        targetWorldKey = mc.world.getRegistryKey();
        return true;
    }

    private BlockPos findSpawnerAround(BlockPos anchor, int radius, boolean preferNearestToPlayer) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        double radiusSq = (radius + 0.5) * (radius + 0.5);

        HitResult hit = mc.crosshairTarget;
        if (hit instanceof BlockHitResult bhr
            && hit.getType() == HitResult.Type.BLOCK
            && mc.world.getBlockState(bhr.getBlockPos()).isOf(Blocks.SPAWNER)
            && bhr.getBlockPos().isWithinDistance(Vec3d.ofCenter(anchor), radius + 0.5)
            && (autoWalkToSpawner.get() || mc.player.squaredDistanceTo(Vec3d.ofCenter(bhr.getBlockPos())) <= MAX_INTERACT_RANGE_SQ)) {
            return bhr.getBlockPos().toImmutable();
        }

        // Giong ESP: Spawner co block entity, nen uu tien duyet block entities loaded thay vi chi quet 5 block.
        for (BlockEntity blockEntity : Utils.blockEntities()) {
            if (blockEntity == null) continue;
            BlockPos pos = blockEntity.getPos();
            if (pos == null || !mc.world.getBlockState(pos).isOf(Blocks.SPAWNER)) continue;
            if (pos.getSquaredDistance(anchor) > radiusSq) continue;
            if (!autoWalkToSpawner.get() && mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)) > MAX_INTERACT_RANGE_SQ) continue;

            double dist = preferNearestToPlayer
                ? mc.player.squaredDistanceTo(Vec3d.ofCenter(pos))
                : pos.getSquaredDistance(anchor);
            if (dist < bestDistance) {
                best = pos.toImmutable();
                bestDistance = dist;
            }
        }
        if (best != null) return best;

        int blockScanRadius = Math.min(radius, 16);
        double blockScanRadiusSq = (blockScanRadius + 0.5) * (blockScanRadius + 0.5);
        for (int x = -blockScanRadius; x <= blockScanRadius; x++) {
            for (int y = -blockScanRadius; y <= blockScanRadius; y++) {
                for (int z = -blockScanRadius; z <= blockScanRadius; z++) {
                    BlockPos pos = anchor.add(x, y, z);
                    if (pos.getSquaredDistance(anchor) > blockScanRadiusSq) continue;
                    if (!mc.world.getBlockState(pos).isOf(Blocks.SPAWNER)) continue;
                    if (!autoWalkToSpawner.get() && mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)) > MAX_INTERACT_RANGE_SQ) continue;

                    double dist = preferNearestToPlayer
                        ? mc.player.squaredDistanceTo(Vec3d.ofCenter(pos))
                        : pos.getSquaredDistance(anchor);
                    if (dist < bestDistance) {
                        best = pos.toImmutable();
                        bestDistance = dist;
                    }
                }
            }
        }

        return best;
    }

    private boolean isTargetStillValid(boolean notify) {
        if (targetSpawnerPos == null || targetWorldKey == null || mc.world == null || mc.player == null) {
            if (notify) error("Target Spawner khong hop le.");
            return false;
        }
        if (!mc.world.getRegistryKey().equals(targetWorldKey)) {
            if (notify) error("Da doi dimension/world - dung de tranh pha sai block.");
            return false;
        }
        if (!mc.world.getBlockState(targetSpawnerPos).isOf(Blocks.SPAWNER)) {
            return refreshTargetSpawner(false);
        }
        if (mc.player.squaredDistanceTo(Vec3d.ofCenter(targetSpawnerPos)) > MAX_INTERACT_RANGE_SQ) {
            if (autoWalkToSpawner.get()) return true;
            if (notify) error("Player qua xa Spawner da luu - khong tu di bo/pathfind.");
            return false;
        }
        return true;
    }

    private PlayerEntity findThreat() {
        if (mc.world == null || mc.player == null) return null;
        double rangeSq = detectionRange.get() * detectionRange.get();
        Vec3d center = targetSpawnerPos == null ? mc.player.getEntityPos() : Vec3d.ofCenter(targetSpawnerPos);

        PlayerEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || player == mc.player || player.isRemoved() || player.isDead()) continue;
            if (Friends.get() != null && Friends.get().isFriend(player)) continue;

            String name = player.getName().getString();
            if (isWhitelisted(name)) continue;

            double dist = player.squaredDistanceTo(center);
            if (dist <= rangeSq && dist < nearestDistance) {
                nearest = player;
                nearestDistance = dist;
            }
        }
        return nearest;
    }

    private void stopOrderAndDropImmediately() {
        stopModuleByName("kami-spawner-drop");
        stopModuleByName("kami-order-bot");
    }

    private void stopModuleByName(String name) {
        Module module = getModuleByName(name);
        if (module != null && module != this && module.isActive()) {
            module.toggle();
            log("Threat detected - da tat ngay " + module.title + ".");
        }
    }

    private Module getModuleByName(String name) {
        if (Modules.get() == null || name == null || name.isBlank()) return null;
        Module module = null;
        try {
            module = Modules.get().get(name);
        } catch (Throwable ignored) {
        }

        if (module == null) {
            String key = name.toLowerCase(Locale.ROOT).replace(' ', '-');
            for (Module candidate : Modules.get().getAll()) {
                if (candidate == null || candidate == this) continue;
                if (candidate.name != null && candidate.name.equalsIgnoreCase(key)) {
                    module = candidate;
                    break;
                }
                if (candidate.title != null && candidate.title.equalsIgnoreCase(name)) {
                    module = candidate;
                    break;
                }
            }
        }

        return module;
    }

    private boolean isWhitelisted(String name) {
        if (name == null) return false;
        for (String entry : whitelist.get()) {
            if (entry != null && entry.trim().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private boolean hasSpawnerInInventory() {
        return countSpawnersInPlayerInventory() > 0;
    }

    private boolean hasNewSpawnerPickedUp() {
        return countSpawnersInPlayerInventory() > spawnerCountBeforeBreak;
    }

    private int countSpawnersInPlayerInventory() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.SPAWNER)) count += stack.getCount();
        }
        ItemStack cursor = mc.player.currentScreenHandler == null ? ItemStack.EMPTY : mc.player.currentScreenHandler.getCursorStack();
        if (!cursor.isEmpty() && cursor.isOf(Items.SPAWNER)) count += cursor.getCount();
        return count;
    }

    private BlockPos findNearestEnderChest() {
        if (mc.player == null || mc.world == null) return null;
        BlockPos origin = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int r = (int) MAX_ENDER_CHEST_RANGE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    double dist = mc.player.squaredDistanceTo(Vec3d.ofCenter(pos));
                    if (dist > MAX_ENDER_CHEST_RANGE_SQ || dist > MAX_INTERACT_RANGE_SQ || dist >= bestDistance) continue;
                    if (!mc.world.getBlockState(pos).isOf(Blocks.ENDER_CHEST)) continue;
                    best = pos.toImmutable();
                    bestDistance = dist;
                }
            }
        }
        return best;
    }

    private boolean hasEnderChestAvailable() {
        if (mc.player == null) return false;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.ENDER_CHEST)) return true;
        }
        return false;
    }

    private boolean selectBestPickaxe() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || targetSpawnerPos == null) {
            return false;
        }

        BlockState state = mc.world.getBlockState(targetSpawnerPos);
        int bestIndex = -1;
        float bestSpeed = 0.0F;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isPickaxe(stack)) continue;

            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestIndex = i;
            }
        }

        if (bestIndex < 0) return false;

        if (bestIndex < 9) {
            InvUtils.swap(bestIndex, false);
            return true;
        }

        int selected = mc.player.getInventory().getSelectedSlot();
        movedToolSourceSlotId = inventoryIndexToPlayerSlotId(bestIndex);
        movedToolHotbarSlot = selected;
        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            movedToolSourceSlotId,
            selected,
            SlotActionType.SWAP,
            mc.player
        );
        return isPickaxe(mc.player.getInventory().getStack(selected));
    }

    private boolean selectCurrentOrHotbarPickaxe() {
        if (mc.player == null || mc.world == null || targetSpawnerPos == null) return false;

        int selected = mc.player.getInventory().getSelectedSlot();
        if (isPickaxe(mc.player.getInventory().getStack(selected))) return true;

        BlockState state = mc.world.getBlockState(targetSpawnerPos);
        int bestHotbar = -1;
        float bestSpeed = 0.0F;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isPickaxe(stack)) continue;

            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestHotbar = i;
            }
        }

        if (bestHotbar < 0) return false;
        InvUtils.swap(bestHotbar, false);
        return true;
    }

    private boolean isPickaxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.isOf(Items.NETHERITE_PICKAXE)
            || stack.isOf(Items.DIAMOND_PICKAXE)
            || stack.isOf(Items.IRON_PICKAXE)
            || stack.isOf(Items.GOLDEN_PICKAXE)
            || stack.isOf(Items.STONE_PICKAXE)
            || stack.isOf(Items.WOODEN_PICKAXE);
    }

    private boolean isSafeToBreakSpawner() {
        if (isBreakPickupSafeNow()) return true;
        beginSellCleanup(getBreakSafetyReason());
        return false;
    }

    private boolean isBreakPickupSafeNow() {
        return countEmptyInventorySlots() >= minEmptySlotsBeforeBreak.get()
            && countGroundItemsNearPlayer() <= maxGroundItemsBeforeBreak.get();
    }

    private String getBreakSafetyReason() {
        int emptySlots = countEmptyInventorySlots();
        int minEmpty = minEmptySlotsBeforeBreak.get();
        if (emptySlots < minEmpty) {
            return "Inventory chi con " + emptySlots + " slot trong, can toi thieu " + minEmpty + ".";
        }

        int nearbyItems = countGroundItemsNearPlayer();
        int maxGround = maxGroundItemsBeforeBreak.get();
        if (nearbyItems > maxGround) {
            return "Quanh chan co " + nearbyItems + " item entity, vuot gioi han " + maxGround + ".";
        }

        return "Chua dat dieu kien an toan de dap Spawner.";
    }

    private int countEmptyInventorySlots() {
        if (mc.player == null) return 0;
        int empty = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) empty++;
        }
        return empty;
    }

    private int countGroundItemsNearPlayer() {
        if (mc.player == null || mc.world == null) return 0;
        double radius = Math.max(0.5, groundItemCheckRadius.get());
        Box box = mc.player.getBoundingBox().expand(radius, 1.0, radius);
        return mc.world.getEntitiesByClass(ItemEntity.class, box, item -> item != null && item.isAlive()).size();
    }

    private int quickMoveSellableItems(int passes) {
        if (mc.player == null || mc.interactionManager == null || !ownsGui()) return 0;
        ScreenHandler menu = mc.player.currentScreenHandler;
        if (menu == null) return 0;

        int playerStart = Math.max(0, menu.slots.size() - 36);
        int moved = 0;
        int loops = Math.max(1, passes);
        for (int pass = 0; pass < loops; pass++) {
            for (int slotId = playerStart; slotId < menu.slots.size(); slotId++) {
                Slot slot = menu.slots.get(slotId);
                ItemStack stack = slot.getStack();
                if (!isSellableCleanupStack(stack)) continue;
                mc.interactionManager.clickSlot(menu.syncId, slotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                moved++;
            }
        }
        return moved;
    }

    private boolean isSellableCleanupStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (isProtectedSellStack(stack)) return false;

        if (sellCleanupMode.get() == SellCleanupMode.Safe_Whitelist) {
            List<Item> whitelistItems = sellWhitelistItems.get();
            if (whitelistItems == null || whitelistItems.isEmpty()) return false;
            for (Item item : whitelistItems) {
                if (item != null && stack.isOf(item)) return true;
            }
            return false;
        }

        return true;
    }

    private boolean isProtectedSellStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        if (stack.isOf(Items.SPAWNER) || stack.isOf(Items.ENDER_CHEST)) return true;
        if (stack.isOf(Items.BUNDLE) || stack.isOf(Items.ENCHANTED_BOOK)) return true;
        if (isPickaxe(stack)) return true;
        if (stack.isDamageable()) return true;
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) return true;
        return false;
    }

    private BlockPos findPlacePosNearPlayer() {
        if (mc.player == null || mc.world == null) return null;
        BlockPos base = mc.player.getBlockPos();
        int radius = placeSearchRadius.get();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        double radiusSq = (radius + 0.5) * (radius + 0.5);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = base.add(x, y, z);
                    double playerDist = mc.player.squaredDistanceTo(Vec3d.ofCenter(pos));
                    if (pos.getSquaredDistance(base) > radiusSq) continue;
                    if (playerDist > MAX_INTERACT_RANGE_SQ) continue;
                    if (!BlockUtils.canPlace(pos, true)) continue;

                    if (playerDist < bestDistance) {
                        best = pos.toImmutable();
                        bestDistance = playerDist;
                    }
                }
            }
        }
        return best;
    }

    private int moveEnderChestToHotbar() {
        if (mc.player == null || mc.interactionManager == null) return -1;
        int sourceInvIndex = -1;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.ENDER_CHEST)) {
                sourceInvIndex = i;
                break;
            }
        }
        if (sourceInvIndex < 0) return -1;

        movedEnderChestSourceSlotId = inventoryIndexToPlayerSlotId(sourceInvIndex);
        int hotbarSlot = findEnderChestHotbarTarget();
        movedEnderChestHotbarSlot = hotbarSlot;
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            movedEnderChestSourceSlotId,
            hotbarSlot,
            SlotActionType.SWAP,
            mc.player
        );
        return hotbarSlot;
    }

    private int findEnderChestHotbarTarget() {
        int selected = mc.player.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        ItemStack selectedStack = mc.player.getInventory().getStack(selected);
        if (!selectedStack.isOf(Items.SPAWNER) && !selectedStack.isOf(Items.ENDER_CHEST)) return selected;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isOf(Items.SPAWNER) && !stack.isOf(Items.ENDER_CHEST) && !isPickaxe(stack)) return i;
        }
        return selected;
    }

    private void restoreMovedEnderChest() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (movedEnderChestSourceSlotId < 0) return;
        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            movedEnderChestSourceSlotId,
            movedEnderChestHotbarSlot,
            SlotActionType.SWAP,
            mc.player
        );
        movedEnderChestSourceSlotId = -1;
        movedEnderChestHotbarSlot = -1;
    }

    private void restoreMovedTool() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (movedToolSourceSlotId < 0) return;
        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            movedToolSourceSlotId,
            movedToolHotbarSlot,
            SlotActionType.SWAP,
            mc.player
        );
        movedToolSourceSlotId = -1;
        movedToolHotbarSlot = -1;
    }

    private int inventoryIndexToPlayerSlotId(int inventoryIndex) {
        return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
    }

    private boolean hasSpaceForSpawner() {
        if (mc.player == null) return false;
        ScreenHandler menu = mc.player.currentScreenHandler;
        if (menu == null) return false;

        int containerSlots = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.slots.get(i).getStack();
            if (stack.isEmpty()) return true;
            if (stack.isOf(Items.SPAWNER) && stack.getCount() < stack.getMaxCount()) return true;
        }
        return false;
    }

    private int findSpawnerSlotInOpenHandler() {
        if (mc.player == null) return -1;
        ScreenHandler menu = mc.player.currentScreenHandler;
        if (menu == null) return -1;

        int playerStart = Math.max(0, menu.slots.size() - 36);
        for (int i = playerStart; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && stack.isOf(Items.SPAWNER)) return i;
        }
        return -1;
    }

    private void sendSneak(boolean sneak) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        if (mc.options != null && mc.options.sneakKey != null) mc.options.sneakKey.setPressed(sneak);
        player.setSneaking(sneak);
    }

    private void clickSlot(int slotId, int button, SlotActionType type) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!isActive() || !ownsGui()) return;
        ScreenHandler menu = mc.player.currentScreenHandler;
        if (menu == null) return;
        if (slotId < 0 || slotId >= menu.slots.size()) return;
        mc.interactionManager.clickSlot(menu.syncId, slotId, button, type, mc.player);
    }

    private boolean ownsGui() {
        return KamiOrderBot.isGuiOwner(GUI_OWNER_PROTECT);
    }

    private boolean isContainerOpen() {
        if (mc.player == null) return false;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return true;
        return mc.currentScreen instanceof HandledScreen;
    }

    private void closeScreen() {
        if (mc.player != null && ownsGui()) mc.player.closeHandledScreen();
    }

    private void clearTarget() {
        targetSpawnerPos = null;
        targetSpawnerSide = null;
        targetHitPos = null;
        targetWorldKey = null;
    }

    private void scheduleDelay() {
        int base = Math.max(1, delay.get());
        int jitter = ThreadLocalRandom.current().nextInt(0, Math.max(2, base / 3 + 1));
        cooldown = base + jitter;
    }

    private void scheduleFixedDelay(int ticks) {
        cooldown = Math.max(0, ticks);
    }

    private void log(String msg) {
        if (chatFeedback.get()) info(msg);
    }

    @Override
    public String getInfoString() {
        return switch (state) {
            case ARMED, SCAN_PLAYERS -> targetSpawnerPos == null ? "armed" : targetSpawnerPos.toShortString();
            case THREAT_CONFIRM -> confirmedThreat == null ? "confirm" : confirmedThreat.getName().getString();
            case MOVE_TO_SPAWNER -> "walk";
            case OPEN_SELL_GUI, SELL_ITEMS, SELL_ITEMS_SECOND_PASS, VERIFY_SELL_SPACE -> "sell cleanup";
            case BREAK_SPAWNER -> "breaking";
            case WAIT_PICKUP -> "pickup";
            case FIND_ENDER_CHEST -> "find echest";
            case OPEN_ENDER_CHEST -> "open echest";
            case STORE_SPAWNER, VERIFY_STORE, FINAL_STORE_CHECK -> "store";
            case COMPLETED -> "done";
            case ERROR -> "error";
            default -> state.name().toLowerCase(Locale.ROOT);
        };
    }

    private enum ToolMode {
        Current_Hotbar,
        Best_Hotbar_Tool
    }

    private enum SellCleanupMode {
        Safe_Whitelist,
        Legacy_Broad
    }

    private enum State {
        IDLE,
        ARMED,
        SCAN_PLAYERS,
        THREAT_CONFIRM,
        MOVE_TO_SPAWNER,
        SWAP_PICKAXE,
        START_SNEAK,
        ROTATE,
        ENSURE_INVENTORY_SPACE,
        OPEN_SELL_GUI,
        SELL_ITEMS,
        SELL_ITEMS_SECOND_PASS,
        VERIFY_SELL_SPACE,
        BREAK_SPAWNER,
        WAIT_PICKUP,
        FIND_ENDER_CHEST,
        PLACE_ENDER_CHEST,
        OPEN_ENDER_CHEST,
        STORE_SPAWNER,
        VERIFY_STORE,
        FINAL_STORE_CHECK,
        RESTORE_STATE,
        COMPLETED,
        ERROR
    }
}
