package fr.eternom.eterKit.module.kit;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Les kits de config.yml > kits, dans l'ordre du fichier (celui du menu). Une ligne invalide est ignorée, avec un avertissement. */
public final class KitCatalog {

    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*([dhm])", Pattern.CASE_INSENSITIVE);

    private final Map<String, Kit> kits;

    private KitCatalog(Map<String, Kit> kits) {
        this.kits = kits;
    }

    public static KitCatalog load(ConfigurationSection section, Logger logger) {
        Map<String, Kit> kits = new LinkedHashMap<>();
        if (section == null) {
            logger.warning("Aucun kit dans config.yml > kits");
            return new KitCatalog(kits);
        }
        Registry<Enchantment> enchantments = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            String key = id.toLowerCase(Locale.ROOT);
            String where = "kits." + id;
            Material icon = Material.matchMaterial(entry.getString("icon", "CHEST"));
            Duration cooldown = parseCooldown(entry.getString("cooldown", "7d"));
            if (cooldown != null && cooldown.isZero()) {
                logger.warning(where + " : délai invalide (« 7d », « 12h » ou « once »), 7 jours utilisés");
                cooldown = Duration.ofDays(7);
            }
            List<ItemStack> items = new ArrayList<>();
            for (String line : entry.getStringList("items")) {
                parseItem(line, enchantments).ifPresentOrElse(items::add,
                        () -> logger.warning(where + " : objet invalide ignoré : " + line));
            }
            if (items.isEmpty()) {
                logger.warning(where + " : aucun objet valable, kit ignoré");
                continue;
            }
            kits.put(key, new Kit(key, icon == null || !icon.isItem() ? Material.CHEST : icon, cooldown,
                    entry.getBoolean("new-players-only", false), List.copyOf(items)));
        }
        return new KitCatalog(kits);
    }

    public Collection<Kit> all() {
        return kits.values();
    }

    public Optional<Kit> get(String id) {
        return Optional.ofNullable(id == null ? null : kits.get(id.toLowerCase(Locale.ROOT)));
    }

    /** "once" : null (une seule fois) ; "7d", "12h", "1d 12h" : la durée ; illisible : Duration.ZERO. */
    private static Duration parseCooldown(String text) {
        if (text.trim().equalsIgnoreCase("once")) {
            return null;
        }
        Duration total = Duration.ZERO;
        Matcher matcher = DURATION.matcher(text);
        while (matcher.find()) {
            long amount = Long.parseLong(matcher.group(1));
            total = total.plus(switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "d" -> Duration.ofDays(amount);
                case "h" -> Duration.ofHours(amount);
                default -> Duration.ofMinutes(amount);
            });
        }
        return total;
    }

    /** "MATIÈRE [quantité] [enchantement:niveau...]", ex : "DIAMOND_PICKAXE 1 efficiency:2 unbreaking:2". */
    private static Optional<ItemStack> parseItem(String line, Registry<Enchantment> enchantments) {
        String[] parts = line.trim().split("\\s+");
        Material material = Material.matchMaterial(parts[0].toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem() || material.isAir()) {
            return Optional.empty();
        }
        int amount = 1;
        int next = 1;
        if (parts.length > 1 && parts[1].chars().allMatch(Character::isDigit)) {
            amount = Integer.parseInt(parts[1]);
            next = 2;
        }
        if (amount < 1 || amount > material.getMaxStackSize()) {
            return Optional.empty();
        }
        ItemStack item = ItemStack.of(material, amount);
        for (int i = next; i < parts.length; i++) {
            String[] enchant = parts[i].toLowerCase(Locale.ROOT).split(":");
            Enchantment enchantment = enchantments.get(NamespacedKey.minecraft(enchant[0]));
            if (enchant.length != 2 || enchantment == null || !enchant[1].chars().allMatch(Character::isDigit)) {
                return Optional.empty();
            }
            item.addUnsafeEnchantment(enchantment, Integer.parseInt(enchant[1]));
        }
        return Optional.of(item);
    }
}
