package com.kami.order.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KamiOrderBot — Auto Order (SMP).
 * <p>
 * Target: Item List (Select + Search) <b>hoặc</b> nhập tay String.<br>
 * /order: list → iron_ingot thành "iron ingot"; manual → dùng đúng chuỗi gõ.<br>
 * Score = PricePerItem × log(Remaining + 1). Confirm H2C8.
 */
public class KamiOrderBot extends Module {
    public static final String GUI_OWNER_NONE = "NONE";
    public static final String GUI_OWNER_ORDER = "ORDER_BOT";
    public static final String GUI_OWNER_SPAWNER = "SPAWNER_DROP";

    private static String guiOwner = GUI_OWNER_NONE;
    private static volatile boolean keepMouseFreeWhileRunning = false;

    /** Lock GUI dùng chung giữa OrderBot và SpawnerDrop. Không phụ thuộc focus/cursor thật. */
    public static synchronized boolean tryAcquireGuiOwner(String owner) {
        if (owner == null || owner.isBlank()) return false;
        if (GUI_OWNER_NONE.equals(guiOwner) || guiOwner.equals(owner)) {
            guiOwner = owner;
            return true;
        }
        return false;
    }

    public static synchronized boolean isGuiOwner(String owner) {
        return owner != null && guiOwner.equals(owner);
    }

    public static synchronized void releaseGuiOwner(String owner) {
        if (owner != null && guiOwner.equals(owner)) guiOwner = GUI_OWNER_NONE;
    }

    public static synchronized String currentGuiOwner() {
        return guiOwner;
    }

    public static boolean shouldPreventMouseLock() {
        if (keepMouseFreeWhileRunning) return true;
        try {
            Class<?> cl = Class.forName("com.kami.order.modules.KamiSpawnerDrop");
            Object value = cl.getMethod("shouldPreventMouseLock").invoke(null);
            return value instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Cách chọn tên item gửi /order. */
    public enum TargetMode {
        /** Bảng cuộn giống Item Highlight */
        Item_List,
        /** Gõ tay string (vd: iron ingot, ender pearl) */
        Manual_String
    }

    /**
     * Cách chọn giá order trong list.
     */
    public enum PriceSelectMode {
        /** Giá cao nhất (+ remaining tốt khi hòa) */
        Highest,
        /** Giá thấp nhất (+ remaining tốt khi hòa) */
        Lowest,
        /** Tự động: cao hơn TB, không lấy max, gần (avg+max)/2 */
        Auto_Balanced
    }

    public enum OrderTargetMode {
        Single_Player,
        Player_List
    }

    public enum PlayerListSource {
        Setting_List,
        Txt_File
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Order Filter");
    private final SettingGroup sgConfirm = settings.createGroup("Confirm Slot");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    private final Setting<TargetMode> targetMode = sgGeneral.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("Item List (chon trong bang) hoac Manual String (go tay).")
        .defaultValue(TargetMode.Item_List)
        .build()
    );

    private final Setting<OrderTargetMode> orderTargetMode = sgGeneral.add(new EnumSetting.Builder<OrderTargetMode>()
        .name("order-target-mode")
        .description("Single_Player dung order-player-name. Player_List order tung name trong danh sach.")
        .defaultValue(OrderTargetMode.Single_Player)
        .build()
    );

    /**
     * Tên người chơi gửi /order &lt;name&gt;.
     * Để trống = dùng tên item (hành vi cũ).
     */
    private final Setting<String> orderPlayerName = sgGeneral.add(new StringSetting.Builder()
        .name("order-player-name")
        .description("Ten nguoi choi: /order <ten>. De trong thi /order theo ten item.")
        .defaultValue("")
        .visible(() -> orderTargetMode.get() == OrderTargetMode.Single_Player)
        .build()
    );

    private final Setting<PlayerListSource> playerListSource = sgGeneral.add(new EnumSetting.Builder<PlayerListSource>()
        .name("player-list-source")
        .description("Nguon danh sach player: Setting_List hoac Txt_File.")
        .defaultValue(PlayerListSource.Setting_List)
        .visible(() -> orderTargetMode.get() == OrderTargetMode.Player_List)
        .build()
    );

    private final Setting<List<String>> orderPlayerList = sgGeneral.add(new StringListSetting.Builder()
        .name("order-player-list")
        .description("Danh sach player de /order, moi dong la mot ten.")
        .defaultValue(List.of())
        .visible(() -> orderTargetMode.get() == OrderTargetMode.Player_List && playerListSource.get() == PlayerListSource.Setting_List)
        .build()
    );

    private final Setting<String> playerListFile = sgGeneral.add(new StringSetting.Builder()
        .name("player-list-file")
        .description("File txt danh sach player, moi dong la mot name. Duong dan tu thu muc .minecraft neu khong phai absolute.")
        .defaultValue("config/kami-order-player-list.txt")
        .visible(() -> orderTargetMode.get() == OrderTargetMode.Player_List && playerListSource.get() == PlayerListSource.Txt_File)
        .build()
    );

    private final Setting<Integer> ordersPerPlayer = sgGeneral.add(new IntSetting.Builder()
        .name("orders-per-player")
        .description("So order hoan tat cho moi player trong danh sach.")
        .defaultValue(1)
        .range(1, 999)
        .sliderRange(1, 20)
        .visible(() -> orderTargetMode.get() == OrderTargetMode.Player_List)
        .build()
    );

    /** Giống Item Highlight: bảng cuộn, Select, Search — item cần bán (lọc GUI). */
    private final Setting<List<Item>> targetItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("target-item")
        .description("Item can ban: loc order trong GUI (so khop ten, bo mau).")
        .defaultValue(List.of(Items.OBSIDIAN))
        .visible(() -> targetMode.get() == TargetMode.Item_List)
        .build()
    );

    /** Nhập tay tên item để lọc GUI (vd: Iron Ingot). */
    private final Setting<String> manualOrderName = sgGeneral.add(new StringSetting.Builder()
        .name("manual-order-name")
        .description("Ten item loc trong GUI (vd: Iron Ingot). Khong phan biet hoa thuong.")
        .defaultValue("Iron Ingot")
        .visible(() -> targetMode.get() == TargetMode.Manual_String)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay co ban (tick) + random nhe giua cac thao tac.")
        .defaultValue(8)
        .range(1, 60)
        .sliderRange(1, 40)
        .build()
    );

    /**
     * Hết item order trong túi → tự bật Kami Spawner Drop (addon riêng).
     */
    private final Setting<Boolean> autoSpawnerDrop = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-spawner-drop")
        .description("Khi het item target trong nguoi -> tu kich hoat Kami Spawner Drop.")
        .defaultValue(false)
        .build()
    );

    /**
     * Số vòng: (Order đến hết item → Drop spawner) × N.
     * Sau drop, Order tự bật lại nếu còn vòng. 0 = lặp vô hạn.
     */
    private final Setting<Integer> loopCount = sgGeneral.add(new IntSetting.Builder()
        .name("loop-count")
        .description("So vong Order -> Drop. 1 = order roi drop xong dung. 0 = lap mai. Can auto-spawner-drop.")
        .defaultValue(1)
        .range(0, 9999)
        .sliderRange(0, 200)
        .visible(autoSpawnerDrop::get)
        .build()
    );

    private final Setting<String> spawnerDropModule = sgGeneral.add(new StringSetting.Builder()
        .name("spawner-drop-module")
        .description("Ten module Spawner Drop (kebab-case). Mac dinh: kami-spawner-drop.")
        .defaultValue("kami-spawner-drop")
        .visible(autoSpawnerDrop::get)
        .build()
    );

    /**
     * Phối hợp với KamiSpawnerDrop (cross-addon).
     * Spawner đọc flag này để biết có bật lại Order không.
     */
    public static volatile boolean resumeOrderAfterDrop = false;
    /** Lần bật Order tiếp theo là resume sau drop (không reset đếm vòng). */
    public static volatile boolean nextActivateIsResume = false;
    public static volatile boolean finalOrderBeforeRespawnRequested = false;
    public static volatile boolean finalOrderBeforeRespawnComplete = false;
    /** Số phase order đã hoàn thành trong session hiện tại. */
    private static int cyclesCompleted = 0;

    /**
     * Thanh trượt giá thấp nhất — chỉ nhận order ≥ mức này.
     */
    private final Setting<Double> priceMin = sgFilter.add(new DoubleSetting.Builder()
        .name("price-min")
        .description("Gia/item thap nhat chap nhan. Order re hon -> bo.")
        .defaultValue(0.0)
        .min(0.0)
        .sliderRange(0.0, 50000.0)
        .build()
    );

    /**
     * Thanh trượt giá cao nhất — chỉ nhận order ≤ mức này.
     * 0 = không giới hạn trần.
     */
    private final Setting<Double> priceMax = sgFilter.add(new DoubleSetting.Builder()
        .name("price-max")
        .description("Gia/item cao nhat chap nhan. 0 = khong gioi han. Order dat hon -> bo.")
        .defaultValue(0.0)
        .min(0.0)
        .sliderRange(0.0, 50000.0)
        .build()
    );

    private final Setting<PriceSelectMode> priceSelectMode = sgFilter.add(new EnumSetting.Builder<PriceSelectMode>()
        .name("price-select-mode")
        .description("Trong khoang [price-min, price-max]: Highest / Lowest / Auto_Balanced.")
        .defaultValue(PriceSelectMode.Auto_Balanced)
        .build()
    );

    private final Setting<Integer> minRemaining = sgFilter.add(new IntSetting.Builder()
        .name("min-remaining")
        .description("Bo order con thieu it hon gia tri nay.")
        .defaultValue(64)
        .range(1, 100000)
        .sliderRange(1, 5000)
        .build()
    );

    private final Setting<Integer> guiWaitTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("gui-wait-timeout")
        .description("Timeout cho GUI (tick).")
        .defaultValue(100)
        .range(20, 400)
        .sliderRange(20, 200)
        .build()
    );

    private final Setting<Integer> resumeItemWaitTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("resume-item-wait-timeout")
        .description("Sau SpawnerDrop, cho toi da tung nay tick de item vao inventory roi moi gui /order.")
        .defaultValue(160)
        .range(20, 600)
        .sliderRange(20, 300)
        .visible(autoSpawnerDrop::get)
        .build()
    );

    private final Setting<Boolean> scanNextPages = sgGeneral.add(new BoolSetting.Builder()
        .name("scan-next-pages")
        .description("Neu trang Order hien tai khong co target item thi bam next page de quet tiep.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxOrderPages = sgGeneral.add(new IntSetting.Builder()
        .name("max-order-pages")
        .description("So trang Order toi da se quet truoc khi dung tim target item.")
        .defaultValue(5)
        .range(1, 20)
        .sliderRange(1, 10)
        .visible(scanNextPages::get)
        .build()
    );

    private final Setting<Integer> nextPageSlot = sgGeneral.add(new IntSetting.Builder()
        .name("next-page-slot")
        .description("Slot nut sang trang tiep theo trong GUI Order. Mac dinh 53.")
        .defaultValue(53)
        .range(0, 89)
        .sliderRange(0, 60)
        .visible(scanNextPages::get)
        .build()
    );

    private final Setting<Integer> orderSearchRestarts = sgGeneral.add(new IntSetting.Builder()
        .name("order-search-restarts")
        .description("Khong tim duoc order phu hop thi dong GUI va gui /order lai toi da tung nay lan.")
        .defaultValue(3)
        .range(0, 10)
        .sliderRange(0, 5)
        .build()
    );

    /**
     * GUI "đơn hàng - xác nhận giao hàng":
     * Hàng 2, Cột 8 (1-based) → slot = (2-1)*9 + (8-1) = <b>16</b>.
     * Ô xanh lá "xác nhận" trên ảnh.
     */
    /**
     * GUI confirm (ảnh): hàng 2 ô xanh "xác nhận" gần mép phải.
     * 16 (H2C8) sai → mặc định <b>17</b> (H2C9 = cuối hàng 2).
     * Vẫn ưu tiên tooltip / lime pane trước slot cố định.
     */
    private final Setting<Integer> confirmSlot = sgConfirm.add(new IntSetting.Builder()
        .name("confirm-slot")
        .description("Slot nut Xac nhan (0-based). Mac dinh 17 = hang 2 cot 9.")
        .defaultValue(17)
        .range(0, 89)
        .sliderRange(0, 30)
        .build()
    );

    private final Setting<Boolean> forceConfirmSlot = sgConfirm.add(new BoolSetting.Builder()
        .name("force-confirm-slot")
        .description("true = uu tien click confirm-slot; van fallback tooltip neu trong/sai.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> postConfirmGuiWait = sgConfirm.add(new IntSetting.Builder()
        .name("post-confirm-gui-wait")
        .description("Sau confirm, cho toi da tung nay tick de server mo lai GUI order roi ESC thoat.")
        .defaultValue(60)
        .range(0, 200)
        .sliderRange(0, 120)
        .build()
    );

    private final Setting<Boolean> fastPostConfirmEsc = sgConfirm.add(new BoolSetting.Builder()
        .name("fast-post-confirm-esc")
        .description("ESC GUI order reopen va tiep tuc ngay tick sau, giam khung sau confirm.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgDebug.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("In thong bao chi tiet ra chat.")
        .defaultValue(true)
        .build()
    );

    // ── State machine ──
    private enum State {
        SEND_ORDER,
        WAIT_ORDER_LIST,
        SCAN_AND_SELECT,
        WAIT_FILL_GUI,
        /** Shift+DoubleClick lần 1 */
        DEPOSIT_SHIFT_1,
        /** Shift+DoubleClick lần 2 */
        DEPOSIT_SHIFT_2,
        /** ESC 1 lần sau 2 lần dump */
        ESC_AFTER_DEPOSIT,
        /** Chờ GUI confirm / nút xanh */
        WAIT_CONFIRM_GUI,
        CONFIRM,
        /** ESC sau khi bấm Xác nhận */
        ESC_AFTER_CONFIRM,
        AFTER_CONFIRM,
        CLOSE_BEFORE_SPAWNER_DROP,
        DONE
    }

    private State state = State.SEND_ORDER;
    private int actionCooldown = 0;
    private int waitTicks = 0;
    private int fillsLeft = 0;
    private int orderRetry = 0;
    private boolean confirmClickedOk = false;
    private int scanEmptyRetries = 0;
    private boolean resumeWaitingForItems = false;
    private int resumeItemWaitTicks = 0;
    private boolean resumeActivation = false;
    private int pagesScanned = 0;
    private int searchRestartsDone = 0;
    private boolean pendingSpawnerDrop = false;
    private int pendingSpawnerDropWaitTicks = 0;
    private boolean postConfirmReopenClosed = false;
    private int currentOrderPlayerIndex = 0;
    private int ordersDoneForCurrentPlayer = 0;
    private List<String> runtimeOrderPlayers = List.of();

    /** 27 stack × 64 = 1 shulker đầy */
    private static final int ITEMS_PER_FILL = 1728;

    private static final Pattern PRICE_PATTERN = Pattern.compile(
        "(?i)(?:gi[aáàảãạ]|price|cost|\\$/?(?:item|cái|cai)?)\\s*[:：]?\\s*([\\d.,]+)\\s*\\$?|" +
            "([\\d.,]+)\\s*\\$"
    );
    private static final Pattern REMAINING_PATTERN = Pattern.compile(
        "(?i)(?:c[oòóỏõọ]n\\s*(?:l[aạ]i|thi[eế]u)|remaining|left|need(?:ed)?)\\s*[:：]?\\s*([\\d.,]+)"
    );
    private static final Pattern DELIVERED_PATTERN = Pattern.compile(
        "(?i)(?:[dđ][aã]\\s*giao|[dđ][aã]\\s*fill|delivered|filled|progress)\\s*[:：]?\\s*([\\d.,]+)"
    );
    private static final Pattern TOTAL_PATTERN = Pattern.compile(
        "(?i)(?:t[oổ]ng|total|amount|quantity|s[oố]\\s*l[uượ]ng)\\s*[:：]?\\s*([\\d.,]+)"
    );
    private static final Pattern PROGRESS_SLASH = Pattern.compile(
        "([\\d.,]+)\\s*/\\s*([\\d.,]+)"
    );
    /** Tên người đặt order / buyer trên lore */
    private static final Pattern OWNER_PATTERN = Pattern.compile(
        "(?i)(?:người\\s*order|nguoi\\s*order|order\\s*by|ordered\\s*by|buyer|seller|"
            + "chủ\\s*order|chu\\s*order|chủ|player|owner|"
            + "người\\s*mua|nguoi\\s*mua|người\\s*bán|nguoi\\s*ban|"
            + "từ|tu|của|cua|by)\\s*[:：]\\s*(.+)"
    );
    /** Username Minecraft: 3–16 ký tự */
    private static final Pattern MC_NAME_PATTERN = Pattern.compile(
        "^[A-Za-z0-9_]{3,16}$"
    );

    public KamiOrderBot() {
        super(Categories.Misc, "kami-order-bot",
            "Auto Order SMP: score = price * log(remaining + 1), fill items, confirm orders.");
    }

    @Override
    public void onActivate() {
        if (!tryAcquireGuiOwner(GUI_OWNER_ORDER)) {
            error("GUI đang được điều khiển bởi " + currentGuiOwner() + " — không bật OrderBot.");
            toggle();
            return;
        }
        keepMouseFreeWhileRunning = true;

        boolean resume = nextActivateIsResume;
        nextActivateIsResume = false;

        if (!resume) {
            // Bật tay / session mới → reset đếm vòng
            cyclesCompleted = 0;
            resumeOrderAfterDrop = false;
            finalOrderBeforeRespawnRequested = false;
            finalOrderBeforeRespawnComplete = false;
        }

        resetState();
        resumeActivation = resume;
        runtimeOrderPlayers = loadConfiguredOrderPlayers();

        String cmdArg = getOrderCommandArg();
        String filter = getTargetItemFilterName();
        if (orderTargetMode.get() == OrderTargetMode.Player_List && getConfiguredOrderPlayers().isEmpty()) {
            error("Player_List dang bat nhung order-player-list dang trong.");
            toggle();
            return;
        }
        if (filter == null || filter.isBlank()) {
            error("Chưa chọn target-item (item cần bán)!");
            toggle();
            return;
        }
        if (cmdArg == null || cmdArg.isBlank()) {
            error("Thiếu order-player-name hoặc target item cho /order.");
            toggle();
            return;
        }
        Item deposit = getDepositItem();
        if (deposit == null || deposit == Items.AIR) {
            warning("Không resolve được Item — dump sẽ khớp theo tên hiển thị.");
        }

        int max = loopCount.get();
        String loopInfo = !autoSpawnerDrop.get() ? "loop off"
            : (max <= 0 ? "loop ∞" : "vòng " + (cyclesCompleted + 1) + "/" + max);
        if (resume) {
            log("Tiếp tục Order sau Drop — " + loopInfo + " | /order " + cmdArg + " | item=" + filter);
        } else {
            log("Bật Auto Order — " + loopInfo + " | /order " + cmdArg + " | lọc item=" + filter);
        }
        state = State.SEND_ORDER;
    }

    @Override
    public void onDeactivate() {
        resetState();
        keepMouseFreeWhileRunning = false;
        if (mc.player != null && isContainerOpen() && ownsGui()) {
            mc.player.closeHandledScreen();
        }
        releaseGuiOwner(GUI_OWNER_ORDER);
    }

    private void resetState() {
        state = State.SEND_ORDER;
        actionCooldown = 0;
        waitTicks = 0;
        fillsLeft = 0;
        orderRetry = 0;
        confirmClickedOk = false;
        scanEmptyRetries = 0;
        resumeWaitingForItems = false;
        resumeItemWaitTicks = 0;
        resumeActivation = false;
        pagesScanned = 0;
        searchRestartsDone = 0;
        pendingSpawnerDrop = false;
        pendingSpawnerDropWaitTicks = 0;
        postConfirmReopenClosed = false;
        currentOrderPlayerIndex = 0;
        ordersDoneForCurrentPlayer = 0;
        runtimeOrderPlayers = List.of();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            resetState();
            keepMouseFreeWhileRunning = false;
            releaseGuiOwner(GUI_OWNER_ORDER);
            return;
        }
        if (!ownsGui()) {
            if (state == State.DONE && isActive()) toggle();
            return;
        }

        if (actionCooldown > 0) {
            actionCooldown--;
            return;
        }

        switch (state) {
            case SEND_ORDER -> handleSendOrder();
            case WAIT_ORDER_LIST -> handleWaitOrderList();
            case SCAN_AND_SELECT -> handleScanAndSelect();
            case WAIT_FILL_GUI -> handleWaitFillGui();
            case DEPOSIT_SHIFT_1 -> handleDepositShift(1);
            case DEPOSIT_SHIFT_2 -> handleDepositShift(2);
            case ESC_AFTER_DEPOSIT -> handleEscAfterDeposit();
            case WAIT_CONFIRM_GUI -> handleWaitConfirmGui();
            case CONFIRM -> handleConfirm();
            case ESC_AFTER_CONFIRM -> handleEscAfterConfirm();
            case AFTER_CONFIRM -> handleAfterConfirm();
            case CLOSE_BEFORE_SPAWNER_DROP -> handleCloseBeforeSpawnerDrop();
            case DONE -> {
                // Message + spawner đã xử lý ở finishOutOfItems / finishDone
                if (isActive()) toggle();
            }
        }
    }

    // ───────────────────── Steps ─────────────────────

    private void handleSendOrder() {
        String cmdArg = getOrderCommandArg();
        if (orderTargetMode.get() == OrderTargetMode.Player_List && getConfiguredOrderPlayers().isEmpty()) {
            error("Player_List dang bat nhung order-player-list dang trong.");
            state = State.DONE;
            return;
        }
        if (cmdArg == null || cmdArg.isBlank()) {
            error("Thiếu order-player-name hoặc target item.");
            state = State.DONE;
            return;
        }

        String filterName = getTargetItemFilterName();
        // Chỉ khi hết item order trong người mới chuyển Spawner (qua finishOutOfItems)
        if (!hasOrderItemsOnPlayer()) {
            if (resumeActivation || resumeWaitingForItems) {
                resumeWaitingForItems = true;
                resumeItemWaitTicks++;
                if (resumeItemWaitTicks <= resumeItemWaitTimeout.get()) {
                    if (resumeItemWaitTicks == 1 || resumeItemWaitTicks % 40 == 0) {
                        log("Resume sau Drop: chờ item " + filterName + " vào inventory ("
                            + resumeItemWaitTicks + "/" + resumeItemWaitTimeout.get() + " tick).");
                    }
                    scheduleDelay();
                    return;
                }
                error("Resume sau Drop nhưng vẫn chưa thấy " + filterName
                    + " trong inventory — không gửi /order để tránh loop sai.");
                state = State.DONE;
                return;
            }
            finishOutOfItems("Hết " + filterName + " trong túi.");
            return;
        }

        resumeWaitingForItems = false;
        resumeItemWaitTicks = 0;
        resumeActivation = false;

        if (isContainerOpen()) {
            closeScreen();
            scheduleDelay();
            return;
        }

        // /order <tên người chơi> (hoặc tên item nếu player-name trống)
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendChatCommand("order " + cmdArg);
            log("Đã gửi /order " + cmdArg + " | lọc item: " + filterName);
        } else if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendChatMessage("/order " + cmdArg);
        }

        waitTicks = 0;
        orderRetry++;
        scanEmptyRetries = 0;
        pagesScanned = 0;
        state = State.WAIT_ORDER_LIST;
        scheduleDelay();
    }

    private void handleWaitOrderList() {
        waitTicks++;
        if (isContainerOpen()) {
            int need = Math.max(delay.get(), 5);
            if (waitTicks < need) return;
            state = State.SCAN_AND_SELECT;
            return;
        }

        if (waitTicks > guiWaitTimeout.get()) {
            if (orderRetry < 3) {
                warning("Timeout GUI Order — thử lại (" + orderRetry + "/3).");
                state = State.SEND_ORDER;
            } else {
                error("Không mở được GUI Order sau 3 lần.");
                if (!skipCurrentPlayerInList("khong mo duoc GUI Order sau 3 lan")) {
                    state = State.DONE;
                }
            }
        }
    }

    private void handleScanAndSelect() {
        if (!isContainerOpen()) {
            state = State.SEND_ORDER;
            return;
        }

        List<OrderEntry> orders = scanOrders();
        if (orders.isEmpty()) {
            if (tryGoNextOrderPage()) {
                return;
            }

            scanEmptyRetries++;
            if (scanEmptyRetries < 8) {
                if (scanEmptyRetries == 1 || scanEmptyRetries % 3 == 0) {
                    log("Chưa quét được order (retry " + scanEmptyRetries
                        + "/8) — đợi lore/slot hoặc nới price-min/max.");
                }
                waitTicks = 0;
                state = State.WAIT_ORDER_LIST;
                scheduleDelay();
                return;
            }
            log("Không còn order phù hợp sau nhiều lần quét.");
            scanEmptyRetries = 0;
            if (!restartOrderSearch("không thấy target/order phù hợp")) {
                closeScreen();
                if (!skipCurrentPlayerInList("khong thay target/order phu hop")) {
                    state = State.DONE;
                }
            }
            return;
        }

        scanEmptyRetries = 0;
        pagesScanned = 0;
        OrderEntry best = pickBestByScore(orders);
        if (best == null) {
            log("Có " + orders.size() + " order parse được nhưng bị lọc (price-min/max / min-remaining).");
            if (!restartOrderSearch("order bị lọc hết bởi filter")) {
                closeScreen();
                if (!skipCurrentPlayerInList("order bi loc het boi filter")) {
                    state = State.DONE;
                }
            }
            return;
        }

        searchRestartsDone = 0;
        // remainingNeeded = tổng - đã giao (đã tính trong entry.remaining)
        fillsLeft = Math.max(1, (int) Math.ceil(best.remaining / (double) ITEMS_PER_FILL));

        String owner = (best.ownerName == null || best.ownerName.isBlank())
            ? "?" : best.ownerName;
        log(String.format(Locale.ROOT,
            "Đã chọn order: %s | item=%s | %s$/item | Còn thiếu %d | Score %.2f | Sẽ fill %d lần",
            owner,
            best.itemName.isBlank() ? getTargetItemFilterName() : best.itemName,
            formatMoney(best.pricePerItem), best.remaining, best.score, fillsLeft));

        clickSlot(best.slotId, 0, SlotActionType.PICKUP);
        waitTicks = 0;
        state = State.WAIT_FILL_GUI;
        scheduleDelay();
    }

    private void handleWaitFillGui() {
        waitTicks++;
        if (!isContainerOpen()) {
            if (waitTicks > guiWaitTimeout.get()) {
                warning("GUI fill/confirm không mở — /order lại.");
                state = State.SEND_ORDER;
            }
            return;
        }
        if (waitTicks >= Math.max(2, delay.get() / 2)) {
            state = State.DEPOSIT_SHIFT_1;
        }
    }

    /**
     * Shift + Double Click lần {@code which} (1 hoặc 2) vào item dưới người → dump all cùng loại.
     * Flow: dump×2 → ESC×1 → Confirm.
     */
    private void handleDepositShift(int which) {
        if (!isContainerOpen()) {
            state = State.SEND_ORDER;
            return;
        }

        Item item = getDepositItem();
        if (item == null || item == Items.AIR) {
            int any = findFirstPlayerItemSlot(null);
            if (any >= 0) {
                item = mc.player.currentScreenHandler.slots.get(any).getStack().getItem();
            }
        }

        if (item == null || item == Items.AIR) {
            log("Không còn item dưới người (shift #" + which + ").");
        } else if (shiftDoubleClickOnPlayerItem(item)) {
            log("Shift+DoubleClick #" + which + " → dump all " + getTargetItemFilterName() + ".");
        } else {
            log("Không tìm thấy item để shift #" + which + ".");
        }

        clearCursorIntoInventory();
        scheduleDelay();

        if (which <= 1) {
            state = State.DEPOSIT_SHIFT_2;
        } else {
            // Dump ×2 xong → ESC → rồi mới confirm
            log("Dump ×2 xong — ESC rồi chờ GUI confirm.");
            state = State.ESC_AFTER_DEPOSIT;
        }
    }

    /**
     * Thứ tự đúng:
     * Shift+DoubleClick ×2 → ESC ×1 → chờ GUI confirm → click Xác nhận.
     */
    private void handleEscAfterDeposit() {
        clearCursorIntoInventory();
        if (isContainerOpen()) {
            closeScreen();
            log("ESC sau dump ×2 — chờ GUI confirm mở.");
        }
        waitTicks = 0;
        confirmClickedOk = false;
        state = State.WAIT_CONFIRM_GUI;
        scheduleDelay();
    }

    /**
     * Chờ GUI confirm: title chứa "xác nhận"/"giao hàng" HOẶC thấy nút xanh/tooltip.
     */
    private void handleWaitConfirmGui() {
        waitTicks++;
        if (!isContainerOpen()) {
            if (waitTicks > guiWaitTimeout.get()) {
                warning("GUI confirm đóng/không mở — /order lại.");
                state = State.SEND_ORDER;
            }
            return;
        }

        int found = resolveConfirmSlot();
        if (found >= 0) {
            log("Đã thấy nút Xác nhận ở slot " + found + " — chuẩn bị click.");
            state = State.CONFIRM;
            return;
        }

        // Title gợi ý đang ở màn confirm nhưng chưa parse được nút
        if (isConfirmScreenTitle() && waitTicks >= delay.get()) {
            log("Title là GUI confirm — thử click slot cấu hình.");
            state = State.CONFIRM;
            return;
        }

        if (waitTicks > guiWaitTimeout.get()) {
            warning("Timeout chờ nút Xác nhận. Dump slot container:");
            dumpConfirmCandidates();
            // Vẫn thử click theo slot cố định
            state = State.CONFIRM;
        }
    }

    /**
     * Click nút Xác nhận (ô xanh).
     * Flow: dump×2 → ESC → confirm click → ESC → tiếp tục.
     */
    private void handleConfirm() {
        if (!isContainerOpen()) {
            state = State.AFTER_CONFIRM;
            scheduleDelay();
            return;
        }

        int slot = resolveConfirmSlot();
        if (slot < 0) {
            // 17 trước (cuối H2), rồi 16, 15 — 16 đã confirm là lệch
            for (int trySlot : new int[]{confirmSlot.get(), 17, 16, 15, 8, 7}) {
                if (trySlot >= 0 && trySlot < mc.player.currentScreenHandler.slots.size()) {
                    ItemStack st = mc.player.currentScreenHandler.slots.get(trySlot).getStack();
                    if (!st.isEmpty() && !isCancelButton(st)) {
                        slot = trySlot;
                        break;
                    }
                }
            }
        }

        if (slot >= 0) {
            clickSlot(slot, 0, SlotActionType.PICKUP);
            confirmClickedOk = true;
            postConfirmReopenClosed = false;
            fillsLeft--;
            log("Đã click Xác nhận → slot " + slot + " — tiếp theo ESC.");
            waitTicks = 0;
            state = State.ESC_AFTER_CONFIRM;
            scheduleDelay();
        } else {
            error("Không click được Xác nhận. Xem debug, đặt confirm-slot = số << CONFIRM?");
            dumpConfirmCandidates();
            closeScreen();
            state = State.SEND_ORDER;
            scheduleDelay();
        }
    }

    /** ESC ngay sau khi bấm Xác nhận. */
    private void handleEscAfterConfirm() {
        if (isContainerOpen()) {
            closeScreen();
            log("ESC sau Xác nhận.");
        }
        waitTicks = 0;
        postConfirmReopenClosed = false;
        state = State.AFTER_CONFIRM;
        scheduleDelay();
    }

    private void handleAfterConfirm() {
        waitTicks++;

        // Sau confirm server có thể mở lại GUI chi tiết của chính order đó.
        // GUI này có thể tới muộn do lag, nên chờ một cửa sổ timeout thay vì ESC đúng một nhịp.
        if (isContainerOpen()) {
            closeScreen();
            postConfirmReopenClosed = true;
            log("ESC thoát GUI order vừa mở lại sau confirm.");
            waitTicks = 0;
            if (!fastPostConfirmEsc.get()) scheduleDelay();
            return;
        }

        if (!postConfirmReopenClosed && waitTicks < Math.max(delay.get(), postConfirmGuiWait.get())) {
            return;
        }

        if (postConfirmReopenClosed && !fastPostConfirmEsc.get() && waitTicks < Math.max(2, delay.get() / 2)) {
            return;
        }

        // Chỉ bật Spawner khi trong người không còn vật phẩm order
        if (!hasOrderItemsOnPlayer()) {
            if (isContainerOpen()) closeScreen();
            finishOutOfItems("Hết item order trong túi.");
            return;
        }

        // Lặp fill đúng order theo timesNeeded
        if (fillsLeft > 0) {
            log("Còn " + fillsLeft + " lần fill — /order lại cùng item.");
            orderRetry = 0;
            state = State.SEND_ORDER;
            return;
        }

        // Hết order hiện tại → tìm order tốt tiếp theo
        if (completePlayerListOrderIfNeeded()) {
            return;
        }

        // Hết order hiện tại → tìm order tốt tiếp theo
        log("Xong order hiện tại — tìm order tiếp theo.");
        orderRetry = 0;
        state = State.SEND_ORDER;
        scheduleDelay();
    }

    // ───────────────────── Chọn order theo price-select-mode ─────────────────────

    /**
     * Lọc order trong khoảng [price-min, price-max] rồi chọn theo mode.
     */
    private void handleCloseBeforeSpawnerDrop() {
        pendingSpawnerDropWaitTicks++;

        if (isContainerOpen()) {
            closeScreen();
            if (pendingSpawnerDropWaitTicks == 1 || pendingSpawnerDropWaitTicks % 10 == 0) {
                log("Dang dong GUI Order truoc khi bat SpawnerDrop...");
            }
            if (pendingSpawnerDropWaitTicks <= guiWaitTimeout.get()) {
                scheduleDelay();
                return;
            }
            warning("Timeout dong GUI Order - van thu bat SpawnerDrop.");
        }

        pendingSpawnerDrop = false;
        pendingSpawnerDropWaitTicks = 0;
        tryActivateSpawnerDrop();
        state = State.DONE;
    }

    private OrderEntry pickBestByScore(List<OrderEntry> orders) {
        List<OrderEntry> valid = filterValidOrders(orders);
        if (valid.isEmpty()) {
            log("Không có order trong khoảng giá ["
                + formatMoney(effectivePriceMin()) + " .. "
                + (effectivePriceMax() < 0 ? "∞" : formatMoney(effectivePriceMax())) + "].");
            return null;
        }
        if (valid.size() == 1) {
            log("Trong khoảng chỉ còn 1 order → "
                + formatMoney(valid.get(0).pricePerItem) + "$" + ownerTag(valid.get(0)));
            return valid.get(0);
        }

        return switch (priceSelectMode.get()) {
            case Highest -> pickHighest(valid);
            case Lowest -> pickLowest(valid);
            case Auto_Balanced -> pickAutoBalanced(valid);
        };
    }

    /** price-min sau khi chuẩn hóa (nếu min>max thì swap). */
    private double effectivePriceMin() {
        double lo = priceMin.get();
        double hi = priceMax.get();
        if (hi > 0 && lo > hi) return hi;
        return Math.max(0, lo);
    }

    /** price-max; &lt; 0 nghĩa là không trần. */
    private double effectivePriceMax() {
        double lo = priceMin.get();
        double hi = priceMax.get();
        if (hi <= 0) return -1; // không giới hạn
        if (lo > hi) return lo;
        return hi;
    }

    /**
     * Chỉ giữ order:
     * <ul>
     *   <li>remaining ≥ min-remaining</li>
     *   <li>price-min ≤ giá ≤ price-max (nếu max &gt; 0)</li>
     * </ul>
     */
    private List<OrderEntry> filterValidOrders(List<OrderEntry> orders) {
        List<OrderEntry> valid = new ArrayList<>();
        if (orders == null || orders.isEmpty()) return valid;

        double lo = effectivePriceMin();
        double hi = effectivePriceMax(); // -1 = no max

        for (OrderEntry o : orders) {
            if (o.remaining < minRemaining.get()) continue;
            if (o.pricePerItem + 1e-9 < lo) continue;
            if (hi > 0 && o.pricePerItem > hi + 1e-9) continue;

            o.score = o.pricePerItem * Math.log(o.remaining + 1.0);
            valid.add(o);
        }
        return valid;
    }

    /** Giá cao nhất; hòa → remaining nhiều hơn. */
    private OrderEntry pickHighest(List<OrderEntry> valid) {
        OrderEntry best = null;
        for (OrderEntry o : valid) {
            if (best == null
                || o.pricePerItem > best.pricePerItem + 1e-9
                || (Math.abs(o.pricePerItem - best.pricePerItem) < 1e-9 && o.remaining > best.remaining)
                || (Math.abs(o.pricePerItem - best.pricePerItem) < 1e-9
                    && o.remaining == best.remaining && o.score > best.score)) {
                best = o;
            }
        }
        if (best != null) {
            log("Trong khoảng → Highest: " + formatMoney(best.pricePerItem) + "$/item"
                + ownerTag(best));
        }
        return best;
    }

    /** Giá thấp nhất trong khoảng; hòa → remaining nhiều hơn. */
    private OrderEntry pickLowest(List<OrderEntry> valid) {
        OrderEntry best = null;
        for (OrderEntry o : valid) {
            if (best == null
                || o.pricePerItem < best.pricePerItem - 1e-9
                || (Math.abs(o.pricePerItem - best.pricePerItem) < 1e-9 && o.remaining > best.remaining)) {
                best = o;
            }
        }
        if (best != null) {
            log("Trong khoảng → Lowest: " + formatMoney(best.pricePerItem) + "$/item"
                + ownerTag(best));
        }
        return best;
    }

    /**
     * Trong khoảng [min,max]: tự tìm giá thích hợp
     * (cao hơn avg trong khoảng, không max, gần (avg+max)/2).
     */
    private OrderEntry pickAutoBalanced(List<OrderEntry> valid) {
        double sum = 0;
        double maxPrice = 0;
        double minP = Double.POSITIVE_INFINITY;
        for (OrderEntry o : valid) {
            sum += o.pricePerItem;
            if (o.pricePerItem > maxPrice) maxPrice = o.pricePerItem;
            if (o.pricePerItem < minP) minP = o.pricePerItem;
        }
        double avg = sum / valid.size();

        List<OrderEntry> midHigh = new ArrayList<>();
        for (OrderEntry o : valid) {
            if (o.pricePerItem > avg && o.pricePerItem < maxPrice - 1e-9) {
                midHigh.add(o);
            }
        }

        List<OrderEntry> pool = midHigh;
        if (pool.isEmpty()) {
            pool = new ArrayList<>();
            for (OrderEntry o : valid) {
                if (o.pricePerItem > avg + 1e-9) pool.add(o);
            }
        }
        if (pool.isEmpty()) pool = valid;

        double target = (avg + maxPrice) / 2.0;
        OrderEntry best = null;
        double bestKey = Double.NEGATIVE_INFINITY;
        for (OrderEntry o : pool) {
            double dist = Math.abs(o.pricePerItem - target);
            double key = -dist * 1000.0 + o.score;
            if (best == null || key > bestKey
                || (Math.abs(key - bestKey) < 1e-9 && o.remaining > best.remaining)) {
                best = o;
                bestKey = key;
            }
        }

        if (best != null) {
            log(String.format(Locale.ROOT,
                "Trong khoảng [%s..%s] → Auto pick=%s (avg=%s)%s",
                formatMoney(minP), formatMoney(maxPrice),
                formatMoney(best.pricePerItem), formatMoney(avg), ownerTag(best)));
        }
        return best;
    }

    private static String ownerTag(OrderEntry e) {
        if (e == null || e.ownerName == null || e.ownerName.isBlank()) return "";
        return " | " + e.ownerName;
    }

    /**
     * Quét GUI order:
     * 1) Bỏ slot rỗng / nút điều hướng
     * 2) Chỉ giữ order đúng Target Item (so tên, bỏ màu, ignore case)
     * 3) Parse giá + số lượng
     */
    private List<OrderEntry> scanOrders() {
        List<OrderEntry> result = new ArrayList<>();
        ScreenHandler menu = mc.player.currentScreenHandler;
        int containerSlots = Math.max(0, menu.slots.size() - 36);
        String want = normalizeItemName(getTargetItemFilterName());

        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.slots.get(i).getStack();
            if (stack.isEmpty()) continue;

            String rawName = stack.getName().getString();
            String nameLow = normalizeItemName(rawName);
            // Bỏ nút điều hướng / confirm trên list
            if (containsAny(nameLow, "next", "prev", "page", "back", "close", "đóng", "trang",
                "xác nhận", "xac nhan", "confirm", "store", "steal")) {
                continue;
            }

            // Chỉ order đúng item mình muốn bán
            if (!want.isEmpty() && !matchesTargetItem(stack, want)) {
                continue;
            }

            OrderEntry e = parseOrderFromStack(i, stack);
            if (e != null) {
                e.itemName = stripColorCodes(rawName).trim();
                result.add(e);
            }
        }
        return result;
    }

    private boolean tryGoNextOrderPage() {
        if (!scanNextPages.get() || !isContainerOpen() || mc.player == null) return false;
        if (pagesScanned >= Math.max(1, maxOrderPages.get()) - 1) return false;

        int slot = resolveNextPageSlot();
        if (slot < 0) return false;

        pagesScanned++;
        clickSlot(slot, 0, SlotActionType.PICKUP);
        log("Không thấy target ở trang hiện tại — sang trang tiếp theo bằng slot "
            + slot + " (" + (pagesScanned + 1) + "/" + maxOrderPages.get() + ").");

        waitTicks = 0;
        scanEmptyRetries = 0;
        state = State.WAIT_ORDER_LIST;
        scheduleDelay();
        return true;
    }

    private boolean restartOrderSearch(String reason) {
        int max = Math.max(0, orderSearchRestarts.get());
        if (searchRestartsDone >= max) {
            warning("Đã thử restart tìm order " + searchRestartsDone + "/" + max
                + " lần nhưng vẫn lỗi: " + reason + ".");
            return false;
        }

        searchRestartsDone++;
        closeScreen();
        waitTicks = 0;
        orderRetry = 0;
        scanEmptyRetries = 0;
        pagesScanned = 0;
        state = State.SEND_ORDER;
        scheduleDelay();
        warning("Restart tìm order lần " + searchRestartsDone + "/" + max
            + " vì " + reason + " — sẽ gửi /order lại.");
        return true;
    }

    private int resolveNextPageSlot() {
        ScreenHandler menu = mc.player.currentScreenHandler;
        if (menu == null) return -1;

        int configured = nextPageSlot.get();
        if (configured >= 0 && configured < menu.slots.size()) {
            ItemStack stack = menu.slots.get(configured).getStack();
            if (!stack.isEmpty() && (isNextPageButton(stack) || isArrowLike(stack))) return configured;
        }

        int containerSlots = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.slots.get(i).getStack();
            if (!stack.isEmpty() && isNextPageButton(stack)) return i;
        }
        return -1;
    }

    private boolean isNextPageButton(ItemStack stack) {
        String all = collectText(stack).toLowerCase(Locale.ROOT);
        if (containsAny(all,
            "next", "next page", "page next", "trang sau", "sang trang", "trang kế",
            "trang tiep", "trang tiếp", "tiep theo", "tiếp theo", "forward", ">"
        )) {
            return true;
        }
        return false;
    }

    private boolean isArrowLike(ItemStack stack) {
        String id = Registries.ITEM.getId(stack.getItem()).getPath();
        return id.equals("arrow") || id.equals("spectral_arrow");
    }

    /**
     * So khớp item slot với Target Item:
     * - bỏ mã màu §/&amp;
     * - không phân biệt hoa thường
     * - so tên hiển thị, tên vanilla, id path (iron_ingot ↔ iron ingot)
     */
    private boolean matchesTargetItem(ItemStack stack, String wantNormalized) {
        if (wantNormalized == null || wantNormalized.isEmpty()) return true;

        String hover = normalizeItemName(stack.getName().getString());
        if (targetNameMatches(hover, wantNormalized)) return true;

        try {
            String vanilla = normalizeItemName(stack.getItem().getName().getString());
            if (targetNameMatches(vanilla, wantNormalized)) return true;
        } catch (Throwable ignored) {
        }

        String path = Registries.ITEM.getId(stack.getItem()).getPath().replace('_', ' ');
        if (targetNameMatches(normalizeItemName(path), wantNormalized)) return true;

        // Item object từ setting list
        Item target = getDepositItem();
        if (target != null && target != Items.AIR && stack.isOf(target)) return true;

        // Lore đôi khi ghi "item: Bone"; không dùng contains rộng để tránh Bone ăn nhầm Bone Block.
        for (String line : getAllTextLines(stack)) {
            if (loreLineMatchesTarget(normalizeItemName(line), wantNormalized)) return true;
        }
        return false;
    }

    private static boolean targetNameMatches(String actualNormalized, String wantNormalized) {
        if (actualNormalized == null || wantNormalized == null) return false;
        if (actualNormalized.isEmpty() || wantNormalized.isEmpty()) return false;
        return actualNormalized.equals(wantNormalized);
    }

    private static boolean loreLineMatchesTarget(String lineNormalized, String wantNormalized) {
        if (targetNameMatches(lineNormalized, wantNormalized)) return true;
        return lineNormalized.endsWith(": " + wantNormalized)
            || lineNormalized.endsWith("- " + wantNormalized)
            || lineNormalized.endsWith("| " + wantNormalized);
    }

    private static boolean namesMatch(String a, String b) {
        if (a == null || b == null) return false;
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    /** Chuẩn hóa tên item: bỏ màu, lower-case, _ → space, gộp khoảng trắng. */
    private static String normalizeItemName(String s) {
        if (s == null) return "";
        String t = stripColorCodes(s).toLowerCase(Locale.ROOT);
        t = t.replace('_', ' ');
        t = t.replaceAll("[\\[\\]\\{\\}\\(\\)]", " ");
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private OrderEntry parseOrderFromStack(int slotId, ItemStack stack) {
        List<String> lines = getAllTextLines(stack);
        if (lines.isEmpty()) {
            // Vẫn cho qua nếu chỉ cần khớp item + giá fallback
            lines = List.of(stack.getName().getString());
        }
        String joined = String.join(" | ", lines);

        double price = parsePrice(joined);
        int delivered = parseIntPattern(DELIVERED_PATTERN, joined, -1);
        int total = parseIntPattern(TOTAL_PATTERN, joined, -1);
        int remaining = parseIntPattern(REMAINING_PATTERN, joined, -1);
        String owner = parseOwnerName(lines, stack);

        // "đã giao / tổng" dạng 100/500
        Matcher slash = PROGRESS_SLASH.matcher(joined);
        if (slash.find()) {
            int a = parseNumber(slash.group(1));
            int b = parseNumber(slash.group(2));
            if (delivered < 0) delivered = a;
            if (total < 0) total = b;
        }

        // remainingNeeded = tổng - đã giao
        if (remaining < 0 && total >= 0 && delivered >= 0) {
            remaining = Math.max(0, total - delivered);
        }
        if (remaining < 0 && total >= 0) remaining = total;

        // Nếu parse remaining fail nhưng đã khớp item: dùng count stack làm gợi ý
        if (remaining < 0 && stack.getCount() > 0) {
            remaining = Math.max(stack.getCount(), minRemaining.get());
        }
        // Giá bắt buộc để chọn order
        if (price < 0 || remaining < 0) return null;

        OrderEntry e = new OrderEntry();
        e.slotId = slotId;
        e.pricePerItem = price;
        e.remaining = remaining;
        e.total = total;
        e.delivered = delivered;
        e.ownerName = owner;
        return e;
    }

    /**
     * Lấy tên người order từ lore / tên item.
     * Ưu tiên dòng kiểu "Người order: Steve", "Buyer: ...", rồi display name nếu giống username.
     */
    private String parseOwnerName(List<String> lines, ItemStack stack) {
        for (String line : lines) {
            if (line == null) continue;
            String clean = stripColorCodes(line).trim();
            if (clean.isEmpty()) continue;

            Matcher m = OWNER_PATTERN.matcher(clean);
            if (m.find()) {
                String name = stripColorCodes(m.group(1)).trim();
                // Bỏ phần sau | hoặc -
                int cut = indexOfAny(name, " |", " - ", " • ");
                if (cut > 0) name = name.substring(0, cut).trim();
                if (!name.isEmpty() && !isGenericOrderWord(name)) {
                    return name;
                }
            }
        }

        // Fallback: display name item nếu giống username MC
        String hover = stripColorCodes(stack.getName().getString()).trim();
        if (MC_NAME_PATTERN.matcher(hover).matches() && !isGenericOrderWord(hover)) {
            return hover;
        }

        // Fallback: dòng lore chỉ có username
        for (String line : lines) {
            String clean = stripColorCodes(line).trim();
            if (MC_NAME_PATTERN.matcher(clean).matches() && !isGenericOrderWord(clean)) {
                return clean;
            }
        }
        return "";
    }

    private static String stripColorCodes(String s) {
        if (s == null) return "";
        // §a / &a
        return s.replaceAll("(?i)[§&][0-9a-fk-or]", "").trim();
    }

    private static int indexOfAny(String s, String... tokens) {
        int best = -1;
        for (String t : tokens) {
            int i = s.indexOf(t);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    private static boolean isGenericOrderWord(String s) {
        String low = s.toLowerCase(Locale.ROOT);
        return low.equals("order") || low.equals("buyer") || low.equals("seller")
            || low.contains("xác nhận") || low.contains("confirm")
            || low.contains("còn") || low.contains("giá") || low.contains("price");
    }

    // ───────────────────── Confirm slot ─────────────────────

    /**
     * Tìm slot nút Xác nhận (ô xanh):
     * 1) Tooltip/tên chứa "xác nhận"
     * 2) force → confirm-slot nếu không trống
     * 3) Ô lime/green glass pane trong container
     * 4) Thử 16, 17, 15
     */
    private int resolveConfirmSlot() {
        ScreenHandler menu = mc.player.currentScreenHandler;
        if (menu == null) return -1;

        // 1) Tooltip — chắc nhất theo ảnh GUI
        int byTip = findConfirmByTooltip();
        if (byTip >= 0) return byTip;

        int configured = confirmSlot.get();

        // 2) Force slot cố định (giống spawner 52)
        if (forceConfirmSlot.get() && configured >= 0 && configured < menu.slots.size()) {
            ItemStack stack = menu.slots.get(configured).getStack();
            if (!stack.isEmpty()) {
                // Chỉ nhận nếu không phải item "hủy"
                if (!isCancelButton(stack)) return configured;
            }
        }

        // 3) Lime / green pane
        int byColor = findLimeConfirmPane();
        if (byColor >= 0) return byColor;

        // 4) Thử các slot hay gặp (17 = cuối hàng 2 trước)
        for (int s : new int[]{configured, 17, 16, 15, 8, 7}) {
            if (s < 0 || s >= menu.slots.size()) continue;
            ItemStack st = menu.slots.get(s).getStack();
            if (!st.isEmpty() && !isCancelButton(st) && (isConfirmButton(st) || isLimeLike(st))) {
                return s;
            }
        }
        return -1;
    }

    private int findConfirmByTooltip() {
        ScreenHandler menu = mc.player.currentScreenHandler;
        int containerSlots = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.slots.get(i).getStack();
            if (!stack.isEmpty() && isConfirmButton(stack)) return i;
        }
        return -1;
    }

    private int findLimeConfirmPane() {
        ScreenHandler menu = mc.player.currentScreenHandler;
        int containerSlots = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.slots.get(i).getStack();
            if (stack.isEmpty() || isCancelButton(stack)) continue;
            if (isLimeLike(stack)) return i;
        }
        return -1;
    }

    private boolean isLimeLike(ItemStack stack) {
        String id = Registries.ITEM.getId(stack.getItem()).getPath();
        return id.contains("lime") || id.contains("green_stained_glass")
            || id.contains("emerald_block") || id.equals("lime_stained_glass_pane")
            || id.equals("lime_concrete") || id.equals("lime_wool");
    }

    private boolean isConfirmButton(ItemStack stack) {
        String all = collectText(stack).toLowerCase(Locale.ROOT);
        if (isCancelButton(stack)) return false;
        return containsAny(all, "xác nhận", "xac nhan", "confirm", "bấm vào để xác nhận", "ban se nhan");
    }

    private boolean isCancelButton(ItemStack stack) {
        String all = collectText(stack).toLowerCase(Locale.ROOT);
        return containsAny(all, "hủy", "huy", "cancel", "deny", "từ chối", "tu choi");
    }

    private boolean isConfirmScreenTitle() {
        if (mc.currentScreen == null) return false;
        try {
            String t = mc.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
            return t.contains("xác nhận") || t.contains("xac nhan")
                || t.contains("giao hàng") || t.contains("giao hang")
                || t.contains("đơn hàng") || t.contains("don hang");
        } catch (Throwable e) {
            return false;
        }
    }

    private void dumpConfirmCandidates() {
        if (!chatFeedback.get() || mc.player == null) return;
        ScreenHandler menu = mc.player.currentScreenHandler;
        int containerSlots = Math.max(0, menu.slots.size() - 36);
        log("--- Container slots (confirm debug), total=" + containerSlots + " ---");
        for (int i = 0; i < containerSlots; i++) {
            ItemStack st = menu.slots.get(i).getStack();
            if (st.isEmpty()) continue;
            int row = i / 9 + 1;
            int col = i % 9 + 1;
            boolean mark = isConfirmButton(st) || isLimeLike(st);
            log(String.format(Locale.ROOT, "  slot %d (H%d C%d) \"%s\"%s",
                i, row, col, st.getName().getString(), mark ? " << CONFIRM?" : ""));
        }
    }

    // ───────────────────── Shift + Double Click (inventory dưới) ─────────────────────

    /**
     * Giả lập đúng thao tác tay:
     * <b>Shift + Double Click</b> vào <b>một</b> stack item ở inventory dưới người
     * → chuyển hết mọi stack cùng loại vào order (phía trên).
     * <p>
     * Vanilla client sau khi detect double-click + shift sẽ QUICK_MOVE
     * mọi slot cùng phía inventory có cùng item — không click 2 lần cùng 1 ô trống.
     */
    private boolean shiftDoubleClickOnPlayerItem(Item item) {
        if (item == null || mc.player == null || mc.interactionManager == null) return false;

        ScreenHandler menu = mc.player.currentScreenHandler;
        int playerStart = Math.max(0, menu.slots.size() - 36);

        // Tìm 1 stack dưới người (ô được “double-click”)
        int clicked = findFirstPlayerItemSlot(item);
        if (clicked < 0) return false;

        // Vanilla: sau shift+double-click trên 1 stack → QUICK_MOVE tất cả slot
        // cùng inventory player có cùng item (gom list trước, mỗi slot 1 lần).
        List<Integer> sameType = new ArrayList<>();
        for (int i = playerStart; i < menu.slots.size(); i++) {
            ItemStack s = menu.slots.get(i).getStack();
            if (!s.isEmpty() && s.isOf(item)) {
                sameType.add(i);
            }
        }
        if (sameType.isEmpty()) return false;

        // Đảm bảo ô “double-click” đứng đầu (giống click đúng stack đó trước)
        sameType.remove((Integer) clicked);
        sameType.add(0, clicked);

        for (int slotId : sameType) {
            clearCursorIntoInventory();
            ItemStack now = menu.slots.get(slotId).getStack();
            if (now.isEmpty() || !now.isOf(item)) continue;
            // Shift-click = QUICK_MOVE
            clickSlot(slotId, 0, SlotActionType.QUICK_MOVE);
        }
        clearCursorIntoInventory();
        return true;
    }

    /** Slot index item dưới người (36 ô cuối GUI). item == null → stack bất kỳ. */
    private int findFirstPlayerItemSlot(Item item) {
        if (mc.player == null) return -1;
        ScreenHandler menu = mc.player.currentScreenHandler;
        int playerStart = Math.max(0, menu.slots.size() - 36);
        for (int i = playerStart; i < menu.slots.size(); i++) {
            ItemStack s = menu.slots.get(i).getStack();
            if (s.isEmpty()) continue;
            if (item == null || s.isOf(item)) return i;
        }
        return -1;
    }

    /** Cursor đang cầm đồ → đặt lại inventory dưới (không vứt ra đất). */
    private void clearCursorIntoInventory() {
        if (mc.player == null || mc.interactionManager == null) return;
        ScreenHandler menu = mc.player.currentScreenHandler;
        if (menu.getCursorStack().isEmpty()) return;

        int playerStart = Math.max(0, menu.slots.size() - 36);
        ItemStack cursor = menu.getCursorStack();

        for (int i = playerStart; i < menu.slots.size(); i++) {
            ItemStack s = menu.slots.get(i).getStack();
            if (!s.isEmpty() && ItemStack.areItemsAndComponentsEqual(s, cursor)
                && s.getCount() < s.getMaxCount()) {
                clickSlot(i, 0, SlotActionType.PICKUP);
                if (menu.getCursorStack().isEmpty()) return;
            }
        }
        for (int i = playerStart; i < menu.slots.size(); i++) {
            if (menu.slots.get(i).getStack().isEmpty()) {
                clickSlot(i, 0, SlotActionType.PICKUP);
                if (menu.getCursorStack().isEmpty()) return;
            }
        }
    }

    // ───────────────────── Helpers ─────────────────────

    /**
     * Đối số lệnh /order:
     * - Có order-player-name → /order &lt;tên người chơi&gt;
     * - Trống → fallback tên item (hành vi cũ)
     */
    private String getOrderCommandArg() {
        if (orderTargetMode.get() == OrderTargetMode.Player_List) {
            String player = getCurrentOrderPlayerName();
            if (player != null && !player.isBlank()) return player;
            return "";
        }
        String player = orderPlayerName.get();
        if (player != null && !player.isBlank()) return player.trim();
        return getTargetItemFilterName();
    }

    private List<String> getConfiguredOrderPlayers() {
        return runtimeOrderPlayers == null ? List.of() : runtimeOrderPlayers;
    }

    private List<String> loadConfiguredOrderPlayers() {
        if (orderTargetMode.get() != OrderTargetMode.Player_List) return List.of();
        if (playerListSource.get() == PlayerListSource.Txt_File) {
            return loadOrderPlayersFromTxt();
        }
        return normalizeOrderPlayerLines(orderPlayerList.get());
    }

    private List<String> loadOrderPlayersFromTxt() {
        Path path = resolvePlayerListFilePath();
        if (path == null) return List.of();

        try {
            if (!Files.exists(path)) {
                Path parent = path.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.createFile(path);
                warning("Da tao file player list trong: " + path);
                return List.of();
            }
            return normalizeOrderPlayerLines(Files.readAllLines(path));
        } catch (IOException e) {
            error("Khong doc duoc player-list-file: " + path + " (" + e.getMessage() + ")");
            return List.of();
        }
    }

    private Path resolvePlayerListFilePath() {
        String raw = playerListFile.get();
        if (raw == null || raw.isBlank()) raw = "config/kami-order-player-list.txt";
        try {
            Path path = Path.of(raw.trim());
            if (!path.isAbsolute()) {
                path = FabricLoader.getInstance().getGameDir().resolve(path);
            }
            return path.normalize();
        } catch (RuntimeException e) {
            error("player-list-file khong hop le: " + raw);
            return null;
        }
    }

    private List<String> normalizeOrderPlayerLines(List<String> raw) {
        List<String> names = new ArrayList<>();
        if (raw == null) return names;
        for (String entry : raw) {
            if (entry == null) continue;
            String name = entry.trim();
            if (name.startsWith("#")) continue;
            if (!name.isBlank()) names.add(name);
        }
        return names;
    }

    private String getCurrentOrderPlayerName() {
        List<String> names = getConfiguredOrderPlayers();
        if (names.isEmpty()) return "";
        if (currentOrderPlayerIndex < 0) currentOrderPlayerIndex = 0;
        if (currentOrderPlayerIndex >= names.size()) return "";
        return names.get(currentOrderPlayerIndex);
    }

    private void resetOrderSearchForPlayer() {
        waitTicks = 0;
        orderRetry = 0;
        scanEmptyRetries = 0;
        pagesScanned = 0;
        searchRestartsDone = 0;
    }

    private boolean skipCurrentPlayerInList(String reason) {
        if (orderTargetMode.get() != OrderTargetMode.Player_List) return false;

        String current = getCurrentOrderPlayerName();
        List<String> names = getConfiguredOrderPlayers();
        currentOrderPlayerIndex++;
        ordersDoneForCurrentPlayer = 0;
        resetOrderSearchForPlayer();

        if (currentOrderPlayerIndex >= names.size()) {
            log("Player_List: het danh sach sau khi bo qua "
                + (current == null || current.isBlank() ? "player hien tai" : current)
                + " (" + reason + ") - dung OrderBot.");
            state = State.DONE;
            return true;
        }

        log("Player_List: bo qua " + current + " (" + reason + ") -> /order "
            + names.get(currentOrderPlayerIndex) + ".");
        state = State.SEND_ORDER;
        scheduleDelay();
        return true;
    }

    private boolean completePlayerListOrderIfNeeded() {
        if (orderTargetMode.get() != OrderTargetMode.Player_List) return false;

        String current = getCurrentOrderPlayerName();
        ordersDoneForCurrentPlayer++;
        int max = Math.max(1, ordersPerPlayer.get());
        resetOrderSearchForPlayer();

        if (ordersDoneForCurrentPlayer < max) {
            log("Player_List: " + current + " da xong " + ordersDoneForCurrentPlayer
                + "/" + max + " order - tiep tuc cung player.");
            state = State.SEND_ORDER;
            scheduleDelay();
            return true;
        }

        List<String> names = getConfiguredOrderPlayers();
        currentOrderPlayerIndex++;
        ordersDoneForCurrentPlayer = 0;

        if (currentOrderPlayerIndex >= names.size()) {
            log("Player_List: da hoan tat danh sach (" + names.size()
                + " player, " + max + " order/player) - dung OrderBot.");
            state = State.DONE;
            return true;
        }

        log("Player_List: hoan tat " + current + " x" + max + " - chuyen sang /order "
            + names.get(currentOrderPlayerIndex) + ".");
        state = State.SEND_ORDER;
        scheduleDelay();
        return true;
    }

    /**
     * Tên item cần bán — dùng để lọc slot trong GUI Order.
     * Item_List: tên hiển thị vanilla (vd Iron Ingot) + path.
     * Manual_String: chuỗi gõ tay.
     */
    private String getTargetItemFilterName() {
        if (targetMode.get() == TargetMode.Manual_String) {
            String raw = manualOrderName.get();
            if (raw != null && !raw.isBlank()) return raw.trim();
        }
        return getListItemFilterName();
    }

    private String getListItemFilterName() {
        Item item = getListItem();
        if (item == null || item == Items.AIR) return "";
        try {
            String display = item.getName().getString();
            if (display != null && !display.isBlank()) return display.trim();
        } catch (Throwable ignored) {
        }
        return Registries.ITEM.getId(item).getPath().replace('_', ' ');
    }

    /** Item dùng để khớp inventory khi dump (list hoặc resolve từ manual string). */
    private Item getDepositItem() {
        if (targetMode.get() == TargetMode.Manual_String) {
            Item manual = resolveItemFromName(manualOrderName.get());
            if (manual != null && manual != Items.AIR) return manual;
        }
        return getListItem();
    }

    private Item getListItem() {
        List<Item> list = targetItems.get();
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }

    /**
     * Resolve "iron ingot" / "iron_ingot" / "minecraft:iron_ingot" → Item.
     */
    private Item resolveItemFromName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);

        // Full id: minecraft:iron_ingot
        if (s.contains(":")) {
            try {
                Item item = Registries.ITEM.get(Identifier.of(s));
                if (item != null && item != Items.AIR) return item;
            } catch (Exception ignored) {
            }
        }

        // iron ingot → iron_ingot
        String path = s.replace(' ', '_');
        try {
            Item item = Registries.ITEM.get(Identifier.of("minecraft", path));
            if (item != null && item != Items.AIR) return item;
        } catch (Exception ignored) {
        }

        // Thử path gốc nếu user gõ có _
        try {
            Item item = Registries.ITEM.get(Identifier.of("minecraft", s));
            if (item != null && item != Items.AIR) return item;
        } catch (Exception ignored) {
        }

        return null;
    }

    private boolean hasTargetInInventory(Item item) {
        if (item == null || item == Items.AIR || mc.player == null) return false;
        // Main inventory + hotbar
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.isOf(item)) return true;
        }
        // Offhand
        ItemStack off = mc.player.getOffHandStack();
        if (!off.isEmpty() && off.isOf(item)) return true;
        // Cursor (đang cầm)
        if (mc.player.currentScreenHandler != null) {
            ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
            if (!cursor.isEmpty() && cursor.isOf(item)) return true;
        }
        return false;
    }

    /**
     * Còn item order trong người không?
     * — Item_List / Manual resolve được: theo Item type.
     * — Manual không resolve: theo tên hiển thị khớp filter.
     * Dùng làm điều kiện DUY NHẤT bật Spawner Drop.
     */
    private boolean hasOrderItemsOnPlayer() {
        if (mc.player == null) return false;

        if (targetMode.get() == TargetMode.Item_List) {
            List<Item> list = targetItems.get();
            if (list != null) {
                for (Item it : list) {
                    if (it != null && it != Items.AIR && hasTargetInInventory(it)) return true;
                }
            }
            return false;
        }

        // Manual_String
        Item deposit = resolveItemFromName(manualOrderName.get());
        if (deposit != null && deposit != Items.AIR) {
            return hasTargetInInventory(deposit);
        }

        // Không resolve được Item → khớp theo tên hiển thị
        String want = normalizeItemName(getTargetItemFilterName());
        if (want == null || want.isBlank()) return false;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            if (stackMatchesOrderFilter(s, want)) return true;
        }
        ItemStack off = mc.player.getOffHandStack();
        if (off != null && !off.isEmpty() && stackMatchesOrderFilter(off, want)) return true;
        return false;
    }

    private boolean stackMatchesOrderFilter(ItemStack stack, String wantNormalized) {
        if (stack == null || stack.isEmpty() || wantNormalized == null || wantNormalized.isBlank()) {
            return false;
        }
        try {
            String display = normalizeItemName(stack.getName().getString());
            if (namesMatch(display, wantNormalized) || display.contains(wantNormalized)) return true;
            String vanilla = normalizeItemName(stack.getItem().getName().getString());
            if (namesMatch(vanilla, wantNormalized) || vanilla.contains(wantNormalized)) return true;
            String path = Registries.ITEM.getId(stack.getItem()).getPath().replace('_', ' ');
            if (namesMatch(normalizeItemName(path), wantNormalized)) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Hết item order → (tuỳ chọn) bật Kami Spawner Drop → DONE.
     * Chỉ bật Spawner khi trong người THỰC SỰ không còn item order.
     * Đặt {@link #resumeOrderAfterDrop} để Spawner biết có bật lại Order không.
     */
    private void finishOutOfItems(String reason) {
        // Gate cứng: còn item order → không coi là hết, không bật Spawner
        if (hasOrderItemsOnPlayer()) {
            int count = countOrderItemsOnPlayer();
            warning(reason + " nhưng vẫn còn ~" + count
                + " item order trong túi — KHÔNG bật Spawner. Tiếp tục order.");
            orderRetry = 0;
            state = State.SEND_ORDER;
            scheduleDelay();
            return;
        }

        if (finalOrderBeforeRespawnRequested) {
            finalOrderBeforeRespawnRequested = false;
            finalOrderBeforeRespawnComplete = true;
            resumeOrderAfterDrop = false;
            nextActivateIsResume = false;
            log(reason + " - hoan tat Order lan cuoi truoc respawn, khong bat Drop lai.");
            state = State.DONE;
            return;
        }

        cyclesCompleted++;
        int max = loopCount.get();
        boolean shouldDropThisPhase = autoSpawnerDrop.get() && (max <= 0 || cyclesCompleted <= max);
        // Còn vòng? 0 = vô hạn; còn thì sau drop sẽ bật lại Order
        boolean moreCycles = autoSpawnerDrop.get() && (max <= 0 || cyclesCompleted < max);

        resumeOrderAfterDrop = moreCycles;

        log(reason + " — kết thúc phase order #" + cyclesCompleted
            + (max > 0 ? "/" + max : "")
            + (shouldDropThisPhase
                ? (moreCycles ? " → Drop rồi Order lại." : " → Drop lần cuối, hết vòng.")
                : " — dừng."));

        if (shouldDropThisPhase) {
            pendingSpawnerDrop = true;
            pendingSpawnerDropWaitTicks = 0;
            if (isContainerOpen()) closeScreen();
            state = State.CLOSE_BEFORE_SPAWNER_DROP;
            scheduleDelay();
            return;
        }

        state = State.DONE;
    }

    private int countOrderItemsOnPlayer() {
        if (mc.player == null) return 0;
        int total = 0;
        Item deposit = getDepositItem();
        if (deposit != null && deposit != Items.AIR) {
            for (int i = 0; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (!s.isEmpty() && s.isOf(deposit)) total += s.getCount();
            }
            ItemStack off = mc.player.getOffHandStack();
            if (!off.isEmpty() && off.isOf(deposit)) total += off.getCount();
            return total;
        }
        String want = normalizeItemName(getTargetItemFilterName());
        if (want == null || want.isBlank()) return 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && stackMatchesOrderFilter(s, want)) total += s.getCount();
        }
        return total;
    }

    public boolean hasOrderItemsForDropResume() {
        return hasOrderItemsOnPlayer();
    }

    /**
     * Bật module Spawner Drop qua Meteor Modules API (addon riêng).
     * <b>Chỉ khi túi không còn vật phẩm order.</b>
     * Tên mặc định: {@code kami-spawner-drop}.
     */
    private void tryActivateSpawnerDrop() {
        if (!autoSpawnerDrop.get()) return;

        // Điều kiện bắt buộc: trong người không có item order
        if (hasOrderItemsOnPlayer()) {
            int count = countOrderItemsOnPlayer();
            warning("Còn ~" + count + " item order trong người — KHÔNG bật Spawner Drop.");
            resumeOrderAfterDrop = false;
            return;
        }

        String name = spawnerDropModule.get();
        if (name == null || name.isBlank()) name = "kami-spawner-drop";
        name = name.trim();

        Module mod = findModuleByName(name);
        if (mod == null) {
            warning("Không tìm thấy module \"" + name
                + "\". Cài KamiSpawnerDrop addon và kiểm tra spawner-drop-module.");
            resumeOrderAfterDrop = false;
            return;
        }

        if (mod.isActive()) {
            log("Spawner Drop đã bật sẵn (" + mod.title + ").");
            return;
        }

        releaseGuiOwner(GUI_OWNER_ORDER);
        mod.toggle(); // bật
        if (mod.isActive()) {
            log("Đã tự bật " + mod.title
                + " (túi hết item order)"
                + (resumeOrderAfterDrop ? " — sau drop sẽ bật lại Order." : " — sau drop dừng."));
        } else {
            warning("Gọi toggle " + mod.title + " nhưng module vẫn tắt.");
            resumeOrderAfterDrop = false;
        }
    }

    private Module findModuleByName(String name) {
        if (Modules.get() == null) return null;

        // API get(String) nếu có
        try {
            Module m = Modules.get().get(name);
            if (m != null) return m;
        } catch (Throwable ignored) {
        }

        // Fallback: duyệt tất cả module
        String key = name.toLowerCase(Locale.ROOT).replace(' ', '-');
        for (Module m : Modules.get().getAll()) {
            if (m == null) continue;
            if (m.name != null && m.name.equalsIgnoreCase(key)) return m;
            if (m.name != null && m.name.equalsIgnoreCase(name)) return m;
            if (m.title != null && m.title.equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    /**
     * Click slot silent — chỉ packet clickSlot, không GLFW set cursor / mouse move.
     * Dùng PICKUP hoặc QUICK_MOVE (shift-click) tùy {@code type}.
     */
    private void clickSlot(int slotId, int button, SlotActionType type) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!isActive() || !ownsGui()) return;
        ScreenHandler menu = mc.player.currentScreenHandler;
        if (menu == null) return;
        if (slotId < 0 || slotId >= menu.slots.size()) return;
        mc.interactionManager.clickSlot(menu.syncId, slotId, button, type, mc.player);
    }

    private boolean ownsGui() {
        return isGuiOwner(GUI_OWNER_ORDER);
    }

    private boolean isContainerOpen() {
        if (mc.player == null) return false;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return true;
        return mc.currentScreen instanceof HandledScreen;
    }

    private void closeScreen() {
        if (mc.player != null && ownsGui()) mc.player.closeHandledScreen();
    }

    private List<String> getAllTextLines(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        lines.add(stack.getName().getString());
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text t : lore.lines()) lines.add(t.getString());
            try {
                for (Text t : lore.styledLines()) {
                    String s = t.getString();
                    if (!lines.contains(s)) lines.add(s);
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            for (Text t : stack.getTooltip(Item.TooltipContext.create(mc.world), mc.player, TooltipType.BASIC)) {
                String s = t.getString();
                if (!lines.contains(s)) lines.add(s);
            }
        } catch (Throwable ignored) {
        }
        return lines;
    }

    private String collectText(ItemStack stack) {
        return String.join(" ", getAllTextLines(stack));
    }

    private double parsePrice(String text) {
        Matcher m = PRICE_PATTERN.matcher(text);
        if (m.find()) {
            String g = m.group(1) != null ? m.group(1) : m.group(2);
            return parseNumberDouble(g);
        }
        // Lấy TẤT CẢ số kèm $ rồi chọn giá trị lớn nhất (tránh nhầm 1.500 → 1.5)
        Matcher any = Pattern.compile("([\\d.,]+)\\s*\\$").matcher(text);
        double best = -1;
        while (any.find()) {
            double v = parseNumberDouble(any.group(1));
            if (v > best) best = v;
        }
        return best;
    }

    private int parseIntPattern(Pattern p, String text, int def) {
        Matcher m = p.matcher(text);
        if (m.find()) return parseNumber(m.group(1));
        return def;
    }

    private int parseNumber(String raw) {
        double v = parseNumberDouble(raw);
        if (v < 0) return 0;
        return (int) Math.round(v);
    }

    /**
     * Parse số an toàn với dấu . và , (VN / US / EU).
     * <ul>
     *   <li>{@code 1.500} hoặc {@code 1,500} → <b>1500</b> (3 chữ số sau 1 dấu = hàng nghìn)</li>
     *   <li>{@code 1.234.567} → 1234567</li>
     *   <li>{@code 1.234,56} → 1234.56 (EU)</li>
     *   <li>{@code 1,234.56} → 1234.56 (US)</li>
     *   <li>{@code 1.5} / {@code 12.5} → thập phân thật</li>
     *   <li>{@code 2k} → 2000</li>
     * </ul>
     * Tránh lỗi cũ: xóa hết {@code ,} rồi parse {@code 1.500} thành 1.5 → chọn order giá thấp.
     */
    private double parseNumberDouble(String raw) {
        if (raw == null) return -1;
        String s = raw.trim().replace(" ", "").replace("_", "");
        if (s.isEmpty()) return -1;

        double mult = 1;
        if (s.toLowerCase(Locale.ROOT).endsWith("k")) {
            mult = 1000;
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) return -1;

        try {
            s = normalizeDecimalString(s);
            return Double.parseDouble(s) * mult;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Chuẩn hóa chuỗi số về dạng parse được bởi {@link Double#parseDouble}
     * (dấu chấm = thập phân, không còn dấu nghìn).
     */
    private static String normalizeDecimalString(String s) {
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');

        if (lastDot >= 0 && lastComma >= 0) {
            // Có cả . và , → dấu đứng sau là thập phân
            if (lastComma > lastDot) {
                // 1.234,56 (EU/VN)
                return s.replace(".", "").replace(',', '.');
            }
            // 1,234.56 (US)
            return s.replace(",", "");
        }

        if (lastComma >= 0) {
            long commas = s.chars().filter(c -> c == ',').count();
            int after = s.length() - lastComma - 1;
            // Nhiều dấu , hoặc đúng 3 số sau → nghìn: 1,500 / 1,234,567
            if (commas > 1 || after == 3) {
                return s.replace(",", "");
            }
            // 1,5 → 1.5
            return s.replace(',', '.');
        }

        if (lastDot >= 0) {
            long dots = s.chars().filter(c -> c == '.').count();
            int after = s.length() - lastDot - 1;
            // Nhiều dấu . hoặc đúng 3 số sau 1 dấu → nghìn: 1.500 = 1500 (không phải 1.5)
            if (dots > 1 || after == 3) {
                return s.replace(".", "");
            }
            // 1.5 / 12.75 → giữ nguyên
            return s;
        }

        return s;
    }

    private String formatMoney(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) {
            return String.format(Locale.ROOT, "%.0f", v);
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private void scheduleDelay() {
        int base = Math.max(1, delay.get());
        int jitter = ThreadLocalRandom.current().nextInt(0, Math.max(2, base / 3 + 1));
        actionCooldown = base + jitter + ThreadLocalRandom.current().nextInt(0, 4);
    }

    private boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private void log(String msg) {
        if (chatFeedback.get()) info(msg);
    }

    @Override
    public String getInfoString() {
        String p = orderPlayerName.get();
        String item = getTargetItemFilterName();
        if (p != null && !p.isBlank()) {
            return p.trim() + "/" + (item.isBlank() ? "?" : item)
                + (fillsLeft > 0 ? " x" + fillsLeft : "");
        }
        if (item == null || item.isBlank()) item = "?";
        return item + (fillsLeft > 0 ? " x" + fillsLeft : "");
    }

    private static final class OrderEntry {
        int slotId;
        double pricePerItem;
        int remaining;
        int total;
        int delivered;
        double score;
        /** Tên người đặt order (buyer) */
        String ownerName = "";
        /** Tên item hiển thị trên slot order */
        String itemName = "";
    }
}
