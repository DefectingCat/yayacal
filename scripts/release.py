#!/usr/bin/env python3
"""前后端分别读取版本和 changelog；tag 校验通过后输出发布元数据。"""
import argparse
import datetime
import json
import os
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
STABLE_VERSION = r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)"


def read_version(root, component):
    if component == "server":
        content = (root / "server/Cargo.toml").read_text()
        package = re.search(r"(?ms)^\[package\]\s*\n(.*?)(?=^\[|\Z)", content)
        match = re.search(r'^version\s*=\s*"([^"\n]+)"[ \t]*(?:#.*)?$', package[1], re.M) if package else None
    else:
        content = (root / "gradle.properties").read_text()
        match = re.search(r"^app\.version\.base\s*=\s*(\S+)\s*$", content, re.M)
    if not match or not re.fullmatch(STABLE_VERSION, match[1]):
        raise ValueError(f"Missing or invalid {component} base version")
    return match[1]


def extract_notes(content, version):
    headings = list(re.finditer(rf"(?m)^## \[{re.escape(version)}\] - ([0-9]{{4}}-[0-9]{{2}}-[0-9]{{2}})\s*$", content))
    if len(headings) != 1:
        raise ValueError(f"Changelog must contain exactly one dated [{version}] section")
    heading = headings[0]
    datetime.date.fromisoformat(heading[1])
    remainder = content[heading.end():]
    end = re.search(r"(?m)^## |^---\s*$|^\[[^\]]+\]:", remainder)
    notes = remainder[:end.start() if end else len(remainder)].strip()
    if not re.search(r"(?m)^[-*] \S", notes):
        raise ValueError(f"Changelog [{version}] has no release entries")
    return notes


def validate_release(root, component, tag):
    prefix = "server-v" if component == "server" else "v"
    if not re.fullmatch(re.escape(prefix) + STABLE_VERSION, tag):
        raise ValueError(f"Expected {component} tag {prefix}X.Y.Z, got {tag!r}")
    version = read_version(root, component)
    if tag != prefix + version:
        raise ValueError(f"Tag {tag} does not match {component} version {version}")
    changelog = root / ("server/CHANGELOG.md" if component == "server" else "CHANGELOG.md")
    return extract_notes(changelog.read_text(), version)


def should_publish_latest(version, releases):
    current = tuple(map(int, version.split(".")))
    for item in releases:
        tag = item["tagName"]
        if item.get("isDraft") or item.get("isPrerelease") or not re.fullmatch("server-v" + STABLE_VERSION, tag):
            continue
        if tuple(map(int, tag.removeprefix("server-v").split("."))) > current:
            return False
    return True


def metadata(root, component):
    version = read_version(root, component)
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    if not re.fullmatch(r"[0-9a-f]{40}|[0-9a-f]{64}", sha):
        raise ValueError("Git did not return a full commit SHA")
    repository = os.environ.get("GITHUB_REPOSITORY", "DefectingCat/yayacal").lower()
    if not re.fullmatch(r"[a-z0-9_.-]+/[a-z0-9_.-]+", repository):
        raise ValueError("Invalid GITHUB_REPOSITORY")
    prefix = "server-v" if component == "server" else "v"
    name = "YaYa Server" if component == "server" else "YaYa"
    info = {
        "version": version,
        "sha": sha,
        "short_sha": sha[:7],
        "tag": prefix + version,
        "title": f"{name} v{version}",
    }
    if component == "server":
        info["display_version"] = f"v{version}-{sha[:7]}"
        info["image"] = f"ghcr.io/{repository}-server"
    return info


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("component", choices=["android", "server"])
    parser.add_argument("tag", nargs="?")
    parser.add_argument("--notes-file", type=pathlib.Path)
    parser.add_argument("--github-output", type=pathlib.Path)
    parser.add_argument("--published-releases", type=pathlib.Path)
    args = parser.parse_args()
    try:
        info = metadata(ROOT, args.component)
        if args.published_releases:
            if args.component != "server":
                raise ValueError("--published-releases applies to server releases")
            releases = json.loads(args.published_releases.read_text())
            info["publish_latest"] = str(should_publish_latest(info["version"], releases)).lower()
        if args.tag:
            notes = validate_release(ROOT, args.component, args.tag)
            if args.notes_file:
                args.notes_file.write_text(notes + "\n")
        elif args.notes_file:
            raise ValueError("--notes-file requires a release tag")
        if args.github_output:
            with args.github_output.open("a") as output:
                for key, value in info.items():
                    output.write(f"{key}={value}\n")
        print(json.dumps(info, ensure_ascii=False))
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
