package com.xpulse.tracker

object Config {
    // ⚠️ EDIT THIS ONE LINE after you push the project to GitHub.
    // Replace YOUR_GITHUB_USERNAME with your username. Keep the repo name and
    // branch the same unless you changed them.
    //
    // Format:
    //   https://raw.githubusercontent.com/<user>/<repo>/<branch>/data/price_history.json
    const val RAW_HISTORY_URL =
        "https://raw.githubusercontent.com/Me-FoxXY/xpulse-price-tracker/main/data/price_history.json"

    // How often the background check runs. 6 hours is plenty for a bike price
    // that changes a few times a year; Android requires at least 15 minutes.
    const val CHECK_INTERVAL_HOURS = 6L
}
