package fr.eternom.eterKit.listeners;

import fr.eternom.eterKit.Main;
import fr.eternom.eterKit.module.kit.KitListener;
import org.bukkit.event.Listener;

public class Events {

    public Events(Main main) {
        // Les clics dans les menus sont gérés par EterLib, pour tous les plugins Eter.
        register(main, new KitListener(main.getKitService()));
    }

    private void register(Main main, Listener listener) {
        main.getServer().getPluginManager().registerEvents(listener, main);
    }
}
