# Xpulse Price Tracker

Track the **Hero Xpulse 200 4V** on-road price in **Thiruvananthapuram**, see its
history as a graph, and get a phone notification when it drops.

It has two parts:

```
┌─────────────────────────────┐        ┌──────────────────────────────┐
│  CLOUD  (GitHub Actions)     │        │  ANDROID APP (Kotlin/Compose) │
│  Runs once a day, free:      │        │                               │
│  • scrape BikeWale+BikeDekho │  reads │  • shows current price        │
│  • save data/price_history   │───────▶│  • draws the history graph 📈 │
│    .json back to this repo   │  JSON  │  • notifies you on a drop 🔔   │
└─────────────────────────────┘        └──────────────────────────────┘
```

The cloud does the daily watching (a phone app can't do this reliably on its
own — Android kills background work). The app reads the history and alerts you.

---

## Part A — Put it on GitHub and turn on the daily check

1. **Create a new PUBLIC repo** on GitHub named `xpulse-price-tracker`.
   (Public so the app can read the JSON without a login. It contains only bike
   prices — nothing private.)

2. **Push this folder** to it:
   ```bash
   cd xpulse-price-tracker
   git init
   git add .
   git commit -m "Xpulse price tracker: scraper, workflow, Android app"
   git branch -M main
   git remote add origin https://github.com/YOUR_GITHUB_USERNAME/xpulse-price-tracker.git
   git push -u origin main
   ```

3. **Enable Actions:** open the repo's **Actions** tab. If it asks, click
   *"I understand my workflows, enable them"*.

4. **Run it once to check it works:** Actions → *Daily Xpulse price check* →
   **Run workflow**. After ~1 minute it should commit an updated
   `data/price_history.json`. Open that file — you should see today's price.

   > The workflow already grants itself write access (`permissions: contents:
   > write`). If the push step ever fails, go to **Settings → Actions → General
   > → Workflow permissions** and pick **Read and write permissions**.

After this, it runs by itself every day at **09:00 IST**. A new dated record is
added each day, so the graph fills in over time.

---

## Part B — Build the app

1. **Set your repo URL in the app.** Open
   `android/app/src/main/java/com/xpulse/tracker/Config.kt` and change
   `YOUR_GITHUB_USERNAME` to your GitHub username:
   ```kotlin
   const val RAW_HISTORY_URL =
       "https://raw.githubusercontent.com/YOUR_GITHUB_USERNAME/xpulse-price-tracker/main/data/price_history.json"
   ```

2. **Open the app in Android Studio:** *File → Open* and choose the **`android`**
   folder (not the repo root). Let it finish **Gradle sync** — it downloads
   Gradle and the libraries on first open.

   > If Android Studio says the Gradle **wrapper** is missing, let it set one up,
   > or run `gradle wrapper --gradle-version 8.9` inside the `android` folder.

3. **Run it** ▶ on an emulator or your phone. When it asks, **allow
   notifications** (needed for drop alerts on Android 13+).

That's it — you'll see the current price, the history graph, and the app will
check every 6 hours in the background and notify you on a drop.

---

## How the alert works

- The app remembers the last on-road price it saw.
- Every ~6 hours (and each time you open it) it fetches the latest price.
- If the new price is **lower**, you get a notification: *"Xpulse price dropped
  to ₹X — down ₹Y."*

**To test the notification now** without waiting for a real drop: in the repo,
hand-edit the last `on_road` value in `data/price_history.json` to something
lower, commit & push, then tap **Check now** in the app.

---

## Change the bike, city, or variant

Everything is in **`scraper/scrape.py`** at the top (`BIKE`, `VARIANT`, `CITY`,
and the two source URLs in `scrape_bikewale()` / `scrape_bikedekho()`). Update
those URLs to the matching BikeWale/BikeDekho pages for your bike and city.

---

## ⚠️ Reality check before you rely on this

- **Bike prices move slowly** — a few times a year (festive offers like Onam /
  Diwali, year-end clearance, insurance changes), not daily. This tracker is for
  catching those windows, not a live ticker.
- **"Book now, pay later at the low price" usually doesn't hold.** A booking is a
  small token amount, and most dealers **invoice at the price on the delivery
  date**, not the booking date. Some honour booking-time offers — it's
  dealer-specific. **Get the dealer's price-protection policy in writing before
  you book.** The real win here is: get alerted when a good offer appears, then
  go buy at that moment.
- Aggregator prices are indicative. Always confirm the final on-road quote with a
  Hero dealer.

---

## What's in here

```
scraper/scrape.py            # the daily price scraper (Python)
scraper/requirements.txt
.github/workflows/price-check.yml   # runs the scraper daily (free)
data/price_history.json      # the price history (written by the cloud job)
android/                     # the Kotlin / Jetpack Compose app
```
