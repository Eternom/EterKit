package fr.eternom.eterKit.module.kit;

import java.util.Map;

/**
 * Où en est un joueur avec ses kits, lu en base au moment d'ouvrir le menu ou de réclamer.
 * claims : dernière réclamation de chaque kit (ms) ; newPlayer : arrivé après l'installation (kit de départ).
 */
public record KitState(Map<String, Long> claims, boolean newPlayer) {

    public enum Status {
        /** Réclamable maintenant. */
        AVAILABLE,
        /** Déjà réclamé, le délai court encore. */
        WAITING,
        /** Kit d'une seule fois, déjà réclamé. */
        CLAIMED,
        /** Pas la permission. */
        LOCKED,
        /** Kit de départ d'un joueur arrivé avant l'installation : pas montré. */
        HIDDEN
    }

    public Status status(Kit kit, boolean permitted, long now) {
        if (kit.newPlayersOnly() && !newPlayer) {
            return Status.HIDDEN;
        }
        if (!permitted) {
            return Status.LOCKED;
        }
        Long last = claims.get(kit.id());
        if (last == null) {
            return Status.AVAILABLE;
        }
        if (kit.once()) {
            return Status.CLAIMED;
        }
        return now >= last + kit.cooldown().toMillis() ? Status.AVAILABLE : Status.WAITING;
    }

    /** Secondes avant de pouvoir réclamer de nouveau (0 si réclamable). */
    public long secondsLeft(Kit kit, long now) {
        Long last = claims.get(kit.id());
        if (last == null || kit.once()) {
            return 0;
        }
        return Math.max(0, (last + kit.cooldown().toMillis() - now + 999) / 1000);
    }
}
