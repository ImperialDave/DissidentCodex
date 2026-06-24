package com.codex.app.utils

import com.codex.app.models.Post

object FeedRank {
    const val FEED_DM_STRIP_LIMIT = 5
    const val FEED_FAVORITE_POSTS_LIMIT = 8

    data class PartitionedPosts(
        val favoritePosts: List<Post>,
        val allPosts: List<Post>
    )

    fun partitionFeedPosts(
        posts: List<Post>,
        favoriteTopicNames: Set<String>,
        favoriteLimit: Int = FEED_FAVORITE_POSTS_LIMIT
    ): PartitionedPosts {
        if (favoriteTopicNames.isEmpty()) {
            return PartitionedPosts(emptyList(), posts.sortedByDescending { it.createdAt?.seconds ?: 0L })
        }

        val sorted = posts.sortedByDescending { it.createdAt?.seconds ?: 0L }
        val favoritePosts = mutableListOf<Post>()
        val favoriteIds = mutableSetOf<String>()

        for (post in sorted) {
            if (favoritePosts.size >= favoriteLimit) break
            if (favoriteTopicNames.contains(post.category.lowercase())) {
                favoritePosts.add(post)
                favoriteIds.add(post.id)
            }
        }

        val rest = sorted.filter { it.id !in favoriteIds }
        return PartitionedPosts(favoritePosts, rest)
    }
}