---
layout: default
lang: pl
title: "Ile oszczędza bojler w najtańszych godzinach Nord Pool"
permalink: /pl/blog/save-on-water-heating/
date: 2026-11-04
---

Elektryczny bojler to najlepszy pierwszy cel automatyzacji cen giełdowych:
woda pozostaje gorąca przez godziny, więc można grzać wtedy, gdy prąd jest najtańszy —
rodzina nie zauważy różnicy.

## Obliczenie

Założenia (dostosuj do swojego bojlera):

- bojler 80 litrów, grzałka 2 kW
- jeden pełny cykl grzania dziennie ≈ **4 kWh**
- średnia cena Nord Pool z VAT i marżą: **€0.15/kWh**
- średnia cena w 4 najtańszych godzinach: **€0.07/kWh**

| Scenariusz | Koszt energii rocznie |
|---|---|
| Grzanie o losowych porach / wieczorem (4 kWh × 365 × €0.15) | **€219** |
| Grzanie w 4 najtańszych godzinach (4 kWh × 365 × €0.07) | **€102** |
| **Oszczędzone** | **≈ €117/rok** |

Pomnóż przez swoją realną różnicę cen. W zmiennych miesiącach różnica między średnią ceną
dnia a najtańszymi godzinami jest dużo większa — oszczędność €150–300/rok na jednym
bojlerze jest realna w krajach bałtyckich i Finlandii.

## Dlaczego gniazdko, a nie sama aplikacja

Sztuczka polega na tym, że harmonogram zapisywany jest **w samym gniazdku** (zarówno Tasmota,
jak i Shelly przechowują timery w urządzeniu). Telefon może być wyłączony, internet może
zniknąć — bojler i tak grzeje w tanich godzinach. Codziennie aplikacja przepisuje timery
zgodnie z cenami na kolejny dzień.

## Co jest potrzebne

1. Gniazdko z oprogramowaniem [Tasmota](https://tasmota.github.io/docs/), [OpenBeken](https://github.com/openshwprojects/OpenBK7231T_App) lub **Shelly** —
   na przykład [Nous A1T](https://nous.technology/product/a1t.html) albo
   [Shelly Plug M Gen3](https://www.shelly.com/products/shelly-plug-m-gen3-white)
2. Darmowa [aplikacja Spot Price Plugs](https://github.com/derrij/SpotPricePlugs/releases/latest)
3. Trzy minuty konfiguracji: znajdź gniazdka → wybierz godziny → gotowe

Bez subskrypcji, bez chmury, Home Assistant niepotrzebny.

*Liczby są poglądowe — oszczędności zależą od taryfy, wielkości bojlera i sposobu użytkowania.*
