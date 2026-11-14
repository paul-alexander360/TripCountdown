package com.example.tripcountdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/** A banner ad that fits the width of the screen. The ad unit id comes from res/values/ads.xml. */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val unitId = ctx.getString(R.string.banner_ad_unit)
    val adView = remember(widthDp) {
        AdView(ctx).apply {
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
            adUnitId = unitId
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }
    AndroidView(factory = { adView }, modifier = modifier.fillMaxWidth())
}
