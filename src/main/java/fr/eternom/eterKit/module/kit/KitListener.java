package fr.eternom.eterKit.module.kit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** À l'arrivée : le kit de départ d'un nouveau joueur (une fois son inventaire chargé). */
public class KitListener implements Listener {

    private final KitService service;

    public KitListener(KitService service) {
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.welcome(event.getPlayer());
    }
}
