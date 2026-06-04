/*
 *     Copyright (C) 2024 Akane Foundation
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

package org.akanework.gramophone.ui.adapters

import android.net.Uri
import org.akanework.gramophone.logic.comparators.SupportComparator
import org.akanework.gramophone.logic.utils.CalculationUtils
import java.io.File

class Sorter<T>(
    val sortingHelper: Helper<T>,
    private val naturalOrderHelper: NaturalOrderHelper<T>?,
    private val rawOrderExposed: Type? = null
) {

    abstract class Helper<T>(typesSupported: Set<Type>) {
        init {
            if (typesSupported.contains(Type.NaturalOrder) || typesSupported.contains(Type.None))
                throw IllegalStateException()
        }

        val typesSupported = typesSupported.toMutableSet().apply { add(Type.None) }.toSet()
        abstract fun getTitle(item: T): String?
        abstract fun getId(item: T): String
        abstract fun getCover(item: T): Uri?

        open fun getArtist(item: T): String? = throw UnsupportedOperationException()
        open fun getFile(item: T): File = throw UnsupportedOperationException()
        open fun getAlbumTitle(item: T): String? = throw UnsupportedOperationException()
        open fun getAlbumArtist(item: T): String? = throw UnsupportedOperationException()
        open fun getAlbumYear(item: T): Long? = throw UnsupportedOperationException()
        open fun getSize(item: T): Int = throw UnsupportedOperationException()
        open fun getAlbumSize(item: T): Int = throw UnsupportedOperationException()
        open fun getAddDate(item: T): Long = throw UnsupportedOperationException()
        open fun getReleaseDate(item: T): Long = throw UnsupportedOperationException()
        open fun getModifiedDate(item: T): Long = throw UnsupportedOperationException()
        open fun getDiscAndTrack(item: T): Int = throw UnsupportedOperationException()
        fun canGetTitle(): Boolean = typesSupported.contains(Type.ByTitleAscending)
                || typesSupported.contains(Type.ByTitleDescending)

        fun canGetArtist(): Boolean = typesSupported.contains(Type.ByArtistAscending)
                || typesSupported.contains(Type.ByArtistDescending)

        fun canGetAlbumTitle(): Boolean = typesSupported.contains(Type.ByAlbumTitleAscending)
                || typesSupported.contains(Type.ByAlbumTitleDescending)

        fun canGetAlbumArtist(): Boolean = typesSupported.contains(Type.ByAlbumArtistAscending)
                || typesSupported.contains(Type.ByAlbumArtistDescending)

        fun canGetAlbumYear(): Boolean = typesSupported.contains(Type.ByAlbumYearAscending)
                || typesSupported.contains(Type.ByAlbumYearDescending)

        fun canGetSize(): Boolean = typesSupported.contains(Type.BySizeAscending)
                || typesSupported.contains(Type.BySizeDescending)

        fun canGetAlbumSize(): Boolean = typesSupported.contains(Type.ByAlbumSizeAscending)
                || typesSupported.contains(Type.ByAlbumSizeDescending)

        fun canGetDiskAndTrack(): Boolean = typesSupported.contains(Type.ByDiscAndTrack)

        fun canGetAddDate(): Boolean = typesSupported.contains(Type.ByAddDateAscending)
                || typesSupported.contains(Type.ByAddDateDescending)

        fun canGetFile(): Boolean = typesSupported.contains(Type.ByFilePathAscending)
                || typesSupported.contains(Type.ByFilePathDescending)

        fun canGetReleaseDate(): Boolean = typesSupported.contains(Type.ByReleaseDateAscending)
                || typesSupported.contains(Type.ByReleaseDateDescending)

        fun canGetModifiedDate(): Boolean = typesSupported.contains(Type.ByModifiedDateAscending)
                || typesSupported.contains(Type.ByModifiedDateDescending)
    }

    fun interface NaturalOrderHelper<T> {
        fun lookup(item: T): Int
    }

    enum class Type {
        ByTitleDescending, ByTitleAscending,
        ByArtistDescending, ByArtistAscending,
        ByArtistYearDescending, ByArtistYearAscending,
        ByAlbumTitleDescending, ByAlbumTitleAscending,
        ByAlbumArtistDescending, ByAlbumArtistAscending,
        ByAlbumArtistYearDescending, ByAlbumArtistYearAscending,
        ByAlbumYearDescending, ByAlbumYearAscending,
        BySizeDescending, BySizeAscending,
        ByAlbumSizeDescending, ByAlbumSizeAscending,
        NaturalOrder, ByAddDateDescending, ByAddDateAscending,
        ByReleaseDateDescending, ByReleaseDateAscending,
        ByModifiedDateDescending, ByModifiedDateAscending,
        ByFilePathDescending, ByFilePathAscending,
        ByDiscAndTrack,
        None;

        companion object {
            fun inverse(type: Type): Type? = when (type) {
                ByTitleDescending -> ByTitleAscending
                ByTitleAscending -> ByTitleDescending
                ByArtistDescending -> ByArtistAscending
                ByArtistAscending -> ByArtistDescending
                ByArtistYearDescending -> ByArtistYearAscending
                ByArtistYearAscending -> ByArtistYearDescending
                ByAlbumTitleDescending -> ByAlbumTitleAscending
                ByAlbumTitleAscending -> ByAlbumTitleDescending
                ByAlbumArtistDescending -> ByAlbumArtistAscending
                ByAlbumArtistAscending -> ByAlbumArtistDescending
                ByAlbumArtistYearDescending -> ByAlbumArtistYearAscending
                ByAlbumArtistYearAscending -> ByAlbumArtistYearDescending
                ByAlbumYearDescending -> ByAlbumYearAscending
                ByAlbumYearAscending -> ByAlbumYearDescending
                BySizeDescending -> BySizeAscending
                BySizeAscending -> BySizeDescending
                ByAlbumSizeDescending -> ByAlbumSizeAscending
                ByAlbumSizeAscending -> ByAlbumSizeDescending
                NaturalOrder -> null
                ByAddDateDescending -> ByAddDateAscending
                ByAddDateAscending -> ByAddDateDescending
                ByReleaseDateDescending -> ByReleaseDateAscending
                ByReleaseDateAscending -> ByReleaseDateDescending
                ByModifiedDateDescending -> ByModifiedDateAscending
                ByModifiedDateAscending -> ByModifiedDateDescending
                ByFilePathDescending -> ByFilePathAscending
                ByFilePathAscending -> ByFilePathDescending
                ByDiscAndTrack -> null
                None -> null
            }
        }
    }

    fun getSupportedTypes(): Set<Type> {
        return sortingHelper.typesSupported.let { types ->
            if (naturalOrderHelper != null || rawOrderExposed == Type.NaturalOrder)
                types + Type.NaturalOrder
            else types
        }
    }

    fun getComparator(type: Type): Pair<HintedComparator<T>?, Boolean> {
        if (rawOrderExposed != null && type == Type.inverse(rawOrderExposed)) {
            if (!getSupportedTypes().contains(type))
                throw IllegalArgumentException("Unsupported type ${type.name}")
            return null to true
        }
        return getComparatorNoReverse(type) to false
    }

    private fun getComparatorNoReverse(type: Type): HintedComparator<T>? {
        if (!getSupportedTypes().contains(type))
            throw IllegalArgumentException("Unsupported type ${type.name}")
        if (type == rawOrderExposed || type == Type.None) return null
        return WrappingHintedComparator(
            type, when (type) {
            Type.ByTitleDescending -> {
                SupportComparator.createAlphanumericComparator(true, {
                    sortingHelper.getTitle(it)
                }, null)
            }

            Type.ByTitleAscending -> {
                SupportComparator.createAlphanumericComparator(false, {
                    sortingHelper.getTitle(it)
                }, null)
            }

            Type.ByArtistDescending -> {
                SupportComparator.createAlphanumericComparator(
                    true, {
                        sortingHelper.getArtist(it)
                    }, getComparatorNoReverse(
                        Type.ByTitleDescending
                    )
                )
            }

            Type.ByArtistAscending -> {
                SupportComparator.createAlphanumericComparator(
                    false,
                    {
                        sortingHelper.getArtist(it)
                    },
                    getComparatorNoReverse(Type.ByTitleAscending)
                )
            }

            Type.ByArtistYearDescending -> {
                SupportComparator.createAlphanumericComparator(
                    true, {
                        sortingHelper.getArtist(it)
                    }, getComparatorNoReverse(
                        Type.ByReleaseDateAscending
                    )
                )
            }

            Type.ByArtistYearAscending -> {
                SupportComparator.createAlphanumericComparator(
                    false,
                    {
                        sortingHelper.getArtist(it)
                    },
                    getComparatorNoReverse(Type.ByReleaseDateDescending)
                )
            }

            Type.ByAlbumTitleDescending -> {
                SupportComparator.createAlphanumericComparator(true, {
                    sortingHelper.getAlbumTitle(it)
                }, getComparatorNoReverse(Type.ByDiscAndTrack))
            }

            Type.ByAlbumTitleAscending -> {
                SupportComparator.createAlphanumericComparator(false, {
                    sortingHelper.getAlbumTitle(it)
                }, getComparatorNoReverse(Type.ByDiscAndTrack))
            }

            Type.ByAlbumArtistDescending -> {
                SupportComparator.createAlphanumericComparator(true, {
                    sortingHelper.getAlbumArtist(it)
                }, getComparatorNoReverse(Type.ByAlbumTitleDescending))
            }

            Type.ByAlbumArtistAscending -> {
                SupportComparator.createAlphanumericComparator(false, {
                    sortingHelper.getAlbumArtist(it)
                }, getComparatorNoReverse(Type.ByAlbumTitleAscending))
            }

            Type.ByAlbumArtistYearDescending -> {
                SupportComparator.createAlphanumericComparator(true, {
                    sortingHelper.getAlbumArtist(it)
                }, getComparatorNoReverse(Type.ByAlbumYearDescending))
            }

            Type.ByAlbumArtistYearAscending -> {
                SupportComparator.createAlphanumericComparator(false, {
                    sortingHelper.getAlbumArtist(it)
                }, getComparatorNoReverse(Type.ByAlbumYearAscending))
            }

            Type.ByAlbumYearDescending -> {
                SupportComparator.createInversionComparator(compareBy {
                    sortingHelper.getAlbumYear(it) ?: Long.MIN_VALUE
                }, true, getComparatorNoReverse(Type.ByAlbumTitleAscending))
            }

            Type.ByAlbumYearAscending -> {
                SupportComparator.createInversionComparator(compareBy {
                    sortingHelper.getAlbumYear(it) ?: Long.MIN_VALUE
                }, false, getComparatorNoReverse(Type.ByAlbumTitleDescending))
            }

            Type.BySizeDescending -> {
                SupportComparator.createInversionComparator(
                    compareBy { sortingHelper.getSize(it) }, true
                )
            }

            Type.BySizeAscending -> {
                SupportComparator.createInversionComparator(
                    compareBy { sortingHelper.getSize(it) }, false
                )
            }

            Type.ByAlbumSizeDescending -> {
                SupportComparator.createInversionComparator(
                    compareBy { sortingHelper.getAlbumSize(it) }, true
                )
            }

            Type.ByAlbumSizeAscending -> {
                SupportComparator.createInversionComparator(
                    compareBy { sortingHelper.getAlbumSize(it) }, false
                )
            }

            Type.ByAddDateDescending -> {
                SupportComparator.createInversionComparator(
                    compareBy { sortingHelper.getAddDate(it) }, true
                )
            }

            Type.ByAddDateAscending -> {
                SupportComparator.createInversionComparator(
                    compareBy { sortingHelper.getAddDate(it) }, false
                )
            }

            Type.ByReleaseDateDescending -> {
                SupportComparator.createInversionComparator(
                    compareBy { sortingHelper.getReleaseDate(it) }, true,
                    if (sortingHelper.canGetDiskAndTrack()) getComparatorNoReverse(Type.ByDiscAndTrack) else null
                )
            }

            Type.ByReleaseDateAscending -> {
                SupportComparator.createInversionComparator(
                    compareBy { sortingHelper.getReleaseDate(it) }, false,
                    if (sortingHelper.canGetDiskAndTrack()) getComparatorNoReverse(Type.ByDiscAndTrack) else null
                )
            }

            Type.ByModifiedDateDescending -> {
                SupportComparator.createInversionComparator(
                    compareBy { sortingHelper.getModifiedDate(it) }, true
                )
            }

            Type.ByModifiedDateAscending -> {
                SupportComparator.createInversionComparator(
                    compareBy { sortingHelper.getModifiedDate(it) }, false
                )
            }

            Type.ByFilePathDescending -> {
                SupportComparator.createAlphanumericComparator(true, {
                    sortingHelper.getFile(it).path
                }, null)
            }

            Type.ByFilePathAscending -> {
                SupportComparator.createAlphanumericComparator(false, {
                    sortingHelper.getFile(it).path
                }, null)
            }

            Type.ByDiscAndTrack -> {
                compareBy { sortingHelper.getDiscAndTrack(it) }
            }

            Type.NaturalOrder -> {
                SupportComparator.createInversionComparator(
                    compareBy { naturalOrderHelper!!.lookup(it) }, false
                )
            }

            Type.None -> throw IllegalStateException()
        }
        )
    }

    fun getFastScrollHintFor(item: T, pos: Int, sortType: Type): String? {
        return when (sortType) {
            Type.ByTitleDescending, Type.ByTitleAscending -> {
                sortingHelper.getTitle(item)?.firstOrNull()?.toString()
            }

            Type.ByArtistDescending, Type.ByArtistAscending -> {
                sortingHelper.getArtist(item)?.firstOrNull()?.toString()
            }

            Type.ByArtistYearDescending, Type.ByArtistYearAscending -> {
                sortingHelper.getArtist(item)?.firstOrNull()?.toString()
            }

            Type.ByAlbumTitleDescending, Type.ByAlbumTitleAscending -> {
                sortingHelper.getAlbumTitle(item)?.firstOrNull()?.toString()
            }

            Type.ByAlbumArtistDescending, Type.ByAlbumArtistAscending -> {
                sortingHelper.getAlbumArtist(item)?.firstOrNull()?.toString()
            }

            Type.ByAlbumArtistYearDescending, Type.ByAlbumArtistYearAscending -> {
                sortingHelper.getAlbumArtist(item)?.firstOrNull()?.toString()
            }

            Type.ByAlbumYearDescending, Type.ByAlbumYearAscending -> {
                sortingHelper.getAlbumYear(item)?.toString()
            }

            Type.ByFilePathDescending, Type.ByFilePathAscending -> {
                sortingHelper.getFile(item).name.firstOrNull()?.toString()
            }

            Type.BySizeDescending, Type.BySizeAscending -> {
                sortingHelper.getSize(item).toString()
            }

            Type.ByAlbumSizeAscending, Type.ByAlbumSizeDescending -> {
                sortingHelper.getAlbumSize(item).toString()
            }

            Type.ByDiscAndTrack -> {
                sortingHelper.getDiscAndTrack(item).toString()
            }

            Type.ByAddDateDescending, Type.ByAddDateAscending -> {
                CalculationUtils.convertUnixTimestampToMonthDay(sortingHelper.getAddDate(item))
            }

            Type.ByReleaseDateDescending, Type.ByReleaseDateAscending -> {
                CalculationUtils.convertUnixTimestampToMonthDay(sortingHelper.getReleaseDate(item))
            }

            Type.ByModifiedDateDescending, Type.ByModifiedDateAscending -> {
                CalculationUtils.convertUnixTimestampToMonthDay(sortingHelper.getAddDate(item))
            }

            Type.NaturalOrder -> {
                (if (rawOrderExposed == sortType) {
                    pos
                } else {
                    naturalOrderHelper!!.lookup(item)
                } + 1).toString()
            }

            Type.None -> null
        }?.ifEmpty { null }?.uppercase()
    }

    abstract class HintedComparator<T>(val type: Type) : Comparator<T>
    private class WrappingHintedComparator<T>(type: Type, private val comparator: Comparator<T>) :
        HintedComparator<T>(type) {
        override fun compare(o1: T, o2: T): Int {
            return comparator.compare(o1, o2)
        }
    }
}
