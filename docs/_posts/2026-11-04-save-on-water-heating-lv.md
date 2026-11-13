---
layout: default
lang: lv
title: "Cik daudz ūdens sildītājs ietaupa lētākajās Nord Pool stundās"
permalink: /lv/blog/save-on-water-heating/
date: 2026-11-04
---

Elektriskais ūdens sildītājs ir labākais biržas cenu automatizācijas mērķis:
ūdens paliek karsts vairākas stundas, tāpēc to var sildīt tad, kad elektrība ir lētākā —
ģimene nemanīs atšķirību.

## Aprēķins

Pieņēmumi (pielāgojiet savam sildītājam):

- 80 litru boilers, 2 kW sildītājs
- viens pilns sildīšanas cikls dienā ≈ **4 kWh**
- vidējā Nord Pool cena ar PVN un uzcenojumu: **€0.15/kWh**
- vidējā cena 4 lētākajās stundās: **€0.07/kWh**

| Scenārijs | Enerģijas izmaksas gadā |
|---|---|
| Sildīšana jebkurā laikā / vakarā (4 kWh × 365 × €0.15) | **€219** |
| Sildīšana 4 lētākajās stundās (4 kWh × 365 × €0.07) | **€102** |
| **Ietaupīts** | **≈ €117/gadā** |

Reiziniet ar savu reālo cenu starpību. Nepastāvīgos mēnešos starpība starp dienas vidējo
cenu un lētākajām stundām ir daudz lielāka — ietaupījums €150–300/gadā uz vienu boileru
ir reāls Baltijā un Somijā.

## Kāpēc rozete, ne tikai lietotne

Triks ir tajā, ka grafiks tiek ierakstīts **pašā rozetē** (gan Tasmota, gan Shelly glabā
taimerus ierīcē). Telefons var būt izslēgts, internets var pazust — boilers joprojām
silda lētākajās stundās. Katru dienu lietotne pārraksta taimerus nākamās dienas cenām.

## Ko jums vajag

1. Rozete ar [Tasmota](https://tasmota.github.io/docs/) vai **Shelly** programmaparatūru —
   piemēram, [Nous A1T](https://nous.technology/product/a1t.html) vai
   [Shelly Plug M Gen3](https://www.shelly.com/products/shelly-plug-m-gen3-white)
2. Bezmaksas [Spot Price Plugs lietotne](https://github.com/derrij/SpotPricePlugs/releases/latest)
3. Trīs minūšu uzstādīšana: atrast rozetes → iestatīt stundas → gatavs

Nav abonementa, nav mākoņa, nav vajadzīgs Home Assistant.

*Skaitļi ir ilustratīvi — jūsu ietaupījums atkarīgs no tarifa, boilera izmēra un lietošanas.*
