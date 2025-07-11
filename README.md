# SunriseCalc 🌅

**Calculez facilement les heures de lever du soleil et des aubes pour votre position !**

SunriseCalc est une application Android simple et intuitive qui vous permet d'obtenir les heures précises pour :
*   L'aube astronomique
*   L'aube nautique
*   L'aube civile (Aurore)
*   Le lever du soleil

Entrez simplement votre QTH Locator (un système de coordonnées utilisé par les radioamateurs et d'autres passionnés) ou utilisez le GPS de votre appareil pour déterminer automatiquement votre position et obtenir les informations.

## Fonctionnalités ✨

*   **Saisie manuelle du QTH Locator :** Entrez votre QTH pour obtenir les heures correspondantes.
*   **Utilisation du GPS :** Laissez l'application déterminer votre QTH à partir de votre position actuelle pour une saisie rapide et facile.
*   **Affichage clair des événements :** Visualisez les heures pour les différentes aubes et le lever du soleil.
*   **Informations sur les événements :** Apprenez-en plus sur la signification de chaque événement astronomique grâce à une section d'information intégrée.
*   **Interface utilisateur simple et épurée.**

## Comment utiliser l'application ? 🤔

1.  **Ouvrez l'application SunriseCalc.**
2.  **Entrez votre QTH Locator** dans le champ prévu à cet effet (par exemple, `JN23FS`).
    *   **OU**
    *   **Appuyez sur l'icône GPS** 🛰️ à côté du champ de saisie. Si vous y êtes invité, autorisez l'application à accéder à votre position. Votre QTH sera automatiquement rempli.
3.  **Appuyez sur le bouton "Obtenir les heures".**
4.  Les résultats s'afficheront sous le bouton, indiquant les différentes heures pour votre position et la date actuelle.
5.  Pour comprendre ce que signifie chaque événement (Aube Astronomique, Nautique, Civile), appuyez sur le bouton "**Afficher les infos sur les événements**".

## Pour les développeurs (Configuration) 👨‍💻

Ce projet utilise :
*   Kotlin
*   Jetpack Compose pour l'interface utilisateur
*   OkHttp pour les requêtes réseau
*   Les services de localisation Google Play pour la fonctionnalité GPS

**Permissions requises :**
*   `android.permission.INTERNET` (pour récupérer les données depuis le serveur)
*   `android.permission.ACCESS_FINE_LOCATION` (pour la fonctionnalité GPS)

L'application communique avec un serveur backend pour obtenir les données de lever du soleil. L'URL du serveur est actuellement codée en dur dans l'application.

## Contributions 🤝

Les contributions sont les bienvenues ! N'hésitez pas à ouvrir une issue ou à soumettre une pull request.

---
