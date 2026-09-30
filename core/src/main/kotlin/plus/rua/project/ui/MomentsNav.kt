package plus.rua.project.ui

/** 朋友圈跨 Activity 导航协议。 */
object MomentsNav {
    /** 作者主页、相册及搜索的作者身份。 */
    const val EXTRA_AUTHOR_ID = "extra_moment_author_id"

    /** 动态列表/个人相册 → 详情页的动态 ID。 */
    const val EXTRA_POST_ID = "extra_moment_post_id"

    /** 从“评论”入口打开详情时自动聚焦输入框。 */
    const val EXTRA_FOCUS_COMMENT = "extra_moment_focus_comment"
}
