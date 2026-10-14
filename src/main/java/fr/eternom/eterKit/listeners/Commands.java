package fr.eternom.eterKit.listeners;

import fr.eternom.eterKit.Main;
import fr.eternom.eterKit.module.kit.AdminCommand;
import fr.eternom.eterKit.module.kit.KitCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;

import java.util.Objects;

public class Commands {

    public Commands(Main main) {
        register(main, "kit", new KitCommand(main.getKitService(), main.getMessages()));
        register(main, "eterkit", new AdminCommand(main.getKitService(), main.getPlayerDirectory(), main.getMessages()));
    }

    private void register(Main main, String name, TabExecutor executor) {
        PluginCommand command = Objects.requireNonNull(main.getCommand(name), "Commande absente du plugin.yml : " + name);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
