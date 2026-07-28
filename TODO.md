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

## In Progress

- Description: In-game validation on the live Donut SMP GUI.
  Priority: High
  Status: In Progress
  Files: `src/main/java/com/kami/order/modules/KamiOrderBot.java`
  Notes: Build is verified, but server GUI behavior must be validated manually.

## Not Started

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
    sibling `KamiSpawnerDrop`
  Notes: Current reflection approach avoids adding a dependency between jars.
