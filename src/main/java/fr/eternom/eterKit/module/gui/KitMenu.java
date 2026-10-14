package fr.eternom.eterKit.module.gui;

import fr.eternom.eterKit.module.kit.Kit;
import fr.eternom.eterKit.module.kit.KitService;
import fr.eternom.eterKit.module.kit.KitState;
import fr.eternom.eterKit.module.kit.KitState.Status;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.gui.Frame;
import fr.eternom.eterLib.helper.gui.Items;
import fr.eternom.eterLib.helper.gui.Menu;
import fr.eternom.eterLib.helper.gui.Sounds;
import fr.eternom.eterLib.helper.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu /kit, 5 lignes :
 * <pre>
 *  ▣ ▣ ▢ ▢ ☺ ▢ ▢ ▣ ▣     ☺ = joueur
 *  ▣ · · · · · · · ▣
 *  ▢ · K K K K K · ▢     kits accessibles ou à débloquer, centrés (7 au plus) : clic gauche réclamer, droit voir
 *  ▣ · · · · · · · ▣
 *  ▣ ▣ ▢ ▢ « ▢ ▢ ▣ ▣     « = retour (commande de la config) ou fermer
 * </pre>
 * Le kit de départ n'est montré qu'aux joueurs arrivés après l'installation.
 */
public class KitMenu implements Menu {

    private static final int INFO = 4;
    private static final int ROW = 18;
    private static final int MAX_KITS = 7;
    private static final int BACK = 40;

    private final KitService service;
    private final Messages messages;
    private final Player viewer;
    private final KitState state;
    private final Inventory inventory;
    private final Map<Integer, Kit> slots = new HashMap<>();

    public KitMenu(KitService service, Player viewer, KitState state) {
        this.service = service;
        this.messages = service.messages();
        this.viewer = viewer;
        this.state = state;
        this.inventory = Bukkit.createInventory(this, 45, messages.get(viewer, "menu.title"));
        render();
    }

    /** Lit l'état du joueur puis ouvre le menu. */
    public static void open(KitService service, Player player) {
        service.withState(player, state -> player.openInventory(new KitMenu(service, player, state).getInventory()));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == BACK) {
            service.backButton().click(player);
            return;
        }
        Kit kit = slots.get(slot);
        if (kit == null) {
            return;
        }
        if (click.isRightClick()) {
            Sounds.page(player);
            player.openInventory(new KitPreviewMenu(service, player, kit, this).getInventory());
        } else if (state.status(kit, player.hasPermission(kit.permission()), System.currentTimeMillis()) == Status.AVAILABLE) {
            Sounds.click(player);
            player.closeInventory();
            service.claim(player, kit, false, () -> open(service, player));
        } else {
            Sounds.refuse(player);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void render() {
        Frame.draw(inventory, Material.ORANGE_STAINED_GLASS_PANE);
        long now = System.currentTimeMillis();
        List<Kit> shown = service.catalog().all().stream()
                .filter(kit -> state.status(kit, viewer.hasPermission(kit.permission()), now) != Status.HIDDEN)
                .limit(MAX_KITS)
                .toList();
        int first = ROW + 1 + (MAX_KITS - shown.size()) / 2;
        for (int i = 0; i < shown.size(); i++) {
            Kit kit = shown.get(i);
            slots.put(first + i, kit);
            inventory.setItem(first + i, kitItem(kit, now));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(text("menu.player-lore"));
        if (service.disabledHere()) {
            lore.add(Component.empty());
            lore.add(text("menu.disabled-here"));
        }
        inventory.setItem(INFO, Items.head(viewer.getPlayerProfile(), text("menu.player", "player", viewer.getName()), lore));
        inventory.setItem(BACK, service.backButton().item(viewer));
    }

    private ItemStack kitItem(Kit kit, long now) {
        Status status = state.status(kit, viewer.hasPermission(kit.permission()), now);
        List<Component> lore = new ArrayList<>();
        lore.add(switch (status) {
            case AVAILABLE -> text("menu.status.available");
            case WAITING -> text("menu.status.waiting", "time", EterLib.get().formatDuration(viewer, state.secondsLeft(kit, now)));
            case CLAIMED -> text("menu.status.claimed");
            default -> text("menu.status.locked");
        });
        lore.add(kit.once() ? text("menu.once")
                : kit.cooldown().toHours() % 24 == 0
                ? text("menu.every-days", "days", String.valueOf(kit.cooldown().toDays()))
                : text("menu.every", "time", EterLib.get().formatDuration(viewer, kit.cooldown().toSeconds())));
        lore.add(text("menu.items", "count", String.valueOf(kit.items().size())));
        if (status == Status.LOCKED && messages.raw(viewer, "kits." + kit.id() + ".how") != null) {
            lore.add(Component.empty());
            lore.add(text("kits." + kit.id() + ".how"));
        }
        lore.add(Component.empty());
        if (status == Status.AVAILABLE) {
            lore.add(text("menu.claim"));
        }
        lore.add(text("menu.preview"));
        Material icon = status == Status.LOCKED ? Material.GRAY_DYE : status == Status.AVAILABLE ? kit.icon() : Material.CLOCK;
        return Items.item(icon, text("menu.kit-name", "kit", service.plainName(viewer, kit)), lore, status == Status.AVAILABLE);
    }

    void reopen(Player player) {
        player.openInventory(inventory);
    }

    private Component text(String key, String... placeholders) {
        return messages.get(viewer, key, placeholders);
    }
}
