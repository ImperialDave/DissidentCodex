package com.codex.app.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codex.app.R
import com.codex.app.databinding.ItemNotificationBinding
import com.codex.app.models.AppNotification

class NotificationAdapter(
    private val onClick: (AppNotification) -> Unit,
    private val onDelete: (AppNotification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.VH>() {

    private var items: List<AppNotification> = emptyList()

    fun submitList(list: List<AppNotification>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VH(private val b: ItemNotificationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: AppNotification) {
            b.titleText.text = item.title
            b.bodyText.text = item.body
            b.unreadDot.visibility = if (item.read) View.GONE else View.VISIBLE
            b.typeIcon.setImageResource(iconFor(item.type))
            val millis = (item.createdAt?.seconds ?: 0L) * 1000L
            b.timeText.text = if (millis > 0L) {
                DateUtils.getRelativeTimeSpanString(
                    millis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            } else {
                ""
            }
            b.root.setOnClickListener { onClick(item) }
            b.deleteBtn.setOnClickListener {
                onDelete(item)
            }
        }

        private fun iconFor(type: String): Int = when (type) {
            AppNotification.TYPE_CHESS_TURN -> R.drawable.ic_chess
            AppNotification.TYPE_POST_LIKE -> R.drawable.ic_like
            AppNotification.TYPE_POST_COMMENT,
            AppNotification.TYPE_COMMENT_REPLY -> R.drawable.ic_comment
            else -> R.drawable.ic_notifications
        }
    }
}