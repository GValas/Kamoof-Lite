# KamoofLite

Plugin de déguisement pour serveur **Paper** (Minecraft), inspiré du style *Kamoof SMP*.

> Tu lâches ta tête en mourant, et un clic-droit sur la tête d'un joueur te transforme en lui.

## Fonctionnalités

- 💀 **Drop de tête à la mort** — tu lâches ta propre tête (avec ton skin) quand tu meurs.
- 🎭 **Déguisement au clic-droit** — clic-droit sur une tête de joueur → tu prends son apparence (skin + pseudo, au-dessus de la tête et dans la TAB), pour toi comme pour les autres.
- 🔄 **`/undisguise`** — tu reprends ton apparence d'origine. La mort réinitialise aussi le déguisement.
- ⏳ **Pas de persistance** — un déguisement ne survit pas à une reconnexion (volontaire).

## Compatibilité

- **Paper 26.1.2** (API `1.21`), testé sur Java 25.
- Utilise une couche NMS *additive* (réflexion, en `try/catch`) pour corriger le nom flottant et les skins ; si les mappings d'une version ne collent pas, le skin fonctionne quand même en repli.

## Installation

1. Construire le jar (voir [Build](#build)) ou récupérer `KamoofLite.jar`.
2. Le déposer dans le dossier `plugins/` du serveur.
3. Redémarrer (ou `restart` dans la console).

## Commandes

| Commande      | Description                          |
|---------------|--------------------------------------|
| `/undisguise` | Reprendre son apparence d'origine.   |

## Build

Compilation **locale** avec le **JDK 25** (l'API Paper 26.1.2 est en class version 69). Le classpath = `paper-*.jar` + tous les jars de `libraries/` ; les scripts retrouvent la racine du serveur automatiquement en remontant depuis `src/`.

```powershell
# Windows (méthode principale)
powershell -ExecutionPolicy Bypass -File src\build.ps1
```

```bash
# Linux / WSL
bash src/build.sh
```

Les scripts sauvegardent l'ancien `plugins/KamoofLite.jar` en `.bak`, puis produisent le nouveau jar. Détails et arborescence : [`src/README.md`](src/README.md).

## Structure

```
KamoofLite/
├── src/
│   ├── kamoof/KamoofLite.java   code du plugin
│   ├── plugin.yml               metadata
│   ├── build.ps1 / build.sh     build local
│   └── README.md                doc dev détaillée
├── .vscode/                     classpath IntelliSense (sans Maven ni Docker)
└── CLAUDE.md                    contexte serveur + historique des versions
```

## Auteur

Développé par Claude (Anthropic) pour le serveur HORUS.
