package com.codex.app.utils

import com.codex.app.models.Comment

data class CommentThreadItem(
    val comment: Comment,
    val depth: Int
)

object CommentThread {
    fun flatten(comments: List<Comment>): List<CommentThreadItem> {
        val byParent = comments.groupBy { it.parentCommentId.orEmpty().ifBlank { "" } }
        fun walk(parentId: String, depth: Int): List<CommentThreadItem> {
            val children = byParent[parentId].orEmpty().sortedBy { it.createdAt?.seconds ?: 0L }
            return children.flatMap { comment ->
                listOf(CommentThreadItem(comment, depth)) + walk(comment.id, depth + 1)
            }
        }
        return walk("", 0)
    }

    fun collectDescendantIds(rootId: String, comments: List<Comment>): Set<String> {
        val byParent = comments.groupBy { it.parentCommentId.orEmpty().ifBlank { "" } }
        val result = mutableSetOf<String>()
        fun walk(parentId: String) {
            for (child in byParent[parentId].orEmpty()) {
                result.add(child.id)
                walk(child.id)
            }
        }
        walk(rootId)
        return result
    }
}