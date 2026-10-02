mod build_support;

use std::{env, path::PathBuf, process::Command};

fn git(args: &[&str]) -> Option<String> {
    let output = Command::new("git")
        .args(args)
        .current_dir(env::var_os("CARGO_MANIFEST_DIR")?)
        .output()
        .ok()?;
    output
        .status
        .success()
        .then(|| String::from_utf8_lossy(&output.stdout).trim().to_owned())
}

fn watch_git_file(name: &str) {
    if let Some(path) = git(&["rev-parse", "--git-path", name]) {
        let path = PathBuf::from(env::var_os("CARGO_MANIFEST_DIR").unwrap()).join(path);
        if path.exists() {
            println!("cargo::rerun-if-changed={}", path.display());
        }
    }
}

fn main() {
    println!("cargo::rerun-if-changed=build.rs");
    println!("cargo::rerun-if-changed=build_support.rs");
    println!("cargo::rerun-if-env-changed=YAYA_GIT_SHA");
    println!("cargo::rerun-if-env-changed=YAYA_REQUIRE_GIT_SHA");

    let sha = env::var("YAYA_GIT_SHA")
        .ok()
        .filter(|value| !value.is_empty())
        .or_else(|| {
            // HEAD 和实际引用都可能变化；git --git-path 同样支持 worktree。
            watch_git_file("HEAD");
            watch_git_file("packed-refs");
            if let Some(reference) = git(&["symbolic-ref", "-q", "HEAD"]) {
                watch_git_file(&reference);
            }
            git(&["rev-parse", "HEAD"])
        });

    let hash = match sha {
        Some(sha) => build_support::short_git_sha(&sha).expect("invalid build commit SHA"),
        None if env::var("YAYA_REQUIRE_GIT_SHA").as_deref() == Ok("1") => {
            panic!("a real commit SHA is required for release builds; set YAYA_GIT_SHA")
        }
        None => {
            println!("cargo::warning=Git metadata unavailable; using unknown build hash");
            "unknown".into()
        }
    };
    println!("cargo::rustc-env=YAYA_BUILD_GIT_HASH={hash}");
}
