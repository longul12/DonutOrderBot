# TODO

## Completed

- Description: Build against Minecraft 1.21.11 / Java 21.
  Priority: High
  Status: Completed
  Files: `build.gradle.kts`, `gradle.properties`
  Notes: Verified with `.\gradlew.bat build`.

- Description: Use `clickSlot` for GUI slot interaction.
  Priority: Critical
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`
  Notes: Slot operations use `PICKUP` and `QUICK_MOVE`.

- Description: Add GUI ownership coordination with SpawnerDrop.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`
  Notes: Static ownership methods are used by SpawnerDrop through reflection.

- Description: Prevent mouse grab while bot is running.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/mixin/MouseLockMixin.java`,
    `src/main/resources/kami-order-bot.mixins.json`,
    `src/main/resources/fabric.mod.json`
  Notes: The mixin cancels `Mouse.lockCursor`; it does not warp the cursor.

- Description: Resume safely after SpawnerDrop.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`
  Notes: Handles resume activation and avoids staying stuck active after
    ownership handoff.

- Description: Merge SpawnerDrop into the OrderBot addon JAR.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/KamiOrderAddon.java`,
    `src/main/java/com/kami/order/modules/KamiSpawnerDrop.java`,
    `build.gradle.kts`, `gradle.properties`, `src/main/resources/fabric.mod.json`
  Notes: `KamiOrderAddon` now registers OrderBot, SpawnerDrop, and
    SpawnerProtect. Version bumped to `0.4.0`.

- Description: Remove auto-enable SpawnerProtect from OrderBot and SpawnerDrop.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`,
    `src/main/java/com/kami/order/modules/KamiSpawnerDrop.java`,
    `gradle.properties`
  Notes: Automatic activation can conflict with Order/Drop GUI ownership.
    SpawnerProtect remains registered but must be enabled manually. Version
    bumped to `0.4.6`.

- Description: Convert OrderBot and SpawnerDrop GUI setting descriptions to
    ASCII text.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`,
    `src/main/java/com/kami/order/modules/KamiSpawnerDrop.java`,
    `gradle.properties`
  Notes: Avoids missing glyphs/mojibake in Meteor's module settings UI. Version
    bumped to `0.4.2`.

- Description: Prevent SpawnerProtect from breaking spawners when pickup safety
    is poor.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `gradle.properties`
  Notes: Added `min-empty-slots-before-break`,
    `max-ground-items-before-break`, and `ground-item-check-radius`. Removed
    automatic item dropping before/while picking up the spawner. Version bumped
    to `0.4.3`.

- Description: Add `/sell` cleanup before SpawnerProtect breaks a spawner.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `gradle.properties`
  Notes: If inventory space is low or nearby dropped items exceed the threshold,
    Protect opens `/sell`, quick-moves sellable inventory stacks, closes the GUI,
    waits briefly for pickup, and retries until the break safety checks pass.
    Version bumped to `0.4.4`.

- Description: Make SpawnerProtect sell cleanup more aggressive.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `gradle.properties`
  Notes: Each `/sell` GUI now runs two quick-move passes. Default ground item
    threshold is `0`, so Protect keeps selling/waiting until nearby dropped
    items are gone before breaking a spawner. Version bumped to `0.4.5`.

- Description: Fix Meteor Orbit crash when OrderBot toggles SpawnerDrop.
  Priority: Critical
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerDrop.java`,
    `src/main/java/com/kami/order/KamiOrderAddon.java`,
    `src/main/java/com/kami/order/modules/KamiOrderBot.java`,
    `build.gradle.kts`, `gradle.properties`
  Notes: SpawnerDrop now lives under `com.kami.order.modules`, matching the
    addon package returned by `KamiOrderAddon#getPackage()`. This fixes
    `NoLambdaFactoryException` when Meteor subscribes SpawnerDrop event handlers.
    Version bumped to `0.4.7`.

- Description: Reduce OrderBot pause after post-confirm GUI reopen.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`,
    `gradle.properties`
  Notes: Added `fast-post-confirm-esc`, enabled by default. When the server
    reopens the order GUI after confirm, OrderBot closes it and continues on the
    next tick instead of waiting another delay window. Version bumped to `0.4.8`.

- Description: Remove final-order cleanup and fix Drop-to-Order resume.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`,
    `src/main/java/com/kami/order/modules/KamiSpawnerDrop.java`,
    `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `gradle.properties`
  Notes: SpawnerDrop no longer starts a separate final order during respawn.
    SpawnerProtect no longer waits for final Order before breaking. Drop now
    resumes OrderBot after drop so OrderBot can continue if target items are in
    inventory, and OrderBot stops triggering Drop after loop-count is exceeded.
    Version bumped to `0.4.9`.

- Description: Expand SpawnerProtect player detection range.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `gradle.properties`
  Notes: `detection-range` now defaults to 64 blocks and its slider goes up to
    256 blocks. Actual detection still depends on player entities being loaded
    by the client/server. Version bumped to `0.5.0`.

- Description: Speed up SpawnerProtect sell cleanup before breaking.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: Added fixed tick settings for sell open delay, sell close delay, and
    post-close pickup wait. The sell GUI still quick-moves sellable stacks twice
    per cleanup attempt and repeats until break safety checks pass. Version
    bumped to `0.5.1`.

- Description: Use shift-click-all twice for SpawnerProtect sell cleanup.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: Removed the mistaken Sell All/Dump All button flow. Protect now opens
    `/sell`, quick-moves every sellable inventory stack into the sell GUI, and
    repeats that shift-click pass twice per cleanup attempt. Version bumped to
    `0.5.3`.

- Description: Force-close Order/Drop GUI before SpawnerProtect takeover.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: When a threat appears while OrderBot/SpawnerDrop owns a GUI,
    SpawnerProtect now stops peers first, closes the open GUI up to three times
    across ticks, then acquires GUI ownership before `/sell`, breaking, and
    storing. Version bumped to `0.5.4`.

- Description: Restore final Order after SpawnerDrop Sell All stop-item handling.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerDrop.java`,
    `src/main/java/com/kami/order/modules/KamiOrderBot.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: When SpawnerDrop sees a stop item, it still clicks Sell All once first.
    After closing the spawner GUI, it checks for remaining Order target items,
    runs OrderBot one final time if needed, and then waits for respawn without
    OrderBot enabling Drop again. Version bumped to `0.5.5`.

- Description: Add OrderBot player-list ordering mode.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: Added `order-target-mode`, `order-player-list`, and
    `orders-per-player`. In `Player_List` mode, OrderBot orders the current name
    until the shared count is completed, then advances to the next configured
    name. Version bumped to `0.5.6`.

- Description: Add txt-file source for OrderBot player list.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: Added `player-list-source` and `player-list-file`. `Txt_File` reads
    `config/kami-order-player-list.txt` by default, one player name per line,
    ignoring blank lines and `#` comments. Version bumped to `0.5.7`.

- Description: Fix SpawnerProtect sneak release and safe sell cleanup.
  Priority: Critical
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: Added `STOP_SNEAK_BEFORE_OPEN_CHEST` before Ender Chest interaction to
    avoid toggle-sneak blocking the GUI. Added `sell-cleanup-mode` and
    `sell-whitelist-items`; the default `Safe_Whitelist` only shift-clicks
    configured sell items and always protects spawners, ender chests, gear,
    bundles, enchanted books, and shulker boxes. Version bumped to `0.5.8`.

- Description: Batch SpawnerProtect storage after several spawner stacks.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: Added `store-after-spawner-stacks`. After pickup, Protect keeps sneak
    active and continues mining nearby spawners until the configured stack count
    is reached, no nearby spawner remains, or no threat remains. Version bumped
    to `0.5.9`.

- Description: Refine SpawnerProtect mine-store loop.
  Priority: Critical
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: Ender Chest open now retries every tick, ignores non-storage GUI
    handlers, and returns to the mining loop after storing a batch instead of
    going through the slower guard cycle. Version bumped to `0.5.10`.

- Description: Add SpawnerProtect hold/toggle sneak control mode.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: Added `sneak-control-mode` with `Hold` and `Toggle` options so users
    can match Minecraft's sneak key behavior. Version bumped to `0.5.11`.

- Description: Split SpawnerProtect `/sell` cleanup into two sync-separated passes.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: Added `SELL_ITEMS_SECOND_PASS`; Protect now shift-clicks sellable
    items once, waits one tick, then shift-clicks again before closing `/sell`.
    Version bumped to `0.5.12`.

- Description: Restore SpawnerProtect 0.5.7-style mine-store flow.
  Priority: Critical
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: Removed batch storage and the extra stop-sneak-before-chest state.
    Protect now releases sneak as soon as the spawner block is gone, waits for
    pickup, opens Ender Chest, stores, and then continues like version `0.5.7`.
    Later sell cleanup and sneak-control settings remain. Version bumped to
    `0.5.13`.

- Description: Prevent SpawnerProtect from opening Ender Chest while still sneaking.
  Priority: Critical
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: `OPEN_ENDER_CHEST` now checks `mc.player.isSneaking()` and waits one
    tick after `sendSneak(false)` before interacting with Ender Chest. Version
    bumped to `0.5.14`.

- Description: Continue SpawnerProtect after storing when keep-running is enabled.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: `afterOneSpawnerCycle` now refreshes the nearest target and continues
    when a nearby spawner exists, instead of requiring the original threat to
    still be detected. Version bumped to `0.5.15`.

- Description: Store spawners during pickup wait even when pickup counter is stale.
  Priority: Critical
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: `WAIT_PICKUP` now falls back to `hasSpawnerInInventory()` after a short
    wait and on timeout, so later cycles still open Ender Chest and store even
    if `countSpawnersInPlayerInventory() > spawnerCountBeforeBreak` does not
    become true. Version bumped to `0.5.16`.

## In Progress

- Description: In-game validation on the live Donut SMP GUI.
  Priority: High
  Status: In Progress
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`,
    `src/main/java/com/kami/order/modules/KamiSpawnerDrop.java`
  Notes: Build is verified, but server GUI behavior and the unified JAR must be
    validated manually in Minecraft.

## Not Started

- Description: Validate the obfuscated JAR in a real Minecraft client.
  Priority: High
  Status: Not Started
  Files: `build/libs/kami-order-bot-0.5.16-obfuscated.jar`
  Notes: Confirm Fabric Loader loads the addon, Meteor registers both modules,
    mixin startup succeeds, settings load, and OrderBot/SpawnerDrop handoff
    reflection still works.

- Description: Tighten yGuard keep rules after runtime stability is proven.
  Priority: Medium
  Status: Not Started
  Files: `build.gradle.kts`
  Notes: Current keep rules intentionally preserve module class names and public
    API members because Meteor settings/config and sibling-addon reflection rely
    on stable names.

- Description: Add automated tests for lore number parsing.
  Priority: Medium
  Status: Not Started
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`
  Notes: Parser currently has no test harness.

- Description: Add optional default module keybinds.
  Priority: Low
  Status: Not Started
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`
  Notes: Meteor users can bind modules manually today.

- Description: Review confirm slot detection against current server GUI.
  Priority: Medium
  Status: Not Started
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`
  Notes: Current implementation combines tooltip, color, and fallback slots.

- Description: Consider a shared library for cross-addon ownership.
  Priority: Low
  Status: Not Started
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`,
    `src/main/java/com/kami/order/modules/KamiSpawnerDrop.java`
  Notes: Current reflection approach still works inside the unified JAR.
