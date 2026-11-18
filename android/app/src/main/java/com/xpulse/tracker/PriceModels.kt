package com.xpulse.tracker

/** One day's price record, as produced by the cloud scraper. */
data class PricePoint(
    val date: String,
    val onRoad: Int,
    val exShowroom: Int,
    val city: String,
    val variant: String,
)
