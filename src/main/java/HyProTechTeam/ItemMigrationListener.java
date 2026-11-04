package HyProTechTeam;

import java.util.HashMap;
import java.util.Map;

import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

public class ItemMigrationListener {

    private static Map<String, String> replace_map = new HashMap<>();

    static {
        // Tools
        replace_map.put("HyProTech_Border_Torch",                       "HyProTech_Border_Torch");
        replace_map.put("HyProTech_Cable_Upgrade_Tool",                 "HyProTech_Cable_Upgrade_Tool");
        // Benches
        replace_map.put("HyProTech_Electrical_Workbench",               "HyProTech_Electrical_Workbench");
        // Machines
        replace_map.put("HyProTech_Metal_Plate_Press",                  "HyProTech_Press");
        replace_map.put("HyProTech_Large_Battery",                      "HyProTech_Battery_Rack_L");
        // Battery
        replace_map.put("HyProTech_Battery_Rack_S",                     "HyProTech_Battery_Rack_S"); // TMP
        replace_map.put("HyProTech_Battery",                            "HyProTech_Battery_Rack_S");
        replace_map.put("HyProTech_Battery_T1",                         "HyProTech_Battery_Rack_S_T1");
        replace_map.put("HyProTech_Battery_T2",                         "HyProTech_Battery_Rack_S_T2");
        replace_map.put("HyProTech_Battery_T3",                         "HyProTech_Battery_Rack_S_T3");
        replace_map.put("HyProTech_Battery_T4",                         "HyProTech_Battery_Rack_S_T4");
        replace_map.put("HyProTech_Battery_T5",                         "HyProTech_Battery_Rack_S_T5");
        // Electric_Furnace
        replace_map.put("HyProTech_Electric_Furnace",                   "HyProTech_Electric_Furnace");
        replace_map.put("HyProTech_Electric_Furnace_T1",                "HyProTech_Electric_Furnace_T1");
        replace_map.put("HyProTech_Electric_Furnace_T2",                "HyProTech_Electric_Furnace_T2");
        replace_map.put("HyProTech_Electric_Furnace_T3",                "HyProTech_Electric_Furnace_T3");
        replace_map.put("HyProTech_Electric_Furnace_T4",                "HyProTech_Electric_Furnace_T4");
        replace_map.put("HyProTech_Electric_Furnace_T5",                "HyProTech_Electric_Furnace_T5");
        // Quarry
        replace_map.put("HyProTech_Quarry",                             "HyProTech_Quarry");
        replace_map.put("HyProTech_Quarry_T1",                          "HyProTech_Quarry_T1");
        replace_map.put("HyProTech_Quarry_T2",                          "HyProTech_Quarry_T2");
        replace_map.put("HyProTech_Quarry_T3",                          "HyProTech_Quarry_T3");
        replace_map.put("HyProTech_Quarry_T4",                          "HyProTech_Quarry_T4");
        replace_map.put("HyProTech_Quarry_T5",                          "HyProTech_Quarry_T5");
        // Alloy_Smelter
        replace_map.put("HyProTech_Alloy_Smelter",                      "HyProTech_Alloy_Smelter");
        replace_map.put("HyProTech_Alloy_Smelter_T1",                   "HyProTech_Alloy_Smelter_T1");
        replace_map.put("HyProTech_Alloy_Smelter_T2",                   "HyProTech_Alloy_Smelter_T2");
        replace_map.put("HyProTech_Alloy_Smelter_T3",                   "HyProTech_Alloy_Smelter_T3");
        replace_map.put("HyProTech_Alloy_Smelter_T4",                   "HyProTech_Alloy_Smelter_T4");
        replace_map.put("HyProTech_Alloy_Smelter_T5",                   "HyProTech_Alloy_Smelter_T5");
        // Wind_Turbine
        replace_map.put("HyProTech_Wind_Turbine",                       "HyProTech_Wind_Turbine");
        replace_map.put("HyProTech_Wind_Turbine_T1",                    "HyProTech_Wind_Turbine_T1");
        replace_map.put("HyProTech_Wind_Turbine_T2",                    "HyProTech_Wind_Turbine_T2");
        replace_map.put("HyProTech_Wind_Turbine_T3",                    "HyProTech_Wind_Turbine_T3");
        replace_map.put("HyProTech_Wind_Turbine_T4",                    "HyProTech_Wind_Turbine_T4");
        replace_map.put("HyProTech_Wind_Turbine_T5",                    "HyProTech_Wind_Turbine_T5");
        // Solar_Panel
        replace_map.put("HyProTech_Solar_Panel",                        "HyProTech_Solar_Panel");
        replace_map.put("HyProTech_Solar_Panel_T1",                     "HyProTech_Solar_Panel_T1");
        replace_map.put("HyProTech_Solar_Panel_T2",                     "HyProTech_Solar_Panel_T2");
        replace_map.put("HyProTech_Solar_Panel_T3",                     "HyProTech_Solar_Panel_T3");
        replace_map.put("HyProTech_Solar_Panel_T4",                     "HyProTech_Solar_Panel_T4");
        replace_map.put("HyProTech_Solar_Panel_T5",                     "HyProTech_Solar_Panel_T5");
        // Ore_Crusher
        replace_map.put("HyProTech_Ore_Crusher",                        "HyProTech_Ore_Crusher");
        replace_map.put("HyProTech_Ore_Crusher_T1",                     "HyProTech_Ore_Crusher_T1");
        replace_map.put("HyProTech_Ore_Crusher_T2",                     "HyProTech_Ore_Crusher_T2");
        replace_map.put("HyProTech_Ore_Crusher_T3",                     "HyProTech_Ore_Crusher_T3");
        replace_map.put("HyProTech_Ore_Crusher_T4",                     "HyProTech_Ore_Crusher_T4");
        replace_map.put("HyProTech_Ore_Crusher_T5",                     "HyProTech_Ore_Crusher_T5");
        // Energy cables
        replace_map.put("HyProTech_Energy_Cable",                       "HyProTech_Cable_Copper");
        replace_map.put("HyProTech_Energy_Cable_T1",                    "HyProTech_Cable_Copper_S1");
        replace_map.put("HyProTech_Energy_Cable_T2",                    "HyProTech_Cable_Copper_S2");
        replace_map.put("HyProTech_Energy_Cable_T3",                    "HyProTech_Cable_Copper_S3");
        replace_map.put("HyProTech_Energy_Cable_T4",                    "HyProTech_Cable_Copper_S4");
        replace_map.put("HyProTech_Energy_Cable_T5",                    "HyProTech_Cable_Copper_S5");
        // Item cables
        replace_map.put("HyProTech_Item_Cable",                         "HyProTech_Item_Cable");
        replace_map.put("HyProTech_Item_Cable_T1",                      "HyProTech_Item_Cable_T1");
        replace_map.put("HyProTech_Item_Cable_T2",                      "HyProTech_Item_Cable_T2");
        replace_map.put("HyProTech_Item_Cable_T3",                      "HyProTech_Item_Cable_T3");
        replace_map.put("HyProTech_Item_Cable_T4",                      "HyProTech_Item_Cable_T4");
        replace_map.put("HyProTech_Item_Cable_T5",                      "HyProTech_Item_Cable_T5");
        // Item cables Fix who already use
        replace_map.put("HyProTech_Item_Cable",                           "HyProTech_Item_Cable");
        replace_map.put("HyProTech_Item_Cable_S1",                        "HyProTech_Item_Cable_T1");
        replace_map.put("HyProTech_Item_Cable_S2",                        "HyProTech_Item_Cable_T2");
        replace_map.put("HyProTech_Item_Cable_S3",                        "HyProTech_Item_Cable_T3");
        replace_map.put("HyProTech_Item_Cable_S4",                        "HyProTech_Item_Cable_T4");
        replace_map.put("HyProTech_Item_Cable_S5",                        "HyProTech_Item_Cable_T5");
        // Parts
        replace_map.put("HyProTech_Heavy_Spring",                       "HyProTech_Spring_Heavy_Iron");
        replace_map.put("HyProTech_Spring",                             "HyProTech_Spring_Iron");
        replace_map.put("HyProTech_Circuit_Board",                      "HyProTech_Printed_Circuit_Board");
        replace_map.put("HyProTech_Circuit_Board",                        "HyProTech_Printed_Circuit_Board");
        replace_map.put("HyProTech_Copper_Wire",                          "HyProTech_Wire_Copper");
        // Rods
        replace_map.put("HyProTech_Metal_Rod",                          "HyProTech_Rod_Iron");
        replace_map.put("HyProTech_Rod_Iron",                           "HyProTech_Rod_Iron");
        replace_map.put("HyProTech_Reinforced_Rod",                     "HyProTech_Rod_Reinforced_Iron");
        // Frames
        replace_map.put("HyProTech_Machine_Frame",                      "HyProTech_Frame_Iron");
        replace_map.put("HyProTech_Machine_Frame",                        "HyProTech_Frame_Iron");
        replace_map.put("HyProTech_Reinforced_Frame",                   "HyProTech_Frame_Reinforced_Iron");
        replace_map.put("HyProTech_Reinforced_Frame",                     "HyProTech_Frame_Reinforced_Iron");
        // Plates
        replace_map.put("HyProTech_Metal_Plate",                        "HyProTech_Plate_Iron");
        replace_map.put("HyProTech_Plate_Iron",                         "HyProTech_Plate_Iron");
        replace_map.put("HyProTech_Reinforced_Plate",                   "HyProTech_Plate_Reinforced_Iron");
        replace_map.put("HyProTech_Tungsten_Plate",                     "HyProTech_Plate_Tungsten");
        replace_map.put("HyProTech_Titanium_Plate",                     "HyProTech_Plate_Titanium");
        replace_map.put("HyProTech_Dense_Composite_Plate",              "HyProTech_Plate_Dense_Composite");
        // Gears   
        replace_map.put("HyProTech_Gears",                              "HyProTech_Gear_Iron");
        replace_map.put("HyProTech_Gear_Iron",                          "HyProTech_Gear_Iron");
        replace_map.put("HyProTech_Reinforced_Gears",                   "HyProTech_Gear_Reinforced_Iron");
        // Ingots   
        replace_map.put("HyProTech_Chromium_Ingot",                     "HyProTech_Ingot_Chromium");
        replace_map.put("HyProTech_Tin_Ingot",                          "HyProTech_Ingot_Tin");
        replace_map.put("HyProTech_Aluminum_Ingot",                     "HyProTech_Ingot_Aluminum");
        replace_map.put("HyProTech_Plutonium_Ingot",                    "HyProTech_Ingot_Plutonium");
        replace_map.put("HyProTech_Tungsten_Ingot",                     "HyProTech_Ingot_Tungsten");
        replace_map.put("HyProTech_Nickel_Ingot",                       "HyProTech_Ingot_Nickel");
        replace_map.put("HyProTech_Lithium_Ingot",                      "HyProTech_Ingot_Lithium");
        replace_map.put("HyProTech_Manganese_Ingot",                    "HyProTech_Ingot_Manganese");
        replace_map.put("HyProTech_Titanium_Ingot",                     "HyProTech_Ingot_Titanium");
        replace_map.put("HyProTech_Uranium_Ingot",                      "HyProTech_Ingot_Uranium");
        replace_map.put("HyProTech_Vanadium_Ingot",                     "HyProTech_Ingot_Vanadium");
        replace_map.put("HyProTech_Bronze_Blend",                       "Ingredient_Bar_Bronze");
        replace_map.put("HyProTech_Titanium_Vanadium_Alloy",            "HyProTech_Ingot_TiV");
        replace_map.put("HyProTech_Composite_Alloy_Ingot",              "HyProTech_Ingot_Composite_Alloy");
        replace_map.put("HyProTech_Alloy_Steel",                        "HyProTech_Ingot_Steel");
        replace_map.put("HyProTech_Alloy_Invar",                        "HyProTech_Ingot_Invar");
        replace_map.put("HyProTech_Alloy_Electrum",                     "HyProTech_Ingot_Electrum");
        replace_map.put("HyProTech_Alloy_Constantan",                   "HyProTech_Ingot_Constantan");
        replace_map.put("HyProTech_High_Tier_Alloy_Blend",              "HyProTech_Ingot_High_Tier_Alloy");
        replace_map.put("HyProTech_Silicon",                            "HyProTech_Ingot_Silicon");
        replace_map.put("HyProTech_Superalloy_Blend",                   "HyProTech_Ingot_Superalloy");
        // Powders
        replace_map.put("HyProTech_Silicon_Powder",                     "HyProTech_Powder_Silicon");
        replace_map.put("HyProTech_Adamantite_Powder",                  "HyProTech_Powder_Adamantite");
        replace_map.put("HyProTech_Silver_Powder",                      "HyProTech_Powder_Silver");
        replace_map.put("HyProTech_Stone_Dust",                         "HyProTech_Powder_Stone");
        replace_map.put("HyProTech_Iron_Powder",                        "HyProTech_Powder_Iron");
        replace_map.put("HyProTech_Thorium_Powder",                     "HyProTech_Powder_Thorium");
        replace_map.put("HyProTech_Tin_Powder",                         "HyProTech_Powder_Tin");
        replace_map.put("HyProTech_Titanium_Powder",                    "HyProTech_Powder_Titanium");
        replace_map.put("HyProTech_Tungsten_Powder",                    "HyProTech_Powder_Tungsten");
        replace_map.put("HyProTech_Uranium_Powder",                     "HyProTech_Powder_Uranium");
        replace_map.put("HyProTech_Vanadium_Powder",                    "HyProTech_Powder_Vanadium");
        replace_map.put("HyProTech_Nickel_Powder",                      "HyProTech_Powder_Nickel");
        replace_map.put("HyProTech_Gold_Powder",                        "HyProTech_Powder_Gold");
        replace_map.put("HyProTech_Manganese_Powder",                   "HyProTech_Powder_Manganese");
        replace_map.put("HyProTech_Copper_Powder",                      "HyProTech_Powder_Copper");
        replace_map.put("HyProTech_Lithium_Powder",                     "HyProTech_Powder_Lithium");
        replace_map.put("HyProTech_Cobalt_Powder",                      "HyProTech_Powder_Cobalt");
        replace_map.put("HyProTech_Chromium_Powder",                    "HyProTech_Powder_Chromium");
        replace_map.put("HyProTech_Aluminum_Powder",                    "HyProTech_Powder_Aluminum");
        replace_map.put("HyProTech_Alloy_Steel_Powder",                 "HyProTech_Powder_Steel");
        replace_map.put("HyProTech_Alloy_Constantan_Powder",            "HyProTech_Powder_Constantan");
        replace_map.put("HyProTech_Alloy_Electrum_Powder",              "HyProTech_Powder_Electrum");
        replace_map.put("HyProTech_Titanium_Vanadium_Alloy_Powder",     "HyProTech_Powder_TiV");
        replace_map.put("HyProTech_Composite_Alloy_Powder",             "HyProTech_Powder_Composite_Alloy");
        replace_map.put("HyProTech_Ingot_Invar_Powder",                 "HyProTech_Powder_Invar");
        replace_map.put("HyProTech_Alloy_Invar_Powder",                 "HyProTech_Powder_Invar");
        replace_map.put("HyProTech_Superalloy_Blend_Powder",            "HyProTech_Powder_Superalloy");
        replace_map.put("HyProTech_High_Tier_Alloy_Blend_Powder",       "HyProTech_Powder_High_Tier_Alloy");
        replace_map.put("HyProTech_Mithril_Powder",                     "HyProTech_Powder_Mithril");
        replace_map.put("HyProTech_Onyxium_Powder",                     "HyProTech_Powder_Onyxium");
        replace_map.put("HyProTech_Prisma_Powder",                      "HyProTech_Powder_Prisma");
        // Byproducts
        replace_map.put("HyProTech_Ore_Chips",                          "HyProTech_Ore_Chips");
        replace_map.put("HyProTech_Slag",                               "HyProTech_Slag");
        replace_map.put("HyProTech_Scrap",                              "HyProTech_Scrap");
        replace_map.put("HyProTech_Tungsten_Carbide_Chunk",             "HyProTech_Chunk_Tungsten_Carbide");
    }

    public static void onPlayerJoin(PlayerConnectEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        // TODO(0.7.5): the player's storage container can be absent while the player is still
        // connecting (on the 2026.03.x API it was always present). Skip the migration instead of
        // failing the whole PlayerConnectEvent dispatch.
        com.hypixel.hytale.server.core.inventory.Inventory playerInventory =
                event.getPlayer().getInventory();
        ItemContainer inventory = playerInventory == null ? null : playerInventory.getStorage();
        if (inventory == null) {
            return;
        }
        try {
            for (short i = 0; i < inventory.getCapacity(); i++) {
                ItemStack stack = inventory.getItemStack(i);
                if (stack == null) {
                    continue;
                }
                String oldId = stack.getItemId();
                String newId = replace_map.get(oldId);
                if (newId == null || newId.equals(oldId)) {
                    continue;
                }
                ItemStack newStack = new ItemStack(newId, stack.getQuantity(), stack.getMetadata());
                inventory.replaceItemStackInSlot(i, stack, newStack);
            }
        } catch (RuntimeException ignored) {
            // Best effort: a failed migration must never break player join.
        }
    }
}
