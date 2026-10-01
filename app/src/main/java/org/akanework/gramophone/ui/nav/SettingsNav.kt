/*
 *     Copyright (C) 2025 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.nav

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** A settings page. Plain classes like the library keys, so nav3 never conflates two pushes. */
sealed interface SettingsKey : AppNavKey {
    override val wantsPlayer: Boolean get() = false
}

@Parcelize
data object MainSettingsKey : SettingsKey, Parcelable

@Parcelize
data object AppearanceSettingsKey : SettingsKey, Parcelable

@Parcelize
data object ThemeSettingsKey : SettingsKey, Parcelable

@Parcelize
data object PlayerSettingsKey : SettingsKey, Parcelable

@Parcelize
data object LyricSettingsKey : SettingsKey, Parcelable

@Parcelize
data object BehaviorSettingsKey : SettingsKey, Parcelable

@Parcelize
data object AudioSettingsKey : SettingsKey, Parcelable

@Parcelize
data object ReplayGainSettingsKey : SettingsKey, Parcelable

@Parcelize
data object ExperimentalSettingsKey : SettingsKey, Parcelable

@Parcelize
data object AboutSettingsKey : SettingsKey, Parcelable

@Parcelize
data object BlacklistKey : SettingsKey, Parcelable

@Parcelize
data object ContributorsKey : SettingsKey, Parcelable

@Parcelize
data object OssLicensesKey : SettingsKey, Parcelable
