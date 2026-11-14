package HyProTechTeam.ui;

import HyProTechTeam.changelog.ChangelogManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEventType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Method;

/** Displays changelog text and records when the player dismisses the page. */
public final class ChangelogPage extends InteractiveCustomUIPage<ChangelogEvent> {
    private static final String PAGE_LAYOUT = "HyProTech_Changelog.ui";
    private static final String ACTION_CLOSE = "Close";

    private final ChangelogManager changelogManager;

    /** Creates a dismissible page using the changelog event codec. */
    public ChangelogPage(PlayerRef playerRef, ChangelogManager changelogManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, ChangelogEvent.CODEC);
        this.changelogManager = changelogManager;
    }

    /** Loads the layout, fills its text, and binds the close button. */
    @Override
    public void build(
            Ref<EntityStore> playerRef,
            UICommandBuilder uiCommandBuilder,
            UIEventBuilder uiEventBuilder,
            Store<EntityStore> store) {
        uiCommandBuilder.append(PAGE_LAYOUT);
        uiCommandBuilder.set("#ChangelogText.Value", ChangelogManager.CHANGELOG_TEXT);
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Action", ACTION_CLOSE));
    }

    /** Marks the changelog seen on the close action and attempts to dismiss the page. */
    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, ChangelogEvent data) {
        if (data == null || data.getAction() == null) {
            return;
        }
        if (!ACTION_CLOSE.equalsIgnoreCase(data.getAction())) {
            return;
        }
        if (changelogManager != null) {
            changelogManager.markSeen(getPlayerUuid());
        }
        try {
            close();
            return;
        } catch (Throwable ignored) {
        }
        dismiss(playerRef, store);
    }

    /** Marks the changelog seen when the page is dismissed by another route. */
    @Override
    public void onDismiss(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        if (changelogManager != null) {
            changelogManager.markSeen(getPlayerUuid());
        }
        super.onDismiss(playerRef, store);
    }

    /** Tries compatible page-manager close methods, then sends a dismiss event as a fallback. */
    private void dismiss(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        if (playerRef == null || store == null) {
            return;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        PageManager pageManager = player.getPageManager();
        if (pageManager == null) {
            return;
        }
        if (tryClosePage(pageManager, playerRef, store)) {
            return;
        }
        try {
            pageManager.handleEvent(
                    playerRef,
                    store,
                    new CustomPageEvent(CustomPageEventType.Dismiss, ""));
        } catch (Throwable ignored) {
            // Avoid leaving the client in a loading state if dismissal fails.
            sendUpdate(new UICommandBuilder());
        }
    }

    /** Returns the page player's UUID, or null when no player reference is available. */
    private java.util.UUID getPlayerUuid() {
        return playerRef == null ? null : playerRef.getUuid();
    }

    /** Tries known close method names and argument combinations for API compatibility. */
    private boolean tryClosePage(PageManager pageManager, Ref<EntityStore> playerRef, Store<EntityStore> store) {
        if (pageManager == null) {
            return false;
        }
        String[] methods = {
                "closeCustomPage",
                "dismissCustomPage",
                "closePage",
                "dismissPage",
                "close",
                "dismiss"
        };
        for (String method : methods) {
            if (invokePageMethod(pageManager, method, playerRef, store)) {
                return true;
            }
            if (invokePageMethod(pageManager, method, playerRef)) {
                return true;
            }
            if (invokePageMethod(pageManager, method)) {
                return true;
            }
        }
        return false;
    }

    /** Invokes the first compatible named overload that succeeds, returning whether one worked. */
    private boolean invokePageMethod(PageManager pageManager, String name, Object... args) {
        if (pageManager == null || name == null) {
            return false;
        }
        Method[] methods = pageManager.getClass().getMethods();
        for (Method method : methods) {
            if (!name.equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length != args.length) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < params.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    continue;
                }
                if (!params[i].isAssignableFrom(arg.getClass())) {
                    matches = false;
                    break;
                }
            }
            if (!matches) {
                continue;
            }
            try {
                method.invoke(pageManager, args);
                return true;
            } catch (Exception ignored) {
                // Try next overload.
            }
        }
        return false;
    }
}
