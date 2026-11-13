---
layout: default
lang: de
title: "Wie viel ein Warmwasserboiler in den günstigsten Nord Pool-Stunden spart"
permalink: /de/blog/save-on-water-heating/
date: 2026-11-04
---

Ein elektrischer Warmwasserboiler ist das beste erste Ziel für Spotpreis-Automatisierung:
das Wasser bleibt stundenlang heiß, man kann also heizen, wenn der Strom am günstigsten ist —
die Familie merkt keinen Unterschied.

## Die Rechnung

Annahmen (an Ihren eigenen Boiler anpassen):

- 80-Liter-Boiler, 2 kW Heizstab
- ein voller Heizzyklus pro Tag ≈ **4 kWh**
- durchschnittlicher Nord Pool-Preis inkl. MwSt. und Marge: **€0.15/kWh**
- Durchschnittspreis in den 4 günstigsten Stunden: **€0.07/kWh**

| Szenario | Energiekosten pro Jahr |
|---|---|
| Heizen zu zufälligen Zeiten / abends (4 kWh × 365 × €0.15) | **€219** |
| Heizen in den 4 günstigsten Stunden (4 kWh × 365 × €0.07) | **€102** |
| **Gespart** | **≈ €117/Jahr** |

Multiplizieren Sie mit Ihrer realen Preisdifferenz. In volatilen Monaten ist die Lücke zwischen
dem Tagesdurchschnitt und den günstigsten Stunden viel größer — eine Ersparnis von
€150–300/Jahr für einen Boiler ist in den baltischen Ländern und Finnland realistisch.

## Warum die Steckdose und nicht nur die App

Der Trick: Der Zeitplan wird **direkt in der Steckdose gespeichert** (sowohl Tasmota als auch
Shelly speichern Timer im Gerät). Das Telefon kann aus sein, das Internet kann wegfallen —
der Boiler heizt trotzdem in den billigen Stunden. Jeden Tag schreibt die App die Timer mit
den Preisen des nächsten Tages neu.

## Was Sie brauchen

1. Eine Steckdose mit [Tasmota](https://tasmota.github.io/docs/)- oder **Shelly**-Firmware —
   zum Beispiel [Nous A1T](https://nous.technology/product/a1t.html) oder
   [Shelly Plug M Gen3](https://www.shelly.com/products/shelly-plug-m-gen3-white)
2. Die kostenlose [Spot Price Plugs-App](https://github.com/derrij/SpotPricePlugs/releases/latest)
3. Drei Minuten Einrichtung: Steckdosen finden → Stunden wählen → fertig

Kein Abo, keine Cloud, Home Assistant nicht erforderlich.

*Die Zahlen sind beispielhaft — Ihre Ersparnis hängt von Tarif, Boilergröße und Nutzung ab.*
