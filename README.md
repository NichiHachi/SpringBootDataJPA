---
titlepage: true
title: Rex fotoshare
author: Eymeric Dechelette, Kevin Angelier-Leplan
date: 2025-12-05
---

# Répartition des taches

Pour de développement de l'application, nous avons suivie cette repartition des taches :

- Eymeric : 
  - Création des vm alpine linux
  - configuration des vm
  - configuration réseau et montage nfs
  - configuration des bases de données
  - Création de configuration docker/docker-compose pour un environnement de développement plus simple et *reproducible*
  - Vérification de la sécurité des fonctionnalités
- Kevin : 
  - Création de la structure du projet java
  - Architecture logicielle
  - Developpement des templates
  - Developpement du backend
  

# Avis sur le module

## Point positif

Le module était intéressant, surtout parce qu’on a enfin pu faire un peu de système, ce qu’on n’a pas vraiment l’habitude de faire en profondeur (à part lors des TP Windows). Le fait de travailler sur une architecture 3-tiers, comme en entreprise, était aussi une bonne idée et permettait de comprendre comment une application est réellement structurée.

## Point négatif

Une petite explication théorique au début aurait été utile, par exemple sur comment le flux circule, pourquoi utiliser un montage NFS, ou plus généralement comment l’architecture est pensée. Ça aurait donné une vision d’ensemble avant de commencer.

Beaucoup de problèmes dans les VM auraient aussi pu être évités si tout le monde avait le même environnement, par exemple avec des VM fournies sur un serveur de Polytech pour standardiser les setups.

Enfin, utiliser Java + Thymeleaf n’apporte rien de vraiment nouveau : c’est juste un autre langage et un autre framework. On aurait pu aller plus loin sur la partie système, par exemple avec des pipelines de déploiement, du load balancing, du conteneur, du cluster Kubernetes ou autre. Cela aurait rendu le module plus complet et plus orienté système et technologie actuelle.
