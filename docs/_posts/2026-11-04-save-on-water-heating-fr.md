---
layout: default
lang: fr
title: "Combien économise un chauffe-eau pendant les heures les moins chères de Nord Pool"
permalink: /fr/blog/save-on-water-heating/
date: 2026-11-04
---

Un chauffe-eau électrique est la meilleure première cible pour l'automatisation selon
les prix de gros : l'eau reste chaude pendant des heures, on peut donc chauffer quand
l'électricité est la moins chère — la famille ne voit aucune différence.

## Le calcul

Hypothèses (à adapter à votre chauffe-eau) :

- chauffe-eau de 80 litres, résistance de 2 kW
- un cycle complet de chauffe par jour ≈ **4 kWh**
- prix moyen Nord Pool TTC et marges comprises : **€0.15/kWh**
- prix moyen pendant les 4 heures les moins chères : **€0.07/kWh**

| Scénario | Coût énergétique par an |
|---|---|
| Chauffe à des heures aléatoires / le soir (4 kWh × 365 × €0.15) | **€219** |
| Chauffe pendant les 4 heures les moins chères (4 kWh × 365 × €0.07) | **€102** |
| **Économisé** | **≈ €117/an** |

Multipliez par votre propre écart de prix. Les mois volatils creusent bien plus l'écart
entre la moyenne journalière et les heures les moins chères — une économie de
€150–300/an sur un seul chauffe-eau est réaliste dans les pays baltes et en Finlande.

## Pourquoi la prise, pas seulement l'application

L'astuce : le planning est écrit **dans la prise elle-même** (Tasmota comme Shelly
stockent les minuteurs dans l'appareil). Le téléphone peut être éteint, Internet peut
tomber — le chauffe-eau chauffe quand même pendant les heures creuses. Chaque jour,
l'application réécrit les minuteurs avec les prix du lendemain.

## Ce qu'il vous faut

1. Une prise avec le firmware [Tasmota](https://tasmota.github.io/docs/) ou **Shelly** —
   par exemple [Nous A1T](https://nous.technology/product/a1t.html) ou
   [Shelly Plug M Gen3](https://www.shelly.com/products/shelly-plug-m-gen3-white)
2. L'application gratuite [Spot Price Plugs](https://github.com/derrij/SpotPricePlugs/releases/latest)
3. Trois minutes d'installation : trouver les prises → choisir les heures → c'est tout

Pas d'abonnement, pas de cloud, Home Assistant inutile.

*Les chiffres sont illustratifs — vos économies dépendent de votre tarif, de la taille
du chauffe-eau et de votre usage.*
