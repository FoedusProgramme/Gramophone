/*
 *     Copyright (C) 2026 The Gramophone authors
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

package org.akanework.gramophone.di

import kotlinx.coroutines.flow.MutableStateFlow
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.ApplicationScope
import org.akanework.gramophone.logic.GramophoneApplication
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.nift4.gramophone.hificore.UacManager
import uk.akane.libphonograph.reader.FlowReader

val appModule = module {
    single { ApplicationScope() }
    single { UacManager(androidContext()) }
    single {
        // TODO(U2): take the filter flows from SettingsRepository instead of the Application.
        val app = androidApplication() as GramophoneApplication
        FlowReader(
            androidContext(),
            if (BuildConfig.DISABLE_MEDIA_STORE_FILTER) MutableStateFlow(0) else
                app.minSongLengthSecondsFlow,
            app.blackListSetFlow,
            app.whiteListSetFlow,
            if (hasScopedStorageWithMediaTypes()) MutableStateFlow(null) else
                app.shouldUseEnhancedCoverReadingFlow!!,
            app.recentlyAddedFilterSecondFlow
        )
    }
}
