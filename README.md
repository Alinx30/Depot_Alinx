# Calendrier-sideral
Calendrier Sidéral composé de 364 jours, 13 mois de 28 jours. avec modification de l'espace temps de la seconde.

## Fioles Enchantées

Jeu de tri de potions (puzzle) en un seul fichier : ouvrez `index.html` dans un navigateur.

- Touchez une fiole puis une autre pour verser la couleur du dessus.
- On ne peut verser que sur la même couleur ou dans une fiole vide.
- Gagnez en remplissant chaque fiole d'une seule couleur.
- Boutons : Annuler (5 par niveau), Recommencer, + Fiole (1 par niveau).
- Niveaux générés automatiquement et toujours solubles ; la progression est sauvegardée.

## Application Android (APK)

`FiolesEnchantees.apk` s'installe sur Android 7.0 ou plus récent (autorisez « Installer des applications inconnues »).

Pour reconstruire l'APK après une modification de `index.html` :

    python3 android/build.py --tools <dossier contenant android.jar, dx.jar et apksig.jar>

Le script n'a pas besoin du SDK Android : il génère lui-même le manifeste binaire et `resources.arsc`.
La clé de signature (`android/fioles.p12`) n'est pas versionnée ; si elle est absente, une nouvelle clé est créée.
