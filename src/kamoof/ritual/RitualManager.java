package kamoof.ritual;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Etat + persistance du rituel des tetes (porte de RitualHandler de KamoofSMP S2).
 * Reglages codes en dur (= defauts config S2). Persistance dans plugins/KamoofLite/ritual.yml.
 */
public final class RitualManager {

    // --- Reglages (defauts S2, en dur) ---
    public static final long MIN_TIME = 0L;
    public static final long MAX_TIME = 24000L;
    public static final int DUPELIMIT = 1;
    public static final int HP_BOOST = 10;            // +10 = +5 coeurs
    public static final int BLOODY_HEADS = 3;
    public static final int FORGOTTEN_WEAKNESS = 1;

    // Animation
    public static final long ANIM_TIME_INCR = 250L;
    public static final long ANIM_TIME_STOP = 18000L;
    public static final long ANIM_TIME_SPEED = 2L;
    public static final Color ANIM_COLOR = Color.RED;
    public static final float ANIM_SIZE = 1.0f;
    public static final int LIGHTNING_QTY = 11;
    public static final long LIGHTNING_INTERVAL = 2L;
    public static final double SPHERE_RADIUS = 2.0;
    public static final int SPHERE_QTY = 750;
    public static final Color SPHERE_COLOR = Color.AQUA;
    public static final float SPHERE_SIZE = 1.0f;
    public static final double SPHERE_LAVA_CHANCE = 5.0;   // pourcent
    public static final boolean SPHERE_LAVA_SOUND = false;
    // Pacte accepte (burst de particules sur le joueur)
    public static final double ACCEPTED_LAVA_RADIUS = 1.5;
    public static final int ACCEPTED_LAVA_QTY = 300;
    public static final boolean ACCEPTED_LAVA_SOUND = false;
    public static final int ACCEPTED_FLAME_QTY = 500;
    public static final double ACCEPTED_FLAME_SPEED = 0.5;
    public static final boolean ACCEPTED_FLAME_SOUL = true;

    // Messages (MiniMessage) — textes exacts de la S2
    public static final String MSG_RITUALDONE = "<dark_red>Le Pacte des tetes a ete acheve. CRAIGNEZ SON POUVOIR SI VOUS L'OSEZ !!";
    public static final String MSG_CHOSE_BLOODY = "<red>Tu as accepte le pacte, voici ton du, <b>5</b> coeurs supplementaires, enfin, <dark_red><b>jusqu'a ta prochaine mort";
    public static final String MSG_CHOSE_FORGOTTEN = "<gray>Tu as accepte le pacte, voici ton du, faiblesse <b>1</b>, mais <dark_gray><b>ta tete ne tombera pas";
    public static final String MSG_ALREADY_CHOSE = "<red>Tu as deja choisi un pacte";
    public static final String MSG_DEATH_BLOODY = "<red>Tu as perdu les effets de ton pacte, si tu es capable de mourir avec ce don, alors peut-etre que tu n'es pas <dark_red><b>meritant de ce pouvoir";
    public static final String MSG_DEATH_FORGOTTEN = "<gray>Tu as perdu les effets de ton pacte, mais avec cela <dark_gray><b>ta tete n'est pas tombee";
    public static final String MSG_WRONG_TIME = "<red>Tu ne peux pas lancer le rituel maintenant, reviens plus tard";

    // Offsets (x, z) des 8 stands en octogone autour du centre.
    public static final double[][] OFFSETS = {
            {0, -6}, {4, -4}, {6, 0}, {4, 4}, {0, 6}, {-4, 4}, {-6, 0}, {-4, -4}
    };

    public static final NamespacedKey KEY = new NamespacedKey("kamooflite", "ritualstand");
    private static final NamespacedKey KEY_HP = new NamespacedKey("kamooflite", "pacte");

    public static final List<ArmorStand> armorStands = new ArrayList<>();
    public static Location location;
    public static boolean setup = false;

    private static JavaPlugin plugin;
    private static File dataFile;
    private static YamlConfiguration data;
    private static AttributeModifier hpModifier;

    private RitualManager() { }

    public static JavaPlugin plugin() { return plugin; }

    public static Component mini(String s) {
        return MiniMessage.miniMessage().deserialize(s.replace("<br>", "<newline>"));
    }

    public static void send(Player player, String miniStr) {
        player.sendMessage(mini(miniStr));
    }

    public static AttributeModifier hpModifier() {
        if (hpModifier == null) {
            hpModifier = new AttributeModifier(KEY_HP, HP_BOOST,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY);
        }
        return hpModifier;
    }

    // ---------------------------------------------------------------------
    // Persistance
    // ---------------------------------------------------------------------

    public static void load(JavaPlugin pl) {
        plugin = pl;
        dataFile = new File(pl.getDataFolder(), "ritual.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        setup = false;
        location = data.getLocation("ritual.data.location");
        if (location == null || location.getWorld() == null) return;

        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                location.getWorld().loadChunk((location.getBlockX() >> 4) + x, (location.getBlockZ() >> 4) + z);
            }
        }
        armorStands.clear();
        for (ArmorStand entity : location.getWorld().getEntitiesByClass(ArmorStand.class)) {
            if (entity.getPersistentDataContainer().has(KEY)) armorStands.add(entity);
        }
        if (armorStands.size() < 9) {
            armorStands.forEach(Entity::remove);
            armorStands.clear();
            return;
        }
        setup = true;
    }

    public static void save() {
        try {
            if (dataFile == null) return;
            plugin.getDataFolder().mkdirs();
            data.save(dataFile);
        } catch (Throwable t) {
            plugin.getLogger().warning("[rituel] sauvegarde ritual.yml echouee: " + t.getMessage());
        }
    }

    public static YamlConfiguration data() { return data; }

    // ---------------------------------------------------------------------
    // Mise en place de l'autel (armor stands)
    // ---------------------------------------------------------------------

    public static void setRitual(Location loc, Player player) {
        armorStands.forEach(Entity::remove);
        armorStands.clear();
        location = loc.clone();

        float angle = -45.0f;
        World world = player.getWorld();
        for (double[] offset : OFFSETS) {
            double x = loc.getBlockX() + offset[0] + 0.5;
            double y = loc.getBlockY() - 1.45;
            double z = loc.getBlockZ() + offset[1] + 0.5;
            player.spawnParticle(Particle.DUST, x, loc.getBlockY() + 0.5, z, 4, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.YELLOW, 3.0f), true);
            angle += 45.0f;
            armorStands.add(makeArmorStand(new Location(world, x, y, z, angle, 0.0f)));
        }
        armorStands.add(makeArmorStand(new Location(world,
                loc.getBlockX() + 0.5, loc.getBlockY() - 1.45, loc.getBlockZ() + 0.5)));
        setup = true;

        player.sendMessage("§aNouvel emplacement de rituel : §e"
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());

        Location saved = loc.clone().add(0.5, 0.5, 0.5);
        data.set("ritual.data.location", saved);
        save();
    }

    private static ArmorStand makeArmorStand(Location loc) {
        ArmorStand entity = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        entity.setArms(false);
        entity.setBasePlate(false);
        entity.setVisible(false);
        entity.setCollidable(false);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setAI(false);
        entity.getPersistentDataContainer().set(KEY, PersistentDataType.BOOLEAN, false);
        return entity;
    }

    // Lance l'animation quand la derniere tete est posee.
    public static void runAnimation() {
        for (ArmorStand entity : armorStands) {
            entity.getPersistentDataContainer().set(KEY, PersistentDataType.BOOLEAN, true);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (ArmorStand entity : armorStands) {
                if (entity.getEquipment() == null || entity.getEquipment().getHelmet() == null) return;
            }
            RitualAnimation.execute(location);
        }, 5L);
    }

    // ---------------------------------------------------------------------
    // Tokens des livres (usage unique) + pactes
    // ---------------------------------------------------------------------

    public static UUID addNewToken() {
        UUID uuid = UUID.randomUUID();
        List<String> list = data.getStringList("pactes");
        list.add(uuid.toString());
        data.set("pactes", list);
        save();
        return uuid;
    }

    public static boolean isValidToken(UUID uuid) {
        List<String> list = data.getStringList("pactes");
        if (!list.contains(uuid.toString())) return false;
        list.remove(uuid.toString());
        data.set("pactes", list);
        save();
        return true;
    }

    public static String getPacte(Player player) {
        return data.getString("pacte." + player.getUniqueId());
    }

    public static void setPacte(Player player, String pacte) {
        data.set("pacte." + player.getUniqueId(), pacte);
        save();
        if (pacte == null) return;

        switch (pacte) {
            case "1" -> {
                player.getAttribute(Attribute.MAX_HEALTH).addModifier(hpModifier());
                send(player, MSG_CHOSE_BLOODY);
            }
            case "2" -> {
                int level = FORGOTTEN_WEAKNESS - 1;
                if (level >= 0) player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, -1, level));
                send(player, MSG_CHOSE_FORGOTTEN);
            }
            default -> { return; }
        }

        // Burst de particules sur le joueur (sphere de lave + flammes).
        Particle lava = ACCEPTED_LAVA_SOUND ? Particle.DRIPPING_DRIPSTONE_LAVA : Particle.DRIPPING_LAVA;
        World world = player.getWorld();
        Location loc = player.getLocation().clone().add(0.0, 0.9, 0.0);
        for (int i = 0; i < ACCEPTED_LAVA_QTY; ++i) {
            double phi = Math.PI * 2 * Math.random();
            double posY = loc.getY() + ACCEPTED_LAVA_RADIUS * Math.cos(phi);
            double phiSin = Math.sin(phi);
            double a = Math.PI * 2 * Math.random();
            double posX = loc.getX() + ACCEPTED_LAVA_RADIUS * Math.cos(a) * phiSin;
            double posZ = loc.getZ() + ACCEPTED_LAVA_RADIUS * Math.sin(a) * phiSin;
            world.spawnParticle(lava, posX, posY, posZ, 1, 0, 0, 0, 0, null, true);
        }
        world.spawnParticle(ACCEPTED_FLAME_SOUL ? Particle.SOUL_FIRE_FLAME : Particle.FLAME,
                loc, ACCEPTED_FLAME_QTY, 0, 0, 0, ACCEPTED_FLAME_SPEED, null, true);
    }
}
