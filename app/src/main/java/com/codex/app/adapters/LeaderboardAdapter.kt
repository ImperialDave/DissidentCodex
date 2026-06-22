package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codex.app.R
import com.codex.app.databinding.ItemLeaderboardEntryBinding
import com.codex.app.databinding.ItemLeaderboardSectionBinding
import com.codex.app.models.LeaderboardEntry
import com.google.firebase.Timestamp

sealed class LeaderboardListItem {
    data class Section(val title: String) : LeaderboardListItem()
    data class Entry(val entry: LeaderboardEntry) : LeaderboardListItem()
    data object SeeAllFooter : LeaderboardListItem()
}

class LeaderboardAdapter(
    private val onEntryClick: (LeaderboardEntry) -> Unit,
    private val onSeeAllClick: (() -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<LeaderboardListItem> = emptyList()

    companion object {
        private const val TYPE_SECTION = 0
        private const val TYPE_ENTRY = 1
        private const val TYPE_FOOTER = 2
    }

    fun submitLists(
        sectionTopicsTitle: String?,
        topTopics: List<LeaderboardEntry>,
        sectionChatsTitle: String?,
        topChats: List<LeaderboardEntry>,
        showSeeAll: Boolean = false
    ) {
        val list = mutableListOf<LeaderboardListItem>()
        if (topTopics.isNotEmpty() && sectionTopicsTitle != null) {
            list.add(LeaderboardListItem.Section(sectionTopicsTitle))
            topTopics.forEach { list.add(LeaderboardListItem.Entry(it)) }
        }
        if (topChats.isNotEmpty() && sectionChatsTitle != null) {
            list.add(LeaderboardListItem.Section(sectionChatsTitle))
            topChats.forEach { list.add(LeaderboardListItem.Entry(it)) }
        }
        if (showSeeAll) list.add(LeaderboardListItem.SeeAllFooter)
        items = list
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is LeaderboardListItem.Section -> TYPE_SECTION
        is LeaderboardListItem.Entry -> TYPE_ENTRY
        is LeaderboardListItem.SeeAllFooter -> TYPE_FOOTER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SECTION -> SectionViewHolder(
                ItemLeaderboardSectionBinding.inflate(inflater, parent, false)
            )
            TYPE_FOOTER -> FooterViewHolder(
                inflater.inflate(R.layout.item_leaderboard_footer, parent, false)
            )
            else -> EntryViewHolder(
                ItemLeaderboardEntryBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is LeaderboardListItem.Section -> (holder as SectionViewHolder).bind(item.title)
            is LeaderboardListItem.Entry -> (holder as EntryViewHolder).bind(item.entry)
            is LeaderboardListItem.SeeAllFooter -> (holder as FooterViewHolder).bind()
        }
    }

    override fun getItemCount() = items.size

    inner class SectionViewHolder(private val binding: ItemLeaderboardSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.sectionTitle.text = title
        }
    }

    inner class EntryViewHolder(private val binding: ItemLeaderboardEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: LeaderboardEntry) {
            binding.rankBadge.text = entry.rank.toString()
            binding.entryTitle.text = entry.title
            val meta = if (entry.isTopic && entry.postCount > 0) {
                binding.root.context.getString(
                    R.string.leaderboard_meta_topic,
                    entry.messageCount,
                    entry.postCount
                )
            } else {
                binding.root.context.getString(R.string.leaderboard_meta_chat, entry.messageCount)
            }
            binding.entryMeta.text = meta
            binding.liveBadge.visibility = if (isRecentlyActive(entry.lastMessageAt)) {
                View.VISIBLE
            } else {
                View.GONE
            }
            binding.root.setOnClickListener { onEntryClick(entry) }
        }

        private fun isRecentlyActive(ts: Timestamp?): Boolean {
            val seconds = ts?.seconds ?: return false
            val now = System.currentTimeMillis() / 1000
            return seconds >= now - 300
        }
    }

    inner class FooterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind() {
            itemView.setOnClickListener { onSeeAllClick?.invoke() }
        }
    }
}