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
import org.akanework.gramophone.logic.AppUiEvents
import org.akanework.gramophone.logic.ApplicationScope
import org.akanework.gramophone.logic.library.LibraryReadiness
import org.akanework.gramophone.logic.library.LibraryRefresher
import org.akanework.gramophone.logic.library.LibraryWriteRepository
import org.akanework.gramophone.logic.library.MediaConsentRequester
import org.akanework.gramophone.logic.settings.LibraryFilterSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.nift4.gramophone.hificore.UacManager
import uk.akane.libphonograph.reader.FlowReader

// ApplicationScope is only bound as itself: constructors that take a CoroutineScope get it
// passed explicitly, so nothing can inject the process scope just by asking for a scope.
val appModule = module {
    singleOf(::ApplicationScope)
    singleOf(::UacManager)
    single { LibraryFilterSettings(androidContext(), get<ApplicationScope>()) }
    single {
        val settings = get<LibraryFilterSettings>()
        FlowReader(
            androidContext(),
            if (BuildConfig.DISABLE_MEDIA_STORE_FILTER) MutableStateFlow(0) else
                settings.minSongLengthSecondsFlow,
            settings.blackListSetFlow,
            settings.whiteListSetFlow,
            settings.shouldUseEnhancedCoverReadingFlow,
            MutableStateFlow(LibraryFilterSettings.RECENTLY_ADDED_WINDOW_SECONDS),
        )
    }
    singleOf(::MediaConsentRequester)
    single { LibraryWriteRepository(androidContext(), get(), get<ApplicationScope>(), get()) }
    single { LibraryRefresher(androidContext(), get(), get<ApplicationScope>(), get()) }
    singleOf(::LibraryReadiness)
    singleOf(::AppUiEvents)
}
