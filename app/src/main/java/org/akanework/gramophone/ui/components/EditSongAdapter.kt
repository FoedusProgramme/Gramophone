package org.akanework.gramophone.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.ui.placeholderScaleToFit
import org.akanework.gramophone.logic.utils.convertDurationToTimeStamp

// Like SongAdapter, but without layouts, sorting, or flows; instead supporting drag, swipe & remove
abstract class EditSongAdapter(
    private val context: Context
) : MyRecyclerView.Adapter<EditSongAdapter.ViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder =
        ViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.adapter_list_card, parent, false) as ConstraintLayout,
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.songName.text = item.mediaMetadata.title
        holder.songArtist.text = context.getString(R.string.artist_time,
            item.mediaMetadata.durationMs?.convertDurationToTimeStamp(),
            item.mediaMetadata.artist ?: context.getString(R.string.unknown_artist))
        holder.songCover.load(item.mediaMetadata.artworkUri) {
            placeholderScaleToFit(R.drawable.ic_default_cover)
            crossfade(true)
            error(R.drawable.ic_default_cover)
        }
        holder.closeButton.setOnClickListener { v ->
            ViewCompat.performHapticFeedback(v, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) { // if -1, user clicked remove twice on same item
                removeItem(pos)
            }
        }
        holder.itemView.setOnClickListener { v ->
            ViewCompat.performHapticFeedback(v, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) { // if -1, user clicked remove on this item
                onClick(pos)
            }
        }
        holder.dragHandle.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN)
                startDrag(holder)
            false
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.songCover.dispose()
        holder.closeButton.setOnClickListener(null)
        holder.itemView.setOnClickListener(null)
        holder.dragHandle.setOnClickListener(null)
        super.onViewRecycled(holder)
    }

    abstract override fun getItemCount(): Int
    abstract fun startDrag(holder: ViewHolder)
    abstract fun onClick(pos: Int)
    abstract fun getItem(pos: Int): MediaItem
    abstract fun onRowMoved(from: Int, to: Int) // must call notifyItemMoved() if actually moving
    abstract fun removeItem(pos: Int) // must call notifyItemRemoved() if actually removing

    inner class PlaylistCardMoveCallback() :
        ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.START or ItemTouchHelper.END) {
        override fun isLongPressDragEnabled(): Boolean {
            return true
        }

        override fun isItemViewSwipeEnabled(): Boolean {
            return true
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val vhBap = viewHolder.bindingAdapterPosition
            val tBap = target.bindingAdapterPosition
            if (vhBap != RecyclerView.NO_POSITION && tBap != RecyclerView.NO_POSITION) {
                onRowMoved(vhBap, tBap)
                return true
            }
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val vhBap = viewHolder.bindingAdapterPosition
            if (vhBap != RecyclerView.NO_POSITION) {
                removeItem(vhBap)
            }
        }
    }

    class ViewHolder(
        view: ConstraintLayout,
    ) : RecyclerView.ViewHolder(view) {
        val songName: TextView = view.findViewById(R.id.title)
        val songArtist: TextView = view.findViewById(R.id.artist)
        val songCover: ImageView = view.findViewById(R.id.cover)
        val nowPlaying: ImageView = view.findViewById(R.id.now_playing)
        val closeButton: MaterialButton = view.findViewById(R.id.more)
        val dragHandle = ImageView(view.context)
        init {
            // don't want the hundreds of other ViewHolders using this layout to inflate view it
            // will never use, and also don't want to diverge layout for 1 view (it was diverged
            // before when I wrote this commit and was completely out of sync after a while). so, do
            // it programmatically.
            dragHandle.id = R.id.dragHandle
            val dp8 = 8.dpToPx(view.context)
            dragHandle.setPaddingRelative(10.dpToPx(view.context), dp8, 0, dp8)
            dragHandle.setImageResource(R.drawable.baseline_drag_handle_24)
            view.addView(dragHandle, ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            })
            view.findViewById<View>(R.id.coverCardView).updateLayoutParams<ConstraintLayout.LayoutParams> {
                startToStart = ConstraintLayout.LayoutParams.UNSET
                startToEnd = R.id.dragHandle
            }
            closeButton.setIconResource(R.drawable.outline_close_24)
            TooltipCompat.setTooltipText(closeButton,
                closeButton.context.getString(R.string.remove))
            closeButton.contentDescription = closeButton.context.getString(R.string.remove)
        }
    }
}