# KamiOrderBot

Meteor Client addon for automating the Donut SMP order GUI.

## Overview

`KamiOrderBot` is a Fabric/Meteor Client addon for Minecraft `1.21.11`.
It registers one Meteor module in `Categories.Misc`: `kami-order-bot`.

The module sends `/order`, scans the opened order container, parses item
names/lore for price and delivery progress, chooses a suitable order, moves
matching inventory items into the order with `clickSlot`, and confirms delivery.

This repository is separate from `KamiSpawnerDrop`, but the two addons cooperate
through Meteor's `Modules` registry and static/reflection-based flags.

## Current Features

- Select target item with Meteor `ItemListSetting`.
- Optional manual item name mode.
- Sends `/order <item-or-player-name>`.
- Parses order lore for price, delivered count, total count, remaining amount,
  and owner name where available.
- Filters by price and minimum remaining amount.
- Chooses orders by highest, lowest, or balanced scoring mode.
- Deposits matching items with `SlotActionType.QUICK_MOVE`.
- Confirms delivery with tooltip/color/fallback slot detection.
- Can trigger `KamiSpawnerDrop` when target items are depleted.
- Includes GUI ownership guards shared with SpawnerDrop.
- Includes a `MouseLockMixin` that prevents Minecraft from grabbing the mouse
  while a bot is running. It does not warp or click the system cursor.

## Architecture

- Entrypoint: `com.kami.order.KamiOrderAddon`
- Module: `com.kami.order.modules.KamiOrderBot`
- Mixin: `com.kami.order.mixin.MouseLockMixin`
- Fabric metadata: `src/main/resources/fabric.mod.json`
- Mixin metadata: `src/main/resources/kami-order-bot.mixins.json`

The module is implemented as a tick-driven state machine using Meteor
`TickEvent.Pre`. GUI work is done through the current `ScreenHandler`.

## Directory Structure

```text
src/main/java/com/kami/order/KamiOrderAddon.java
src/main/java/com/kami/order/modules/KamiOrderBot.java
src/main/java/com/kami/order/mixin/MouseLockMixin.java
src/main/resources/fabric.mod.json
src/main/resources/kami-order-bot.mixins.json
src/main/resources/assets/kamiorder/icon.png
build.gradle.kts
gradle.properties
```

## Environment

- Minecraft: `1.21.11`
- Yarn mappings: `1.21.11+build.6`
- Fabric Loader: `0.16.14`
- Meteor Client: `1.21.11-SNAPSHOT`
- Java: `21`
- Gradle wrapper: configured in `gradle/wrapper/gradle-wrapper.properties`

## Build

On Windows:

```powershell
.\gradlew.bat build
```

Output JAR:

```text
build/libs/kami-order-bot-0.3.5.jar
```

## Maintainer Release Build

The normal Fabric Loom build remains unchanged for development and testing:

```powershell
.\gradlew.bat clean build
```

To create the release obfuscated JAR, run the separate yGuard task:

```powershell
.\gradlew.bat clean obfuscateJar
```

Artifacts:

```text
build/libs/kami-order-bot-0.3.5.jar
build/libs/kami-order-bot-0.3.5-obfuscated.jar
```

yGuard mapping for crash-log reading is written locally to:

```text
build/yguard/yguard-map.xml
```

Do not publish or commit the mapping file. The current obfuscation is
rename-only after Loom `remapJar`; shrink, string encryption, control-flow
obfuscation, and resource rewriting are intentionally not enabled.

## Run

Copy the built JAR into the Minecraft mods folder with Meteor Client and Fabric
Loader installed. In Meteor, enable the module:

```text
Misc -> kami-order-bot
```

Configure `target-item`, order naming settings, price filters, loop/drop options,
and confirm-slot settings before running.

## Dependencies

Declared in `build.gradle.kts` and `gradle.properties`:

- `net.fabricmc:fabric-loader`
- `meteordevelopment:meteor-client`
- Minecraft and Yarn mappings through Fabric Loom

No Fabric API dependency is declared in this repository.

## Current Project Status

Version `0.3.5` builds successfully. The latest verified work added background
operation support, GUI ownership coordination, mouse-lock prevention while bots
run, and safer resume behavior after `KamiSpawnerDrop` finishes.

## Known Limitations

- No server-side test harness exists in this repository.
- Order GUI parsing depends on item names and lore exposed by the server.
- Confirm button detection uses tooltip/color/fallback slot logic and may need
  adjustment if the server changes its GUI.
- The background behavior still depends on Minecraft continuing to tick; disable
  Pause on Lost Focus and background throttling mods if needed.
- Keybinds are not hardcoded by this addon. Use Meteor's module bind UI.
