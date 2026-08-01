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

- Description: Use Sell All/Dump All twice for SpawnerProtect sell cleanup.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiSpawnerProtect.java`,
    `README.md`, `AGENTS.md`, `TODO.md`, `gradle.properties`
  Notes: Added `sell-dump-slot` and `force-sell-dump-slot`. Protect now clicks
    the Sell All/Dump All GUI button twice per `/sell` cleanup attempt and only
    falls back to shift-click if no button slot can be resolved. Version bumped
    to `0.5.2`.

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
  Files: `build/libs/kami-order-bot-0.5.2-obfuscated.jar`
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
