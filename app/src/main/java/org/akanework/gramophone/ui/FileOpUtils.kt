package org.akanework.gramophone.ui

import org.akanework.gramophone.R
import org.akanework.gramophone.ui.adapters.AlbumAdapter
import org.akanework.gramophone.ui.adapters.ArtistAdapter
import org.akanework.gramophone.ui.adapters.DateAdapter
import org.akanework.gramophone.ui.adapters.DetailedFolderAdapter
import org.akanework.gramophone.ui.adapters.GenreAdapter
import org.akanework.gramophone.ui.adapters.PlaylistAdapter
import org.akanework.gramophone.ui.adapters.SongAdapter
import org.akanework.gramophone.ui.fragments.AdapterFragment

fun getAdapterType(adapter: AdapterFragment.BaseInterface<*>) =
    when {
        adapter is AlbumAdapter && adapter.isSubFragment == null -> {
            0
        }

        adapter is ArtistAdapter -> {
            1
        }

        adapter is DateAdapter -> {
            2
        }

        adapter is GenreAdapter -> {
            3
        }

        adapter is PlaylistAdapter -> {
            4
        }

        adapter is SongAdapter && !adapter.folder && adapter.isSubFragment == null -> {
            5
        }

        adapter is SongAdapter && adapter.folder -> {
            6
        }

        adapter is SongAdapter && adapter.isSubFragment == R.id.search -> {
            7
        }

        adapter is SongAdapter && adapter.isSubFragment == R.id.playlist -> {
            8
        }

        adapter is SongAdapter && adapter.isSubFragment == R.id.genre -> {
            9
        }

        adapter is SongAdapter && adapter.isSubFragment == R.id.date -> {
            10
        }

        adapter is SongAdapter && (adapter.isSubFragment == R.id.artist
                || adapter.isSubFragment == R.id.album_artist) -> {
            11
        }

        adapter is SongAdapter && adapter.isSubFragment == R.id.album -> {
            12
        }

        adapter is AlbumAdapter && (adapter.isSubFragment == R.id.artist
                || adapter.isSubFragment == R.id.album_artist) -> {
            13
        }

        adapter is DetailedFolderAdapter && !adapter.isDetailed -> {
            14
        }

        adapter is DetailedFolderAdapter && adapter.isDetailed -> {
            15
        }

        adapter is SongAdapter && adapter.isSubFragment == R.id.edit -> {
            16
        }

        else -> {
            throw IllegalStateException()
        }
    }