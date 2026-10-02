/// 构建参数使用完整 commit SHA，显示时统一取七位小写十六进制字符。
pub fn short_git_sha(sha: &str) -> Result<String, &'static str> {
    if !matches!(sha.len(), 40 | 64) || !sha.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        return Err("YAYA_GIT_SHA must be a full 40- or 64-character hexadecimal commit SHA");
    }
    Ok(sha[..7].to_ascii_lowercase())
}
