package com.codex.app.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.codex.app.R
import com.codex.app.databinding.ActivitySearchHubBinding
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayoutMediator

class SearchHubActivity : BaseThemedActivity() {

    private lateinit var binding: ActivitySearchHubBinding
    private lateinit var pagerAdapter: SearchPagerAdapter
    private val tabFragments = listOf(
        SearchTabFragment.newInstance(SearchTabFragment.TabType.POSTS),
        SearchTabFragment.newInstance(SearchTabFragment.TabType.TOPICS),
        SearchTabFragment.newInstance(SearchTabFragment.TabType.CHATS)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        pagerAdapter = SearchPagerAdapter(this, tabFragments)
        binding.searchPager.adapter = pagerAdapter

        TabLayoutMediator(binding.searchTabs, binding.searchPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.search_posts_tab)
                1 -> getString(R.string.search_topics_tab)
                else -> getString(R.string.search_chats_tab)
            }
        }.attach()

        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                tabFragments.forEach { it.updateQuery(q) }
                updateRecentVisibility(q)
            }
        })

        binding.searchInput.setOnEditorActionListener { _, _, _ ->
            val q = binding.searchInput.text?.toString()?.trim().orEmpty()
            if (q.isNotBlank()) saveRecentSearch(q)
            false
        }

        loadRecentSearches()
    }

    private fun updateRecentVisibility(query: String) {
        binding.recentScroll.visibility = if (query.isBlank()) View.VISIBLE else View.GONE
    }

    private fun loadRecentSearches() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val recent = prefs.getStringSet(KEY_RECENT, emptySet())?.toList()?.sorted() ?: emptyList()
        binding.recentChips.removeAllViews()
        if (recent.isEmpty()) {
            binding.recentScroll.visibility = View.GONE
            return
        }
        recent.take(8).forEach { term ->
            val chip = Chip(this).apply {
                text = term
                isClickable = true
                setOnClickListener {
                    binding.searchInput.setText(term)
                    binding.searchInput.setSelection(term.length)
                }
            }
            binding.recentChips.addView(chip)
        }
    }

    private fun saveRecentSearch(query: String) {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_RECENT, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        existing.add(query.lowercase())
        val trimmed = existing.toList().takeLast(8).toSet()
        prefs.edit().putStringSet(KEY_RECENT, trimmed).apply()
        loadRecentSearches()
    }

    private class SearchPagerAdapter(
        activity: FragmentActivity,
        private val fragments: List<Fragment>
    ) : FragmentStateAdapter(activity) {
        override fun getItemCount() = fragments.size
        override fun createFragment(position: Int) = fragments[position]
    }

    companion object {
        private const val PREFS = "search_hub"
        private const val KEY_RECENT = "recent_searches"
    }
}