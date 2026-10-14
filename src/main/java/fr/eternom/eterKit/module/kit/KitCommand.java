package fr.eternom.eterKit.module.kit;

import fr.eternom.eterKit.module.gui.KitMenu;
import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /kit : le menu des kits ; /kit &lt;kit&gt; : réclame ce kit directement. */
public class KitCommand implements TabExecutor {

    private final KitService service;
    private final Messages messages;

    public KitCommand(KitService service, Messages messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        if (args.length == 0) {
            KitMenu.open(service, player);
            return true;
        }
        service.catalog().get(args[0]).ifPresentOrElse(
                kit -> service.claim(player, kit, false, () -> {
                }),
                () -> messages.send(player, "kit.unknown", "kit", args[0]));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String start = args[0].toLowerCase(Locale.ROOT);
        return service.catalog().all().stream()
                .filter(kit -> sender.hasPermission(kit.permission()) && !kit.newPlayersOnly())
                .map(Kit::id)
                .filter(id -> id.startsWith(start))
                .toList();
    }
}
