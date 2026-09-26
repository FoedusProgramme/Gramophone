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

import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.intent.DefaultPlayIntentExecutor
import org.akanework.gramophone.ui.intent.PlayIntentExecutor
import org.akanework.gramophone.ui.intent.PlayIntentViewModel
import org.akanework.gramophone.ui.nav.NavViewModel
import org.akanework.gramophone.ui.state.HomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import uk.akane.libphonograph.reader.FlowReader

val viewModelModule = module {
    viewModelOf(::MediaControllerViewModel)
    viewModelOf(::NavViewModel)
    viewModelOf(::HomeViewModel)
    factory<PlayIntentExecutor> { DefaultPlayIntentExecutor(androidContext(), get<FlowReader>(), get()) }
    viewModelOf(::PlayIntentViewModel)
}
