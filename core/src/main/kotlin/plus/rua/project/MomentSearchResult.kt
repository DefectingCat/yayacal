package plus.rua.project

/** 单条动态的搜索命中字段与首个命中附近的原文摘要。 */
internal data class MomentSearchResult(
    val post: MomentPost,
    val matchedFields: List<String>,
    val previewText: String,
)

/** 搜索本机动态的正文、位置和评论文本，包含私密动态，按发布时间倒序返回。 */
internal fun searchMoments(
    posts: List<MomentPost>,
    query: String,
): List<MomentSearchResult> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return emptyList()

    return posts.mapNotNull { post ->
        val sources = listOfNotNull(
            "正文" to post.text,
            post.location?.let { "位置" to it },
            post.locationAddress?.let { "位置" to it },
        ) + post.comments.filterNot { it.deleted }.map { "评论" to it.text }
        val matches = sources.filter { (_, text) -> text.contains(keyword, ignoreCase = true) }
        if (matches.isEmpty()) {
            null
        } else {
            MomentSearchResult(
                post = post,
                matchedFields = matches.map { it.first }.distinct(),
                previewText = searchPreview(matches.first().second, keyword),
            )
        }
    }.sortedByDescending { it.post.timestamp }
}

private fun searchPreview(
    text: String,
    keyword: String,
): String {
    val hit = text.indexOf(keyword, ignoreCase = true)
    var start = (hit - 20).coerceAtLeast(0)
    var end = (hit + keyword.length + 60).coerceAtMost(text.length)
    // 摘要边界不要截断表情等补充平面字符的代理对。
    if (start > 0 && text[start].isLowSurrogate() && text[start - 1].isHighSurrogate()) start--
    if (end < text.length && text[end].isLowSurrogate() && text[end - 1].isHighSurrogate()) end++
    return (if (start > 0) "…" else "") + text.substring(start, end) + (if (end < text.length) "…" else "")
}
