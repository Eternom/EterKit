package fr.eternom.eterKit.module.kit;

import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterLib.module.player.PlayerDirectory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * /eterkit (eterkit.admin, console comprise) :
 * give &lt;joueur&gt; &lt;kit&gt; : donne le contenu à un joueur connecté sur ce serveur, sans délai ni permission ;
 * reset &lt;joueur&gt; [kit] : efface ses réclamations (d'un kit, ou de tous), pour qu'il puisse réclamer de nouveau.
 */
public class AdminCommand implements TabExecutor {

    private final KitService service;
    private final PlayerDirectory players;
    private final Messages messages;

    public AdminCommand(KitService service, PlayerDirectory players, Messages messages) {
        this.service = service;
        this.players = players;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (action.equals("give") && args.length == 3) {
            give(sender, args[1], args[2]);
        } else if (action.equals("reset") && (args.length == 2 || args.length == 3)) {
            reset(sender, args[1], args.length == 3 ? args[2] : null);
        } else {
            messages.send(sender, "admin.usage");
        }
        return true;
    }

    private void give(CommandSender sender, String name, String kitId) {
        Optional<Kit> kit = service.catalog().get(kitId);
        if (kit.isEmpty()) {
            messages.send(sender, "kit.unknown", "kit", kitId);
            return;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            messages.send(sender, "admin.not-here", "player", name);
            return;
        }
        service.giveFree(target, kit.get());
        messages.send(sender, "admin.given", "player", target.getName(), "kit", service.plainName(target, kit.get()));
    }

    private void reset(CommandSender sender, String name, String kitId) {
        Kit kit = null;
        if (kitId != null) {
            Optional<Kit> found = service.catalog().get(kitId);
            if (found.isEmpty()) {
                messages.send(sender, "kit.unknown", "kit", kitId);
                return;
            }
            kit = found.get();
        }
        Kit chosen = kit;
        // Recherche dans l'annuaire du réseau (base) : en tâche de fond
        Tasks.async(service.plugin(), () -> {
            Optional<PlayerDirectory.NetworkPlayer> target = players.find(name);
            Bukkit.getScheduler().runTask(service.plugin(), () -> {
                if (target.isEmpty()) {
                    messages.send(sender, "player.unknown", "player", name);
                    return;
                }
                service.reset(target.get().uuid(), chosen, removed -> messages.send(sender,
                        chosen == null ? "admin.reset-all" : "admin.reset",
                        "player", target.get().name(), "kit", chosen == null ? "" : chosen.id()));
            });
        }, "Recherche du joueur " + name + " impossible");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        Stream<String> options = switch (args.length) {
            case 1 -> Stream.of("give", "reset");
            case 2 -> Bukkit.getOnlinePlayers().stream().map(Player::getName);
            case 3 -> service.catalog().all().stream().map(Kit::id);
            default -> Stream.empty();
        };
        return options.filter(option -> option.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
