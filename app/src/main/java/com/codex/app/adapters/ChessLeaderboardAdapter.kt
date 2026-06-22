package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.codex.app.R
import com.codex.app.databinding.ItemChessLeaderboardBinding
import com.codex.app.models.ChessLeaderboardEntry

class ChessLeaderboardAdapter(
    private val onEntryClick: (ChessLeaderboardEntry) -> Unit
) : RecyclerView.Adapter<ChessLeaderboardAdapter.VH>() {

    private var items: List<ChessLeaderboardEntry> = emptyList()

    fun submitList(list: List<ChessLeaderboardEntry>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChessLeaderboardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VH(private val b: ItemChessLeaderboardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(entry: ChessLeaderboardEntry) {
            b.rankBadge.text = entry.rank.toString()
            b.nameText.text = entry.displayName
            b.eloText.text = entry.elo.toString()
            b.recordText.text = b.root.context.getString(
                R.string.chess_record,
                entry.wins,
                entry.losses,
                entry.draws
            )
            Glide.with(b.avatar)
                .load(entry.photoUrl?.takeIf { it.isNotBlank() } ?: R.drawable.default_avatar)
                .placeholder(R.drawable.default_avatar)
                .into(b.avatar)
            b.root.setOnClickListener { onEntryClick(entry) }
        }
    }
}