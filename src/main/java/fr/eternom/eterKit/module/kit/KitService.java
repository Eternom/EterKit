package fr.eternom.eterKit.module.kit;

import fr.eternom.eterKit.module.kit.KitState.Status;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.gui.BackButton;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterLib.module.player.PlayerDirectory;
import fr.eternom.eterSync.api.SyncApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Réclamer un kit, partout sur le réseau sauf sur les serveurs exclus (prison) : là où le joueur se trouve, dans son
 * inventaire. Jamais pendant le chargement de l'inventaire par EterSync (il serait écrasé), jamais s'il manque de
 * place (le kit n'est pas consommé, le joueur sait combien de cases libérer).
 *
 * La base enregistre d'abord (une seule réclamation possible, même sur deux serveurs à la fois), puis le kit est donné.
 * Kit de départ (new-players-only) : donné de lui-même à l'arrivée d'un nouveau joueur, une fois son inventaire chargé.
 */
public class KitService {

    /** Attente maximale du chargement de l'inventaire à l'arrivée (en vérifiant toutes les demi-secondes). */
    private static final int LOAD_CHECKS = 60;

    private final JavaPlugin plugin;
    private final KitCatalog catalog;
    private final KitStore store;
    private final PlayerDirectory players;
    private final Messages messages;
    private final BackButton backButton;
    private final boolean disabledHere;

    public KitService(JavaPlugin plugin, KitCatalog catalog, KitStore store, PlayerDirectory players, Messages messages,
                      BackButton backButton, List<String> disabledServers, String serverName) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.store = store;
        this.players = players;
        this.messages = messages;
        this.backButton = backButton;
        String server = serverName.toLowerCase(Locale.ROOT);
        this.disabledHere = disabledServers.stream().anyMatch(prefix -> server.startsWith(prefix.toLowerCase(Locale.ROOT)));
    }

    /** Lit l'état du joueur en tâche de fond, puis le donne sur le thread principal. */
    public void withState(Player player, Consumer<KitState> then) {
        UUID uuid = player.getUniqueId();
        Tasks.async(plugin, player, () -> state(uuid), then, () -> messages.send(player, "error.generic"));
    }

    /** Bloquant. Un joueur pas encore dans l'annuaire vient d'arriver : il est nouveau. */
    private KitState state(UUID uuid) {
        long firstSeen = players.get(uuid).map(PlayerDirectory.NetworkPlayer::firstSeen).orElse(System.currentTimeMillis());
        return new KitState(store.claims(uuid), firstSeen >= store.installedAt());
    }

    /** Clic dans le menu (auto : kit de départ donné à l'arrivée). Thread principal. */
    public void claim(Player player, Kit kit, boolean auto, Runnable after) {
        String name = plainName(player, kit);
        if (disabledHere) {
            messages.send(player, "kit.disabled-here");
            return;
        }
        if (!player.hasPermission(kit.permission())) {
            messages.send(player, "kit.locked", "kit", name);
            return;
        }
        if (loading(player)) {
            messages.send(player, "kit.loading");
            return;
        }
        int missing = missingSlots(player, kit.items());
        if (missing > 0) {
            if (auto) {
                sendWithButton(player, "kit.starter-no-space");
            } else {
                messages.send(player, "kit.no-space", "slots", String.valueOf(missing));
            }
            return;
        }
        UUID uuid = player.getUniqueId();
        Tasks.async(plugin, player, () -> {
            long now = System.currentTimeMillis();
            KitState state = state(uuid);
            Status status = state.status(kit, true, now);
            if (status != Status.AVAILABLE) {
                return new Outcome(status, state.secondsLeft(kit, now), null, now);
            }
            Long previous = state.claims().get(kit.id());
            if (store.claim(uuid, kit.id(), previous, now)) {
                return new Outcome(Status.AVAILABLE, 0, previous, now);
            }
            // Réclamé au même instant ailleurs (autre clic, autre serveur)
            return kit.once() ? new Outcome(Status.CLAIMED, 0, null, now)
                    : new Outcome(Status.WAITING, kit.cooldown().toSeconds(), null, now);
        }, outcome -> {
            switch (outcome.status()) {
                case AVAILABLE -> give(player, kit, auto);
                case WAITING -> messages.send(player, "kit.waiting", "kit", name,
                        "time", EterLib.get().formatDuration(player, outcome.secondsLeft()));
                case CLAIMED -> messages.send(player, "kit.once", "kit", name);
                default -> messages.send(player, "kit.locked", "kit", name);
            }
            after.run();
        }, () -> messages.send(player, "error.generic"), outcome -> {
            // Parti entre l'enregistrement et la remise : la réclamation est annulée, le kit l'attend
            if (outcome.status() == Status.AVAILABLE) {
                Tasks.async(plugin, () -> store.undo(uuid, kit.id(), outcome.previous(), outcome.claimedAt()),
                        "Réclamation du kit " + kit.id() + " non annulée pour " + uuid);
            }
        });
    }

    /** previous et claimedAt : pour annuler une réclamation enregistrée mais pas donnée. */
    private record Outcome(Status status, long secondsLeft, Long previous, long claimedAt) {
    }

    /** À l'arrivée : le kit de départ d'un nouveau joueur, dès que son inventaire est chargé. */
    public void welcome(Player player) {
        if (disabledHere) {
            return;
        }
        List<Kit> starters = catalog.all().stream()
                .filter(kit -> kit.newPlayersOnly() && player.hasPermission(kit.permission()))
                .toList();
        if (starters.isEmpty()) {
            return;
        }
        withState(player, state -> {
            long now = System.currentTimeMillis();
            for (Kit kit : starters) {
                if (state.status(kit, true, now) == Status.AVAILABLE) {
                    whenLoaded(player, () -> claim(player, kit, true, () -> {
                    }));
                }
            }
        });
    }

    /** Lance action une fois l'inventaire du joueur chargé par EterSync (tout de suite sans EterSync). */
    private void whenLoaded(Player player, Runnable action) {
        int[] checks = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || ++checks[0] > LOAD_CHECKS) {
                task[0].cancel();
            } else if (!loading(player)) {
                task[0].cancel();
                action.run();
            }
        }, 10, 10);
    }

    /** Staff : donne le contenu d'un kit sans délai ni permission, rien n'est enregistré. Thread principal. */
    public void giveFree(Player target, Kit kit) {
        give(target, kit, false);
    }

    /** Staff : efface les réclamations d'un joueur (kit null : toutes), puis then sur le thread principal (lignes effacées). */
    public void reset(UUID player, Kit kit, Consumer<Integer> then) {
        Tasks.async(plugin, () -> {
            int removed = store.reset(player, kit == null ? null : kit.id());
            Bukkit.getScheduler().runTask(plugin, () -> then.accept(removed));
        }, "Réclamations de kit non effacées pour " + player);
    }

    /** Thread principal. Ce qui ne rentre plus (inventaire rempli entre-temps) est posé aux pieds du joueur. */
    private void give(Player player, Kit kit, boolean starter) {
        boolean dropped = false;
        for (ItemStack item : kit.copies()) {
            for (ItemStack leftover : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItem(player.getLocation(), leftover);
                dropped = true;
            }
        }
        if (starter) {
            sendWithButton(player, "kit.starter-given");
        } else {
            messages.send(player, "kit.claimed", "kit", plainName(player, kit));
        }
        if (dropped) {
            messages.send(player, "kit.dropped");
        }
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0);
    }

    private void sendWithButton(Player player, String key) {
        messages.send(player, key);
        Component button = messages.get(player, "kit.button")
                .clickEvent(ClickEvent.runCommand("/kit"))
                .hoverEvent(HoverEvent.showText(messages.get(player, "kit.button-hover")));
        player.sendMessage(messages.prefix().append(button));
    }

    /** EterSync en softdepend : sa classe n'est touchée que s'il tourne ici. */
    private static boolean loading(Player player) {
        return Bukkit.getPluginManager().isPluginEnabled("EterSync") && SyncApi.get().map(sync -> sync.isLoading(player.getUniqueId())).orElse(false);
    }

    /** Cases à libérer pour que tout le kit rentre (en comptant les piles déjà commencées). */
    static int missingSlots(Player player, List<ItemStack> items) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        Inventory copy = Bukkit.createInventory(null, 36);
        for (int slot = 0; slot < storage.length && slot < 36; slot++) {
            copy.setItem(slot, storage[slot] == null ? null : storage[slot].clone());
        }
        int missing = 0;
        for (ItemStack item : items) {
            for (ItemStack leftover : copy.addItem(item.clone()).values()) {
                missing += (leftover.getAmount() + leftover.getMaxStackSize() - 1) / leftover.getMaxStackSize();
            }
        }
        return missing;
    }

    /** Nom du kit (lang/ > kits.&lt;id&gt;.name), son identifiant s'il n'en a pas. */
    public String plainName(Player viewer, Kit kit) {
        return messages.raw(viewer, "kits." + kit.id() + ".name") == null ? kit.id() : messages.plain(viewer, "kits." + kit.id() + ".name");
    }

    public boolean disabledHere() {
        return disabledHere;
    }

    public KitCatalog catalog() {
        return catalog;
    }

    public Messages messages() {
        return messages;
    }

    public BackButton backButton() {
        return backButton;
    }

    public JavaPlugin plugin() {
        return plugin;
    }
}
