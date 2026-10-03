package plus.rua.project

/** 朋友圈各图片入口共用的上传边界，大小单位与后端一致。 */
internal object MomentsImagePolicy {
    const val MAX_UPLOAD_BYTES = 50L * 1024 * 1024
    const val SIZE_ERROR = "图片不能超过 50 MiB"
}
