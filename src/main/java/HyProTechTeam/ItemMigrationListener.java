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
        replace_map.put("Machinarium_Border_Torch",                       "HyProTech_Border_Torch");
        replace_map.put("Machinarium_Cable_Upgrade_Tool",                 "HyProTech_Cable_Upgrade_Tool");
        // Benches
        replace_map.put("Machinarium_Electrical_Workbench",               "HyProTech_Electrical_Workbench");
        // Machines
        replace_map.put("Machinarium_Metal_Plate_Press",                  "HyProTech_Press");
        replace_map.put("Machinarium_Large_Battery",                      "HyProTech_Battery_Rack_L");
        // Battery
        replace_map.put("Machinarium_Battery_Rack_S",                     "HyProTech_Battery_Rack_S"); // TMP
        replace_map.put("Machinarium_Battery",                            "HyProTech_Battery_Rack_S");
        replace_map.put("Machinarium_Battery_T1",                         "HyProTech_Battery_Rack_S_T1");
        replace_map.put("Machinarium_Battery_T2",                         "HyProTech_Battery_Rack_S_T2");
        replace_map.put("Machinarium_Battery_T3",                         "HyProTech_Battery_Rack_S_T3");
        replace_map.put("Machinarium_Battery_T4",                         "HyProTech_Battery_Rack_S_T4");
        replace_map.put("Machinarium_Battery_T5",                         "HyProTech_Battery_Rack_S_T5");
        // Electric_Furnace
        replace_map.put("Machinarium_Electric_Furnace",                   "HyProTech_Electric_Furnace");
        replace_map.put("Machinarium_Electric_Furnace_T1",                "HyProTech_Electric_Furnace_T1");
        replace_map.put("Machinarium_Electric_Furnace_T2",                "HyProTech_Electric_Furnace_T2");
        replace_map.put("Machinarium_Electric_Furnace_T3",                "HyProTech_Electric_Furnace_T3");
        replace_map.put("Machinarium_Electric_Furnace_T4",                "HyProTech_Electric_Furnace_T4");
        replace_map.put("Machinarium_Electric_Furnace_T5",                "HyProTech_Electric_Furnace_T5");
        // Quarry
        replace_map.put("Machinarium_Quarry",                             "HyProTech_Quarry");
        replace_map.put("Machinarium_Quarry_T1",                          "HyProTech_Quarry_T1");
        replace_map.put("Machinarium_Quarry_T2",                          "HyProTech_Quarry_T2");
        replace_map.put("Machinarium_Quarry_T3",                          "HyProTech_Quarry_T3");
        replace_map.put("Machinarium_Quarry_T4",                          "HyProTech_Quarry_T4");
        replace_map.put("Machinarium_Quarry_T5",                          "HyProTech_Quarry_T5");
        // Alloy_Smelter
        replace_map.put("Machinarium_Alloy_Smelter",                      "HyProTech_Alloy_Smelter");
        replace_map.put("Machinarium_Alloy_Smelter_T1",                   "HyProTech_Alloy_Smelter_T1");
        replace_map.put("Machinarium_Alloy_Smelter_T2",                   "HyProTech_Alloy_Smelter_T2");
        replace_map.put("Machinarium_Alloy_Smelter_T3",                   "HyProTech_Alloy_Smelter_T3");
        replace_map.put("Machinarium_Alloy_Smelter_T4",                   "HyProTech_Alloy_Smelter_T4");
        replace_map.put("Machinarium_Alloy_Smelter_T5",                   "HyProTech_Alloy_Smelter_T5");
        // Wind_Turbine
        replace_map.put("Machinarium_Wind_Turbine",                       "HyProTech_Wind_Turbine");
        replace_map.put("Machinarium_Wind_Turbine_T1",                    "HyProTech_Wind_Turbine_T1");
        replace_map.put("Machinarium_Wind_Turbine_T2",                    "HyProTech_Wind_Turbine_T2");
        replace_map.put("Machinarium_Wind_Turbine_T3",                    "HyProTech_Wind_Turbine_T3");
        replace_map.put("Machinarium_Wind_Turbine_T4",                    "HyProTech_Wind_Turbine_T4");
        replace_map.put("Machinarium_Wind_Turbine_T5",                    "HyProTech_Wind_Turbine_T5");
        // Solar_Panel
        replace_map.put("Machinarium_Solar_Panel",                        "HyProTech_Solar_Panel");
        replace_map.put("Machinarium_Solar_Panel_T1",                     "HyProTech_Solar_Panel_T1");
        replace_map.put("Machinarium_Solar_Panel_T2",                     "HyProTech_Solar_Panel_T2");
        replace_map.put("Machinarium_Solar_Panel_T3",                     "HyProTech_Solar_Panel_T3");
        replace_map.put("Machinarium_Solar_Panel_T4",                     "HyProTech_Solar_Panel_T4");
        replace_map.put("Machinarium_Solar_Panel_T5",                     "HyProTech_Solar_Panel_T5");
        // Ore_Crusher
        replace_map.put("Machinarium_Ore_Crusher",                        "HyProTech_Ore_Crusher");
        replace_map.put("Machinarium_Ore_Crusher_T1",                     "HyProTech_Ore_Crusher_T1");
        replace_map.put("Machinarium_Ore_Crusher_T2",                     "HyProTech_Ore_Crusher_T2");
        replace_map.put("Machinarium_Ore_Crusher_T3",                     "HyProTech_Ore_Crusher_T3");
        replace_map.put("Machinarium_Ore_Crusher_T4",                     "HyProTech_Ore_Crusher_T4");
        replace_map.put("Machinarium_Ore_Crusher_T5",                     "HyProTech_Ore_Crusher_T5");
        // Energy cables
        replace_map.put("Machinarium_Energy_Cable",                       "HyProTech_Cable_Copper");
        replace_map.put("Machinarium_Energy_Cable_T1",                    "HyProTech_Cable_Copper_S1");
        replace_map.put("Machinarium_Energy_Cable_T2",                    "HyProTech_Cable_Copper_S2");
        replace_map.put("Machinarium_Energy_Cable_T3",                    "HyProTech_Cable_Copper_S3");
        replace_map.put("Machinarium_Energy_Cable_T4",                    "HyProTech_Cable_Copper_S4");
        replace_map.put("Machinarium_Energy_Cable_T5",                    "HyProTech_Cable_Copper_S5");
        // Item cables
        replace_map.put("Machinarium_Item_Cable",                         "HyProTech_Item_Cable");
        replace_map.put("Machinarium_Item_Cable_T1",                      "HyProTech_Item_Cable_T1");
        replace_map.put("Machinarium_Item_Cable_T2",                      "HyProTech_Item_Cable_T2");
        replace_map.put("Machinarium_Item_Cable_T3",                      "HyProTech_Item_Cable_T3");
        replace_map.put("Machinarium_Item_Cable_T4",                      "HyProTech_Item_Cable_T4");
        replace_map.put("Machinarium_Item_Cable_T5",                      "HyProTech_Item_Cable_T5");
        // Item cables Fix who already use
        replace_map.put("HyProTech_Item_Cable",                           "HyProTech_Item_Cable");
        replace_map.put("HyProTech_Item_Cable_S1",                        "HyProTech_Item_Cable_T1");
        replace_map.put("HyProTech_Item_Cable_S2",                        "HyProTech_Item_Cable_T2");
        replace_map.put("HyProTech_Item_Cable_S3",                        "HyProTech_Item_Cable_T3");
        replace_map.put("HyProTech_Item_Cable_S4",                        "HyProTech_Item_Cable_T4");
        replace_map.put("HyProTech_Item_Cable_S5",                        "HyProTech_Item_Cable_T5");
        // Parts
        replace_map.put("Machinarium_Heavy_Spring",                       "HyProTech_Spring_Heavy_Iron");
        replace_map.put("Machinarium_Spring",                             "HyProTech_Spring_Iron");
        replace_map.put("Machinarium_Circuit_Board",                      "HyProTech_Printed_Circuit_Board");
        replace_map.put("HyProTech_Circuit_Board",                        "HyProTech_Printed_Circuit_Board");
        replace_map.put("HyProTech_Copper_Wire",                          "HyProTech_Wire_Copper");
        // Rods
        replace_map.put("Machinarium_Metal_Rod",                          "HyProTech_Rod_Iron");
        replace_map.put("Machinarium_Rod_Iron",                           "HyProTech_Rod_Iron");
        replace_map.put("Machinarium_Reinforced_Rod",                     "HyProTech_Rod_Reinforced_Iron");
        // Frames
        replace_map.put("Machinarium_Machine_Frame",                      "HyProTech_Frame_Iron");
        replace_map.put("HyProTech_Machine_Frame",                        "HyProTech_Frame_Iron");
        replace_map.put("Machinarium_Reinforced_Frame",                   "HyProTech_Frame_Reinforced_Iron");
        replace_map.put("HyProTech_Reinforced_Frame",                     "HyProTech_Frame_Reinforced_Iron");
        // Plates
        replace_map.put("Machinarium_Metal_Plate",                        "HyProTech_Plate_Iron");
        replace_map.put("Machinarium_Plate_Iron",                         "HyProTech_Plate_Iron");
        replace_map.put("Machinarium_Reinforced_Plate",                   "HyProTech_Plate_Reinforced_Iron");
        replace_map.put("Machinarium_Tungsten_Plate",                     "HyProTech_Plate_Tungsten");
        replace_map.put("Machinarium_Titanium_Plate",                     "HyProTech_Plate_Titanium");
        replace_map.put("Machinarium_Dense_Composite_Plate",              "HyProTech_Plate_Dense_Composite");
        // Gears   
        replace_map.put("Machinarium_Gears",                              "HyProTech_Gear_Iron");
        replace_map.put("Machinarium_Gear_Iron",                          "HyProTech_Gear_Iron");
        replace_map.put("Machinarium_Reinforced_Gears",                   "HyProTech_Gear_Reinforced_Iron");
        // Ingots   
        replace_map.put("Machinarium_Chromium_Ingot",                     "HyProTech_Ingot_Chromium");
        replace_map.put("Machinarium_Tin_Ingot",                          "HyProTech_Ingot_Tin");
        replace_map.put("Machinarium_Aluminum_Ingot",                     "HyProTech_Ingot_Aluminum");
        replace_map.put("Machinarium_Plutonium_Ingot",                    "HyProTech_Ingot_Plutonium");
        replace_map.put("Machinarium_Tungsten_Ingot",                     "HyProTech_Ingot_Tungsten");
        replace_map.put("Machinarium_Nickel_Ingot",                       "HyProTech_Ingot_Nickel");
        replace_map.put("Machinarium_Lithium_Ingot",                      "HyProTech_Ingot_Lithium");
        replace_map.put("Machinarium_Manganese_Ingot",                    "HyProTech_Ingot_Manganese");
        replace_map.put("Machinarium_Titanium_Ingot",                     "HyProTech_Ingot_Titanium");
        replace_map.put("Machinarium_Uranium_Ingot",                      "HyProTech_Ingot_Uranium");
        replace_map.put("Machinarium_Vanadium_Ingot",                     "HyProTech_Ingot_Vanadium");
        replace_map.put("Machinarium_Bronze_Blend",                       "Ingredient_Bar_Bronze");
        replace_map.put("Machinarium_Titanium_Vanadium_Alloy",            "HyProTech_Ingot_TiV");
        replace_map.put("Machinarium_Composite_Alloy_Ingot",              "HyProTech_Ingot_Composite_Alloy");
        replace_map.put("Machinarium_Alloy_Steel",                        "HyProTech_Ingot_Steel");
        replace_map.put("Machinarium_Alloy_Invar",                        "HyProTech_Ingot_Invar");
        replace_map.put("Machinarium_Alloy_Electrum",                     "HyProTech_Ingot_Electrum");
        replace_map.put("Machinarium_Alloy_Constantan",                   "HyProTech_Ingot_Constantan");
        replace_map.put("Machinarium_High_Tier_Alloy_Blend",              "HyProTech_Ingot_High_Tier_Alloy");
        replace_map.put("Machinarium_Silicon",                            "HyProTech_Ingot_Silicon");
        replace_map.put("Machinarium_Superalloy_Blend",                   "HyProTech_Ingot_Superalloy");
        // Powders
        replace_map.put("Machinarium_Silicon_Powder",                     "HyProTech_Powder_Silicon");
        replace_map.put("Machinarium_Adamantite_Powder",                  "HyProTech_Powder_Adamantite");
        replace_map.put("Machinarium_Silver_Powder",                      "HyProTech_Powder_Silver");
        replace_map.put("Machinarium_Stone_Dust",                         "HyProTech_Powder_Stone");
        replace_map.put("Machinarium_Iron_Powder",                        "HyProTech_Powder_Iron");
        replace_map.put("Machinarium_Thorium_Powder",                     "HyProTech_Powder_Thorium");
        replace_map.put("Machinarium_Tin_Powder",                         "HyProTech_Powder_Tin");
        replace_map.put("Machinarium_Titanium_Powder",                    "HyProTech_Powder_Titanium");
        replace_map.put("Machinarium_Tungsten_Powder",                    "HyProTech_Powder_Tungsten");
        replace_map.put("Machinarium_Uranium_Powder",                     "HyProTech_Powder_Uranium");
        replace_map.put("Machinarium_Vanadium_Powder",                    "HyProTech_Powder_Vanadium");
        replace_map.put("Machinarium_Nickel_Powder",                      "HyProTech_Powder_Nickel");
        replace_map.put("Machinarium_Gold_Powder",                        "HyProTech_Powder_Gold");
        replace_map.put("Machinarium_Manganese_Powder",                   "HyProTech_Powder_Manganese");
        replace_map.put("Machinarium_Copper_Powder",                      "HyProTech_Powder_Copper");
        replace_map.put("Machinarium_Lithium_Powder",                     "HyProTech_Powder_Lithium");
        replace_map.put("Machinarium_Cobalt_Powder",                      "HyProTech_Powder_Cobalt");
        replace_map.put("Machinarium_Chromium_Powder",                    "HyProTech_Powder_Chromium");
        replace_map.put("Machinarium_Aluminum_Powder",                    "HyProTech_Powder_Aluminum");
        replace_map.put("Machinarium_Alloy_Steel_Powder",                 "HyProTech_Powder_Steel");
        replace_map.put("Machinarium_Alloy_Constantan_Powder",            "HyProTech_Powder_Constantan");
        replace_map.put("Machinarium_Alloy_Electrum_Powder",              "HyProTech_Powder_Electrum");
        replace_map.put("Machinarium_Titanium_Vanadium_Alloy_Powder",     "HyProTech_Powder_TiV");
        replace_map.put("Machinarium_Composite_Alloy_Powder",             "HyProTech_Powder_Composite_Alloy");
        replace_map.put("Machinarium_Ingot_Invar_Powder",                 "HyProTech_Powder_Invar");
        replace_map.put("Machinarium_Alloy_Invar_Powder",                 "HyProTech_Powder_Invar");
        replace_map.put("Machinarium_Superalloy_Blend_Powder",            "HyProTech_Powder_Superalloy");
        replace_map.put("Machinarium_High_Tier_Alloy_Blend_Powder",       "HyProTech_Powder_High_Tier_Alloy");
        replace_map.put("Machinarium_Mithril_Powder",                     "HyProTech_Powder_Mithril");
        replace_map.put("Machinarium_Onyxium_Powder",                     "HyProTech_Powder_Onyxium");
        replace_map.put("Machinarium_Prisma_Powder",                      "HyProTech_Powder_Prisma");
        // Byproducts
        replace_map.put("Machinarium_Ore_Chips",                          "HyProTech_Ore_Chips");
        replace_map.put("Machinarium_Slag",                               "HyProTech_Slag");
        replace_map.put("Machinarium_Scrap",                              "HyProTech_Scrap");
        replace_map.put("Machinarium_Tungsten_Carbide_Chunk",             "HyProTech_Chunk_Tungsten_Carbide");
    }

    public static void onPlayerJoin(PlayerConnectEvent event) {
/*         if (event.isCancelled()) {
            return;
        } */
        // Получаем инвентарь игрока
        ItemContainer inventory = event.getPlayer().getInventory().getStorage();
        
        // Проходим по всем слотам
        for (short i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItemStack(i);
            
            // Если слот не пуст
            if (stack != null) {
                String OLD_ID = stack.getItemId();
                // Проверяем наличие устаревшего ID
                if( replace_map.containsKey( OLD_ID ) == true ) {
                    // Создаем новый стак с тем же количеством и метаданными
                    ItemStack newStack = new ItemStack(
                        replace_map.get( OLD_ID ), 
                        stack.getQuantity(), 
                        stack.getMetadata()
                    );
                    
                    // Устанавливаем новый предмет в тот же слот
                    inventory.replaceItemStackInSlot(i, stack, newStack);
                }
            }
        }
    }
}
