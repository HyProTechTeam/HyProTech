package HyProTechTeam.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Supplies windows associated with a player entity and entity store. */
public interface WindowProvider {
    /** Creates the windows for the supplied player context. */
    Window[] createWindows(Ref<EntityStore> playerEntityRef, Store<EntityStore> store);
}
