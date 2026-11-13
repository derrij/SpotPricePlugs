---
layout: default
lang: en
title: "How much a water heater saves on the cheapest Nord Pool hours"
permalink: /en/blog/save-on-water-heating/
date: 2026-11-04
---

A typical electric water heater is the best first target for spot-price automation:
water stays hot for hours, so you can heat it whenever electricity is cheapest —
nobody in the family notices the difference.

## The math

Assumptions (adjust for your own heater):

- 80 litre boiler, 2 kW heating element
- one full heating cycle per day ≈ **4 kWh**
- average Nord Pool price incl. VAT and margins: **€0.15/kWh**
- average price during the 4 cheapest hours: **€0.07/kWh**

| Scenario | Energy cost per year |
|---|---|
| Heating at random / evening hours (4 kWh × 365 × €0.15) | **€219** |
| Heating during the 4 cheapest hours (4 kWh × 365 × €0.07) | **€102** |
| **Saved** | **≈ €117/year** |

Multiply by your real price difference. In volatile months the gap between the daily
average and the cheapest hours is much wider than in this example — savings of
€150–300/year on one boiler are realistic in the Baltics and Finland.

## Why the plug, not the app alone

The trick is that the schedule is written **into the plug itself** (Tasmota, OpenBeken and Shelly
both store timers on the device). The phone can be off, the internet can drop — the
boiler still heats during the cheap hours. Every day the app rewrites the timers for
the next day's prices.

## What you need

1. A plug running [Tasmota](https://tasmota.github.io/docs/), [OpenBeken](https://github.com/openshwprojects/OpenBK7231T_App) or **Shelly** firmware —
   for example [Nous A1T](https://nous.technology/product/a1t.html) or
   [Shelly Plug M Gen3](https://www.shelly.com/products/shelly-plug-m-gen3-white)
2. The free [Spot Price Plugs app](https://github.com/derrij/SpotPricePlugs/releases/latest)
3. Three minutes of setup: find plugs → set hours → done

No subscription, no cloud, no Home Assistant required.

*The numbers above are illustrative — your savings depend on your tariff, heater size
and usage pattern.*
