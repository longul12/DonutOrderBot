package com.kami.order.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
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
        .description("Ban kinh tim va pha tat ca Spawner gan target goc.")
        .defaultValue(5)
        .range(1, 10)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Boolean> keepRunning = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-running")
        .description("Cat xong mot Spawner thi tiep tuc tim Spawner ke tiep thay vi tu tat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> dropLargestWhenFull = sgGeneral.add(new BoolSetting.Builder()
        .name("drop-largest-when-full")
        .description("Inventory day thi vut stack nhieu nhat, bo qua Spawner va Ender Chest.")
        .defaultValue(true)
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
        .description("Ban kinh phat hien nguoi la quanh Spawner da luu.")
        .defaultValue(8.0)
        .min(1.0)
        .sliderRange(1.0, 32.0)
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
    private int spawnerCountBeforeBreak;

    public KamiSpawnerProtect() {
        super(Categories.Misc, "kami-spawner-protect",
            "Bao ve Spawner da luu: gap nguoi la thi pha va cat vao Ender Chest.");
    }

    public static boolean shouldPreventMouseLock() {
        return false;
    }

    @Override
    public void onActivate() {
        if (!KamiOrderBot.tryAcquireGuiOwner(GUI_OWNER_PROTECT)) {
            error("GUI dang duoc dieu khien boi " + KamiOrderBot.currentGuiOwner() + " - khong bat SpawnerProtect.");
            toggle();
            return;
        }

        resetRuntime();
        if (!captureSpawnerTarget()) {
            error("Hay nhin dung vao block Spawner truoc khi bat KamiSpawnerProtect.");
            KamiOrderBot.releaseGuiOwner(GUI_OWNER_PROTECT);
            toggle();
            return;
        }

        originalSlot = mc.player.getInventory().getSelectedSlot();
        wasSneaking = mc.player.isSneaking();
        state = State.ARMED;
        log("Da luu Spawner " + targetSpawnerPos.toShortString() + " - dang bao ve.");
    }

    @Override
    public void onDeactivate() {
        restoreState();
        if (mc.player != null && isContainerOpen() && ownsGui()) mc.player.closeHandledScreen();
        clearTarget();
        resetRuntime();
        KamiOrderBot.releaseGuiOwner(GUI_OWNER_PROTECT);
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
        spawnerCountBeforeBreak = 0;
        sneakStarted = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            state = State.ERROR;
            return;
        }
        if (!ownsGui()) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        switch (state) {
            case IDLE -> state = State.ARMED;
            case ARMED -> handleArmed();
            case SCAN_PLAYERS -> handleScanPlayers();
            case THREAT_CONFIRM -> handleThreatConfirm();
            case SWAP_PICKAXE -> handleSwapPickaxe();
            case START_SNEAK -> handleStartSneak();
            case ROTATE -> handleRotate();
            case ENSURE_INVENTORY_SPACE -> handleEnsureInventorySpace();
            case BREAK_SPAWNER -> handleBreakSpawner();
            case WAIT_PICKUP -> handleWaitPickup();
            case FIND_ENDER_CHEST -> handleFindEnderChest();
            case PLACE_ENDER_CHEST -> handlePlaceEnderChest();
            case OPEN_ENDER_CHEST -> handleOpenEnderChest();
            case STORE_SPAWNER -> handleStoreSpawner();
            case VERIFY_STORE -> handleVerifyStore();
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
        if (!isTargetStillValid(true)) {
            state = State.ERROR;
            return;
        }

        scanTicks++;
        if (scanTicks < scanInterval.get()) return;
        scanTicks = 0;

        PlayerEntity threat = findThreat();
        if (threat == null) return;

        stopOrderAndDropImmediately();
        confirmedThreat = threat;
        threatTicks = 0;
        state = State.THREAT_CONFIRM;
        log("Phat hien nguoi la: " + threat.getName().getString() + " - dang xac nhan.");
    }

    private void handleThreatConfirm() {
        if (!isTargetStillValid(true)) {
            state = State.ERROR;
            return;
        }

        PlayerEntity threat = findThreat();
        if (threat == null) {
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
                warning("Co threat nhung khong con Spawner nao trong " + protectRadius.get() + " block.");
                state = State.SCAN_PLAYERS;
                return;
            }
            log("Xac nhan threat: " + threat.getName().getString() + " - cat Spawner " + targetSpawnerPos.toShortString() + ".");
            state = State.SWAP_PICKAXE;
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

        if (isInventoryFull() && dropLargestWhenFull.get()) {
            if (dropLargestStack()) {
                log("Inventory day - da vut stack nhieu nhat de cho nhat Spawner.");
                scheduleDelay();
                return;
            }
            warning("Inventory day nhung khong co stack phu hop de vut.");
        }

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

        BlockUtils.breakBlock(targetSpawnerPos, true);
    }

    private void handleWaitPickup() {
        waitTicks++;
        if (isInventoryFull() && !hasNewSpawnerPickedUp() && dropLargestWhenFull.get()) {
            dropLargestStack();
        }

        if (hasNewSpawnerPickedUp()) {
            log("Spawner da vao inventory - tim Ender Chest gan nhat.");
            state = State.FIND_ENDER_CHEST;
            scheduleDelay();
            return;
        }

        if (waitTicks > waitPickupTimeout.get()) {
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
            if (!moveEnderChestToSelectedHotbar()) {
                error("Khong the dua Ender Chest len hotbar - giu Spawner trong inventory.");
                state = State.ERROR;
                return;
            }
            hotbarChest = new FindItemResult(mc.player.getInventory().getSelectedSlot(), 1);
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
        if (now < spawnerCountBeforeStore || !hasSpawnerInInventory()) {
            closeScreen();
            log("Xac minh cat Spawner thanh cong - dong GUI.");
            afterOneSpawnerCycle();
            scheduleDelay();
            return;
        }

        error("Spawner van con trong inventory sau QUICK_MOVE - co the Ender Chest day.");
        state = State.ERROR;
    }

    private void handleRestoreState() {
        restoreState();
        state = keepRunning.get() ? State.ARMED : State.COMPLETED;
        scheduleDelay();
    }

    private void afterOneSpawnerCycle() {
        restoreState();
        if (!keepRunning.get()) {
            state = State.COMPLETED;
            return;
        }

        if (refreshTargetSpawner(false) && findThreat() != null) {
            state = State.SWAP_PICKAXE;
        } else {
            confirmedThreat = null;
            threatTicks = 0;
            state = State.SCAN_PLAYERS;
        }
    }

    private void restoreState() {
        if (mc.player == null || mc.interactionManager == null) return;
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
        int radius = protectRadius.get();

        BlockPos found = findSpawnerAround(anchor, radius, preferNearestToPlayer);
        if (found == null && !anchor.equals(mc.player.getBlockPos())) {
            found = findSpawnerAround(mc.player.getBlockPos(), radius, true);
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

        HitResult hit = mc.crosshairTarget;
        if (hit instanceof BlockHitResult bhr
            && hit.getType() == HitResult.Type.BLOCK
            && mc.world.getBlockState(bhr.getBlockPos()).isOf(Blocks.SPAWNER)
            && bhr.getBlockPos().isWithinDistance(Vec3d.ofCenter(anchor), radius + 0.5)
            && mc.player.squaredDistanceTo(Vec3d.ofCenter(bhr.getBlockPos())) <= MAX_INTERACT_RANGE_SQ) {
            return bhr.getBlockPos().toImmutable();
        }

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = anchor.add(x, y, z);
                    if (!pos.isWithinDistance(Vec3d.ofCenter(anchor), radius + 0.5)) continue;
                    if (!mc.world.getBlockState(pos).isOf(Blocks.SPAWNER)) continue;
                    if (mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)) > MAX_INTERACT_RANGE_SQ) continue;

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
            if (notify) error("Player qua xa Spawner da luu - khong tu di bo/pathfind.");
            return false;
        }
        return true;
    }

    private PlayerEntity findThreat() {
        if (mc.world == null || mc.player == null || targetSpawnerPos == null) return null;
        double rangeSq = detectionRange.get() * detectionRange.get();
        Vec3d center = Vec3d.ofCenter(targetSpawnerPos);

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
        stopModuleByName("kami-order-bot");
        stopModuleByName("kami-spawner-drop");
    }

    private void stopModuleByName(String name) {
        if (Modules.get() == null || name == null || name.isBlank()) return;
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

        if (module != null && module != this && module.isActive()) {
            module.toggle();
            log("Threat detected - da tat ngay " + module.title + ".");
        }
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

    private boolean isInventoryFull() {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private boolean dropLargestStack() {
        if (mc.player == null || mc.interactionManager == null) return false;
        int bestIndex = -1;
        int bestCount = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) return false;
            if (stack.isOf(Items.SPAWNER) || stack.isOf(Items.ENDER_CHEST)) continue;
            if (stack.getCount() > bestCount) {
                bestIndex = i;
                bestCount = stack.getCount();
            }
        }

        if (bestIndex < 0) return false;
        int slotId = inventoryIndexToPlayerSlotId(bestIndex);
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slotId, 1, SlotActionType.THROW, mc.player);
        return true;
    }

    private BlockPos findPlacePosNearPlayer() {
        if (mc.player == null || mc.world == null) return null;
        BlockPos base = mc.player.getBlockPos();
        BlockPos[] candidates = {
            base.offset(mc.player.getHorizontalFacing()),
            base.offset(mc.player.getHorizontalFacing().rotateYClockwise()),
            base.offset(mc.player.getHorizontalFacing().rotateYCounterclockwise()),
            base.offset(mc.player.getHorizontalFacing().getOpposite()),
            base.up()
        };

        for (BlockPos pos : candidates) {
            if (BlockUtils.canPlace(pos, true)) return pos.toImmutable();
        }
        return null;
    }

    private boolean moveEnderChestToSelectedHotbar() {
        if (mc.player == null || mc.interactionManager == null) return false;
        int sourceInvIndex = -1;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.ENDER_CHEST)) {
                sourceInvIndex = i;
                break;
            }
        }
        if (sourceInvIndex < 0) return false;

        movedEnderChestSourceSlotId = inventoryIndexToPlayerSlotId(sourceInvIndex);
        int selected = mc.player.getInventory().getSelectedSlot();
        movedEnderChestHotbarSlot = selected;
        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            movedEnderChestSourceSlotId,
            selected,
            SlotActionType.SWAP,
            mc.player
        );
        return mc.player.getInventory().getStack(selected).isOf(Items.ENDER_CHEST);
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

    private void log(String msg) {
        if (chatFeedback.get()) info(msg);
    }

    @Override
    public String getInfoString() {
        return switch (state) {
            case ARMED, SCAN_PLAYERS -> targetSpawnerPos == null ? "armed" : targetSpawnerPos.toShortString();
            case THREAT_CONFIRM -> confirmedThreat == null ? "confirm" : confirmedThreat.getName().getString();
            case BREAK_SPAWNER -> "breaking";
            case WAIT_PICKUP -> "pickup";
            case FIND_ENDER_CHEST -> "find echest";
            case OPEN_ENDER_CHEST -> "open echest";
            case STORE_SPAWNER, VERIFY_STORE -> "store";
            case COMPLETED -> "done";
            case ERROR -> "error";
            default -> state.name().toLowerCase(Locale.ROOT);
        };
    }

    private enum ToolMode {
        Current_Hotbar,
        Best_Hotbar_Tool
    }

    private enum State {
        IDLE,
        ARMED,
        SCAN_PLAYERS,
        THREAT_CONFIRM,
        SWAP_PICKAXE,
        START_SNEAK,
        ROTATE,
        ENSURE_INVENTORY_SPACE,
        BREAK_SPAWNER,
        WAIT_PICKUP,
        FIND_ENDER_CHEST,
        PLACE_ENDER_CHEST,
        OPEN_ENDER_CHEST,
        STORE_SPAWNER,
        VERIFY_STORE,
        RESTORE_STATE,
        COMPLETED,
        ERROR
    }
}
