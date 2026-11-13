---
layout: default
lang: nl
title: "Hoeveel een boilercv bespaart in de goedkoopste Nord Pool-uren"
permalink: /nl/blog/save-on-water-heating/
date: 2026-11-04
---

Een elektrische boiler is het beste eerste doelwit voor spotprijsautomatisering:
het water blijft urenlang heet, dus je kunt verwarmen wanneer stroom het goedkoopst is —
het gezin merkt er niets van.

## De berekening

Aannames (pas aan voor je eigen boiler):

- 80 liter boiler, 2 kW verwarmingselement
- één volledige verwarmingscyclus per dag ≈ **4 kWh**
- gemiddelde Nord Poolprijs incl. btw en marge: **€0.15/kWh**
- gemiddelde prijs in de 4 goedkoopste uren: **€0.07/kWh**

| Scenario | energiekosten per jaar |
|---|---|
| Verwarmen op willekeurige tijden / 's avonds (4 kWh × 365 × €0.15) | **€219** |
| Verwarmen in de 4 goedkoopste uren (4 kWh × 365 × €0.07) | **€102** |
| **Bespaard** | **≈ €117/jaar** |

Vermenigvuldig met je echte prijverschil. In volatiele maanden is het gat tussen het
daggemiddelde en de goedkoopste uren veel groter — een besparing van €150–300/jaar op
één boiler is realistisch in de Baltische landen en Finland.

## Waarom de stekker en niet alleen de app

De truc: het schema wordt **direct in de stekker opgeslagen** (zowel Tasmota als Shelly
bewaren timers in het apparaat). De telefoon mag uit zijn, internet mag wegvallen — de
boiler verwarmt gewoon in de goedkope uren. Elke dag herschrijft de app de timers met
de prijzen van de volgende dag.

## Wat je nodig hebt

1. Een stekker met [Tasmota](https://tasmota.github.io/docs/)-, [OpenBeken](https://github.com/openshwprojects/OpenBK7231T_App)- of **Shelly**-firmware —
   bijvoorbeeld [Nous A1T](https://nous.technology/product/a1t.html) of
   [Shelly Plug M Gen3](https://www.shelly.com/products/shelly-plug-m-gen3-white)
2. De gratis [Spot Price Plugs-app](https://github.com/derrij/SpotPricePlugs/releases/latest)
3. Drie minuten installatie: vind stekkers → kies uren → klaar

Geen abonnement, geen cloud, Home Assistant niet nodig.

*De cijfers zijn indicatief — je besparing hangt af van je tarief, de grootte van de
boiler en je gebruik.*
