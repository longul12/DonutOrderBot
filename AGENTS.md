# Agent Notes for KamiOrderBot

## Project Goal

Automate Donut SMP order delivery through Meteor Client without controlling the
system mouse. The addon should keep running while Minecraft is unfocused, as long
as the client continues ticking.

## Overall Architecture

This is a unified Meteor addon repository. `KamiOrderAddon` registers
`KamiOrderBot`, `KamiSpawnerDrop`, and `KamiSpawnerProtect` into
`Categories.Misc`. `KamiOrderBot` and `KamiSpawnerDrop` are tick-driven state
machines that interact with Minecraft GUIs through `ScreenHandler` slot clicks.

`KamiOrderBot` exposes static GUI ownership helpers used by `KamiSpawnerDrop`
through reflection:

- `tryAcquireGuiOwner`
- `isGuiOwner`
- `releaseGuiOwner`
- `currentGuiOwner`
- `shouldPreventMouseLock`

## Module List

- `kami-order-bot`: Main order automation module.
- `kami-spawner-drop`: Spawner GUI drop/sell automation module under
  `com.kami.order.modules`.
- `kami-spawner-protect`: Existing spawner protection module registered by the
  same addon entrypoint.

## Coding Rules

- Keep changes scoped to the current module unless the OrderBot/SpawnerDrop
  handoff contract requires coordinated edits.
- Preserve the tick state machine style; do not introduce blocking loops.
- Interact with Minecraft client state only on the client tick thread.
- Prefer existing helper methods in `KamiOrderBot` over new abstractions.
- Keep comments concise and useful. Existing comments include Vietnamese text.

## GUI Rules

- All slot actions must use:

```java
mc.interactionManager.clickSlot(menu.syncId, slotId, button, actionType, mc.player);
```

- Check `mc.player`, `mc.world`, `mc.interactionManager`, active module state,
  ownership, screen handler, and slot bounds before clicking.
- Use `SlotActionType.PICKUP` or `SlotActionType.QUICK_MOVE` as appropriate.
- Read `ItemStack`, name, lore, and tooltip components directly from slots.
- Do not call `Screen.mouseClicked`.
- Do not click by screen coordinates.

## Do Not Violate

- Do not use `java.awt.Robot`.
- Do not use GLFW cursor warp or `InputUtil.setCursorParameters`.
- Do not move, lock, or click the user's system mouse.
- Do not add `if (!mc.isWindowFocused()) return;` to automation flow.
- Do not keep old screen handlers or `syncId` values after a GUI closes.
- Do not let two modules own/click the GUI in the same tick.

## Build

```powershell
.\gradlew.bat build
```

Expected JAR:

```text
build/libs/kami-order-bot-0.5.6.jar
```

Release obfuscation is a separate rename-only yGuard step that runs after
Fabric Loom `remapJar`:

```powershell
.\gradlew.bat obfuscateJar
```

Obfuscated JAR:

```text
build/libs/kami-order-bot-0.5.6-obfuscated.jar
```

yGuard mapping is written to `build/yguard/yguard-map.xml`. Keep that file
private and versioned outside the public release artifact; do not commit or
publish it. Do not enable shrink, string encryption, control-flow changes, or
resource rewriting without fresh Minecraft startup testing.

Current keep rules preserve the Fabric/Meteor entrypoint
`com.kami.order.KamiOrderAddon`, mixin class
`com.kami.order.mixin.MouseLockMixin`, and module packages
`com.kami.order.modules.**` with public API members for Meteor settings, event
handlers, enum constants, and reflection.

## Debugging

- Enable `chat-feedback` in module settings for detailed chat logs.
- Watch state changes around `SEND_ORDER`, `WAIT_ORDER_LIST`, `SCAN_AND_SELECT`,
  deposit states, confirm states, and `DONE`.
- If Order resumes from Spawner but no GUI opens, inspect whether `/order` was
  sent and whether `hasOrderItemsOnPlayer()` found the selected target item.
- Confirm cross-addon resume flags:
  - `resumeOrderAfterDrop`
  - `nextActivateIsResume`

## Naming Conventions

- Addon package: `com.kami.order`
- SpawnerDrop package: `com.kami.order.modules`
- Module name: `kami-order-bot`
- Addon id: `kami-order-bot`
- Main class: `KamiOrderAddon`
- Module classes: `KamiOrderBot`, `KamiSpawnerDrop`, `KamiSpawnerProtect`
- Mixin class: `MouseLockMixin`

## Notes for Future Development

- `KamiSpawnerDrop` may restart `KamiOrderBot` if Order is stuck active after
  handing off ownership. Preserve this behavior unless replacing the whole
  handoff model.
- Keep Meteor modules with `@EventHandler` inside `com.kami.order...`; moving
  them outside the addon package can crash Meteor Orbit with
  `NoLambdaFactoryException`.
- Do not install the old standalone SpawnerDrop JAR beside this unified addon;
  that can create duplicate module registration.
- `MouseLockMixin` only cancels `Mouse.lockCursor` while a bot is running; it must
  remain free of cursor positioning calls.
- Fabric metadata uses Gradle resource expansion for `${version}`.
- SpawnerProtect `/sell` cleanup intentionally uses its own fixed tick waits
  (`sell-open-delay`, `sell-close-delay`, `sell-pickup-wait`) instead of the
  randomized module `delay`; it should shift-click all sellable inventory stacks
  into the sell GUI twice. Do not replace this with a Sell All/Dump All button
  click unless the server GUI flow is deliberately changed.
- When SpawnerProtect takes over because of a threat, it may close an existing
  OrderBot/SpawnerDrop GUI up to three times before acquiring GUI ownership.
- SpawnerDrop stop-item handling must keep the Sell All step. The intended flow
  is Sell All once, close the spawner GUI, run OrderBot one final time if target
  items remain, then wait for respawn without allowing OrderBot to enable Drop.
- OrderBot `Player_List` mode uses `order-player-list` plus one shared
  `orders-per-player` count. Keep the old `Single_Player` path as the default
  fallback behavior.
- There are no tests; build and in-game verification are both important.
