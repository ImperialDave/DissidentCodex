package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codex.app.databinding.ItemSearchTopicBinding
import com.codex.app.models.PostCategory

class SearchTopicAdapter(
    private val onTopicClick: (PostCategory) -> Unit,
    private val onModMenu: ((PostCategory) -> Unit)? = null
) : RecyclerView.Adapter<SearchTopicAdapter.TopicViewHolder>() {

    private var topics: List<PostCategory> = emptyList()
    private var showModActions = false

    fun submitList(newTopics: List<PostCategory>) {
        topics = newTopics
        notifyDataSetChanged()
    }

    fun setShowModActions(show: Boolean) {
        showModActions = show
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicViewHolder {
        val binding = ItemSearchTopicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TopicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TopicViewHolder, position: Int) {
        holder.bind(topics[position])
    }

    override fun getItemCount() = topics.size

    inner class TopicViewHolder(private val binding: ItemSearchTopicBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(topic: PostCategory) {
            binding.topicName.text = topic.name
            binding.root.setOnClickListener { onTopicClick(topic) }
            val showMenu = showModActions && onModMenu != null
            binding.topicMenuBtn.visibility = if (showMenu) View.VISIBLE else View.GONE
            binding.topicMenuBtn.setOnClickListener { onModMenu?.invoke(topic) }
        }
    }
}