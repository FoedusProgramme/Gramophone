package org.akanework.gramophone.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.R

class PreviewPlayerPagerAdapter (private val context: Context) :
    RecyclerView.Adapter<PreviewPlayerPagerAdapter.ViewHolder>() {

    private var titles = listOf<String>()
    private var artists = listOf<String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: MarqueeTextView = view.findViewById(R.id.preview_song_name)
        val artist: MarqueeTextView = view.findViewById(R.id.preview_artist_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.preview_player_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.title.text = titles[position]
        holder.artist.text = artists[position]
    }

    override fun getItemCount() = titles.size

    fun updateData(newTitles: List<String>, newSubtitles: List<String>) {
        titles = newTitles
        artists = newSubtitles
        notifyDataSetChanged()
    }
}