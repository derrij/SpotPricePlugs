<div align="center">

**English** · [Русский](README.ru.md)

# ⚡ Spot Price Plugs

**Tasmota and Shelly smart plugs switch on by themselves during the cheapest Nord Pool hours.**

[![Download APK](https://img.shields.io/github/v/release/derrij/SpotPricePlugs?label=Download%20APK&style=for-the-badge&color=3EA6FF)](https://github.com/derrij/SpotPricePlugs/releases/latest)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20me%20a%20coffee-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/derrij)

![Android 8+](https://img.shields.io/badge/Android-8.0%2B-34A853?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
![License GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)

</div>

A scooter charger, a water heater, a radiator, a dishwasher — they don't care
*when* they run, but your bill does: on the Nord Pool market electricity gets
several times cheaper and pricier during the day. **Spot Price Plugs** finds
the plugs on your home Wi-Fi, downloads prices every day and writes a schedule
for the cheapest 15-minute slots straight into each plug. No cloud, no MQTT,
no server, no subscription.

<!-- Screenshots: put PNGs into docs/ and uncomment
<p align="center">
  <img src="docs/screen-main.png" width="260">
  <img src="docs/screen-plug.png" width="260">
</p>
-->

## Features

- 🔍 **Finds plugs automatically** — scans the subnet, detects Tasmota, OpenBeken and Shelly (Gen1, Gen2/3).
- 💶 **Cheapest hours** — picks as many hours per day as you need, VAT included, never above your price cap.
- 📅 **The schedule lives in the plug** — timers are stored on the device and keep working with the phone off.
- 🔁 **Updates itself** — daily after prices are published (≈13:00 CET), retrying if the exchange is late.
- 📊 **Clear at a glance** — colour price strip for today and tomorrow, current price, time to next switch.
- 💰 **Tracks savings** — how many euros the plan saved per day, week and month.
- ⚙️ **Configure via the device name** — `TeslaCharger DE 4h 3000W max0.20`.
- 🌍 **Nord Pool areas** — LV, EE, LT, FI, SE1–4, DK1–2, NO1–5, PL, DE, AT, BE, NL, FR.
- 🌐 **13 UI languages** — English, Russian, Latvian, Estonian, Lithuanian, Finnish, Swedish, Danish, Norwegian, Polish, German, Dutch, French.
- 🔔 **Update notifications** from GitHub.
- 🆓 **Free and open** — no ads, no accounts, no tracking.

## Quick start

1. Download the APK from the [latest release](https://github.com/derrij/SpotPricePlugs/releases/latest) and install it
   (allow installs from unknown sources).
2. Connect the phone to the same Wi-Fi as the plugs and tap **Find plugs**.
   Every Tasmota and Shelly device on the network is listed; the app only
   touches the ones you tap **Control this plug** on (plugs already named
   like `Boiler DE 4h` are controlled from the start).
3. Set your region (e.g. `DE`) in Settings or in the device name.
4. Tap **Update all** — the schedule is written. From now on it runs by itself.

> To stop Android from delaying background updates, grant **Exact alarms** and
> **Battery: unrestricted** — the app has buttons for both.

## Device name

```
ScooterCharger DE 4h 55W max0.99
               │  │  │   └── never pay more than 0.99 €/kWh (incl. VAT)
               │  │  └────── load power — for the savings counter
               │  └───────── hours per day to stay on
               └──────────── Nord Pool area
```

Any order; separate with spaces, `_` or `-`. Also understood: `2.5h`, `4 hours`,
`1.5kW`, `limit 0.20`, `threshold 0.20`, `<0.20`, and legacy names like
`Scooter_DE_4_hours_limit_0.20`. Everything is optional — missing values come
from Settings and can be edited per plug. **Copy name** in the plug card builds
the right name from its current settings — paste it into the plug's web UI.

## Requirements

- Android 8.0+, on the same local network as the plugs at update time.
- A plug running **Tasmota** or **Shelly** firmware (no web-UI password).
- The plug must have its clock set via NTP (internet access); the app adapts to the plug's time zone.

## Supported plugs

Any smart plug with **Tasmota** or **Shelly** firmware works. For example:

- [Nous A1T](https://nous.technology/product/a1t.html) — a Tasmota plug (EU, 16 A)
- [Shelly Plug M Gen3](https://www.shelly.com/products/shelly-plug-m-gen3-white) — a Shelly Gen3 plug

They can be bought from the [Shelly official website](https://www.shelly.com/), the
[Nous store](https://nous.technology/) or your local electronics shops.

## Documentation

How the plan is built, device limits, background scheduling, troubleshooting
and building from source: **[docs/DETAILS.en.md](docs/DETAILS.en.md)**
([in Russian](docs/DETAILS.md)).

Build: `./gradlew assembleDebug` (JDK 17+, Android SDK 37).

## Support the project

The app is free. If it saves you money, buy the author a coffee ☕.

<a href="https://buymeacoffee.com/derrij"><img src="https://img.shields.io/badge/Buy%20me%20a%20coffee-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black" alt="Buy Me a Coffee"></a>

A ⭐ star, [bug reports](https://github.com/derrij/SpotPricePlugs/issues) and pull requests help too.

## License

[GNU GPL v3](LICENSE) — use, modify and share freely; derivative works must
stay open source under GPL v3.

---

<sub>Not affiliated with Nord Pool AS, Shelly Group or the Tasmota project. Prices come from the public Nord Pool API.</sub>
