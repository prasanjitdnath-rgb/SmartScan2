package com.smartscan.app

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {
    // Google's official TEST ad units. Replace with your own AdMob unit IDs before publishing.
    const val BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val APP_OPEN_ID = "ca-app-pub-3940256099942544/9257395921"

    private var interstitial: InterstitialAd? = null
    private var actionCount = 0
    private var launchShown = false
    private val startTime = SystemClock.elapsedRealtime()

    fun init(ctx: Context) {
        MobileAds.initialize(ctx) { }
        loadInterstitial(ctx)
    }

    /** App-open ad shown once when the app launches. */
    fun showOnLaunch(activity: Activity) {
        if (launchShown) return
        AppOpenAd.load(activity, APP_OPEN_ID, AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    if (launchShown || activity.isFinishing || activity.isDestroyed) return
                    if (SystemClock.elapsedRealtime() - startTime > 8000) return
                    launchShown = true
                    ad.show(activity)
                }
                override fun onAdFailedToLoad(e: LoadAdError) { }
            })
    }

    private fun loadInterstitial(ctx: Context) {
        InterstitialAd.load(ctx, INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null }
            })
    }

    /** Full-screen ad after every 2nd scan/export action. */
    fun afterAction(activity: Activity) {
        actionCount++
        val ad = interstitial
        if (actionCount % 2 == 0 && ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { interstitial = null; loadInterstitial(activity) }
                override fun onAdFailedToShowFullScreenContent(e: AdError) { interstitial = null; loadInterstitial(activity) }
            }
            ad.show(activity)
        }
    }
}
