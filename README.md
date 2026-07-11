# PDF Empire

Lecteur et boîte à outils PDF pour Android, 100 % hors ligne, développé et compilé **sans ordinateur** : téléphone + GitHub + GitHub Actions.

## Ce qui est livré dans la v0.2.0 (fonctionnel, rien de factice)

- **Accueil** : actions rapides, documents récents, favoris
- **Ouverture de PDF** via le sélecteur de fichiers (SAF), « Ouvrir avec » et réception de partage
- **Lecteur** : défilement vertical page par page, zoom par pincement (2 doigts) et double-tap, indicateur de page, « aller à la page », reprise à la dernière page lue
- **Historique et favoris** (base Room) : recherche, tri (nom/date/taille), retrait avec confirmation
- **Fusionner des PDF** (Apache PDFBox) : ordre modifiable, sortie via SAF
- **Extraire des pages** : plage validée (ex. `1-4, 8, 10-12`), nouveau fichier créé, original intact
- **Images vers PDF** : plusieurs images, ordre modifiable
- **Partage** de tout document ou résultat (FileProvider)
- **Remplir et signer** (à la Acrobat) : détection des champs de formulaire (texte, cases à cocher, listes déroulantes) rendus cliquables sur la page, saisie des valeurs avec **aperçu en temps réel** (le PDF se re-rend avec tes valeurs incrustées avant même d'enregistrer), **signature et paraphe dessinés au doigt** puis placés où tu veux (taille ajustable) et incrustés dans le PDF — **aperçu en temps réel** : chaque valeur saisie est incrustée et re-rendue sur la page avant l'enregistrement — le résultat est enregistré en copie (formulaire aplati, visible dans tous les lecteurs), l'original jamais touché
- **Réglages** : thème système/clair/sombre, vider le cache, effacer l'historique
- **Messages d'erreur clairs** : PDF protégé par mot de passe, fichier corrompu
- **Confidentialité** : aucune permission Internet, aucune donnée envoyée
- **Tests unitaires** exécutés à chaque build
- **CI GitHub Actions** : APK debug en artefact à chaque push, workflow release signé sur tag

## Pas encore inclus (prochaines étapes, dans l'ordre prévu)

1. Scanner avec la caméra (CameraX) + recadrage
2. OCR (ML Kit, hors ligne)
3. Annotations (surlignage, notes, dessin libre)
4. Compression / optimisation
5. Protection et déverrouillage par mot de passe
6. Rotation / suppression / réorganisation de pages
7. Filigrane, numérotation
8. Hilt (le projet utilise pour l'instant une injection manuelle simple — `AppContainer` — choisie pour garantir une compilation fiable dès la première version ; la migration est directe)

## Comment obtenir l'APK (sans ordinateur)

1. Crée un dépôt **vide** sur github.com (bouton *New*).
2. Envoie le projet :
   - **Option Termux** : dézippe le projet, `cd pdf-pocket-lite`, puis `bash push_to_github_termux.sh` (le script te guide, il faut un jeton GitHub).
   - **Option interface web** : *uploading an existing file* fonctionne aussi, mais Termux est plus fiable pour un projet complet.
3. Onglet **Actions** du dépôt → workflow **Build APK** se lance seul à chaque push.
4. Quand il est vert (~5 min), ouvre le run → section **Artifacts** → télécharge `pdf-pocket-lite-debug-apk` → dézippe → installe `app-debug.apk` (autorise les sources inconnues).

## Release signée (optionnel)

Ajoute ces secrets dans *Settings → Secrets and variables → Actions* :

| Secret | Contenu |
|---|---|
| `KEYSTORE_BASE64` | ton keystore encodé : `base64 -w0 monkeystore.jks` |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | alias de la clé |
| `KEY_PASSWORD` | mot de passe de la clé |

Puis pousse un tag : `git tag v0.2.0 && git push origin v0.2.0` → workflow **Build Release APK**. Sans secrets, la release sort non signée.

Pour créer un keystore dans Termux :
```
pkg install openjdk-21
keytool -genkeypair -v -keystore monkeystore.jks -alias macle -keyalg RSA -keysize 2048 -validity 10000
```

## Personnalisation

- **Nom de l'app** : `app/src/main/res/values/strings.xml` (`app_name`) et `values-en`
- **Package / applicationId** : `app/build.gradle.kts` (`namespace`, `applicationId`) + renommer `app/src/main/java/com/pdfpocket/lite`
- **Icône** : `app/src/main/res/drawable/ic_launcher_foreground.xml` et couleur dans `values/colors.xml`
- **Couleurs du thème** : `app/src/main/java/com/pdfpocket/lite/ui/theme/Color.kt`
- **Langues** : le français est la langue par défaut (`values/strings.xml`), l'anglais dans `values-en/`. Pour ajouter une langue : copier dans `values-xx/`.

## Architecture

```
app/src/main/java/com/pdfpocket/lite/
├── PdfPocketApp.kt        Application + AppContainer (DI manuel)
├── MainActivity.kt        Intents entrants (VIEW/SEND), thème
├── core/                  Parser de plages, validation de noms, fichiers, fabrique de ViewModels
├── data/                  Room (historique/favoris), DataStore (réglages)
├── pdf/                   PdfToolbox (fusion, extraction, images→PDF), FormToolbox (formulaires, signatures)
├── navigation/            Routes + Scaffold + barre inférieure
├── ui/theme/              Material 3, couleurs dynamiques (Android 12+)
└── features/              home, files, viewer, fillsign, tools (merge/split/images), settings
```

Choix techniques : Kotlin 2.0.20, Jetpack Compose (BOM 2024.09), Material 3, Room + KSP, DataStore, Navigation Compose, `PdfRenderer` système pour l'affichage, [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache 2.0) pour la manipulation. minSdk 26, targetSdk 35, JDK 21.

Règles respectées : aucun fichier original n'est jamais écrasé (toute sortie passe par « Créer un document » du système), aucun secret dans le code, aucun bouton sans fonction réelle.

## Licences

- Projet : MIT (voir `LICENSE`)
- PdfBox-Android © Tom Roush et contributeurs — Apache License 2.0
- AndroidX / Jetpack Compose — Apache License 2.0
