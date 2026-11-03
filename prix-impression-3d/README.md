# Prix Impression 3D (Android)

Application Android qui calcule le **prix de vente d'une pièce imprimée en 3D**.

## Ce qui est pris en compte

| Poste | Calcul |
|---|---|
| Matière | poids (g) ÷ 1000 × prix de la bobine (€/kg) |
| Électricité | puissance (W) ÷ 1000 × durée (h) × prix du kWh |
| Machine | (prix imprimante ÷ durée de vie (h) + entretien €/h) × durée |
| Main d'œuvre | temps (min) ÷ 60 × taux horaire |
| Échecs | % appliqué à matière + électricité + machine |
| Frais supplémentaires | montant fixe (emballage, peinture, vis…) |

- **Coût de revient** = somme des postes
- **Prix HT** = coût de revient + marge (%)
- **Prix TTC** = prix HT + TVA (%) — mettre 0 % en franchise de TVA (micro-entreprise)
- **Prix conseillé** = prix TTC arrondi aux 10 centimes supérieurs
- Gestion de la quantité, partage du devis (SMS, mail, WhatsApp…), valeurs mémorisées.

## Récupérer l'APK

Chaque push déclenche le workflow GitHub Actions **« Android – Prix Impression 3D »**.
Ouvrir l'onglet *Actions* du dépôt → dernière exécution → section *Artifacts* →
télécharger `Prix-Impression-3D-apk`, dézipper, puis installer `Prix-Impression-3D.apk`
sur le téléphone (autoriser « installer des applications inconnues »).

## Compiler soi-même

Android Studio : *Open* → dossier `prix-impression-3d`, puis *Run*.
En ligne de commande (SDK Android installé) :

```bash
cd prix-impression-3d
./gradlew assembleRelease   # APK dans app/build/outputs/apk/release/
./gradlew testDebugUnitTest # tests du calcul
```

Kotlin · Jetpack Compose · Material 3 · Android 8.0+ (API 26).
