# Spot Price Plugs — Detailed Guide

[← back to README](../README.md) · [Русская версия](DETAILS.md)

A native app instead of a Python script: it finds Tasmota and Shelly plugs on
your home network by itself, reads parameters from the device name, downloads
Nord Pool prices and writes timers/schedules. No PC, Termux, MQTT broker or
cloud needed.

## What's inside

| | |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (Material 3) |
| Minimum version | Android 8.0 (API 26) |
| Build | AGP 9.4.1, Gradle 9.6, compileSdk 37, targetSdk 36 |
| Network | HTTP to the plug on the LAN, HTTPS to the Nord Pool API |
| Background | `AlarmManager` (exact alarm) + `WorkManager` (fallback) |
| Storage | `SharedPreferences` (IP, settings) + file cache for prices |

## Devices

Two families with an open HTTP API are supported:

| | Tasmota | Shelly |
|---|---|---|
| Detection during scan | `Status0` | `/rpc/Shelly.GetDeviceInfo` (Gen2/Gen3), then `/shelly` (Gen1) |
| Relay, buttons in the card | `Power` | `Switch.Set` / `/relay/0?turn=` |
| Schedule | 16 `Timer N` timers | Gen2/3: one `Schedule.Create` job (cron `timespec`) per switch; Gen1: `/settings/relay/0?schedule_rules=` |
| Pause the plan | `Timers 0/1` | Gen2/3: `Schedule.Update enable=false/true`; Gen1: `/settings/relay/0?schedule=false/true` |
| Power meter | none (watts in the name) | yes (`apower`/`meters`) — picked up automatically if there is no `W` in the name |

The name and parameters (region, hours, wattage, limit) are read the same way
for both families — from the device name (`name` on Shelly, `DeviceName`
on Tasmota). The badge under the IP shows the firmware: `Tasmota`,
`Shelly Gen2/3 · SPLM-002PE01EU`, etc.

**OpenBeken** (OpenBK7231T_App: BK7231, BL602 … plugs) answers the Tasmota
`cm?cmnd=` endpoint and is recognised by its firmware version in `Status0`.
Relay and clock work as on Tasmota, but it has no `Timer` commands, so the
schedule is written as clock events (`addClockEvent HH:mm <days> <id> POWER
ON|OFF`, ids 7301–7316; the user's own events are left alone). Limitations:

* clock events live in the plug's memory and are lost when it reboots, so
  the same commands are also kept in `autoexec.bat` on the plug's file
  system (between `// spotpriceplugs begin` / `end`; other lines of the file
  are not touched), which the firmware runs at boot. On a plug without a
  file system the app notices the reboot by the plug's uptime at its hourly
  check and writes the schedule again;
* the plug keeps a fixed UTC offset and does not follow daylight saving.
  Before writing a schedule the app sets `ntp_timeZoneOfs` to the phone's
  current offset and puts the same line into its `autoexec.bat` block, which
  overrides the offset in the plug's startup command. After a clock change
  the plug's clock is an hour off until the next update, but the events
  already on it still fire at the right moments;
* the NTP driver must be running on the plug (`startDriver ntp` in its
  startup command), otherwise the update stops with "the plug's clock is not
  set";
* pausing the plan removes the events, resuming writes them again.

Shelly limitations:

* up to 20 schedule jobs per plug (our limit is 8 blocks = 16 jobs);
* Gen2/3: the app remembers the ids of its own jobs in the plug's KVS
  (`spotpriceplugs_jobs`) and only replaces those — schedules you created
  yourself are left alone (if they leave too little room, the update stops
  with a message);
* Gen1 has a single rule list per relay, so the app owns it completely and
  overwrites it on every update;
* if the Shelly has a password, the app cannot control it without login —
  password entry is not implemented yet;
* savings in euros for Tasmota are calculated only from the wattage in the
  name/card; for Shelly the wattage is additionally read from the power meter
  during each update and remembered.

## Installation

1. Download the APK from the [Releases](https://github.com/derrij/SpotPricePlugs/releases/latest)
   page and open it on the phone (or `adb install`).
2. Allow installs from unknown sources for your file manager.
3. Open the app. It will immediately ask for:
   * **Notifications** — optional, but with them Android throttles the
     background less;
   * **Exact alarms** — the "Alarms" button (Android 13+ does not grant this
     automatically);
   * **Battery → Unrestricted** — the "Battery" button. On Xiaomi/Samsung also
     allow autostart in the vendor settings.
   Permission statuses in the app refresh as soon as you return from the
   system settings — no restart needed.
4. The phone must be on the same Wi-Fi network as the plugs.

## First run

1. The subnet is detected automatically (`192.168.1`, `10.85.228`, …). If the
   wrong one is detected — enter it manually and press "Auto" to re-detect.
2. **Find plugs** — the app polls 254 addresses of the subnet (Tasmota
   `Status0`, then Shelly). Takes 5–30 seconds.
3. Found plugs are saved. For each you see the name, IP, region, hours, limit,
   wattage, time of the last and **next** update (they can differ: it depends
   on how many days the plan covers). The "Edit" button changes region, hours,
   limit and wattage for that specific plug, "Recalculate" recalculates only
   it, "Remove plug" removes it from the list.
4. **Update all** — downloads prices, builds the plan and writes timers.

## Plug card

Each found plug looks like this:

* **"Turn on / Turn off" buttons** — direct relay control (`Power ON/OFF`),
  regardless of the plan;
* **"Pause plan" / "Resume"** — `Timers 0` and `Timers 1`: while paused the
  plug stops following the schedule, its timers are simply disabled. The
  daily recalculation still writes fresh timers into a paused plug but keeps
  them disabled, so "Resume" always starts from an up-to-date plan;
* **chart** — a colour strip for the price of each 15-minute slot (blue —
  cheap, red — expensive), green chunks — scheduled ON time, white line —
  current moment. Today and tomorrow are shown, with a note of how many
  hours the plan covers;
* **the "Now" row**: current price and time to the next switch — the
  countdown refreshes every 30 seconds;
* **"saved"** — accumulated estimate, see the section below.

## Savings

| Where you see it | What it means |
|---|---|
| "19% cheaper" | the average price of the chosen slots is below the daily average |
| "0.53 €" | plan savings in money; appears once the wattage is known |
| "saved: today X € · 7 days Y €" | sum of estimates across all downloaded plans |

The app takes the wattage from the device name (`1500W`, `2kW`) or from the
card ("Edit" → "Watts"). Without wattage only percentages are shown — euros
cannot be calculated honestly, because the app does not know how many kWh the
load consumes.

Formula: `(daily average price − slot price) × 0.25 h × kW` for each chosen
slot. For today only future slots count, so the "today" estimate is lower than
a full day. Manually switching the relay during an expensive hour does not
count towards savings — the counter honestly shows only the plan's benefit.

## Device name

```
Boiler DE 4h 2000W max0.20
       │  │  │     └── maximum price, EUR/kWh incl. VAT
       │  │  └──────── load wattage, W (for the savings counter)
       │  └─────────── hours per day (0.5h, 2.5h, 8h … are fine)
       └────────────── Nord Pool zone: LV, EE, LT, FI, SE3, NO1 …
```

Token order does not matter and case does not matter — except two-letter
zones (`LV`, `DE`, `AT`, `BE`…), which must be written in capitals so that
ordinary words like "at" or "be" are not taken for a zone. Separators are
space, `_` or `-`. What is recognised:

| Parameter | Accepted spellings |
|---|---|
| Region | `LV`, `EE`, `SE3`, `NO1` … as a separate word |
| Hours | `4h`, `2.5h`, `4 hours`, `4ч`, `4_hours` |
| Wattage | `1500W`, `1500 watts`, `2kW`, `1.5kW` |
| Max price | `max0.20`, `max 0.20`, `limit_0.20`, `threshold 0.20`, `<0.20`, `≤0.20` |

Old names like `Scooter_LV_4_hours_1500W_on_limit_0.20` keep working. A comma
as the decimal separator works too: `max0,20`.

All of this is optional. If the region is not in the name, the app takes it
from the settings — the **"Region"** field in the "Settings" block sets the
zone for all plugs whose region is not specified. Hours, price and wattage
without numbers in the name are also taken from the settings.

Region, hours and limit can also be changed in the app, per plug: "Edit" →
the fields → "Save". Values from `DeviceName` are imported at the first scan;
after that what you saved is kept. The region of a specific plug takes
priority over the one in the settings.

## Settings

| Field | Default | Meaning |
|---|---|---|
| VAT, % | empty (auto) | added to the exchange price; empty = the country rate for the plug's region (LV 21, EE 22, LT 21, FI 25.5, SE/DK/NO 25, PL 23, DE 19…); enter your own, including 0 |
| Refresh | 14:05 | daily recalculation time (phone's time zone) |
| Hours per day | 4 | how many hours per day to switch on new plugs |
| Max price | 0.20 | maximum price, EUR/kWh incl. VAT, for new plugs |
| Region | empty | Nord Pool zone for plugs without a region in `DeviceName` |
| Watts | not set | load wattage for plugs without `W` in the name |

Hours, limit, region and wattage in this block are defaults: they apply to
plugs that do not carry this data in their name. Already saved devices keep
their own values and are edited via "Edit".

## When the schedule is refreshed

| Event | What happens |
|---|---|
| Daily at **14:05** in the phone's local time | The exchange has already published tomorrow's prices → the plan is extended by a day |
| End of the plan's last block | The plan is used up → a new one is built immediately |
| Tomorrow's prices not out yet | Retry in 15 minutes (up to 5 attempts), no spam at night |
| Phone reboot, time/time-zone change, APK update | Alarms are re-armed |
| Opening the app | Shows the state; updating — via the button |
| Every 12 hours (on open or in the background) | Check for a new version on GitHub Releases; in the background — a notification |

The time is 14:05, not 13:00, because Nord Pool publishes results around
13:00 CET. In any time zone there is still a margin before publication: the
app counts days in the phone's time zone but requests prices by CET dates, so
moving to another region breaks nothing.

The exact alarm fires even in Doze. If it is somehow missed, WorkManager will
wait for the network and run the job 25 minutes later. If the phone is off at
night, the plug keeps working from its recorded timers — they live in its
flash.

## How the plan is built

1. Prices: `dataportal-api.nordpoolgroup.com/api/DayAheadPrices`, 15-minute
   resolution, EUR/MWh excl. VAT → `EUR/kWh = price / 1000 × (1 + VAT/100)`.
2. The response is cached in memory and in `cacheDir`. A fully published
   day never changes, so it is not requested again; a day that is not out yet
   is asked for again after 5 minutes. If the exchange cannot be reached, the
   cached prices are still used. Several plugs of the same region share
   **one** request.
3. The window runs from the current 15-minute slot to the end of the next
   day. Past slots are excluded: starting at 21:45 plans 21:45–24:00 plus
   tomorrow.
4. For each day the cheapest slots are chosen, not above the limit — exactly
   `hours × 4` slots of 15 minutes.
5. Adjacent slots form on/off blocks. A block across midnight stays one.
   No more than 8 blocks over the whole window (16 Tasmota timers); with two
   days each day may use 4, and tomorrow also gets what today left unused.
   If the cheapest slots are too scattered to fit, the cheapest set that does
   fit is chosen instead — still exactly the requested time and never a slot
   above the limit (if that is impossible, the plug is on for less time).
6. Day masks: Tasmota's `Days` is 7 characters **starting with Sunday**
   (`SMTWTFS`). Today's plan gets "all days except tomorrow", tomorrow's —
   only tomorrow. A block across midnight: ON with the start day's mask, OFF
   — the end day's. If there are only today's prices, the plan repeats every
   day (fallback mode).
7. At the end `Timers 1` is written along with the current relay state per
   the plan.

## Building from source

The easiest way is to open the repository root in Android Studio and press
Run.

From the command line (JDK 17+; Android SDK with platform 37 and build-tools
36):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

The APK appears in `app\build\outputs\apk\debug\app-debug.apk`.

Important: AGP 9 enables Kotlin out of the box, so the
`org.jetbrains.kotlin.android` plugin is **not** applied — otherwise the
build fails with `Cannot add extension with name 'kotlin'`. Only
`org.jetbrains.kotlin.plugin.compose` is applied.

The release build (`assembleRelease`) is signed with the key from
`keystore.properties` (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`); if the file is missing — with the debug key.

## Limitations

* **HTTP only.** Tasmota S26 (ESP8266) cannot do HTTPS, so the app talks over
  `http://IP` and the manifest has `usesCleartextTraffic`. That is fine inside
  a home network; on someone else's Wi-Fi the plugs are unreachable anyway.
* **No MQTT.** The app writes timers directly over HTTP. A broker is only
  needed if the plug is on another network (VPN/router) — use the Python
  version then.
* **The plug must be on the same network at update time.** If the phone is
  on mobile data only at 14:05, nothing is attempted and no errors are shown;
  the job runs as soon as the phone is back on Wi-Fi (and is looked at once an
  hour meanwhile). The plug's timers keep running on the old plan.
* **Only due plugs are touched in the background.** A scheduled run handles
  the plugs whose time has come and skips a plug whose timers would not
  change, so one unreachable plug does not make the others be rewritten.
  "Update all" always writes everything.
* **Plugs are recognised by MAC.** After the router hands out new addresses,
  "Find plugs" moves each plug's settings and savings to its new IP. Before
  every write the app checks that the device at the saved IP is still the
  same one.
* **Clock changes.** A plug whose clock matches the phone's is assumed to
  follow the same daylight-saving rules (Shelly; Tasmota with `Timezone 99`),
  so blocks after the change stay at the right time. A Tasmota on a fixed
  `Timezone` is written with its fixed offset.
* **Maximum 16 timers** = 8 blocks. Tasmota cannot do more.
* **The plug must know the time (NTP).** Before writing timers the app reads
  the plug's clock (`Status 7` on Tasmota, `Sys.GetStatus` / `/status` on
  Shelly) and writes the times in the plug's own time zone, so a default
  `Timezone 1` is fine. If the clock was never set, the update stops with
  "the plug's clock is not set".
* **Manual schedule changes.** The app rewrites all 16 timers on every
  update, so timers created in the Tasmota web UI will be wiped.
* **Updating over an older version** works only if the APK is signed with the
  same key. GitHub releases are signed with one key — install them over each
  other freely.

## If something is wrong

| Symptom | What to do |
|---|---|
| "No plugs found" | The phone is on another network/VPN, the plug is off, or client isolation is on in the router |
| "Set the region in the app or in the device name" | Add `LV` (or your region) as a separate word to the device name, or set the region in the app settings |
| "Data for … is incomplete" | The exchange has not delivered prices yet; wait for 14:05 or press "Update all" later |
| "Nord Pool returned HTTP …" | No internet or the API is down; retry later |
| "… did not answer the command" | The plug is unreachable at this IP (DHCP changed) — scan the network again |
| "A different device answers at this address" | The router gave the plug's IP to another device — scan the network again, the plug is found by its MAC |
| The plug is listed as "Not controlled" | It was found without parameters in its name — tap "Control this plug" |
| Updates do not arrive | Check the "Alarms" and "Battery" permissions, and autostart in the vendor settings |
| The chart in the card is empty | The plug has never been recalculated (or had no plan) — press "Recalculate" |
| "Now ON" but the plug is cold | The relay was switched manually or the plan changed; the "Turn on/Turn off" button controls the relay immediately and does not wait for timers |
| Savings in euros are not shown | Set the wattage (W) in the card or add `1500W` to the DeviceName |
| "The plug's clock is not set" | The plug has no internet/NTP; check `Time` in its console and its NTP servers |

## License and support

The app is free, without ads or limits. The code is open under the
[GNU GPL v3](../LICENSE) licence: you may use, modify and distribute it,
provided derivative works are also published as open source under GPL v3.

If the app saves you money, you can support the project
([Buy Me a Coffee](https://buymeacoffee.com/derrij) or the button in the app).
Bug reports and pull requests are welcome.
