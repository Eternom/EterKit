# EterKit

Les kits du réseau : un kit de départ pour les nouveaux joueurs, puis des kits hebdomadaires (un pour tous, un par
grade), réclamés depuis le menu `/kit` sur n'importe quel serveur. Document développeur, à tenir à jour avec le code.

## Prérequis

- **EterLib 1.13.0+** (`depend`) : base, langues et textes communs, menus (cadre, bouton Retour), annuaire des joueurs.
- **EterSync** (`softdepend`) : `SyncApi#isLoading`, aucun kit n'est donné pendant le chargement de l'inventaire
  (il serait écrasé). Sans EterSync sur un serveur, les kits y sont donnés tout de suite.
- Permissions lues par l'API Bukkit (LuckPerms ou autre).

## Fonctionnement

- **Kits** (`config.yml > kits`, `KitCatalog`) : icône, délai (`7d`, `12h`, `once`), `new-players-only`, objets
  `"MATIÈRE [quantité] [enchantement:niveau...]"`. Accès : permission `eterkit.kit.<kit>`, donnée par un grade LuckPerms,
  la boutique (`lp user … permission settemp eterkit.kit.<kit> true 30d accumulate`) ou le pass de saison d'EterReward
  (récompense `permissions`). Pas de cumul : chaque grade donne seulement son kit.
- **Réclamations** (`KitStore`, table `eterkit_claims`, clé `uuid` + `kit`, `claimed_at` en ms) : communes à tout le
  réseau. La base enregistre d'abord (`INSERT IGNORE`, ou `UPDATE … WHERE claimed_at = <valeur lue>`), puis le kit est
  donné : deux clics ou deux serveurs au même moment n'en donnent qu'un. Joueur parti entre les deux : la réclamation
  est annulée (`undo`), le kit n'est pas perdu.
- **Place** : tout le kit doit rentrer (`KitService#missingSlots`, simulé sur une copie de l'inventaire) ; sinon rien
  n'est consommé et le joueur sait combien de cases libérer. Ce qui ne rentre plus au moment de donner (inventaire
  rempli entre-temps) est posé à ses pieds.
- **Kit de départ** : `new-players-only`, réservé aux joueurs vus pour la première fois (`PlayerDirectory#firstSeen`
  d'EterLib) après l'installation (table `eterkit_install`, écrite par le premier serveur qui démarre). Donné de
  lui-même à l'arrivée, dès que l'inventaire est chargé (vérifié toutes les demi-secondes, 30 s au plus) ; s'il manque
  de place, un message avec un bouton `/kit`. Les anciens joueurs ne le voient pas.
- **Serveurs exclus** (`disabled-servers`, début du nom) : la prison, sans EterSync.
- **Menu `/kit`** (`module/gui`) : cadre orange, un kit par case (7 au plus), état (disponible, temps restant, déjà
  reçu, verrouillé avec « comment l'obtenir » : `lang/ > kits.<kit>.how`) ; clic gauche réclamer, droit voir le contenu.
  Bouton du bas : `menu.back-command`.

## Commandes et permissions

| Commande | Permission | Rôle |
|---|---|---|
| `/kit` (`/kits`) | `eterkit.use` (tous) | Menu des kits |
| `/kit <kit>` | `eterkit.use` + `eterkit.kit.<kit>` | Réclame ce kit |
| `/eterkit give <joueur> <kit>` | `eterkit.admin` (op) | Donne le contenu à un joueur connecté ici, sans délai ni permission, sans rien enregistrer |
| `/eterkit reset <joueur> [kit]` | `eterkit.admin` (op) | Efface ses réclamations (d'un kit ou de tous) ; console comprise |

Accès par défaut : `eterkit.kit.depart` et `eterkit.kit.joueur` (tous) ; `aventurier`, `heros`, `legende` : personne
(grades, boutique, pass).

## Base de données

- `eterkit_claims` : `uuid`, `kit`, `claimed_at` (ms).
- `eterkit_install` : `id` (1), `installed_at` (ms).
