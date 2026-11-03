# IVRY OS — launcher Android TV pour Chromecast avec Google TV 4K

Kotlin · Jetpack Compose · MVVM · DataStore · Coroutines/Flow · sans root, sans modification du firmware.

## État réel du projet

| Élément | État |
|---|---|
| Code source complet | Fourni |
| Compilation | **Non vérifiée** : ce projet a été écrit sans SDK Android disponible. Première étape chez toi : ouvrir dans Android Studio et compiler. |
| APK signé | **À générer** par toi (étape 3 ci-dessous) : la clé de signature doit rester sur ta machine. |
| Tests sur Chromecast | **Non faits**. Aucune fonction n'est à considérer comme validée avant la checklist de la section 7. |
| Identifiants des arrêts IDFM | **À renseigner** (section 4) : je ne les ai pas inventés. |
| Codes de lignes trafic | Préremplis (`C01377` métro 7, `C01727` RER C) — **à vérifier** dans le référentiel PRIM. |
| Station Vélib' 42021 | Vérifiée **à l'exécution** dans les données officielles GBFS ; le nom réel s'affiche dans Paramètres › Sources. |
| Fonds d'écran | Ligne d'horizon d'Ivry et motifs dessinés par thème. Aucune photo inventée. Import de photos personnelles : pas encore.
| Création de thèmes personnalisés | Pas dans cette version (4 thèmes + verre, luminosité, mode sombre, performance). |

## 1. Structure

```
app/src/main/java/fr/ivry/os/
├── MainActivity.kt          activité HOME, écran permanent, suivi d'inactivité
├── MainViewModel.kt         état global, lancements, configuration ADB
├── data/
│   ├── DataEngine.kt        IVRY DATA ENGINE (boucles, cache, fraîcheur, pause en arrière-plan)
│   ├── Sources.kt           Open-Meteo, PRIM (SIRI Lite), GBFS Vélib'
│   ├── Settings.kt          DataStore (thème, sources, favoris, compteurs d'usage)
│   ├── Apps.kt              détection PackageManager, lancement, tri par usage
│   ├── DeviceInfo.kt        version Android, OpenGL ES, mémoire, résolution
│   ├── Http.kt / Models.kt
└── ui/
    ├── Root.kt              thèmes, démarrage, barre latérale, anti-marquage, fonds
    ├── HomeScreen.kt        accueil 4 zones
    ├── Details.kt           détails météo / métro / RER / Vélib'
    ├── AppsScreen.kt        Cinéma, Applications, Favoris (recherche, organisation)
    ├── SettingsScreen.kt    réglages, sources, centre de contrôle, système
    ├── Components.kt / Theme.kt
proxy/                       Cloudflare Worker qui garde la clé PRIM
```

Fréquences d'actualisation : horloge 1 s, météo 15 min, prévisions 30 min, métro et RER 45 s, Vélib' 60 s.
Rythme ×3 après 5 min sans action ; aucun appel réseau quand une autre application est au premier plan.
Les données sont mises en cache sur disque : au démarrage hors ligne, la dernière valeur s'affiche, marquée « PÉRIMÉ » si trop ancienne.

## 2. Clé PRIM et proxy (obligatoire pour métro et RER)

1. Crée un compte sur prim.iledefrance-mobilites.fr et génère une clé API.
2. Crée un compte Cloudflare (gratuit), installe `wrangler` (`npm i -g wrangler`), puis dans `proxy/` :
   ```
   wrangler login
   wrangler secret put PRIM_API_KEY      # colle ta clé PRIM
   wrangler secret put APP_TOKEN         # invente une phrase secrète
   wrangler deploy
   ```
3. Note l'URL affichée (`https://ivry-prim-proxy.<toi>.workers.dev`). Elle et le jeton se saisissent sur la TV (section 4).

Météo et Vélib' fonctionnent sans clé ni proxy.

## 3. Compiler et signer l'APK

1. Android Studio (Koala ou plus récent) › Open › dossier `ivry-os`. Laisse la synchronisation Gradle se faire.
2. Corrige les éventuelles erreurs de compilation signalées (le code n'a pas pu être compilé de mon côté).
3. Build › Generate Signed App Bundle / APK › APK › crée une clé (garde le fichier `.jks` et ses mots de passe) › variante `release`.
4. L'APK se trouve dans `app/release/app-release.apk`.

## 4. Installation sur le Chromecast (ADB)

Sur le Chromecast : Paramètres › Système › À propos › clique 7 fois sur « Version de l'OS Android TV » ; puis Paramètres › Système › Options pour les développeurs › Débogage USB / réseau activé. Note l'adresse IP (Paramètres › Réseau).

```
adb connect 192.168.x.x:5555          # accepter l'autorisation sur la TV
adb install -r app-release.apk
```

Configuration rapide depuis l'ordinateur (évite de taper à la télécommande) :

```
adb shell am start -n fr.ivry.os/.MainActivity \
  --es proxyUrl "https://ivry-prim-proxy.xxx.workers.dev" \
  --es proxyToken "ta-phrase-secrete" \
  --es metroStop "STIF:StopArea:SP:xxxxx:" \
  --es rerStop "STIF:StopArea:SP:xxxxx:" \
  --es rerFilter "Paris,Versailles,Pontoise,Saint-Quentin"
```

Trouver `metroStop` et `rerStop` : dans le jeu de données IDFM « Arrêts et lignes associées » (ou le référentiel PRIM), cherche « Mairie d'Ivry » (métro 7) et « Ivry-sur-Seine » (RER C) et reprends l'identifiant de zone d'arrêt au format `STIF:StopArea:SP:<id>:`.
Le filtre métro est préréglé sur « Courneuve ». Le filtre RER garde les trains dont la destination contient l'un des mots : adapte-le après avoir vu les destinations réelles dans le détail RER.

## 5. Faire d'IVRY OS l'accueil

Google TV ne propose pas de choisir un autre launcher. La méthode courante, sans root, consiste à désactiver le launcher Google :

```
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

Puis appuie sur Accueil : Android propose IVRY OS. Ce qui est perdu tant que le launcher Google est désactivé : recommandations Google TV, profils, et certaines entrées du menu. Les mises à jour système peuvent réactiver le launcher Google ; il suffit alors de relancer ces commandes.

Sans désactiver quoi que ce soit, IVRY OS reste utilisable comme application normale depuis la liste des applis.

## 6. Retour au launcher Google TV d'origine

```
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
adb reboot
```

Pour supprimer complètement : `adb uninstall fr.ivry.os`.
En dernier recours (ADB inaccessible) : réinitialisation d'usine du Chromecast (bouton physique à l'arrière maintenu ~20 s), qui restaure tout.

## 7. Checklist de validation sur le Chromecast réel

- [ ] 1. Installation de l'APK
- [ ] 2. Configuration du launcher (section 5)
- [ ] 3. Démarrage sur l'accueil après appui sur Accueil et après redémarrage
- [ ] 4. Lancement de myCANAL
- [ ] 5. Lancement de YouTube
- [ ] 6. Détection des autres applications (« Le reste », Applications)
- [ ] 7. Navigation télécommande : tous les modules, cartes et menus atteignables ; Retour ferme les détails
- [ ] 8. Météo : valeurs cohérentes avec une autre source
- [ ] 9. Métro 7 : passages direction La Courneuve
- [ ] 10. RER C : trains direction Paris / nord
- [ ] 11. Vélib' : nom de station correct pour le n° 42021
- [ ] 12. Hors ligne : couper le Wi-Fi, vérifier « HORS LIGNE » et les mentions « PÉRIMÉ »
- [ ] 13. Affichage permanent : écran toujours allumé sur l'accueil ; la TV s'éteint bien à la télécommande
- [ ] 14. Retour au launcher Google TV (section 6)

## 8. Notes techniques

- Activité unique déclarée `HOME` + `LEANBACK_LAUNCHER`, `singleTask`. Retour sur l'accueil ne quitte pas le launcher.
- Interface conçue pour 960 × 540 dp, rendu 1080p ou 4K selon la sortie du Chromecast.
- Anti-marquage : décalage de 3 à 5 px toutes les 3 min, atténuation des couleurs après 5 min d'inactivité, réduction de luminosité de 0 à 60 %, mode sombre renforcé.
- Écran permanent : `FLAG_KEEP_SCREEN_ON`, actif seulement tant que l'accueil est visible. La veille du téléviseur reste prioritaire.
- Aucun flou temps réel (coûteux sur Chromecast) : le « verre fumé » est obtenu par transparence et reflet.
- Bannières réduites à 320 × 180 px ; icônes chargées seulement en l'absence de bannière.
- Vélib' : le fichier de statut GBFS (toutes les stations) est téléchargé chaque minute ; c'est le seul moyen officiel d'obtenir une station.
- myCANAL et YouTube sont seulement lancés : aucun contenu interne n'est intercepté ou affiché.
