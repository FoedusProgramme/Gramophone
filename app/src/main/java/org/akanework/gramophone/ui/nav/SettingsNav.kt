package org.akanework.gramophone.ui.nav

import org.akanework.gramophone.R
import org.akanework.gramophone.ui.fragments.settings.AboutSettingsFragment
import org.akanework.gramophone.ui.fragments.settings.AppearanceSettingsFragment
import org.akanework.gramophone.ui.fragments.settings.AudioSettingsFragment
import org.akanework.gramophone.ui.fragments.settings.BehaviorSettingsFragment
import org.akanework.gramophone.ui.fragments.settings.ExperimentalSettingsFragment
import org.akanework.gramophone.ui.fragments.settings.LyricSettingsFragment
import org.akanework.gramophone.ui.fragments.settings.MainSettingsFragment
import org.akanework.gramophone.ui.fragments.settings.PlayerSettingsFragment
import org.akanework.gramophone.ui.fragments.settings.ReplayGainSettingsFragment

class SettingsKey(val fragmentClassName: String, val titleRes: Int) : AppNavKey {
    override val wantsPlayer = false
}

class OssLicensesKey : AppNavKey {
    override val wantsPlayer = false
}

class ContributorsKey : AppNavKey {
    override val wantsPlayer = false
}

class BlacklistKey : AppNavKey {
    override val wantsPlayer = false
}

fun mainSettingsKey() =
    SettingsKey(MainSettingsFragment::class.java.name, R.string.home_menu_settings)

fun appearanceSettingsKey() =
    SettingsKey(AppearanceSettingsFragment::class.java.name, R.string.settings_category_appearance)

fun behaviorSettingsKey() =
    SettingsKey(BehaviorSettingsFragment::class.java.name, R.string.settings_category_behavior)

fun audioSettingsKey() =
    SettingsKey(AudioSettingsFragment::class.java.name, R.string.settings_player_options)

fun replayGainSettingsKey() =
    SettingsKey(ReplayGainSettingsFragment::class.java.name, R.string.settings_replaygain)

fun playerSettingsKey() =
    SettingsKey(PlayerSettingsFragment::class.java.name, R.string.settings_player_ui)

fun lyricSettingsKey() =
    SettingsKey(LyricSettingsFragment::class.java.name, R.string.settings_lyric)

fun experimentalSettingsKey() =
    SettingsKey(ExperimentalSettingsFragment::class.java.name, R.string.settings_experimental_settings)

fun aboutSettingsKey() =
    SettingsKey(AboutSettingsFragment::class.java.name, R.string.settings_about_app)
