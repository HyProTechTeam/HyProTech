# Resource and configuration logic

The Java and `.ui` files contain the executable and interface logic. The asset
files below are data consumed by Hytale or another plugin. Standard JSON does
not support comments, so explanations belong here rather than inside the JSON.
An asset's exact values remain in that asset; this guide describes how to read
the recurring structures without changing their format.

## Plugin manifest

`src/main/resources/manifest.json` identifies the plugin, its entry point,
server version, dependencies and whether it includes an asset pack. The
`Main` value points to `HyProTechTeam.HyProTech`, which registers components,
systems, interactions and pages during setup.

## Items, blocks and recipes

`src/main/resources/Server/Item/Items/` contains item and block definitions
grouped by purpose, including generators, machines, transfer cables, ores and
materials. Definitions can include translation keys, icons, crafting recipes,
interactions and block-model settings. A `BlockEntity.Components` object
provides initial component data for a placed block; runtime systems can then
update and persist that state. For example, the base solar-panel asset defines
an energy-node role, capacity, generation and directional masks.

Recipes may be embedded in an item definition or stored under
`src/main/resources/Server/Item/Recipes/`. Recipe inputs specify item or resource
requirements and quantities; outputs, bench requirements and processing time
describe the result and where it can be produced. The bronze ingot recipe, for
example, requires copper and tin and names the alloy smelter as its processing
bench. The Java recipe-discovery classes determine which recipes a machine UI
actually lists.

`src/main/resources/Server/Item/Block/Hitboxes/` defines block collision and
interaction shapes. These assets describe geometry, rather than the machine
processing logic.

## Interactions and UI

`src/main/resources/Server/Item/Interactions/` and `RootInteractions/` define
asset-side interaction entry points. An interaction can select a custom page
by ID; the plugin registers the matching page factory, whose Java class binds
events and updates the page. The `.ui` files under `Common/UI/Custom/` define
the visual layout and control selectors used by those Java page classes.

## Other resource groups

`Common/Audio/SoundEvents/` contains sound event assets referenced by the sound
helper. `Server/Drops/` contains drop definitions, and language files under
`Common/Languages/` and `Server/Languages/` provide localized strings. The XML
file `Ore_Gen/CustomOreTypes.xml` defines ore spawn parameters and maps ore
variants to host rocks; its entries are documented in the XML itself.

`Placeholders/` contains additional item definitions and backup interaction
assets. Their presence in that folder does not by itself establish that they
are loaded at runtime. Block models, animations, textures and audio are media
assets; their formats are not Java source files. The `HyProTech-Resources`
submodule contains Blockbench model files and a license at the pinned revision.
