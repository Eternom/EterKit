package fr.eternom.eterKit.module.kit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.List;

/**
 * Un kit de la config : son icône, son délai entre deux réclamations (null : une seule fois) et ses objets.
 * newPlayersOnly : seulement pour les joueurs arrivés après l'installation (kit de départ), donné à leur arrivée.
 * Accès : permission eterkit.kit.&lt;id&gt;. Nom et explication « comment l'obtenir » : lang/ > kits.&lt;id&gt;.
 */
public record Kit(String id, Material icon, Duration cooldown, boolean newPlayersOnly, List<ItemStack> items) {

    public static final String PERMISSION_PREFIX = "eterkit.kit.";

    public String permission() {
        return PERMISSION_PREFIX + id;
    }

    public boolean once() {
        return cooldown == null;
    }

    /** Copies des objets : ceux du kit ne doivent jamais partir dans un inventaire. */
    public List<ItemStack> copies() {
        return items.stream().map(ItemStack::clone).toList();
    }
}
