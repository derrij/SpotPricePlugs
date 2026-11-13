---
layout: default
lang: en
title: "Getting started with Spot Price Plugs in 5 minutes"
permalink: /en/blog/getting-started/
date: 2026-11-04
---

This guide takes you from zero to a fully automated plug.

## 1. Install the app

Download the APK from the
[latest release](https://github.com/derrij/SpotPricePlugs/releases/latest) and install it
(allow installs from unknown sources). Android 8.0+ required. The app is free, has no ads
and works entirely on your local network.

## 2. Prepare the plug

- **Shelly**: factory firmware works out of the box. Make sure the web UI has **no password**.
- **Tasmota**: flash the plug if it is not flashed yet and make sure it gets the **time
  via NTP** (internet access). The app adapts timers to the plug's time zone itself.
- **OpenBeken**: found the same way as Tasmota. The schedule is stored on the plug as clock
  events and kept in its `autoexec.bat`, so it survives a reboot.

Connect the plug to the same Wi-Fi network as your phone.

## 3. Find plugs

Open the app and tap **Find plugs**. It scans your subnet and lists every Tasmota, OpenBeken and
Shelly (Gen1, Gen2/3) device it finds.

## 4. Set your region and hours

In **Settings** choose your Nord Pool area (`LV`, `EE`, `LT`, `FI`, `SE1`–`SE4`, `DK1`–`DK2`,
`NO1`–`NO5`, `PL`, `DE`, `AT`, `BE`, `NL`, `FR`), how many cheapest hours per day you want,
and optionally your VAT and price cap.

## 5. Update all

Tap **Update all** — the app downloads tomorrow's prices and writes the schedule into
each plug. From now on it repeats itself daily after ≈13:00 CET.

## Configure by name (optional but powerful)

You can encode all settings into the plug's name:

```
ScooterCharger DE 4h 55W max0.99
TeslaCharger   LV 4h 3000W max0.20
WaterHeater    FI 6h 2000W
```

Format: `Name Area Hours Power maxPrice` — any order, everything optional. The
**Copy name** button in the plug card builds the name from current settings, so you can
paste it into the plug's web UI.

## Two Android settings worth doing

To stop Android from delaying the daily background update:

- grant **Exact alarms** (the app has a button for it)
- set **Battery → Unrestricted** for the app

That's it — the plug now saves you money every day on its own.
