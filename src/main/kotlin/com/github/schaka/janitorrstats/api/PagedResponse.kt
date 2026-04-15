package com.github.schaka.janitorrstats.api

/**
 * Generic pagination wrapper returned by all paginated history endpoints.
 *
 * @param content the items on the current page
 * @param page zero-based page index
 * @param pageSize the requested page size
 * @param totalItems total number of matching records across all pages
 * @param totalPages total number of pages given [pageSize]
 */
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int
) {
    companion object {
        fun <T> of(content: List<T>, page: Int, pageSize: Int, totalItems: Long): PagedResponse<T> {
            val totalPages = if (totalItems == 0L) 1 else ((totalItems + pageSize - 1) / pageSize).toInt()
            return PagedResponse(content, page, pageSize, totalItems, totalPages)
        }
    }
}
