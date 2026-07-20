package com.staticradio.app

import android.app.Application
import com.staticradio.app.data.local.AppDatabase
import com.staticradio.app.data.settings.SettingsRepository
import com.staticradio.app.playback.PlaybackRepository
import com.staticradio.app.playback.RadioController

/**
 * Manual DI container for the skeleton — see PlaybackRepository's own note.
 * Swap for Hilt once the app grows past this stage.
 */
class StaticRadioApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val playbackRepository: PlaybackRepository by lazy { PlaybackRepository.getInstance() }
    val radioController: RadioController by lazy { RadioController(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
}
