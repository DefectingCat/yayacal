#[path = "../build_support.rs"]
mod build_support;

use std::{fs, path::PathBuf, process::Command};

#[test]
fn full_commit_sha_is_shortened_and_normalized() {
    assert_eq!(
        build_support::short_git_sha(&"ABCDEF01".repeat(5)).unwrap(),
        "abcdef0"
    );
}

#[test]
fn sha256_commit_is_supported() {
    assert_eq!(
        build_support::short_git_sha(&"a".repeat(64)).unwrap(),
        "aaaaaaa"
    );
}

#[test]
fn short_or_malformed_commit_is_rejected() {
    for sha in ["", "unknown", "abcdef0", &"x".repeat(40), &"a".repeat(41)] {
        assert!(build_support::short_git_sha(sha).is_err(), "{sha}");
    }
}

#[test]
fn build_script_requires_release_sha_and_tracks_git_head() {
    struct TestDirectory(PathBuf);
    impl Drop for TestDirectory {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.0);
        }
    }
    let directory = TestDirectory(
        std::env::temp_dir().join(format!("yayacal-build-metadata-{}", std::process::id())),
    );
    fs::create_dir(&directory.0).unwrap();
    let executable = directory.0.join("build-script");
    let compile = Command::new("rustc")
        .arg("--edition=2024")
        .arg(PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("build.rs"))
        .arg("-o")
        .arg(&executable)
        .output()
        .unwrap();
    assert!(compile.status.success(), "{:?}", compile);

    let run = |sha: Option<&str>, required: bool| {
        let mut command = Command::new(&executable);
        command
            .current_dir(&directory.0)
            .env("CARGO_MANIFEST_DIR", &directory.0)
            .env("YAYA_REQUIRE_GIT_SHA", if required { "1" } else { "0" })
            .env_remove("YAYA_GIT_SHA")
            .env_remove("GIT_DIR")
            .env_remove("GIT_WORK_TREE");
        if let Some(sha) = sha {
            command.env("YAYA_GIT_SHA", sha);
        }
        command.output().unwrap()
    };
    let missing = run(None, true);
    assert!(!missing.status.success());
    assert!(String::from_utf8_lossy(&missing.stderr).contains("a real commit SHA is required"));
    let fallback = run(None, false);
    assert!(fallback.status.success());
    assert!(String::from_utf8_lossy(&fallback.stdout).contains("YAYA_BUILD_GIT_HASH=unknown"));
    let override_sha = "ABCDEF01".repeat(5);
    let overridden = run(Some(&override_sha), true);
    assert!(overridden.status.success());
    assert!(String::from_utf8_lossy(&overridden.stdout).contains("YAYA_BUILD_GIT_HASH=abcdef0"));
    assert!(!run(Some("invalid"), true).status.success());

    let git = |args: &[&str]| {
        let output = Command::new("git")
            .args(args)
            .current_dir(&directory.0)
            .output()
            .unwrap();
        assert!(output.status.success(), "{:?}", output);
        String::from_utf8(output.stdout).unwrap().trim().to_owned()
    };
    git(&["init", "-q"]);
    for message in ["first", "second"] {
        git(&[
            "-c",
            "user.name=Build Metadata Test",
            "-c",
            "user.email=test@example.com",
            "-c",
            "commit.gpgsign=false",
            "-c",
            "core.hooksPath=/dev/null",
            "commit",
            "--allow-empty",
            "-qm",
            message,
        ]);
        let sha = git(&["rev-parse", "HEAD"]);
        let output = run(None, true);
        assert!(output.status.success());
        let stdout = String::from_utf8(output.stdout).unwrap();
        assert!(stdout.contains(&format!("YAYA_BUILD_GIT_HASH={}", &sha[..7])));
        assert!(stdout.contains(".git/HEAD"));
        assert!(stdout.contains(".git/refs/heads/"));
    }
}
