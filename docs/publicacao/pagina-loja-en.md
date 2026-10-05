<!-- Página do mod para Modrinth e CurseForge (em inglês, o público das lojas). Colar no campo "Description".
     Trocar os caminhos dos GIFs pelos links das imagens enviadas à galeria da loja. -->

<p align="center"><img src="ICON_URL" width="128" alt="StashLink"></p>

<h1 align="center">StashLink</h1>
<p align="center"><b>Your storage, always within reach.</b><br>
Refill, store, craft and organize from the chests around you, as if everything were in your backpack.</p>

<p align="center">
<img src="https://img.shields.io/badge/Minecraft-26.3-62B47A?style=flat-square">
<img src="https://img.shields.io/badge/Fabric-supported-DBD0B4?style=flat-square">
<img src="https://img.shields.io/badge/NeoForge-supported-D7742F?style=flat-square">
<img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square">
</p>

---

## ✨ What it does

You built a storage room. Now stop walking back and forth to it.

StashLink makes the **chests, barrels and shulker boxes around you** behave like an extension of your inventory. Your hand
refills on its own, one key puts everything away, and every crafting station can pull ingredients straight from nearby storage.

![Refill and quick stack](GIF_REFILL)

## 📦 Storage within reach

| | |
|---|---|
| **Hand refill** | When your stack runs out, more comes from shulkers in your inventory and from chests, barrels and shulkers nearby. Build without stopping. |
| **Middle click** | Middle-click a block and its item comes from storage to your hotbar. It never replaces one of your items. |
| **Litematica** (Fabric) | Easy Place and pick block take blocks from your storage. The old block goes back, so your hotbar never fills up. |
| **Adjustable range** | Chests, barrels and stations: 16 blocks by default, up to 32. Placed shulkers: 32 by default, up to 64. |

## ⌨️ One key, done

| Key | Action |
|---|---|
| **N** | Stores your items in nearby chests that already have them. |
| **W** | Takes everything from the open container. |
| **Shift + left click + hover** | Moves every item your mouse passes over. Works in both directions. |
| **O** | Organize all nearby storage, with preview and undo, and search for an item. |
| **J** | Name the container you are looking at. |
| **K** | Open the StashLink settings. |
| **Alt + click** | Reserve a chest slot for one item. |

Choose what the **N** key stores: armor, tools, weapons, food and potions each have their own switch. A button inside each
chest decides if that chest receives items from N at all.

## 🛠️ Crafting stations that see your storage

![Stations](GIF_BENCH)

Crafting table, furnace, smoker, blast furnace, stonecutter, loom, cartography table, grindstone, smithing table, anvil,
enchanting table, brewing stand and beacon all use the storage around you.

- A **recipe-book style panel** with search and tabs shows **only what fits that station**.
- Missing items show in **red**. One click puts the item in the right slot.
- **Lapis lazuli** goes into the enchanting table by itself and goes back to the chest when you close it.
- The **anvil** shows only the enchanted books that fit the item you placed.
- The **brewing stand** lists every potion you can make with what you have, one step per click.
- The **beacon** takes its payment from storage with one click.

Items you left cooking or brewing are **never touched**.

## 🗂️ Organize and find

![Organize](GIF_ORGANIZE)

- **Name your chests** with the ✎ pencil or the **J** key. The name floats in front of the block, with symbols (❤ ⭐ ⚡) and
  item icons (`:apple:`, `:oak_log:`). Shulker boxes keep their name when picked up.
- **Organize** one chest with a button, or all nearby storage with a **preview** before anything moves, then **Undo** if you change your mind.
- **Search** an item and the chest that has it is **highlighted** in the world.

## 🔒 Made for servers

- **Every feature has an on/off switch and a lock**, like the difficulty lock. The switch is yours; the lock belongs to the
  server owner or operators (`/stashlink feature <name> lock|unlock`).
- **Everything that moves items runs on the server** and is checked: distance, permissions and land claims.
- Tested with 2 players, 289 containers and Carpet, with no duplication in the tested cases.
  Scanning stays well under 5 ms per action at range 32.
- **Client mode:** on servers without the mod (like Realms), N, W, hand refill and Shift + hover still work, using normal
  inventory clicks, within normal reach.

## 📥 Installation

1. Install **Fabric** (with Fabric API) or **NeoForge** for **Minecraft 26.3**.
2. Put the StashLink jar in the `mods` folder.
3. Server: install it on the server too to get the full range, stations, names, organize and locks.
4. Optional: **Litematica** (Fabric).

## ⚠️ Known limits

- Client mode and the Litematica slot swap have not been tested in-game yet.
- NeoForge has no automated tests (same code as Fabric, which is tested).
- Reserved slots don't apply to hoppers or to mods that change containers directly.
- Chest names use the game font, so colored emojis (📦) are removed.

## 💬 Feedback

Found a bug or have an idea? Open an issue on [GitHub](https://github.com/Leoascenci0/stashlink/issues).

<p align="center"><sub>MIT License · made by Leoascenci0</sub></p>
