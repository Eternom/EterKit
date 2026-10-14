package fr.eternom.eterKit.module.gui;

import fr.eternom.eterKit.module.kit.Kit;
import fr.eternom.eterKit.module.kit.KitService;
import fr.eternom.eterLib.helper.gui.Frame;
import fr.eternom.eterLib.helper.gui.Items;
import fr.eternom.eterLib.helper.gui.Menu;
import fr.eternom.eterLib.helper.gui.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Contenu d'un kit, 5 lignes : les objets à l'intérieur du cadre (21 au plus), « Kits » en bas pour revenir.
 * Les clics sont annulés par EterLib : rien ne peut sortir de l'aperçu.
 */
class KitPreviewMenu implements Menu {

    private static final int[] CONTENT = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int BACK = 40;

    private final KitMenu parent;
    private final Inventory inventory;

    KitPreviewMenu(KitService service, Player viewer, Kit kit, KitMenu parent) {
        this.parent = parent;
        this.inventory = Bukkit.createInventory(this, 45,
                service.messages().get(viewer, "menu.preview-title", "kit", service.plainName(viewer, kit)));
        Frame.draw(inventory, Material.ORANGE_STAINED_GLASS_PANE);
        var items = kit.copies();
        for (int i = 0; i < items.size() && i < CONTENT.length; i++) {
            inventory.setItem(CONTENT[i], items.get(i));
        }
        inventory.setItem(BACK, Items.item(Material.OAK_DOOR, service.messages().get(viewer, "menu.back-kits"), List.of()));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == BACK) {
            Sounds.page(player);
            parent.reopen(player);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
