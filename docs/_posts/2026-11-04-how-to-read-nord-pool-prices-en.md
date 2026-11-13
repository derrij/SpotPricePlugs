---
layout: default
lang: en
title: "How to read Nord Pool prices (and why 15-minute slots matter)"
permalink: /en/blog/how-to-read-nord-pool-prices/
date: 2026-11-04
---

Since October 2025 Nord Pool publishes electricity prices in **15-minute slots**
instead of hourly ones. That changed how spot tariffs work — and it matters if you
want to automate your appliances.

## Where the prices come from

Nord Pool is the power exchange for the Nordic and Baltic region. Your electricity
retailer buys power there and passes the spot price through to you (plus margins,
grid fees and taxes). The public data is available for free — for example via the
[Elering dashboard](https://dashboard.elering.ee/), which covers Estonia, Latvia,
Lithuania and Finland.

Prices for tomorrow are published around **13:00 CET** every day. That is why a good
spot-price automation updates its schedule shortly after 13:00 — and retries later if
the exchange publishes late.

## What changed with 15-minute slots

- Prices now change up to **96 times per day**, not 24.
- A cheap 15-minute slot can sit right next to an expensive one, so "run it at night"
  is no longer precise enough.
- Appliances with short cycles (dishwasher, washing machine, pump) benefit the most:
  they can fit entirely into the cheapest quarter-hours.

## Price ≠ your bill

The exchange price is only one part of it. Your full price is roughly:

```
exchange price + retailer margin + grid fees + taxes (VAT)
```

When comparing "cheap" and "expensive" hours, always compare the **full** price —
the relative difference between hours stays similar, but the absolute numbers you see
in the app should match your bill. Spot Price Plugs lets you enter your VAT and margin
so the schedule is based on what you actually pay.

## Price cap: the safety net

Chasing the cheapest hours is only half of the story. The other half is **never paying
too much**: set a maximum price (for example €0.20/kWh) and the plug simply will not
switch on above it — even if that means skipping a day. In Spot Price Plugs this is the
`max0.20` part of the device name:

```
WaterHeater LV 4h 2000W max0.20
```

## Automate it

Reading charts every evening gets old fast. A smart plug with the schedule stored
on the device does it for you — see
[how much a water heater saves]({{ site.baseurl }}/en/blog/save-on-water-heating/) or go straight to the
[free app](https://github.com/derrij/SpotPricePlugs/releases/latest).
