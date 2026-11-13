---
layout: default
lang: nb
title: "Hvor mye sparer en vannvarmer i Nord Pools billigste timer"
permalink: /nb/blog/save-on-water-heating/
date: 2026-11-04
---

En elektrisk vannvarmer er det beste første målet for spotprisautomatisering:
vannet forblir varmt i timer, så det kan varmes når strømmen er billigst —
familien legger ikke merke til forskjellen.

## Beregningen

Forutsetninger (tilpass din egen varmer):

- 80-liters vannvarmer, 2 kW varmeelement
- én full varmesyklus per dag ≈ **4 kWh**
- gjennomsnittlig Nord Pool-pris inkl. mva og påslag: **€0.15/kWh**
- gjennomsnittlig pris i de 4 billigste timene: **€0.07/kWh**

| Scenario | Energikostnad per år |
|---|---|
| Oppvarming på vilkårlige tidspunkter / om kvelden (4 kWh × 365 × €0.15) | **€219** |
| Oppvarming i de 4 billigste timene (4 kWh × 365 × €0.07) | **€102** |
| **Spart** | **≈ €117/år** |

Gang opp med din reelle prisforskjell. I volatile måneder er gapet mellom dagens gjennomsnittspris
og de billigste timene mye større — en besparelse på €150–300/år på én vannvarmer er
realistisk i Baltikum og Finland.

## Hvorfor kontakten og ikke bare appen

Trikset er at timeplanen skrives **direkte til kontakten** (både Tasmota og Shelly lagrer
timere i enheten). Telefonen kan være av, internett kan falle bort — vannvarmeren varmer
likevel i de billige timene. Hver dag skriver appen om timerne med neste dags priser.

## Hva du trenger

1. En kontakt med [Tasmota](https://tasmota.github.io/docs/)- eller **Shelly**-fastvare —
   for eksempel [Nous A1T](https://nous.technology/product/a1t.html) eller
   [Shelly Plug M Gen3](https://www.shelly.com/products/shelly-plug-m-gen3-white)
2. Den gratis [Spot Price Plugs-appen](https://github.com/derrij/SpotPricePlugs/releases/latest)
3. Tre minutters oppsett: finn kontaktene → velg timene → ferdig

Ingen abonnement, ingen sky, ingen Home Assistant nødvendig.

*Tallene er veiledende — besparelsen din avhenger av tariffen, varmerens størrelse og bruk.*
