package com.codex.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codex.app.R
import com.codex.app.databinding.ItemModerationCategoryBinding

data class ModerationCategoryItem(
    val id: String,
    val name: String,
    val isRegistered: Boolean
)

class ModerationCategoryAdapter(
    private val onDelete: (ModerationCategoryItem) -> Unit
) : RecyclerView.Adapter<ModerationCategoryAdapter.CategoryViewHolder>() {

    private var categories: List<ModerationCategoryItem> = emptyList()

    fun submitList(newCategories: List<ModerationCategoryItem>) {
        categories = newCategories
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemModerationCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount() = categories.size

    inner class CategoryViewHolder(private val binding: ItemModerationCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ModerationCategoryItem) {
            binding.categoryName.text = item.name
            binding.categoryMeta.text = if (item.isRegistered) {
                binding.root.context.getString(R.string.category_registered)
            } else {
                binding.root.context.getString(R.string.category_feed_only)
            }
            binding.deleteCategoryBtn.setOnClickListener { onDelete(item) }
        }
    }
}