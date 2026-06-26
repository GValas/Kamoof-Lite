# HORUS Minecraft Server

Serveur Paper actif : **Paper 26.1.2** (Java 25), monté sur le partage réseau `\\HORUS\appdata\minecraft`.

## Plugins installés

- **voicechat 2.6.20** (Simple Voice Chat de henkelmax, Modrinth) — installé 2026-06-22, compatible 26.1.2. Serveur vocal sur **UDP 24454** (port forwardé). Config : `plugins/voicechat/voicechat-server.properties`. `voice_host` vide (à remplir avec l'IP/domaine public seulement si des joueurs externes ont un souci vocal). `force_voice_chat=false`. Nécessite le mod client (Fabric/Forge) côté joueur.

## Plugin custom : KamoofLite

`plugins/KamoofLite.jar` — seul plugin custom du serveur, écrit par Claude. Source versionnée dans **`dev/src/`** (tout le projet de dev est sous `dev/` ; le runtime serveur — jar Paper, `libraries/`, `plugins/` — reste à la racine).

Fonctionnalités :
- Lâcher sa tête à la mort.
- Clic-droit sur une tête de joueur → se déguiser en lui (`setPlayerProfile`).
- `/undisguise` → revenir normal. La mort réinitialise aussi le déguisement.
- **Pas de persistance** : le déguisement ne survit pas à une reconnexion.

### Version : v2.8 (fix skin cracké pour les AUTRES joueurs + TAB)

- **Symptôme** : le joueur déguisé voyait le **bon skin** (F5 + TAB chez lui), mais **les autres joueurs** (et la TAB côté autres) voyaient un **skin Steve/cracké**. **Cause identifiée via les logs** : `[KamoofLite] [nom] copie des textures echouee: ...` répété à *chaque* déguisement, sur toutes les versions. Dans `swapProfileName`, après que `setPlayerProfile(disguise)` ait correctement posé nom+textures sur le profil NMS, `forceDisplayName` reconstruisait un `GameProfile(uuid, nom)` **vide** pour corriger le nom au-dessus de la tête, **tentait de recopier les textures par `putAll` réflexion (échec systématique)**, puis **écrasait** le bon profil par ce profil sans textures via `f.set(handle, np)`. → autres = Steve, toi = OK (cache client gardait le skin de l'étape `setPlayerProfile`).
- **Correctif** : authlib 7.0.63 → `GameProfile` est un **record** avec constructeur `GameProfile(UUID, String, PropertyMap)` et accesseur **`properties()`** (pas `getProperties()`, d'où l'échec en boucle). On **ne recopie plus** les textures : on réutilise telle quelle la `PropertyMap` actuelle (déjà remplie par `setPlayerProfile`) et on la passe au constructeur 3-args → `new GameProfile(uuid, nom, propsActuelles)`. Plus aucune recopie fragile, les textures ne peuvent plus se perdre. Chemin de restauration (`/undisguise`, mort) inchangé (il repose `originalProfileNms` verbatim).
- **À VALIDER en live** : déguisement vu par un **autre** joueur = bon skin + bon pseudo (au-dessus de la tête ET TAB). La vue **F5 self** reste sur le `TODO resendToSelf` (déjà OK via cache client).

### Version : v2.6 (message de déconnexion = pseudo du déguisement)

- Si un joueur **déguisé se déconnecte**, le message de quit nomme désormais le **déguisement** et plus le vrai pseudo. Handler `onQuit(PlayerQuitEvent)` : si le joueur est dans `disguiseName`, on remplace le message par `Component.translatable("multiplayer.player.left", <pseudoDéguisement>)` en jaune (mime le format vanilla). Nouveau `Map<UUID,String> disguiseName` rempli dans `applyDisguise`, vidé dans `restoreAppearance`. **Bonus** : `onQuit` purge `original` + `originalProfileNms` + `disguiseName` (corrige une fuite mémoire quand on quittait déguisé ; OK car pas de persistance du déguisement).

### Version : v2.5 (capture/restauration verbatim du vrai GameProfile)

- **Fix v2.5 — validé live le 2026-06-23 (« tout marche »).** À `/undisguise` et à la mort, le skin redevenait **Steve/cracké pour tout le monde** (textures perdues). Cause : la restauration reconstruisait le profil en **relisant** les textures depuis le handle après `setPlayerProfile(original)` (`forceDisplayName(null)` → `swapProfileName`), round-trip qui perdait la property `textures`. Correctif **additif** : on **capture le vrai `GameProfile` authlib** (uuid + vrai nom + textures) **avant** le déguisement (`originalProfileNms`, gardé une seule fois, jamais muté car le déguisement crée toujours un nouveau `GameProfile`), et on le **repose tel quel** via `f.set(handle, savedNms)` à la restauration — plus de reconstruction. Restauration factorisée dans `restoreAppearance(player)` (utilisé par `/undisguise` ET la mort). Le chemin de déguisement (qui marche) n'est pas touché. **Reste** : la vue **F5 du joueur** dépend toujours du `TODO resendToSelf` (relog corrige de toute façon, vu qu'il n'y a pas de persistance).

### Version : v2.3 (own-UUID + couche NMS pour le nom au-dessus de la tête)

- Base = `Bukkit.createProfile(player.getUniqueId(), name)` + `setProperties(headProfile.getProperties())` (propre **UUID**, copie nom + textures) + `player.setPlayerListName(name)`. → skin sur son perso (F5) + pas de doublon TAB + pseudo déguisé dans la TAB.
- **Nouveau v2.3** : couche NMS **additive** `forceDisplayName()` pour corriger le nom flottant **au-dessus de la tête** (qui restait le vrai pseudo en v2.2, car own-UUID empêche Bukkit de changer ce nom). On remplace par réflexion le `GameProfile.name` NMS (champ de type `com.mojang.authlib.GameProfile` sur le handle `ServerPlayer`) par un nouveau `GameProfile(uuid, pseudoDéguisement)` avec textures recopiées, puis `hide`/`show` pour que les autres relisent le nom. **Tout le NMS est en `try/catch`** : si les mappings 26.1.2 ne collent pas, le skin marche quand même (repli v2.2).
- `/undisguise` & mort → `setPlayerProfile(original)` + `setPlayerListName(null)` + `forceDisplayName(player, null)` (remet le vrai nom).

**À FINALISER en test live** : la vue **F5 du joueur lui-même** nécessite un paquet *respawn* dont la signature dépend de la version. `resendToSelf()` logge un **diagnostic** (`[diag] ...`, une fois au 1er déguisement) listant les constructeurs de `ClientboundRespawnPacket` + méthodes spawn/respawn du handle. Le `SELF_REFRESH`/`TODO(live)` reste à compléter une fois la signature confirmée. Le nom pour les **autres** joueurs est déjà corrigé en v2.3.

### Outils de dev

- **`dev/.vscode/settings.json`** : classpath IntelliSense (`java.project.referencedLibraries` → `../paper-*.jar` + `../libraries/`) pour VS Code en ouvrant `dev/`. Pas besoin de Maven ni de conteneur.
- **`dev/.vscode/extensions.json`** : recommande les extensions Java (Java Pack, debug, dependency, Maven, YAML) — VS Code les propose à l'ouverture de `dev/`.
- **Pas de devcontainer** (supprimé) : Docker ne peut pas monter le partage réseau, cf. section Build.

## Build

Source versionnée dans **`dev/src/`** (`dev/src/kamoof/KamoofLite.java` + `dev/src/plugin.yml`). Copie de référence locale : `C:\Users\valas\kamoof_build\`. Le jar Paper + `libraries/` restent à la racine du serveur ; les scripts de build remontent depuis `dev/src` jusqu'au `paper-*.jar` pour la retrouver automatiquement.

**Build en LOCAL, pas de devcontainer.** Docker Desktop refuse de bind-monter le partage réseau `\\HORUS\...` (UNC ET lettre mappée Z: → `bind source path does not exist: /run/desktop/mnt/host/uC/...`), donc un devcontainer est impossible tant que le projet vit sur le share. On compile avec le **JDK 25 local** (déjà installé, `build.ps1` testé et fonctionnel le 2026-06-22). Classpath = `paper-*.jar` + tous les jars sous `libraries/`, **JDK 25** (API Paper en class version 69).
- **Windows (principal)** : `powershell -ExecutionPolicy Bypass -File dev\src\build.ps1` (JDK 25 à `C:\Users\valas\jdk25\jdk-25.0.3+9\`).
- **Linux/WSL** : `bash dev/src/build.sh`.
- Les scripts remontent depuis `dev/src` jusqu'au `paper-*.jar` pour trouver la racine, sauvegardent l'ancien jar en `.bak`, mettent `plugin.yml` à la racine du jar + `kamoof/KamoofLite.class`, puis produisent `plugins/KamoofLite.jar`. Ensuite `restart` dans la console.

## Historique / pièges

- **Persistance (v1.3–v1.6) : toutes revertées**, elles cassaient des choses. Tentatives : `disguises.yml` réappliqué sur `PlayerJoinEvent` (gardait le skin, perdait le nom), `PlayerLoginEvent` (gardait le nom, l'auth Mojang écrasait le skin), `AsyncPlayerPreLoginEvent` mutant `event.getPlayerProfile()` (théoriquement correct mais « ça marche plus du tout » — cassait aussi le déguisement live). Si on réessaie : couche **additive** par-dessus la v1.2 fonctionnelle, tester chaque étape, ne pas toucher au chemin de déguisement live.
- **v1.4** : ancienne version stable (own-UUID, copie nom+textures, refresh `hidePlayer`→`showPlayer` à 2 ticks pour les autres viewers). Utiliser le **target-UUID complet** causait skin par défaut + doublon TAB (player-info keyé par UUID, ancienne entrée jamais retirée au `/undisguise`).

## Autre serveur (INACTIF)

`C:\Users\valas\Programs\paper` — Paper 1.21.3, plugin complet KamoofSMP-1.4.0, derniers logs 2024-12. **Ce n'est PAS le serveur en usage.**
