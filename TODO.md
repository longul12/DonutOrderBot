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
    `src/main/java/com/kami/spawnersdrop/modules/KamiSpawnerDrop.java`,
    `build.gradle.kts`, `gradle.properties`, `src/main/resources/fabric.mod.json`
  Notes: `KamiOrderAddon` now registers OrderBot, SpawnerDrop, and
    SpawnerProtect. Version bumped to `0.4.0`.

- Description: Auto-enable SpawnerProtect from OrderBot and SpawnerDrop.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`,
    `src/main/java/com/kami/spawnersdrop/modules/KamiSpawnerDrop.java`,
    `gradle.properties`
  Notes: Both modules have `auto-spawner-protect` and
    `spawner-protect-module` settings. Version bumped to `0.4.1`.

- Description: Convert OrderBot and SpawnerDrop GUI setting descriptions to
    ASCII text.
  Priority: High
  Status: Completed
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`,
    `src/main/java/com/kami/spawnersdrop/modules/KamiSpawnerDrop.java`,
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

## In Progress

- Description: In-game validation on the live Donut SMP GUI.
  Priority: High
  Status: In Progress
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`,
    `src/main/java/com/kami/spawnersdrop/modules/KamiSpawnerDrop.java`
  Notes: Build is verified, but server GUI behavior and the unified JAR must be
    validated manually in Minecraft.

## Not Started

- Description: Validate the obfuscated JAR in a real Minecraft client.
  Priority: High
  Status: Not Started
  Files: `build/libs/kami-order-bot-0.4.3-obfuscated.jar`
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
    `src/main/java/com/kami/spawnersdrop/modules/KamiSpawnerDrop.java`
  Notes: Current reflection approach still works inside the unified JAR.
