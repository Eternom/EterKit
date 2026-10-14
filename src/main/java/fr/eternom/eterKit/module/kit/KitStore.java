package fr.eternom.eterKit.module.kit;

import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Réclamations, communes à tout le réseau : table eterkit_claims (joueur, kit, date de la dernière réclamation en ms).
 * Table eterkit_install : date d'installation, pour réserver le kit de départ aux joueurs arrivés après.
 * Appels bloquants : hors du thread principal.
 */
public class KitStore {

    private static final String CLAIMS = "claims";
    private static final String INSTALL = "install";

    private final Database database;
    private final long installedAt;

    public KitStore(Database database) {
        this.database = database;
        database.createTable(CLAIMS,
                Column.of("uuid", Column.Type.UUID).primaryKey(),
                Column.of("kit", Column.Type.STRING).length(32).primaryKey(),
                Column.of("claimed_at", Column.Type.LONG).notNull());
        database.createTable(INSTALL,
                Column.of("id", Column.Type.INT).primaryKey(),
                Column.of("installed_at", Column.Type.LONG).notNull());
        // Le premier serveur qui démarre écrit la date, les suivants la lisent
        database.execute("INSERT IGNORE INTO " + database.table(INSTALL) + " (id, installed_at) VALUES (1, ?)", System.currentTimeMillis());
        installedAt = database.getFirst(INSTALL, Map.of("id", 1)).map(row -> row.getLong("installed_at")).orElse(0L);
    }

    public long installedAt() {
        return installedAt;
    }

    /** Dernière réclamation de chaque kit (ms), seulement ceux déjà réclamés. */
    public Map<String, Long> claims(UUID player) {
        Map<String, Long> claims = new HashMap<>();
        database.get(CLAIMS, Map.of("uuid", player)).forEach(row -> claims.put(row.getString("kit"), row.getLong("claimed_at")));
        return claims;
    }

    /**
     * Enregistre la réclamation, seulement si rien n'a changé depuis la lecture (previous : null si jamais réclamé) :
     * deux clics, ou deux serveurs, au même moment ne donnent qu'un kit.
     * @return true si cette réclamation est celle qui compte
     */
    public boolean claim(UUID player, String kit, Long previous, long now) {
        if (previous == null) {
            return database.execute("INSERT IGNORE INTO " + database.table(CLAIMS) + " (uuid, kit, claimed_at) VALUES (?, ?, ?)",
                    player, kit, now) > 0;
        }
        return database.execute("UPDATE " + database.table(CLAIMS) + " SET claimed_at = ? WHERE uuid = ? AND kit = ? AND claimed_at = ?",
                now, player, kit, previous) > 0;
    }

    /** Annule une réclamation enregistrée mais pas donnée (joueur parti entre-temps) : le kit n'est pas perdu. */
    public void undo(UUID player, String kit, Long previous, long claimedAt) {
        if (previous == null) {
            database.delete(CLAIMS, Map.of("uuid", player, "kit", kit, "claimed_at", claimedAt));
        } else {
            database.update(CLAIMS, Map.of("claimed_at", previous), Map.of("uuid", player, "kit", kit, "claimed_at", claimedAt));
        }
    }

    /** Efface les réclamations (staff) : d'un kit, ou de tous (kit null). */
    public int reset(UUID player, String kit) {
        return kit == null
                ? database.delete(CLAIMS, Map.of("uuid", player))
                : database.delete(CLAIMS, Map.of("uuid", player, "kit", kit));
    }
}
