---
layout: default
lang: lt
title: "Kiek vandens šildytuvas sutaupo pigiausiomis Nord Pool valandomis"
permalink: /lt/blog/save-on-water-heating/
date: 2026-11-04
---

Elektrinis vandens šildytuvas — geriausias pirmas biržos kainų automatizacijos tikslas:
 vandens karštis išlieka kelias valandas, todėl jį galima šildyti tada, kai elektra pigiausia —
 šeima skirtumo nepastebės.

## Skaičiavimas

Prielaidos (pritaikykite savo šildytuvui):

- 80 litrų katilas, 2 kW kaitinamasis elementas
- vienas pilnas šildymo ciklas per dieną ≈ **4 kWh**
- vidutinė Nord Pool kaina su PVM ir antkainiu: **€0.15/kWh**
- vidutinė kaina 4 pigiausiomis valandomis: **€0.07/kWh**

| Scenarijus | Energijos sąnaudos per metus |
|---|---|
| Šildymas bet kuriuo laiku / vakare (4 kWh × 365 × €0.15) | **€219** |
| Šildymas 4 pigiausiomis valandomis (4 kWh × 365 × €0.07) | **€102** |
| **Sutaupyta** | **≈ €117/metams** |

Padauginkite iš savo realaus kainų skirtumo. Nepastoviems mėnesiais skirtumas tarp dienos
vidutinės kainos ir pigiausių valandų yra daug didesnis — €150–300/metų taupymas vienam
katilui yra realus Baltijos šalyse ir Suomijoje.

## Kodėl rozetė, o ne tik programėlė

Gudrybė ta, kad grafikas įrašomas **į pačią rozetę** (tiek Tasmota, tiek Shelly saugo
 laikmačius įrenginyje). Telefonas gali būti išjungtas, internetas gali dingti — katilas
 vis tiek šildo pigiausiomis valandomis. Kasdien programėlė perrašo laikmačius pagal
 kitos dienos kainas.

## Ko jums reikia

1. Rozetė su [Tasmota](https://tasmota.github.io/docs/), [OpenBeken](https://github.com/openshwprojects/OpenBK7231T_App) arba **Shelly** programine įranga —
   pavyzdžiui, [Nous A1T](https://nous.technology/product/a1t.html) arba
   [Shelly Plug M Gen3](https://www.shelly.com/products/shelly-plug-m-gen3-white)
2. Nemokama [Spot Price Plugs programėlė](https://github.com/derrij/SpotPricePlugs/releases/latest)
3. Trys minutės setupo: raskite rozetes → pasirinkite valandas → paruošta

Jokio prenumeratos, jokio debesies, nereikia Home Assistant.

*Skaičiai yra iliustratyvūs — jūsų taupymas priklauso nuo tarifo, katilo dydžio ir naudojimo.*
