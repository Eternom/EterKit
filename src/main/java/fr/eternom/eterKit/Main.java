package fr.eternom.eterKit;

import fr.eternom.eterKit.listeners.Commands;
import fr.eternom.eterKit.listeners.Events;
import fr.eternom.eterKit.module.kit.KitCatalog;
import fr.eternom.eterKit.module.kit.KitService;
import fr.eternom.eterKit.module.kit.KitStore;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.module.player.PlayerDirectory;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    /** Préfixe des tables d'EterKit dans la base commune : eterkit_claims, eterkit_install. */
    private static final String TABLE_PREFIX = "eterkit_";
    private static final String REQUIRED_ETERLIB = "1.13.0";

    private Messages messages;
    private PlayerDirectory playerDirectory;
    private KitService kitService;

    @Override
    public void onEnable() {
        // En premier : vérifie la version d'EterLib (un EterLib < 1.3.0 n'a pas requireVersion, d'où le catch)
        try {
            if (!EterLib.requireVersion(this, REQUIRED_ETERLIB)) {
                return;
            }
        } catch (LinkageError tooOld) {
            getLogger().severe("EterLib " + REQUIRED_ETERLIB + " ou plus récent est nécessaire.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();

        EterLib lib = EterLib.get();
        messages = lib.messages(this, "en_us", "fr_fr");
        playerDirectory = lib.getPlayers();
        KitCatalog catalog = KitCatalog.load(getConfig().getConfigurationSection("kits"), getLogger());
        kitService = new KitService(this, catalog, new KitStore(lib.database(TABLE_PREFIX)), playerDirectory, messages,
                lib.backButton(getConfig().getString("menu.back-command", "")),
                getConfig().getStringList("disabled-servers"), lib.getServerName());

        new Commands(this);
        new Events(this);
        getLogger().info(catalog.all().size() + " kit(s) chargé(s)" + (kitService.disabledHere() ? ", désactivés sur ce serveur" : ""));
    }

    public Messages getMessages() {
        return messages;
    }

    public PlayerDirectory getPlayerDirectory() {
        return playerDirectory;
    }

    public KitService getKitService() {
        return kitService;
    }
}
