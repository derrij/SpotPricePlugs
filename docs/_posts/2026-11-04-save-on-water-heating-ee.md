---
layout: default
lang: ee
title: "Kui palju säästab veesoojiti Nord Pool odavaimatel tundidel"
permalink: /ee/blog/save-on-water-heating/
date: 2026-11-04
---

Elektriline veesoojiti on spot-hinna automatiseerimise parim esimene sihtkoht:
vesi püsib kuum mitu tundi, seega saab seda kütta siis, kui elekter on kõige odavam —
pere ei pane vahet tähele.

## Arvutus

Eeldused (kohandage oma soojitaja jaoks):

- 80-liitrine boiler, 2 kW küttekeha
- üks täielik küttsükkel päevas ≈ **4 kWh**
- keskmine Nord Pool hind koos käibemaksu ja marginaaliga: **€0.15/kWh**
- keskmine hind 4 odavaimal tunnil: **€0.07/kWh**

| Stsenaarium | Energiakulu aastas |
|---|---|
| Kütmine suvalisel ajal / õhtul (4 kWh × 365 × €0.15) | **€219** |
| Kütmine 4 odavaimal tunnil (4 kWh × 365 × €0.07) | **€102** |
| **Säästetud** | **≈ €117/aastas** |

Korrutage oma reaalse hinnavahega. Volatiilsetel kuudel on vahe päeva keskmise hinna ja
odavaimate tundide vahel palju suurem — sääst €150–300/aastas ühe boileri kohta on
Baltimaades ja Soomes realistlik.

## Miks pistik, mitte ainult rakendus

Trikk on selles, et kava kirjutatakse **otse pistikusse** (nii Tasmota kui Shelly salvestavad
taimerid seadmesse). Telefon võib olla välja lülitatud, internet võib kaduda — boiler küttab
siiski odavatel tundidel. Iga päev kirjutab rakendus taimerid üle järgmise päeva hindadega.

## Mida vajate

1. Pistik [Tasmota](https://tasmota.github.io/docs/), [OpenBeken](https://github.com/openshwprojects/OpenBK7231T_App) või **Shelly** tarkvaraga —
   näiteks [Nous A1T](https://nous.technology/product/a1t.html) või
   [Shelly Plug M Gen3](https://www.shelly.com/products/shelly-plug-m-gen3-white)
2. Tasuta [Spot Price Plugs rakendus](https://github.com/derrij/SpotPricePlugs/releases/latest)
3. Kolm minutit seadistamist: leia pistikud → vali tunnid → valmis

Pole tellimust, pole pilve, pole vaja Home Assistanti.

*Arvud on illustratiivsed — teie sääst sõltub tariifist, boileri suurusest ja kasutamisest.*
