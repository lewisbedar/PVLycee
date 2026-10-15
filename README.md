# PVLycée

Application Android humoristique qui imprime de faux procès-verbaux pour les petits écarts en classe :
pieds sur les chaises, bavardages, chewing-gums, téléphone…

- Terminal prioritaire : **Sunmi V2** (Android 7.1) et son imprimante thermique 58 mm intégrée.
- Sinon : n'importe quel Android 6.0 ou plus récent avec une **imprimante thermique Bluetooth**
  premier prix (58 mm ou 80 mm, ESC/POS ou « cat printer » GB01, GT01, MX05, MX10…).
- **Aucune permission Internet** : la liste des élèves et les PV restent sur l'appareil.

> Document humoristique sans valeur légale. À utiliser avec bienveillance.

---

## 1. Compiler l'APK

### Prérequis

- JDK 17 ou plus récent.
- Le SDK Android avec la plateforme **Android 37** (`platforms;android-37.0`). Le plus simple est
  d'installer [Android Studio](https://developer.android.com/studio), qui installe le SDK.
  Sans Android Studio, utilisez les *command-line tools* :
  `sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"`.
- Indiquez l'emplacement du SDK, soit avec la variable `ANDROID_HOME`, soit dans un fichier
  `local.properties` à la racine du projet : `sdk.dir=/chemin/vers/Android/Sdk`.

### Compilation

```bash
./gradlew assembleDebug
```

L'APK est produit dans `app/build/outputs/apk/debug/app-debug.apk`.
Sous Windows, utilisez `gradlew.bat assembleDebug`.

### Tests

```bash
./gradlew test
```

Les tests couvrent les règles métier (points, classes de contravention, choix des gages, numérotation,
import CSV), l'encodage d'impression (paquets cat printer et CRC-8, commandes ESC/POS raster) et la
détection du protocole. D'autres tests utilisent Robolectric : ils dessinent de vrais tickets et
parcourent l'interface sur un écran 720 × 1440 (Sunmi V2) et sur un écran 480 × 800. Les images
produites sont rangées dans `app/build/captures/` ; quelques exemples sont dans `docs/captures/`.

---

## 2. Installer l'application

### Méthode A : avec adb (recommandée, notamment pour le Sunmi V2)

1. Sur l'appareil, activez les **options pour les développeurs** : *Paramètres > À propos de
   l'appareil*, puis touchez 7 fois **Numéro de build**.
2. Dans *Paramètres > Options pour les développeurs*, activez **Débogage USB**.
3. Reliez l'appareil à l'ordinateur en USB et acceptez l'empreinte de l'ordinateur sur l'écran.
4. Installez :
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### Méthode B : copie du fichier APK

1. Copiez `app-debug.apk` sur l'appareil (câble USB, carte microSD ou clé USB OTG).
2. Ouvrez-le avec le gestionnaire de fichiers de l'appareil.
3. Autorisez l'installation d'applications de **sources inconnues** :
   - **Android 7.1 et avant (Sunmi V2)** : *Paramètres > Sécurité > Sources inconnues*, à activer.
   - **Android 8 et plus** : au moment d'ouvrir l'APK, Android propose d'autoriser le gestionnaire
     de fichiers. Sinon : *Paramètres > Applications > (votre gestionnaire de fichiers) >
     Installer des applis inconnues*.
4. Confirmez l'installation. Vous pouvez désactiver l'option ensuite.

Certains micrologiciels Sunmi limitent l'installation depuis un fichier. Dans ce cas, utilisez adb.

---

## 3. Premier lancement

1. **Réglages** : saisissez le nom de l'établissement, la salle par défaut et le nom de l'agent
   verbalisateur (le vôtre).
2. **Imprimante** : sur un Sunmi, l'imprimante intégrée est détectée et choisie automatiquement.
   Sinon, voir la section 4.
3. Touchez **Imprimer un ticket de test** et vérifiez la mire (section 5).
4. **Registre** : ajoutez vos classes et vos élèves à la main, ou importez un fichier CSV.

### Format du fichier CSV

Une colonne pour le prénom, une colonne pour la classe. Séparateur `;`, `,` ou tabulation.
La ligne d'en-tête est facultative ; si elle est présente (`Prénom;Classe`), l'ordre des colonnes
peut être inversé. Un export Excel (UTF-8 ou Windows-1252) convient.

```
Prénom;Classe
Léa;2nde 3
Yanis;2nde 3
Inès;1re 2
```

Les élèves déjà présents (même prénom, même classe) ne sont pas ajoutés deux fois.

### Verbaliser en 3 touches

La classe utilisée la dernière fois est déjà sélectionnée. Ensuite :
1. touchez l'**élève** (ou « Contrevenant non identifié ») ;
2. touchez l'**infraction** (la rangée « Fréquentes » regroupe les 4 plus utilisées) ;
3. touchez **VERBALISER**.

Le gage est proposé automatiquement et reste modifiable. Le PV n'est enregistré qu'après une
impression réussie. En cas d'échec, un message explique le problème et le bouton **Réessayer**
relance l'impression du même ticket. Sans imprimante (mode aperçu), le bouton devient
**Enregistrer sans imprimer**.

### Règles appliquées

- Chaque élève a un permis de bonne conduite de **12 points** ; chaque infraction en retire selon
  le catalogue. Le solde ne descend pas sous 0. À 0, le ticket affiche **PERMIS SUSPENDU**.
- **Classe de contravention** = nombre de PV précédents de l'élève + 1, plafonnée à 5.
- **Gage par défaut** : parmi les gages associés à la catégorie, celui dont le rang correspond au
  nombre de récidives (1er PV : simple avertissement), plafonné au dernier gage de la liste.
  L'ordre des gages se règle dans *Réglages > Gages*.
- **Numéro de PV** : `AAAA-0001`, compteur commun à tous les élèves, qui repart à 0001 au
  1er janvier. Un numéro n'est consommé que si le PV est enregistré.
- **Amnistie** (Registre) : rend les 12 points à un élève ou à toute une classe. L'historique et la
  classe de contravention sont conservés.

### Sauvegarde

*Registre > Sauvegarde > Exporter* crée un fichier JSON complet (élèves, PV, catalogue, réglages).
*Restaurer* remplace toutes les données par celles du fichier ; le choix d'imprimante de l'appareil
est conservé.

---

## 4. Appairer une imprimante Bluetooth

Allumez l'imprimante et ouvrez *Réglages > Imprimante* (ou touchez l'indicateur en haut à droite).
Au premier passage, PVLycée demande l'autorisation Bluetooth (sur Android 6 à 11, c'est
l'autorisation de **localisation** qu'Android exige pour rechercher des appareils BLE : elle ne sert
qu'à cela).

### Imprimante ESC/POS « classique » (la plupart des imprimantes 58/80 mm à piles ou batterie)

1. Dans les paramètres Bluetooth d'**Android**, recherchez l'imprimante (souvent nommée
   `MTP-II`, `RPP02`, `PT-210`, `Printer001`…) et appairez-la. Code PIN : `0000` ou `1234`.
2. Dans PVLycée, *Réglages > Imprimante > Appareils appairés* : touchez l'imprimante.

### Cat printer (GB01, GT01, MX05, MX10…) et imprimantes BLE

Ces imprimantes ne s'appairent **pas** dans les paramètres d'Android.
1. Dans PVLycée, *Réglages > Imprimante > Recherche BLE > Rechercher*.
2. Touchez l'imprimante dans la liste (les cat printers probables sont signalées).

Le protocole est détecté automatiquement (cat printer, ESC/POS BLE ou ESC/POS classique) et
mémorisé. L'imprimante choisie est reconnectée automatiquement à chaque lancement.

---

## 5. Le ticket de test

*Réglages > Imprimer un ticket de test* imprime une mire :
- une **règle graduée** avec un trait vertical à chaque bord : les deux traits doivent toucher les
  bords du papier ;
- des **bandes noires** de 4, 8, 16 et 32 points : elles doivent être bien noires et régulières ;
- du **texte en plusieurs tailles** et un damier.

Faites ce test avant le premier vrai PV, et après chaque changement d'imprimante ou de largeur.

---

## 6. Dépannage

### Le ticket sort blanc

- Le rouleau est peut-être **à l'envers** : le papier thermique ne noircit que d'un côté. Grattez-le
  avec l'ongle : la face qui noircit doit être tournée vers la tête d'impression.
- Le papier n'est pas thermique (papier pour imprimante à ruban, par exemple).
- Le **protocole** ne correspond pas : l'imprimante reçoit des données qu'elle ignore. Forcez le
  protocole (voir plus bas) et refaites le ticket de test.
- Batterie de l'imprimante trop faible : rechargez-la.

### Le ticket est trop pâle

- **Cat printer** : *Réglages > Intensité d'impression > Foncée*.
- **Autres imprimantes** : l'intensité se règle sur l'imprimante elle-même (souvent en maintenant
  le bouton d'avance papier au démarrage, voir sa notice). Rechargez la batterie : une batterie
  faible donne des tickets pâles.
- Papier de mauvaise qualité ou ancien : essayez un autre rouleau.

### Caractères illisibles, image en escalier ou coupée

- **Largeur de papier** : une imprimante 58 mm réglée en 80 mm produit une image décalée, en
  escalier ou coupée à droite. Choisissez *Réglages > Papier > 58 mm*. Les traits verticaux de la
  mire doivent toucher les deux bords.
- Des symboles au hasard indiquent un **mauvais protocole** : forcez-en un autre.
- Ticket coupé net au milieu : l'imprimante a manqué de mémoire ou de batterie. Rechargez-la et
  réessayez.

### La connexion échoue

- Vérifiez que l'imprimante est **allumée**, chargée, et à moins de quelques mètres.
- Le **Bluetooth** de l'appareil est-il activé ? L'autorisation a-t-elle été accordée
  (*Réglages > Imprimante > Autoriser*) ?
- Une imprimante ne se connecte qu'à **un seul appareil à la fois** : déconnectez le téléphone
  qui l'utilisait.
- Sur Android 6 à 11, la **localisation** doit être activée pour que la recherche BLE trouve
  quelque chose.
- Imprimante classique : supprimez l'appairage dans les paramètres Bluetooth d'Android, appairez-la
  de nouveau, puis choisissez-la dans PVLycée.
- Éteignez puis rallumez l'imprimante, puis touchez **Reconnecter**.

### Forcer le protocole

*Réglages > Imprimante > Protocole* :
- **Détection automatique** (recommandé) ;
- **Cat printer (BLE)** pour les GB01, GT01, MX05, MX10 et modèles proches ;
- **ESC/POS Bluetooth basse consommation** pour les imprimantes ESC/POS trouvées par la recherche BLE ;
- **ESC/POS Bluetooth classique** pour les imprimantes appairées dans Android.

Après un changement, l'application se reconnecte : refaites un ticket de test.

### Sur le Sunmi V2

- Le message « Plus de papier », « Capot ouvert » ou « Surchauffe » vient de l'imprimante intégrée :
  corrigez puis touchez **Réessayer**.
- Si l'imprimante intégrée n'apparaît pas, redémarrez le terminal : le service d'impression Sunmi
  démarre avec le système.

---

## 7. Confidentialité

- Aucune permission `INTERNET` (elle est explicitement retirée du manifeste).
- La sauvegarde automatique Android (cloud) est désactivée. Les seules copies des données sont les
  fichiers que vous exportez vous-même.
- *Réglages > Remise à zéro complète* efface élèves, PV, catalogue personnalisé et réglages.

---

## 8. Organisation du code

```
app/src/main/java/fr/pvlycee/
├── domain/   règles métier pures : points, classes, gages, numérotation, CSV, catalogue par défaut
├── data/     Room (élèves, PV, catalogue), DataStore (réglages), sauvegarde JSON
├── print/    interface TicketPrinter et pilotes :
│             SunmiTicketPrinter, EscPosClassicPrinter, EscPosBlePrinter, CatPrinter, PreviewOnlyPrinter
│             encodeurs ESC/POS (GS v 0 par bandes de 64 lignes) et cat printer (CRC-8)
├── ticket/   TicketRenderer : ticket et mire dessinés sur Canvas, convertis en noir et blanc pur
└── ui/       écrans Jetpack Compose (Verbaliser, Registre, Réglages, Imprimante, Catalogue, Gages)
```

Polices embarquées (licence SIL Open Font, voir `licenses/`) : **Oswald** pour les titres,
**JetBrains Mono** pour le corps du ticket.

Versions : Kotlin 2.4, Android Gradle Plugin 9.4, Compose BOM 2026.09, Room 2.8, SDK Sunmi
`com.sunmi:printerlibrary:1.0.24`. `minSdk` 23 (Android 6.0) : c'est le minimum exigé par les
versions actuelles de Compose et d'AndroidX.
