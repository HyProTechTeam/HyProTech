package HyProTechTeam.ui;

import HyProTechTeam.BlockIdUtil;
import HyProTechTeam.HyProTechComponents;
import HyProTechTeam.HyProTechIds;
import HyProTechTeam.TieredIdUtil;
import HyProTechTeam.UpgradePersistence;
import HyProTechTeam.energy.CableUpgradeConfig;
import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.energy.EnergySide;
import HyProTechTeam.energy.EnergyUnits;
import HyProTechTeam.item.ItemNodeComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class CableUpgradePage extends InteractiveCustomUIPage<CableUpgradeEvent> {
    public enum CableType {
        ENERGY,
        ITEM
    }

    private static final String ACTION_UPGRADE = "Upgrade";
    private static final String[] UPGRADE_ROW_IDS = {
            "#UpgradeReqRow1",
            "#UpgradeReqRow2",
            "#UpgradeReqRow3",
            "#UpgradeReqRow4"
    };
    private static final String[] UPGRADE_SLOT_IDS = {
            "#UpgradeReqSlot1",
            "#UpgradeReqSlot2",
            "#UpgradeReqSlot3",
            "#UpgradeReqSlot4"
    };
    private static final String[] UPGRADE_NAME_IDS = {
            "#UpgradeReqName1",
            "#UpgradeReqName2",
            "#UpgradeReqName3",
            "#UpgradeReqName4"
    };
    private static final String[] UPGRADE_QTY_IDS = {
            "#UpgradeReqQty1",
            "#UpgradeReqQty2",
            "#UpgradeReqQty3",
            "#UpgradeReqQty4"
    };
    private static final Map<World, Map<UpgradeKey, UpgradeProgress>> UPGRADE_QUEUE = new WeakHashMap<>();

    private final Vector3i targetPos;
    private final CableType cableType;

    public CableUpgradePage(PlayerRef playerRef, Vector3i targetPos, CableType cableType) {
        super(playerRef, CustomPageLifetime.CanDismiss, CableUpgradeEvent.CODEC);
        this.targetPos = targetPos;
        this.cableType = cableType == null ? CableType.ENERGY : cableType;
    }

    @Override
    public void build(
            Ref<EntityStore> playerRef,
            UICommandBuilder uiCommandBuilder,
            UIEventBuilder uiEventBuilder,
            Store<EntityStore> store) {
        uiCommandBuilder.append("HyProTech_CableUpgrade.ui");
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#UpgradeButton",
                EventData.of("Action", ACTION_UPGRADE));
        World world = getWorld(store);
        ItemContainer inventory = getPlayerInventory(store);
        refresh(uiCommandBuilder, world, inventory);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, CableUpgradeEvent data) {
        if (data == null || data.getAction() == null) {
            return;
        }
        if (!ACTION_UPGRADE.equalsIgnoreCase(data.getAction())) {
            return;
        }

        World world = getWorld(store);
        if (world == null) {
            return;
        }

        if (cableType == CableType.ENERGY) {
            handleEnergyUpgrade(playerRef, store, world);
        } else {
            handleItemUpgrade(playerRef, store, world);
        }
    }

    private void handleEnergyUpgrade(
            Ref<EntityStore> playerRef,
            Store<EntityStore> store,
            World world) {
        EnergyNetworkSnapshot snapshot = collectEnergyNetwork(world);
        if (snapshot == null) {
            sendPlayerMessage(playerRef, store, "No cable network found.");
            return;
        }

        int currentTier = snapshot.minTier;
        if (!CableUpgradeConfig.hasNextTier(currentTier)) {
            sendPlayerMessage(playerRef, store, "Cable network is already at max tier.");
            return;
        }

        ItemContainer inventory = getPlayerInventory(store);
        if (inventory == null) {
            return;
        }

        int upgradeCount = snapshot.minTierCount > 0 ? snapshot.minTierCount : snapshot.cableCount;
        CableUpgradeConfig.Requirement[] requirements =
                CableUpgradeConfig.getUpgradeRequirements(currentTier, upgradeCount);
        Vector3i anchor = snapshot.anchor != null ? snapshot.anchor : targetPos;
        UpgradeProgress progress = getUpgradeProgress(world, CableType.ENERGY, anchor, currentTier, true);

        if (isUpgradeComplete(requirements, progress)) {
            completeEnergyUpgrade(world, snapshot, currentTier);
            clearUpgradeProgress(world, CableType.ENERGY, anchor);
            sendPlayerMessage(
                    playerRef,
                    store,
                    "Upgraded cable network to " + CableUpgradeConfig.getTierName(currentTier + 1) + ".");
            refreshAndSend(store, world);
            return;
        }

        int taken = depositUpgradeItems(inventory, requirements, progress);
        if (taken <= 0) {
            if (progress != null && progress.hasAnyPaid()) {
                sendPlayerMessage(playerRef, store, buildProgressMessage(requirements, progress));
            } else {
                sendPlayerMessage(playerRef, store, "Missing upgrade materials.");
            }
            refreshAndSend(store, world);
            return;
        }

        if (isUpgradeComplete(requirements, progress)) {
            completeEnergyUpgrade(world, snapshot, currentTier);
            clearUpgradeProgress(world, CableType.ENERGY, anchor);
            sendPlayerMessage(
                    playerRef,
                    store,
                    "Upgraded cable network to " + CableUpgradeConfig.getTierName(currentTier + 1) + ".");
        } else {
            sendPlayerMessage(playerRef, store, buildProgressMessage(requirements, progress));
        }
        refreshAndSend(store, world);
    }

    private void handleItemUpgrade(
            Ref<EntityStore> playerRef,
            Store<EntityStore> store,
            World world) {
        ItemNetworkSnapshot snapshot = collectItemNetwork(world);
        if (snapshot == null) {
            sendPlayerMessage(playerRef, store, "No cable network found.");
            return;
        }

        int currentTier = snapshot.minTier;
        if (!CableUpgradeConfig.hasNextTier(currentTier)) {
            sendPlayerMessage(playerRef, store, "Cable network is already at max tier.");
            return;
        }

        ItemContainer inventory = getPlayerInventory(store);
        if (inventory == null) {
            return;
        }

        int upgradeCount = snapshot.minTierCount > 0 ? snapshot.minTierCount : snapshot.cableCount;
        CableUpgradeConfig.Requirement[] requirements =
                CableUpgradeConfig.getUpgradeRequirements(currentTier, upgradeCount);
        Vector3i anchor = snapshot.anchor != null ? snapshot.anchor : targetPos;
        UpgradeProgress progress = getUpgradeProgress(world, CableType.ITEM, anchor, currentTier, true);

        if (isUpgradeComplete(requirements, progress)) {
            completeItemUpgrade(world, snapshot, currentTier);
            clearUpgradeProgress(world, CableType.ITEM, anchor);
            sendPlayerMessage(
                    playerRef,
                    store,
                    "Upgraded cable network to " + CableUpgradeConfig.getTierName(currentTier + 1) + ".");
            refreshAndSend(store, world);
            return;
        }

        int taken = depositUpgradeItems(inventory, requirements, progress);
        if (taken <= 0) {
            if (progress != null && progress.hasAnyPaid()) {
                sendPlayerMessage(playerRef, store, buildProgressMessage(requirements, progress));
            } else {
                sendPlayerMessage(playerRef, store, "Missing upgrade materials.");
            }
            refreshAndSend(store, world);
            return;
        }

        if (isUpgradeComplete(requirements, progress)) {
            completeItemUpgrade(world, snapshot, currentTier);
            clearUpgradeProgress(world, CableType.ITEM, anchor);
            sendPlayerMessage(
                    playerRef,
                    store,
                    "Upgraded cable network to " + CableUpgradeConfig.getTierName(currentTier + 1) + ".");
        } else {
            sendPlayerMessage(playerRef, store, buildProgressMessage(requirements, progress));
        }
        refreshAndSend(store, world);
    }

    private void refreshAndSend(Store<EntityStore> store, World world) {
        UICommandBuilder update = new UICommandBuilder();
        ItemContainer inventory = getPlayerInventory(store);
        refresh(update, world, inventory);
        sendUpdate(update);
    }

    private void refresh(UICommandBuilder update, World world, ItemContainer inventory) {
        if (world == null || targetPos == null) {
            showNoNetwork(update);
            return;
        }

        if (cableType == CableType.ENERGY) {
            EnergyNetworkSnapshot snapshot = collectEnergyNetwork(world);
            if (snapshot == null) {
                showNoNetwork(update);
                return;
            }
            update.set("#CableNetworkType.Text", "Energy Cable Network");
            update.set("#CableNetworkSize.Text", "Cables: " + snapshot.cableCount);
            update.set("#CableNetworkStats.Text", buildEnergyStats(snapshot));
            Vector3i anchor = snapshot.anchor != null ? snapshot.anchor : targetPos;
            UpgradeProgress progress = getUpgradeProgress(world, CableType.ENERGY, anchor, snapshot.minTier, false);
            int upgradeCount = snapshot.minTierCount > 0 ? snapshot.minTierCount : snapshot.cableCount;
            updateUpgradePanel(update, snapshot.minTier, snapshot.maxTier, upgradeCount, inventory, true, progress);
        } else {
            ItemNetworkSnapshot snapshot = collectItemNetwork(world);
            if (snapshot == null) {
                showNoNetwork(update);
                return;
            }
            update.set("#CableNetworkType.Text", "Item Cable Network");
            update.set("#CableNetworkSize.Text", "Cables: " + snapshot.cableCount);
            update.set("#CableNetworkStats.Text", buildItemStats(snapshot));
            Vector3i anchor = snapshot.anchor != null ? snapshot.anchor : targetPos;
            UpgradeProgress progress = getUpgradeProgress(world, CableType.ITEM, anchor, snapshot.minTier, false);
            int upgradeCount = snapshot.minTierCount > 0 ? snapshot.minTierCount : snapshot.cableCount;
            updateUpgradePanel(update, snapshot.minTier, snapshot.maxTier, upgradeCount, inventory, false, progress);
        }
    }

    private void showNoNetwork(UICommandBuilder update) {
        update.set("#CableNetworkType.Text", "Cable Network");
        update.set("#CableNetworkSize.Text", "Cables: 0");
        update.set("#CableNetworkStats.Text", "");
        update.set("#UpgradeTier.Text", "Tier: -");
        update.set("#UpgradeNextTier.Text", "Next: -");
        update.set("#UpgradeStats.Text", "");
        update.set("#UpgradeButton.Text", "No Network");
        update.set("#UpgradeReqEmpty.Text", "No cable network found.");
        update.set("#UpgradeReqEmpty.Visible", true);
        for (int i = 0; i < UPGRADE_ROW_IDS.length; i++) {
            update.set(UPGRADE_ROW_IDS[i] + ".Visible", false);
            update.set(UPGRADE_SLOT_IDS[i] + ".ItemId", "");
            update.set(UPGRADE_NAME_IDS[i] + ".Text", "");
            update.set(UPGRADE_QTY_IDS[i] + ".Text", "");
        }
    }

    private void updateUpgradePanel(
            UICommandBuilder update,
            int minTier,
            int maxTier,
            int upgradeCount,
            ItemContainer inventory,
            boolean energyCable,
            UpgradeProgress progress) {
        String tierLabel = minTier == maxTier
                ? CableUpgradeConfig.getTierName(minTier)
                : "Mixed (" + CableUpgradeConfig.getTierName(minTier)
                        + "-" + CableUpgradeConfig.getTierName(maxTier) + ")";
        update.set("#UpgradeTier.Text", "Tier: " + tierLabel);

        boolean hasNextTier = CableUpgradeConfig.hasNextTier(minTier);
        String nextText = hasNextTier
                ? "Next: " + CableUpgradeConfig.getTierName(minTier + 1)
                : "Next: Max";
        update.set("#UpgradeNextTier.Text", nextText);

        update.set("#UpgradeStats.Text", buildUpgradeStatsText(minTier, hasNextTier, energyCable));

        CableUpgradeConfig.Requirement[] requirements =
                hasNextTier ? CableUpgradeConfig.getUpgradeRequirements(minTier, upgradeCount)
                        : new CableUpgradeConfig.Requirement[0];
        boolean readyToUpgrade = hasNextTier;
        boolean hasDepositItems = false;
        for (int i = 0; i < requirements.length; i++) {
            CableUpgradeConfig.Requirement requirement = requirements[i];
            int required = requirement.getQuantity();
            int paidAmount = progress == null ? 0 : progress.getPaid(requirement.getItemId());
            if (paidAmount < required) {
                readyToUpgrade = false;
            }
            int available = inventory == null ? 0 : countItem(inventory, requirement.getItemId());
            if (paidAmount < required && available > 0) {
                hasDepositItems = true;
            }
        }

        String buttonText;
        if (!hasNextTier) {
            buttonText = "Max Tier";
        } else if (readyToUpgrade) {
            buttonText = "Upgrade";
        } else {
            buttonText = hasDepositItems ? "Deposit" : "Missing Items";
        }
        update.set("#UpgradeButton.Text", buttonText);

        update.set("#UpgradeReqEmpty.Text", "No further upgrades.");
        update.set("#UpgradeReqEmpty.Visible", !hasNextTier);
        for (int i = 0; i < UPGRADE_ROW_IDS.length; i++) {
            boolean visible = hasNextTier && i < requirements.length;
            update.set(UPGRADE_ROW_IDS[i] + ".Visible", visible);
            if (visible) {
                CableUpgradeConfig.Requirement requirement = requirements[i];
                int required = requirement.getQuantity();
                int paidAmount = progress == null ? 0 : progress.getPaid(requirement.getItemId());
                int paid = Math.min(paidAmount, Math.max(0, required));
                update.set(UPGRADE_SLOT_IDS[i] + ".ItemId", UiItemIds.safeItemId(requirement.getItemId()));
                update.set(UPGRADE_NAME_IDS[i] + ".Text", formatRequirementName(requirement.getItemId()));
                update.set(UPGRADE_QTY_IDS[i] + ".Text", paid + "/" + required);
            } else {
                update.set(UPGRADE_SLOT_IDS[i] + ".ItemId", "");
                update.set(UPGRADE_NAME_IDS[i] + ".Text", "");
                update.set(UPGRADE_QTY_IDS[i] + ".Text", "");
            }
        }
    }

    private void completeEnergyUpgrade(World world, EnergyNetworkSnapshot snapshot, int currentTier) {
        if (world == null || snapshot == null) {
            return;
        }
        int nextTier = currentTier + 1;
        String upgradedId = buildEnergyCableTieredId(nextTier);
        for (EnergyCableEntry cable : snapshot.cables) {
            if (cable.node.getCableTier() >= nextTier) {
                continue;
            }
            cable.node.setCableTier(nextTier);
            applyEnergyCableTier(cable.node, nextTier);
            cable.components.markNeedsSaving();
            if (cable.position != null) {
                queueCableUpgrade(world, cable.position, HyProTechIds.BLOCK_ENERGY_CABLE, upgradedId, cable.node, null);
            }
        }
    }

    private void completeItemUpgrade(World world, ItemNetworkSnapshot snapshot, int currentTier) {
        if (world == null || snapshot == null) {
            return;
        }
        int nextTier = currentTier + 1;
        String upgradedId = TieredIdUtil.buildTieredId(HyProTechIds.BLOCK_ITEM_CABLE, nextTier);
        for (ItemCableEntry cable : snapshot.cables) {
            if (cable.node.getCableTier() >= nextTier) {
                continue;
            }
            cable.node.setCableTier(nextTier);
            applyItemCableTier(cable.node, nextTier);
            cable.components.markNeedsSaving();
            if (cable.position != null) {
                queueCableUpgrade(world, cable.position, HyProTechIds.BLOCK_ITEM_CABLE, upgradedId, null, cable.node);
            }
        }
    }

    private boolean isUpgradeComplete(
            CableUpgradeConfig.Requirement[] requirements,
            UpgradeProgress progress) {
        if (requirements == null || requirements.length == 0) {
            return true;
        }
        if (progress == null) {
            return false;
        }
        for (CableUpgradeConfig.Requirement requirement : requirements) {
            if (requirement == null) {
                continue;
            }
            int required = requirement.getQuantity();
            if (required <= 0) {
                continue;
            }
            if (progress.getPaid(requirement.getItemId()) < required) {
                return false;
            }
        }
        return true;
    }

    private static String buildEnergyCableTieredId(int tier) {
        if (tier <= 0) {
            return HyProTechIds.BLOCK_ENERGY_CABLE;
        }
        return HyProTechIds.BLOCK_ENERGY_CABLE + "_S" + tier;
    }

    private int depositUpgradeItems(
            ItemContainer inventory,
            CableUpgradeConfig.Requirement[] requirements,
            UpgradeProgress progress) {
        if (inventory == null || requirements == null || requirements.length == 0 || progress == null) {
            return 0;
        }
        int totalTaken = 0;
        for (CableUpgradeConfig.Requirement requirement : requirements) {
            if (requirement == null) {
                continue;
            }
            String itemId = requirement.getItemId();
            int required = requirement.getQuantity();
            if (required <= 0 || itemId == null || itemId.isEmpty()) {
                continue;
            }
            int paid = progress.getPaid(itemId);
            int remaining = required - paid;
            if (remaining <= 0) {
                continue;
            }
            int available = countItem(inventory, itemId);
            int toTake = Math.min(remaining, available);
            if (toTake <= 0) {
                continue;
            }
            List<ItemStack> stacks = new ArrayList<>(1);
            stacks.add(new ItemStack(itemId, toTake));
            ListTransaction<ItemStackTransaction> transaction = inventory.removeItemStacks(stacks);
            if (transaction == null || !transaction.succeeded()) {
                continue;
            }
            progress.addPaid(itemId, toTake);
            totalTaken += toTake;
        }
        return totalTaken;
    }

    private String buildProgressMessage(
            CableUpgradeConfig.Requirement[] requirements,
            UpgradeProgress progress) {
        if (requirements == null || requirements.length == 0 || progress == null) {
            return "Upgrade progress updated.";
        }
        StringBuilder text = new StringBuilder("Upgrade progress: ");
        boolean first = true;
        for (CableUpgradeConfig.Requirement requirement : requirements) {
            if (requirement == null) {
                continue;
            }
            String itemId = requirement.getItemId();
            int required = requirement.getQuantity();
            if (required <= 0 || itemId == null || itemId.isEmpty()) {
                continue;
            }
            if (!first) {
                text.append(", ");
            }
            int paid = Math.min(progress.getPaid(itemId), Math.max(0, required));
            text.append(formatRequirementName(itemId))
                    .append(' ')
                    .append(paid)
                    .append('/')
                    .append(required);
            first = false;
        }
        return text.toString();
    }

    private static UpgradeProgress getUpgradeProgress(
            World world,
            CableType type,
            Vector3i anchor,
            int tier,
            boolean create) {
        if (world == null || type == null || anchor == null) {
            return null;
        }
        UpgradeKey key = new UpgradeKey(type, anchor);
        synchronized (UPGRADE_QUEUE) {
            Map<UpgradeKey, UpgradeProgress> byKey = UPGRADE_QUEUE.get(world);
            if (byKey == null) {
                if (!create) {
                    return null;
                }
                byKey = new HashMap<>();
                UPGRADE_QUEUE.put(world, byKey);
            }
            UpgradeProgress progress = byKey.get(key);
            if (progress == null) {
                if (!create) {
                    return null;
                }
                progress = new UpgradeProgress(tier);
                byKey.put(key, progress);
                return progress;
            }
            if (progress.tier != tier) {
                progress.reset(tier);
            }
            return progress;
        }
    }

    private static void clearUpgradeProgress(World world, CableType type, Vector3i anchor) {
        if (world == null || type == null || anchor == null) {
            return;
        }
        UpgradeKey key = new UpgradeKey(type, anchor);
        synchronized (UPGRADE_QUEUE) {
            Map<UpgradeKey, UpgradeProgress> byKey = UPGRADE_QUEUE.get(world);
            if (byKey == null) {
                return;
            }
            byKey.remove(key);
            if (byKey.isEmpty()) {
                UPGRADE_QUEUE.remove(world);
            }
        }
    }

    private static Vector3i selectAnchor(Vector3i current, Vector3i candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        if (candidate.getX() < current.getX()) {
            return candidate;
        }
        if (candidate.getX() > current.getX()) {
            return current;
        }
        if (candidate.getY() < current.getY()) {
            return candidate;
        }
        if (candidate.getY() > current.getY()) {
            return current;
        }
        if (candidate.getZ() < current.getZ()) {
            return candidate;
        }
        return current;
    }

    private String buildEnergyStats(EnergyNetworkSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        return "Stored: " + EnergyUnits.formatEnergyWithCapacity(snapshot.totalEnergy, snapshot.totalCapacity)
                + " | Max: " + snapshot.maxTransfer + " J/s";
    }

    private String buildItemStats(ItemNetworkSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        return "Max Transfer: " + snapshot.maxTransfer + " items/s";
    }

    private String buildUpgradeStatsText(int tier, boolean hasNextTier, boolean energyCable) {
        if (!hasNextTier) {
            return "Max tier reached.";
        }
        int nextTier = tier + 1;
        if (energyCable) {
            int capacity = CableUpgradeConfig.getEnergyCapacityForTier(nextTier);
            int maxTransfer = CableUpgradeConfig.getEnergyMaxTransferForTier(nextTier);
            return "Next capacity: " + capacity + " each | Max: " + maxTransfer + " J/s";
        }
        int maxTransfer = CableUpgradeConfig.getItemMaxTransferForTier(nextTier);
        return "Next max transfer: " + maxTransfer + " items/s";
    }

    private EnergyNetworkSnapshot collectEnergyNetwork(World world) {
        if (world == null || HyProTechComponents.ENERGY == null) {
            return null;
        }

        EnergyNetworkSnapshot snapshot = new EnergyNetworkSnapshot();
        Deque<Vector3i> queue = new ArrayDeque<>();
        Long2ObjectMap<IntOpenHashSet> visited = new Long2ObjectOpenHashMap<>();

        if (!markVisited(visited, targetPos.getX(), targetPos.getY(), targetPos.getZ())) {
            return null;
        }
        queue.add(targetPos);

        while (!queue.isEmpty()) {
            Vector3i pos = queue.removeFirst();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
            BlockComponentChunk components =
                    world.getChunkStore().getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
            if (components == null) {
                continue;
            }

            int localX = ChunkUtil.localCoordinate((long) pos.getX());
            int localZ = ChunkUtil.localCoordinate((long) pos.getZ());
            int blockIndex = ChunkUtil.indexBlockInColumn(localX, pos.getY(), localZ);
            EnergyNodeComponent node = components.getComponent(blockIndex, HyProTechComponents.ENERGY);
            if (node == null || node.getNodeType() != EnergyNodeComponent.NodeType.CABLE) {
                continue;
            }

            if (snapshot.cableCount == 0) {
                snapshot.cableColor = node.getCableColor();
            } else if (node.getCableColor() != snapshot.cableColor) {
                continue;
            }

            snapshot.addCable(node, components, blockIndex, new Vector3i(pos.getX(), pos.getY(), pos.getZ()));

            for (EnergySide side : EnergySide.VALUES) {
                if (!canCableConnect(node, side)) {
                    continue;
                }
                int nx = pos.getX() + side.dx();
                int ny = pos.getY() + side.dy();
                int nz = pos.getZ() + side.dz();
                if (ny < ChunkUtil.MIN_Y || ny >= ChunkUtil.HEIGHT) {
                    continue;
                }
                if (!markVisited(visited, nx, ny, nz)) {
                    continue;
                }

                long neighborChunkIndex = ChunkUtil.indexChunkFromBlock(nx, nz);
                BlockComponentChunk neighborComponents =
                        world.getChunkStore().getChunkComponent(neighborChunkIndex, BlockComponentChunk.getComponentType());
                if (neighborComponents == null) {
                    continue;
                }
                int nLocalX = ChunkUtil.localCoordinate((long) nx);
                int nLocalZ = ChunkUtil.localCoordinate((long) nz);
                int neighborIndex = ChunkUtil.indexBlockInColumn(nLocalX, ny, nLocalZ);
                EnergyNodeComponent neighbor =
                        neighborComponents.getComponent(neighborIndex, HyProTechComponents.ENERGY);
                EnergySide neighborSide = side.opposite();
                if (neighbor != null
                        && neighbor.getNodeType() == EnergyNodeComponent.NodeType.CABLE
                        && neighbor.getCableColor() == snapshot.cableColor
                        && canCableConnect(neighbor, neighborSide)) {
                    queue.add(new Vector3i(nx, ny, nz));
                }
            }
        }

        return snapshot.cableCount > 0 ? snapshot : null;
    }

    private ItemNetworkSnapshot collectItemNetwork(World world) {
        if (world == null || HyProTechComponents.ITEM == null) {
            return null;
        }

        ItemNetworkSnapshot snapshot = new ItemNetworkSnapshot();
        Deque<Vector3i> queue = new ArrayDeque<>();
        Long2ObjectMap<IntOpenHashSet> visited = new Long2ObjectOpenHashMap<>();

        if (!markVisited(visited, targetPos.getX(), targetPos.getY(), targetPos.getZ())) {
            return null;
        }
        queue.add(targetPos);

        while (!queue.isEmpty()) {
            Vector3i pos = queue.removeFirst();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
            BlockComponentChunk components =
                    world.getChunkStore().getChunkComponent(chunkIndex, BlockComponentChunk.getComponentType());
            if (components == null) {
                continue;
            }

            int localX = ChunkUtil.localCoordinate((long) pos.getX());
            int localZ = ChunkUtil.localCoordinate((long) pos.getZ());
            int blockIndex = ChunkUtil.indexBlockInColumn(localX, pos.getY(), localZ);
            ItemNodeComponent node = components.getComponent(blockIndex, HyProTechComponents.ITEM);
            if (node == null) {
                continue;
            }

            snapshot.addCable(node, components, blockIndex, new Vector3i(pos.getX(), pos.getY(), pos.getZ()));

            for (EnergySide side : EnergySide.VALUES) {
                int nx = pos.getX() + side.dx();
                int ny = pos.getY() + side.dy();
                int nz = pos.getZ() + side.dz();
                if (ny < ChunkUtil.MIN_Y || ny >= ChunkUtil.HEIGHT) {
                    continue;
                }
                if (!markVisited(visited, nx, ny, nz)) {
                    continue;
                }
                long neighborChunkIndex = ChunkUtil.indexChunkFromBlock(nx, nz);
                BlockComponentChunk neighborComponents =
                        world.getChunkStore().getChunkComponent(neighborChunkIndex, BlockComponentChunk.getComponentType());
                if (neighborComponents == null) {
                    continue;
                }
                int nLocalX = ChunkUtil.localCoordinate((long) nx);
                int nLocalZ = ChunkUtil.localCoordinate((long) nz);
                int neighborIndex = ChunkUtil.indexBlockInColumn(nLocalX, ny, nLocalZ);
                ItemNodeComponent neighbor =
                        neighborComponents.getComponent(neighborIndex, HyProTechComponents.ITEM);
                if (neighbor != null) {
                    queue.add(new Vector3i(nx, ny, nz));
                }
            }
        }

        return snapshot.cableCount > 0 ? snapshot : null;
    }

    private boolean canCableConnect(EnergyNodeComponent node, EnergySide side) {
        return node.allowsInput(side) || node.allowsOutput(side);
    }

    private boolean markVisited(Long2ObjectMap<IntOpenHashSet> visited, int x, int y, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        int localX = ChunkUtil.localCoordinate((long) x);
        int localZ = ChunkUtil.localCoordinate((long) z);
        int blockIndex = ChunkUtil.indexBlockInColumn(localX, y, localZ);

        IntOpenHashSet set = visited.get(chunkIndex);
        if (set == null) {
            set = new IntOpenHashSet();
            visited.put(chunkIndex, set);
        }
        return set.add(blockIndex);
    }

    private void applyEnergyCableTier(EnergyNodeComponent node, int tier) {
        int capacity = CableUpgradeConfig.getEnergyCapacityForTier(tier);
        node.setCapacity(capacity);
        if (node.getEnergy() > capacity) {
            node.setEnergy(capacity);
        }
        node.setMaxTransfer(CableUpgradeConfig.getEnergyMaxTransferForTier(tier));
    }

    private void applyItemCableTier(ItemNodeComponent node, int tier) {
        node.setMaxTransfer(CableUpgradeConfig.getItemMaxTransferForTier(tier));
    }

    private ItemContainer getPlayerInventory(Store<EntityStore> store) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null) {
            return null;
        }
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return null;
        }
        Inventory inventory = player.getInventory();
        return inventory == null ? null : inventory.getStorage();
    }

    private int countItem(ItemContainer container, String itemId) {
        if (container == null || itemId == null) {
            return 0;
        }
        short capacity = container.getCapacity();
        int total = 0;
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            if (itemId.equals(stack.getItemId())) {
                total += stack.getQuantity();
            }
        }
        return total;
    }

    private String formatRequirementName(String itemId) {
        if (itemId == null) {
            return "";
        }
        String name = itemId;
        String prefix = "Ingredient_Bar_";
        if (name.startsWith(prefix)) {
            name = name.substring(prefix.length()) + " Ingot";
        }
        return name.replace('_', ' ');
    }

    private void sendPlayerMessage(Ref<EntityStore> playerRef, Store<EntityStore> store, String text) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.sendMessage(Message.raw(text));
    }

    private World getWorld(Store<EntityStore> store) {
        EntityStore entityStore = store.getExternalData();
        return entityStore == null ? null : entityStore.getWorld();
    }

    private void queueCableUpgrade(
            World world,
            Vector3i pos,
            String baseId,
            String upgradedId,
            EnergyNodeComponent energyNode,
            ItemNodeComponent itemNode) {
        if (world == null || pos == null || baseId == null || upgradedId == null) {
            return;
        }
        BlockType blockType = world.getBlockType(pos.getX(), pos.getY(), pos.getZ());
        if (blockType == null || blockType.getId() == null) {
            return;
        }
        String blockId = blockType.getId();
        String resolvedUpgradedId = TieredIdUtil.applyNamespace(blockId, baseId, upgradedId);
        if (isIdOrState(blockId, resolvedUpgradedId)) {
            return;
        }
        UpgradePersistence.queueBlockSwap(world, pos, resolvedUpgradedId, energyNode, itemNode);
    }

    private boolean isIdOrState(String blockId, String baseId) {
        return BlockIdUtil.isIdOrState(blockId, baseId);
    }

    private static final class EnergyNetworkSnapshot {
        private final List<EnergyCableEntry> cables = new ArrayList<>();
        private Vector3i anchor;
        private int cableCount;
        private int minTierCount;
        private int totalEnergy;
        private int totalCapacity;
        private int maxTransfer = Integer.MAX_VALUE;
        private int cableColor;
        private int minTier = Integer.MAX_VALUE;
        private int maxTier = Integer.MIN_VALUE;

        private void addCable(
                EnergyNodeComponent node,
                BlockComponentChunk components,
                int blockIndex,
                Vector3i position) {
            cables.add(new EnergyCableEntry(node, components, blockIndex, position));
            anchor = selectAnchor(anchor, position);
            cableCount++;
            totalEnergy += node.getEnergy();
            totalCapacity += node.getCapacity();
            maxTransfer = Math.min(maxTransfer, node.getMaxTransfer());
            int tier = CableUpgradeConfig.clampTier(node.getCableTier());
            if (tier < minTier) {
                minTier = tier;
                minTierCount = 1;
            } else if (tier == minTier) {
                minTierCount++;
            }
            maxTier = Math.max(maxTier, tier);
        }
    }

    private static final class EnergyCableEntry {
        private final EnergyNodeComponent node;
        private final BlockComponentChunk components;
        private final int blockIndex;
        private final Vector3i position;

        private EnergyCableEntry(
                EnergyNodeComponent node,
                BlockComponentChunk components,
                int blockIndex,
                Vector3i position) {
            this.node = node;
            this.components = components;
            this.blockIndex = blockIndex;
            this.position = position;
        }
    }

    private static final class ItemNetworkSnapshot {
        private final List<ItemCableEntry> cables = new ArrayList<>();
        private Vector3i anchor;
        private int cableCount;
        private int minTierCount;
        private int maxTransfer = Integer.MAX_VALUE;
        private int minTier = Integer.MAX_VALUE;
        private int maxTier = Integer.MIN_VALUE;

        private void addCable(
                ItemNodeComponent node,
                BlockComponentChunk components,
                int blockIndex,
                Vector3i position) {
            cables.add(new ItemCableEntry(node, components, blockIndex, position));
            anchor = selectAnchor(anchor, position);
            cableCount++;
            maxTransfer = Math.min(maxTransfer, node.getMaxTransfer());
            int tier = CableUpgradeConfig.clampTier(node.getCableTier());
            if (tier < minTier) {
                minTier = tier;
                minTierCount = 1;
            } else if (tier == minTier) {
                minTierCount++;
            }
            maxTier = Math.max(maxTier, tier);
        }
    }

    private static final class ItemCableEntry {
        private final ItemNodeComponent node;
        private final BlockComponentChunk components;
        private final int blockIndex;
        private final Vector3i position;

        private ItemCableEntry(
                ItemNodeComponent node,
                BlockComponentChunk components,
                int blockIndex,
                Vector3i position) {
            this.node = node;
            this.components = components;
            this.blockIndex = blockIndex;
            this.position = position;
        }
    }

    private static final class UpgradeKey {
        private final CableType type;
        private final int x;
        private final int y;
        private final int z;

        private UpgradeKey(CableType type, Vector3i anchor) {
            this.type = type;
            this.x = anchor.getX();
            this.y = anchor.getY();
            this.z = anchor.getZ();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof UpgradeKey)) {
                return false;
            }
            UpgradeKey other = (UpgradeKey) obj;
            return x == other.x
                    && y == other.y
                    && z == other.z
                    && type == other.type;
        }

        @Override
        public int hashCode() {
            int result = type == null ? 0 : type.hashCode();
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }

    private static final class UpgradeProgress {
        private int tier;
        private final Map<String, Integer> paid = new HashMap<>();

        private UpgradeProgress(int tier) {
            this.tier = tier;
        }

        private int getPaid(String itemId) {
            if (itemId == null || itemId.isEmpty()) {
                return 0;
            }
            Integer value = paid.get(itemId);
            return value == null ? 0 : value;
        }

        private void addPaid(String itemId, int amount) {
            if (itemId == null || itemId.isEmpty() || amount <= 0) {
                return;
            }
            int current = getPaid(itemId);
            paid.put(itemId, current + amount);
        }

        private void reset(int newTier) {
            tier = newTier;
            paid.clear();
        }

        private boolean hasAnyPaid() {
            return !paid.isEmpty();
        }
    }
}
