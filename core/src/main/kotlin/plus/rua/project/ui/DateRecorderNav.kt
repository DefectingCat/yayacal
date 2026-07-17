package plus.rua.project.ui

/**
 * 日期记录器跨 Activity 导航协议。
 *
 * 由于项目采用 Activity + Intent 滑动转场（无 Compose Navigation），各页面之间
 * 通过 Intent extra 传递照片文件路径与记录 ID。此处集中定义 extra key，避免散落硬编码。
 */
object DateRecorderNav {
    /** 拍照后的临时照片文件绝对路径（相机页 → 编辑器页） */
    const val EXTRA_TEMP_PHOTO_PATH = "extra_temp_photo_path"

    /** 编辑器输出的最终照片文件绝对路径（编辑器页 → 记录编辑页） */
    const val EXTRA_FINAL_PHOTO_PATH = "extra_final_photo_path"

    /** 已有记录的 ID（详情页 → 编辑器页 / 记录编辑页） */
    const val EXTRA_RECORD_ID = "extra_record_id"
}
