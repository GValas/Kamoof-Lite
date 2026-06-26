package kamoof;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KamoofLite extends JavaPlugin implements Listener {

    // Profil d'origine de chaque joueur deguise (pour /undisguise et la mort)
    private final Map<UUID, PlayerProfile> original = new HashMap<>();

    // Profil authlib NMS d'origine (uuid + vrai nom + textures) capture AVANT le deguisement.
    // Repose verbatim a la restauration -> garantit le vrai skin (corrige le skin Steve/crack de v2.4).
    private final Map<UUID, Object> originalProfileNms = new HashMap<>();

    // Pseudo du deguisement en cours (pour le message de deconnexion).
    private final Map<UUID, String> disguiseName = new HashMap<>();

    // Active la tentative de respawn NMS pour rafraichir SA PROPRE vue (F5).
    // Si la version casse, on peut le couper sans toucher au reste.
    private static final boolean SELF_REFRESH = true;
    // Logge une seule fois les signatures NMS trouvees (diagnostic pour finaliser le respawn).
    private boolean diagDone = false;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("KamoofLite v2.8 actif.");
    }

    // Joueur deguise qui se deconnecte : le message de quit doit nommer le DEGUISEMENT,
    // pas le vrai pseudo. On nettoie aussi les Map (pas de persistance du deguisement).
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        String name = disguiseName.remove(id);
        original.remove(id);
        originalProfileNms.remove(id);
        if (name == null) return; // pas deguise -> message vanilla inchange
        event.quitMessage(Component.translatable("multiplayer.player.left",
                Component.text(name)).color(NamedTextColor.YELLOW));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerProfile own = restoreAppearance(player);

        // Drop d'une tete portant le vrai skin (textures) du joueur mort
        try {
            PlayerProfile headProfile = (own != null) ? own : player.getPlayerProfile();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setPlayerProfile(headProfile);
            head.setItemMeta(meta);
            event.getDrops().add(head);
        } catch (Throwable t) {
            getLogger().warning("Drop tete echoue: " + t.getMessage());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PLAYER_HEAD) return;
        if (!(item.getItemMeta() instanceof SkullMeta meta)) return;

        PlayerProfile headProfile = meta.getPlayerProfile();
        if (headProfile == null || headProfile.getName() == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Sauvegarde de l'apparence d'origine (une seule fois) : profil Bukkit + profil authlib NMS.
        UUID id = player.getUniqueId();
        if (!original.containsKey(id)) {
            original.put(id, player.getPlayerProfile());
            try {
                Object handle = player.getClass().getMethod("getHandle").invoke(player);
                Object gp = getGameProfile(handle);
                // On garde la reference vers le VRAI profil : on ne le mute jamais (le deguisement
                // cree toujours un nouveau GameProfile), donc ses textures restent intactes.
                if (gp != null) originalProfileNms.put(id, gp);
            } catch (Throwable t) {
                getLogger().warning("[capture] profil NMS d'origine non sauvegarde: " + t.getMessage());
            }
        }

        String name = headProfile.getName();

        if (headProfile.hasTextures()) {
            applyDisguise(player, headProfile, name);
            consumeOneHead(player);
            return;
        }
        // Ancienne tete sans textures : on les recupere depuis Mojang en async
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            boolean ok;
            try {
                ok = headProfile.complete(true);
            } catch (Throwable t) {
                ok = false;
            }
            final boolean completed = ok && headProfile.hasTextures();
            Bukkit.getScheduler().runTask(this, () -> {
                if (!completed) {
                    player.sendMessage("§cDeguisement impossible : skin introuvable.");
                    return;
                }
                applyDisguise(player, headProfile, name);
                consumeOneHead(player);
            });
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande reservee aux joueurs.");
            return true;
        }
        if (!original.containsKey(player.getUniqueId())) {
            player.sendMessage("§7Tu n'es pas deguise.");
            return true;
        }
        restoreAppearance(player);
        player.sendMessage("§aTu as repris ton apparence.");
        return true;
    }

    // Restaure l'apparence d'origine (utilise par /undisguise ET la mort).
    // Retourne le profil Bukkit d'origine (ou null si le joueur n'etait pas deguise),
    // pour reutilisation (ex: drop de la tete au vrai skin a la mort).
    private PlayerProfile restoreAppearance(Player player) {
        UUID id = player.getUniqueId();
        PlayerProfile own = original.remove(id);
        Object savedNms = originalProfileNms.remove(id);
        disguiseName.remove(id);
        if (own == null && savedNms == null) return null;
        try {
            if (own != null) player.setPlayerProfile(own); // coherence cote API Bukkit
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            if (savedNms != null) {
                // On repose le VRAI GameProfile authlib (uuid + nom + textures) tel quel.
                // -> skin correct pour les autres joueurs, sans reconstruction fragile.
                Field f = findFieldOfType(handle.getClass(), "com.mojang.authlib.GameProfile");
                if (f != null) { f.setAccessible(true); f.set(handle, savedNms); }
                if (SELF_REFRESH) { try { resendToSelf(handle); } catch (Throwable ignore) { } }
            } else {
                forceDisplayName(player, null); // repli : ancien chemin (reconstruit depuis le handle)
            }
            player.setPlayerListName(null); // reset du pseudo TAB
            player.displayName(Component.text(player.getName())); // reset du pseudo CHAT
            refresh(player); // les autres relisent le profil (skin + nom au-dessus de la tete)
        } catch (Throwable t) {
            getLogger().warning("Restore: " + t.getMessage());
        }
        return own;
    }

    private void applyDisguise(Player player, PlayerProfile headProfile, String name) {
        try {
            // On garde TON UUID (ton skin s'affiche sur toi, pas de doublon TAB),
            // on copie le nom + les textures, et on force le pseudo dans le TAB.
            PlayerProfile disguise = Bukkit.createProfile(player.getUniqueId(), name);
            disguise.setProperties(headProfile.getProperties());
            player.setPlayerProfile(disguise);
            player.setPlayerListName(name);
            player.displayName(Component.text(name)); // pseudo affiche dans le CHAT
            // NMS : change le nom AU-DESSUS de la tete (additif ; n'altere pas le skin ci-dessus).
            forceDisplayName(player, name);
            refresh(player);
            disguiseName.put(player.getUniqueId(), name); // pour le message de deconnexion
            player.sendMessage("§aTu es maintenant deguise en " + name);
        } catch (Throwable t) {
            player.sendMessage("§cDeguisement echoue: " + t.getMessage());
        }
    }

    private void consumeOneHead(Player player) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand.getType() == Material.PLAYER_HEAD) {
            inHand.setAmount(inHand.getAmount() - 1);
        }
    }

    // Force les AUTRES clients a recharger le skin (cache puis reaffiche 2 ticks plus tard).
    // Comme le GameProfile NMS porte deja le nouveau nom, le re-spawn affiche le bon nom au-dessus.
    private void refresh(Player target) {
        List<Player> viewers = new ArrayList<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;
            viewer.hidePlayer(this, target);
            viewers.add(viewer);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player viewer : viewers) {
                if (viewer.isOnline() && target.isOnline()) {
                    viewer.showPlayer(this, target);
                }
            }
        }, 2L);
    }

    // ---------------------------------------------------------------------
    // NMS : nom flottant AU-DESSUS de la tete
    // ---------------------------------------------------------------------
    // Le nom au-dessus de la tete vient du GameProfile.name de l'entite (pas du
    // setPlayerListName, qui n'agit que sur la TAB). Avec ton propre UUID, Bukkit
    // refuse de changer ce nom -> on le change directement sur le GameProfile NMS.
    // name == null  -> on remet ton vrai pseudo (depuis ton profil d'origine).
    // Tout est en try/catch : si la version ne colle pas, le skin marche quand meme.

    private void forceDisplayName(Player player, String name) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player); // CraftPlayer -> ServerPlayer
            Object profile = getGameProfile(handle);
            if (profile == null) {
                getLogger().warning("[nom] GameProfile NMS introuvable, nom au-dessus inchange.");
                return;
            }
            String realName = player.getName();
            String wanted = (name != null) ? name : realName;
            swapProfileName(handle, profile, player.getUniqueId(), wanted);
            // Le rafraichissement pour les AUTRES est gere par refresh() (hide/show).
            // Pour TA vue (F5), on tente un respawn NMS.
            if (SELF_REFRESH) resendToSelf(handle);
        } catch (Throwable t) {
            getLogger().warning("[nom] forceDisplayName a echoue (skin OK quand meme): " + t);
        }
    }

    private Object getGameProfile(Object handle) {
        // 1) methode getGameProfile()
        try {
            Method m = findNoArgMethodReturning(handle.getClass(), "com.mojang.authlib.GameProfile");
            if (m != null) return m.invoke(handle);
        } catch (Throwable ignored) { }
        // 2) champ de type GameProfile
        try {
            Field f = findFieldOfType(handle.getClass(), "com.mojang.authlib.GameProfile");
            if (f != null) { f.setAccessible(true); return f.get(handle); }
        } catch (Throwable ignored) { }
        return null;
    }

    // Construit un nouveau GameProfile(uuid, nom) avec les MEMES textures, puis le pose
    // sur le champ GameProfile du handle (sur-ecrit la reference -> compatible record immutable).
    //
    // Fix v2.8 : on ne RECOPIE plus les textures (l'ancienne recopie par putAll echouait a tous
    // les coups -> le profil pose etait sans textures -> les AUTRES joueurs + la TAB voyaient un
    // skin Steve/crack, alors que TOI tu gardais le bon skin via le cache client). A la place on
    // REUTILISE telle quelle la PropertyMap actuelle (celle que setPlayerProfile vient de poser,
    // donc deja remplie avec les textures du deguisement) et on la passe au constructeur record
    // a 3 args GameProfile(UUID, String, PropertyMap). Plus aucune recopie => les textures ne
    // peuvent plus se perdre.
    private void swapProfileName(Object handle, Object oldProfile, UUID uuid, String name) throws Exception {
        Class<?> gp = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> pmClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
        Object props = invokeProperties(gp, oldProfile); // PropertyMap actuelle (textures du deguisement)
        Object np = (props != null)
                ? gp.getConstructor(UUID.class, String.class, pmClass).newInstance(uuid, name, props)
                : gp.getConstructor(UUID.class, String.class).newInstance(uuid, name);
        Field f = findFieldOfType(handle.getClass(), "com.mojang.authlib.GameProfile");
        if (f == null) throw new NoSuchFieldException("champ GameProfile introuvable sur " + handle.getClass());
        f.setAccessible(true);
        f.set(handle, np);
    }

    // Tente de renvoyer un paquet respawn au joueur pour rafraichir SA vue (skin + nom en F5).
    // La signature de ClientboundRespawnPacket depend de la version : on logge un diagnostic
    // une fois, on tente les constructions plausibles, et on echoue proprement sinon.
    private void resendToSelf(Object handle) {
        try {
            Class<?> respawnClass;
            try {
                respawnClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRespawnPacket");
            } catch (ClassNotFoundException e) {
                getLogger().warning("[nom] ClientboundRespawnPacket introuvable (mapping ?). Self-view non rafraichie.");
                return;
            }
            if (!diagDone) {
                diagDone = true;
                getLogger().info("[diag] ClientboundRespawnPacket constructeurs:");
                for (Constructor<?> c : respawnClass.getConstructors()) {
                    getLogger().info("[diag]   " + c);
                }
                getLogger().info("[diag] ServerPlayer methodes 'createCommonSpawnInfo'/respawn-like:");
                for (Method m : handle.getClass().getMethods()) {
                    String n = m.getName().toLowerCase();
                    if (n.contains("spawninfo") || n.contains("respawn") || n.contains("dimension")) {
                        getLogger().info("[diag]   " + m);
                    }
                }
                getLogger().info("[diag] >>> Colle ces lignes a Claude pour finaliser le respawn self-view.");
            }
            // TODO(live): construire ClientboundRespawnPacket avec la vraie signature 26.1.2
            // (a partir du diagnostic ci-dessus) puis: getConnection(handle).send(packet)
            // + repositionner le joueur. Laisse en TODO tant qu'on n'a pas confirme la signature,
            // pour ne pas envoyer un paquet malforme qui glitcherait le client.
        } catch (Throwable t) {
            getLogger().warning("[nom] resendToSelf a echoue: " + t);
        }
    }

    // Recupere la PropertyMap d'un GameProfile en gerant les deux API authlib :
    // getProperties() (ancienne) et properties() (record, MC 26.x). Sans ca, la copie
    // des textures echoue silencieusement et le skin disparait pour les autres joueurs.
    private Object invokeProperties(Class<?> gp, Object profile) throws Exception {
        for (String n : new String[]{"getProperties", "properties"}) {
            try {
                return gp.getMethod(n).invoke(profile);
            } catch (NoSuchMethodException ignored) {
                // on tente le nom suivant
            }
        }
        throw new NoSuchMethodException("GameProfile : ni getProperties() ni properties() trouve");
    }

    // --- petits utilitaires de reflexion ---

    private Method findNoArgMethodReturning(Class<?> cls, String returnTypeName) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getName().equals(returnTypeName)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private Field findFieldOfType(Class<?> cls, String typeName) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().getName().equals(typeName)) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        return null;
    }
}
