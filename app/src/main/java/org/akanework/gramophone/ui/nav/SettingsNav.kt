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

/** A settings page. Plain classes like the library keys, so nav3 never conflates two pushes. */
sealed interface SettingsKey : AppNavKey {
    override val wantsPlayer: Boolean get() = false
}

class MainSettingsKey : SettingsKey
class AppearanceSettingsKey : SettingsKey
class ThemeSettingsKey : SettingsKey
class PlayerSettingsKey : SettingsKey
class LyricSettingsKey : SettingsKey
class BehaviorSettingsKey : SettingsKey
class AudioSettingsKey : SettingsKey
class ReplayGainSettingsKey : SettingsKey
class ExperimentalSettingsKey : SettingsKey
class AboutSettingsKey : SettingsKey
class BlacklistKey : SettingsKey
class ContributorsKey : SettingsKey
class OssLicensesKey : SettingsKey
