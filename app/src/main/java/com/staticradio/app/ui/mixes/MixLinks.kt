package com.staticradio.app.ui.mixes

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Plain ACTION_VIEW is enough here — SoundCloud/Mixcloud both register their
 * own domains as intent-filter targets in their manifests, so Android already
 * routes to the installed app when present and falls back to the browser
 * otherwise. No package-targeting needed (and none of the fragility that
 * comes with hardcoding a package name that could go stale).
 */
fun openMixExternally(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
