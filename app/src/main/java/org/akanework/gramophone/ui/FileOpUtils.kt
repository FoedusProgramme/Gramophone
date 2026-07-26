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
            LibraryAdapterTypes.ALBUM
        }

        adapter is ArtistAdapter -> {
            LibraryAdapterTypes.ARTIST
        }

        adapter is DateAdapter -> {
            LibraryAdapterTypes.DATE
        }

        adapter is GenreAdapter -> {
            LibraryAdapterTypes.GENRE
        }

        adapter is PlaylistAdapter -> {
            LibraryAdapterTypes.PLAYLIST
        }

        adapter is SongAdapter && !adapter.folder && adapter.isSubFragment == null -> {
            LibraryAdapterTypes.SONG
        }

        adapter is SongAdapter && adapter.folder -> {
            LibraryAdapterTypes.FOLDER
        }

        adapter is SongAdapter && adapter.isSubFragment == R.id.search -> {
            LibraryAdapterTypes.SEARCH
        }

        adapter is SongAdapter && adapter.isSubFragment == R.id.playlist -> {
            LibraryAdapterTypes.PLAYLIST_DYNAMIC
        }

        adapter is SongAdapter && adapter.isSubFragment == R.id.genre -> {
            LibraryAdapterTypes.GENRE_SONGS
        }

        adapter is SongAdapter && adapter.isSubFragment == R.id.date -> {
            LibraryAdapterTypes.DATE_SONGS
        }

        adapter is SongAdapter && (adapter.isSubFragment == R.id.artist
                || adapter.isSubFragment == R.id.album_artist) -> {
            LibraryAdapterTypes.ARTIST_SONGS
        }

        adapter is SongAdapter && adapter.isSubFragment == R.id.album -> {
            LibraryAdapterTypes.ALBUM_SONGS
        }

        adapter is AlbumAdapter && (adapter.isSubFragment == R.id.artist
                || adapter.isSubFragment == R.id.album_artist) -> {
            LibraryAdapterTypes.ARTIST_ALBUMS
        }

        adapter is DetailedFolderAdapter && !adapter.isDetailed -> {
            LibraryAdapterTypes.FOLDERS_SHALLOW
        }

        adapter is DetailedFolderAdapter && adapter.isDetailed -> {
            LibraryAdapterTypes.FOLDERS_DETAILED
        }

        adapter is SongAdapter && adapter.isSubFragment == R.id.edit -> {
            LibraryAdapterTypes.EDIT
        }

        adapter is SongAdapter && adapter.isSubFragment == R.id.songs -> {
            LibraryAdapterTypes.CURRENT_QUEUE
        }

        else -> {
            throw IllegalStateException()
        }
    }