<p align="center">
  <img src="./README/icon.png" alt="HyProTech Logo" width="800">
</p>

<h1 align="center">
  HyProTech <br>
  <sub>Modern industrial progression for Hytale — energy, item networks, machines & UI</sub>
  <br><br>
  <a href="https://github.com/YoofeCZ/HyProTech/releases">
    <img src="https://img.shields.io/github/v/release/YoofeCZ/HyProTech?label=Release&style=flat" alt="Release">
  </a>
  <a href="https://github.com/YoofeCZ/HyProTech/issues">
    <img src="https://img.shields.io/github/issues/YoofeCZ/HyProTech?label=Issues&style=flat" alt="Issues">
  </a>
  <a href="https://github.com/YoofeCZ/HyProTech/stargazers">
    <img src="https://img.shields.io/github/stars/YoofeCZ/HyProTech?label=Stars&style=flat" alt="Stars">
  </a>
  <a href="https://discord.gg/Mjmj8gmAPT">
    <img src="https://img.shields.io/badge/Discord-Join-5865F2?style=flat&logo=discord&logoColor=white" alt="Discord">
  </a>
  <a href="https://www.curseforge.com/hytale/mods/hyprotech">
    <img src="https://img.shields.io/curseforge/dt/1439189?logo=curseforge&label=&suffix=%20&style=flat&color=242629&labelColor=F16436&logoColor=1C1C1C" alt="CurseForge">
  </a>
  <br><br>
</h1>
  <br><br>
</h1>

<p>
  <b>HyProTech</b> is a Hytale mod/plugin focused on scalable automation:
  <b>energy networks</b>, <b>item transport</b>, <b>machines</b> (Quarry / Electric Furnace),
  <b>tiered upgrades</b>, and <b>custom UI</b>.
</p>

<p>
  The goal is to keep gameplay <b>system-driven</b>: build networks, route resources, scale throughput,
  and upgrade tiers from early-game to end-game.
</p>

<p>&nbsp;</p>

---

## ✅ What’s implemented (current code + resources)

This section reflects the actual state of the project based on:
`src/main/java` + `src/main/resources`.

### 🧠 Systems (code)

- **Core plugin**
  - Registers components/systems/interactions, custom pages, commands
  - Hooks into break/place for upgrade persistence  
  **Files:** `HyProTech.java`, `HyProTechCommand.java`

- **Energy**
  - `EnergyNodeComponent` types: `SOLAR`, `CABLE`, `BATTERY`, `MACHINE`, `FURNACE`
  - `EnergyNetworkSystem`: tick-based transfer, cable networks, node colors, side modes,
    solar output based on daylight, furnace consumption
  - Integration with **EnergyStorage API** (`EnergyNodeStorage`, `EnergyUnits`)  
  **Path:** `src/main/java/com/example/plugin/energy/*`

- **Item Transport**
  - `ItemNodeComponent`: modes, side configuration, filters, targets (`Input/Fuel/Output`)
  - `ItemNetworkSystem`: network building + transfer between containers / processing bench
  - Transfer rate per tier  
  **Path:** `src/main/java/com/example/plugin/item/*`

- **Machines**
  - `MachineSystem` + `MachineRegistry/Definition` + `MasterMachine`
  - Implemented machine: **Quarry**
    - mining area, energy consumption, drops, storage
    - `QuarryAreaManager` (visual border)
    - `MachineItemAccess`  
  **Path:** `src/main/java/com/example/plugin/machine/*`

- **Upgrades / Persistence**
  - `TieredIdUtil`, `UpgradePersistence` (break/place metadata, block swap, tiered drops)
  - Tier configs: **battery/solar/cable/quarry**  
  **Files:** `UpgradePersistence.java`, `*UpgradeConfig.java`, `QuarryConfig.java`

- **Interactions + UI**
  - **CableSideTool**: toggle side input/output
  - **CableNetworkUpgrade**: UI for upgrading networks
  - **OpenPoweredBench / OpenCustomUIWithWindows**
  - UI pages: Battery / Solar / Cable / Item Cable / Furnace / Quarry + HUD/tooltips  
  **Paths:** `src/main/java/com/example/plugin/interaction/*`, `src/main/java/com/example/plugin/ui/*`

---

### 🎨 Content / Assets (resources)

- **Server item JSON**
  - Battery **T0–T5**
  - Electric Furnace **T0–T5**
  - Solar Panel **T0–T5**
  - Quarry **T0–T5** + `Quarry_Border`
  - Energy Cable **T0–T5**
  - Item Cable **T0–T5**
  - Electrical Workbench
  - Cable Tool + Cable Upgrade Tool  
  **Path:** `src/main/resources/Server/Item/Items`

- **Models / Textures**
  - HyProTech blocks: Alloy Smelter, BasicBattery, border, cable tiers,
    ElectricalWorkbench, ElectricFurnace, OreCrusher, Quarry, SolarPanel  
  **Paths:**  
  `src/main/resources/Common/Blocks/HyProTech`  
  `src/main/resources/Common/BlockTextures`

- **Generated item icons**
  - Batteries / Solars / Furnace / Cables / Quarry / Wrench  
  **Path:** `src/main/resources/Common/Icons/ItemsGenerated`

- **Custom UI layouts**
  - Battery / Cable / CableUpgrade / EmptyHud / Furnace / FurnaceHud
  - Item_Cable / Quarry / Solar / SolarHud  
  **Path:** `src/main/resources/Common/UI/Custom`

- **Localization**
  - `items.lang`, `server.lang`, `fallback.lang` (Common + Server)  
  **Notes:** item names + descriptions

---

## ⚠️ Coverage / Known mismatches (current status)

- **HyProTech_Cable_Tool**
  - has server item JSON, **but missing Icon/Model/Texture** → asset mismatch

- **Alloy Smelter + Ore Crusher**
  - have models/textures, **but no matching item JSON and no code registration found**
  - currently treated as **assets-only** (not usable in-game yet)

---

## 🗺️ Project structure (high-level)

- `src/main/java/com/example/plugin/energy/*` — energy nodes + network system
- `src/main/java/com/example/plugin/item/*` — item nodes + item network system
- `src/main/java/com/example/plugin/machine/*` — machine framework + quarry
- `src/main/java/com/example/plugin/interaction/*` — tools/interactions
- `src/main/java/com/example/plugin/ui/*` — pages/windows/HUD
- `src/main/resources/Server/Item/Items` — item json definitions
- `src/main/resources/Common/*` — block models, textures, icons, UI, localization

---

## 🧩 Roadmap (near-term)

- Fix Cable Tool asset mismatch (icon/model/texture)
- Register Alloy Smelter + Ore Crusher as actual items/blocks (or remove assets)
- Improve distribution logic / priority UI for item networks

---

## 🐞 Issues / Feedback

- Report bugs: https://github.com/YoofeCZ/HyProTech/issues or   <a href="https://discord.gg/Mjmj8gmAPT">
    <img src="https://img.shields.io/badge/Discord-Join-5865F2?style=flat&logo=discord&logoColor=white" alt="Discord">
  </a>
