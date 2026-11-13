---
layout: default
lang: sv
title: "Hur mycket sparar ett varmvattenberåd på Nord Pool:s billigaste timmar"
permalink: /sv/blog/save-on-water-heating/
date: 2026-11-04
---

En eldriven varmvattenberåd är det bästa första målet för spotprisautomatisering:
vattnet förblir varmt i timmar, så det kan värmas när elen är som billigast —
familjen märker ingen skillnad.

## Beräkningen

Antaganden (anpassa till din egen beråd):

- 80 liters beråd, 2 kW värmeelement
- en full värmecykel per dag ≈ **4 kWh**
- medelpris Nord Pool inkl. moms och påslag: **€0.15/kWh**
- medelpris under de 4 billigaste timmarna: **€0.07/kWh**

| Scenario | energikostnad per år |
|---|---|
| Uppvärmning på slumpmässiga tider / kvällar (4 kWh × 365 × €0.15) | **€219** |
| Uppvärmning under de 4 billigaste timmarna (4 kWh × 365 × €0.07) | **€102** |
| **Sparat** | **≈ €117/år** |

Multiplicera med din verkliga prisskillnad. I volatila månader är gapet mellan dagens
medelpris och de billigaste timmarna mycket större — en besparing på €150–300/år för en
beråd är realistisk i Baltikum och Finland.

## Varför kontakten, inte bara appen

Tricket är att schemat skrivs **in i kontakten själva** (både Tasmota, OpenBeken och Shelly lagrar
timers i enheten). Telefonen kan vara avstängd, internetet kan försvinna — beråden värmer
ändå under de billiga timmarna. Varje dag skriver appen om timersna med nästa dags priser.

## Vad du behöver

1. En kontakt med [Tasmota](https://tasmota.github.io/docs/)-, [OpenBeken](https://github.com/openshwprojects/OpenBK7231T_App)- eller **Shelly**-firmware —
   till exempel [Nous A1T](https://nous.technology/product/a1t.html) eller
   [Shelly Plug M Gen3](https://www.shelly.com/products/shelly-plug-m-gen3-white)
2. Den gratis [Spot Price Plugs-appen](https://github.com/derrij/SpotPricePlugs/releases/latest)
3. Tre minuters installation: hitta kontakter → välj timmar → klart

Ingen prenumeration, ingen molntjänst, ingen Home Assistant krävs.

*Siffrorna är illustrativa — din besparing beror på ditt avtal, berådets storlek och användning.*
