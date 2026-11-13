---
layout: default
lang: da
title: "Hvor meget sparer en vandvarmer i Nord Pools billigste timer"
permalink: /da/blog/save-on-water-heating/
date: 2026-11-04
---

En eldrevet vandvarmer er det bedste første mål for spotprisautomatisering:
vandet forbliver varmt i timer, så det kan opvarmes, når strømmen er billigst —
familien lægger ikke mærke til forskellen.

## Beregningen

Antagelser (tilpas til din egen varmer):

- 80 liters vandvarmer, 2 kW varmeelement
- én fuld opvarmningscyklus om dagen ≈ **4 kWh**
- gennemsnitlig Nord Pool-pris inkl. moms og tillæg: **€0.15/kWh**
- gennemsnitlig pris i de 4 billigste timer: **€0.07/kWh**

| Scenario | Energicomkostning per år |
|---|---|
| Opvarmning på vilkårlige tidspunkter / om aftenen (4 kWh × 365 × €0.15) | **€219** |
| Opvarmning i de 4 billigste timer (4 kWh × 365 × €0.07) | **€102** |
| **Sparet** | **≈ €117/år** |

Gang op med din reelle prisforskel. I volatille måneder er gapet mellem dagens gennemsnitspris
og de billigste timer meget større — en besparelse på €150–300/år på én vandvarmer er
realistisk i Baltikum og Finland.

## Hvorfor stikket og ikke bare appen

Tricket er, at skemaet skrives **direkte i stikket** (både Tasmota, OpenBeken og Shelly gemmer timere
i enheden). Telefonen kan være slukket, internettet kan gå væk — vandvarmeren varmer stadig
i de billige timer. Hver dag skriver appen timerne om med næste dags priser.

## Hvad du skal bruge

1. Et stik med [Tasmota](https://tasmota.github.io/docs/)-, [OpenBeken](https://github.com/openshwprojects/OpenBK7231T_App)- eller **Shelly**-firmware —
   for eksempel [Nous A1T](https://nous.technology/product/a1t.html) eller
   [Shelly Plug M Gen3](https://www.shelly.com/products/shelly-plug-m-gen3-white)
2. Den gratis [Spot Price Plugs-app](https://github.com/derrij/SpotPricePlugs/releases/latest)
3. Tre minutters opsætning: find stikkene → vælg timerne → færdig

Intet abonnement, ingen sky, ingen Home Assistant nødvendig.

*Tallene er vejledende — din besparelse afhænger af din tarif, varmerens størrelse og brug.*
