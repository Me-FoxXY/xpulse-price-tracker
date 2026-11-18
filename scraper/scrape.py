#!/usr/bin/env python3
"""
Xpulse 200 4V price scraper.

Reads the ex-showroom + on-road price of the Hero Xpulse 200 4V (base STD
variant) in Thiruvananthapuram from BikeWale and BikeDekho, cross-checks the two
sources, and appends a dated record to data/price_history.json.

Designed to run headless on GitHub Actions once a day. No external services, no
API keys. If one source fails or returns a nonsense value, it is skipped and the
other source is still used; the run only fails if BOTH sources are unusable.
"""

from __future__ import annotations

import json
import re
import sys
from datetime import datetime, timezone, timedelta
from pathlib import Path

import requests

# --- What we are tracking (change these to track a different bike/city) -------
BIKE = "Hero Xpulse 200 4V"
VARIANT = "STD"
CITY = "Thiruvananthapuram"

# Plausible price window (INR). Anything outside this is treated as a bad parse
# so a layout glitch or a stray number never triggers a false alert.
MIN_PRICE = 120_000
MAX_PRICE = 260_000

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    ),
    "Accept-Language": "en-IN,en;q=0.9",
}

# IST timestamp – the price is an Indian figure, so record it in Indian time.
IST = timezone(timedelta(hours=5, minutes=30))

DATA_FILE = Path(__file__).resolve().parent.parent / "data" / "price_history.json"


def _to_int(raw: str) -> int | None:
    """'1,86,189' -> 186189, with a sanity-check against the price window."""
    try:
        val = int(raw.replace(",", "").strip())
    except ValueError:
        return None
    return val if MIN_PRICE <= val <= MAX_PRICE else None


def _first(patterns: list[str], html: str) -> int | None:
    for pat in patterns:
        m = re.search(pat, html, re.I | re.S)
        if m:
            val = _to_int(m.group(1))
            if val is not None:
                return val
    return None


def _fetch(url: str) -> str:
    r = requests.get(url, headers=HEADERS, timeout=30)
    r.raise_for_status()
    return r.text


def _visible_text(html: str) -> str:
    """Strip tags and collapse whitespace so we read what a human sees, not the
    page's embedded JSON (which holds prices for every variant and nearby city)."""
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", html))


def scrape_bikewale() -> dict | None:
    url = "https://www.bikewale.com/hero-bikes/xpulse-200-4v/price-in-thiruvananthapuram/"
    try:
        html = _fetch(url)
    except Exception as e:  # noqa: BLE001 - network errors are expected sometimes
        print(f"[bikewale] fetch failed: {e}", file=sys.stderr)
        return None
    # Read the visible headline figures (base STD), not the per-variant JSON.
    text = _visible_text(html)
    ex = _first([r"Ex-?showroom\s*(?:₹|Rs\.?|&#8377;)?\s*([\d,]{6,9})"], text)
    on = _first([r"On-?Road\s*Price\s*(?:₹|Rs\.?|&#8377;)?\s*([\d,]{6,9})"], text)
    if ex is None and on is None:
        print("[bikewale] no prices parsed", file=sys.stderr)
        return None
    return {"source": "bikewale", "ex_showroom": ex, "on_road": on, "url": url}


def scrape_bikedekho() -> dict | None:
    url = "https://www.bikedekho.com/hero/xpulse-200/price-in-trivandrum"
    try:
        html = _fetch(url)
    except Exception as e:  # noqa: BLE001
        print(f"[bikedekho] fetch failed: {e}", file=sys.stderr)
        return None
    ex = _first([r'"Ex-?showroom"\s*,\s*"price"\s*:\s*"?([\d,]{6,9})'], html)
    on = _first(
        [
            r'On-?road\s*price[^0-9]{0,90}?%s"[^0-9]{0,40}"price"\s*:\s*"?([\d,]{6,9})' % VARIANT,
            r'%s","priceCurrency":"[A-Z]{3}","price":"?([\d,]{6,9})' % VARIANT,
        ],
        html,
    )
    if ex is None and on is None:
        print("[bikedekho] no prices parsed", file=sys.stderr)
        return None
    return {"source": "bikedekho", "ex_showroom": ex, "on_road": on, "url": url}


def load_history() -> list[dict]:
    if DATA_FILE.exists():
        try:
            return json.loads(DATA_FILE.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            print("[history] file unreadable, starting fresh", file=sys.stderr)
    return []


def main() -> int:
    sources = [s for s in (scrape_bikewale(), scrape_bikedekho()) if s]
    if not sources:
        print("ERROR: every source failed; not writing a record.", file=sys.stderr)
        return 1

    on_roads = [s["on_road"] for s in sources if s["on_road"]]
    ex_shows = [s["ex_showroom"] for s in sources if s["ex_showroom"]]

    record = {
        "date": datetime.now(IST).strftime("%Y-%m-%d"),
        "checked_at": datetime.now(IST).isoformat(timespec="seconds"),
        "bike": BIKE,
        "variant": VARIANT,
        "city": CITY,
        # The headline number the app graphs & alerts on: the lowest on-road
        # price across sources (best deal we can currently see).
        "on_road": min(on_roads) if on_roads else None,
        "ex_showroom": min(ex_shows) if ex_shows else None,
        "sources": {s["source"]: {"on_road": s["on_road"], "ex_showroom": s["ex_showroom"]} for s in sources},
    }

    if record["on_road"] is None and record["ex_showroom"] is None:
        print("ERROR: sources returned no usable price.", file=sys.stderr)
        return 1

    history = load_history()

    # Overwrite today's entry if the job runs more than once in a day.
    history = [h for h in history if h.get("date") != record["date"]]

    prev = history[-1] if history else None
    history.append(record)
    DATA_FILE.parent.mkdir(parents=True, exist_ok=True)
    DATA_FILE.write_text(json.dumps(history, indent=2, ensure_ascii=False), encoding="utf-8")

    # Human-readable log line for the Actions console.
    cur = record["on_road"]
    if prev and prev.get("on_road") and cur:
        delta = cur - prev["on_road"]
        arrow = "DOWN" if delta < 0 else ("UP" if delta > 0 else "same")
        print(f"On-road: Rs {cur:,} ({arrow} {abs(delta):,} vs {prev['date']})  ex-showroom Rs {record['ex_showroom']:,}")
    else:
        print(f"On-road: Rs {cur:,}  ex-showroom Rs {record['ex_showroom']:,}  (first record)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
