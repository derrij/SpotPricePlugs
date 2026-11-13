---
layout: default
lang: fi
title: "Kuinka paljon vesivaraaja säästää Nord Pool:n halvimpina tunteina"
permalink: /fi/blog/save-on-water-heating/
date: 2026-11-04
---

Sähköinen vesivaraaja on paras ensimmäinen kohde pörssihinnan automaatiolle:
vesi pysyy kuumana tunteja, joten sen voi lämmittää silloin, kun sähkö on halvinta —
perhe ei huomaa eroa.

## Laskelma

Oletukset (muokkaa oma varaajallesi):

- 80 litran vesivaraaja, 2 kW lämmitysvastus
- yksi täysi lämmityskierros päivässä ≈ **4 kWh**
- keskimääräinen Nord Pool -hinta ALV ja marginaali mukaan lukien: **€0.15/kWh**
- keskimääräinen hinta 4 halvimmalla tunnilla: **€0.07/kWh**

| Tilanne | Energian kustannus vuodessa |
|---|---|
| Lämmitys satunnaisiin aikoihin / iltaisin (4 kWh × 365 × €0.15) | **€219** |
| Lämmitys 4 halvimmalla tunnilla (4 kWh × 365 × €0.07) | **€102** |
| **Säästetty** | **≈ €117/vuodessa** |

Kerro omalla todellisella hintaerolla. Epävakaina kuukausina ero päivän keskihinnan ja
halvimpien tuntien välillä on paljon suurempi — säästö €150–300/vuodessa yhdelle
boilerille on realistinen Baltiassa ja Suomessa.

## Miksi pistorasia, ei pelkkä sovellus

Temppu on siinä, että aikataulu kirjoitetaan **itse pistorasiaan** (sekä Tasmota että
Shelly tallentavat ajastimet laitteeseen). Puhelin voi olla pois päältä, internet voi
katketa — boileri lämmittää silti halvina tunteina. Joka päivä sovellus kirjoittaa
ajastimet uudelleen seuraavan päivän hintojen mukaan.

## Mitä tarvitset

1. Pistorasia [Tasmota](https://tasmota.github.io/docs/)-, [OpenBeken](https://github.com/openshwprojects/OpenBK7231T_App)- tai **Shelly**-ohjelmistolla —
   esimerkiksi [Nous A1T](https://nous.technology/product/a1t.html) tai
   [Shelly Plug M Gen3](https://www.shelly.com/products/shelly-plug-m-gen3-white)
2. Ilmainen [Spot Price Plugs -sovellus](https://github.com/derrij/SpotPricePlugs/releases/latest)
3. Kolme minuuttia asennusta: etsi pistorasiat → valitse tunnit → valmista

Ei tilausta, ei pilveä, ei Home Assistantia.

*Luvut ovat esimerkillisiä — säästösi riippuu tariffistasi, varaajan koosta ja käytöstä.*
