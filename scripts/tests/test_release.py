import importlib.util
import pathlib
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("release", pathlib.Path(__file__).resolve().parents[1] / "release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        (self.root / "server").mkdir()
        (self.root / "server/Cargo.toml").write_text('[package]\nname = "server"\nversion = "0.1.0"\n\n[dependencies]\nother = "1.0.0"\n')
        (self.root / "gradle.properties").write_text("app.version.base=1.9.0\n")
        (self.root / "CHANGELOG.md").write_text("## [1.9.0] - 2026-10-02\n\n### Added\n\n- Android change\n")
        (self.root / "server/CHANGELOG.md").write_text("## [0.1.0] - 2026-10-02\n\n### Added\n\n- Server change\n")

    def test_components_use_their_own_version_and_changelog(self):
        self.assertIn("Server change", release.validate_release(self.root, "server", "server-v0.1.0"))
        self.assertIn("Android change", release.validate_release(self.root, "android", "v1.9.0"))

    def test_other_component_tag_is_rejected(self):
        for component, tag in [("android", "server-v0.1.0"), ("server", "v1.9.0")]:
            with self.subTest(component=component), self.assertRaises(ValueError):
                release.validate_release(self.root, component, tag)

    def test_tag_must_match_version_file(self):
        with self.assertRaisesRegex(ValueError, "does not match"):
            release.validate_release(self.root, "server", "server-v0.2.0")

    def test_tag_cannot_include_hash_or_invalid_semver(self):
        for tag in ["server-v0.1.0-abcdef0", "server-v00.1.0", "server-v0.1", "server-v0.1.0\n"]:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.validate_release(self.root, "server", tag)

    def test_notes_stop_at_next_release(self):
        content = "## [Unreleased]\n\n- Future change\n\n## [0.1.0] - 2026-10-02\n\n- Current change\n\n## [0.0.1] - 2026-09-30\n\n- Old change\n"
        self.assertEqual(release.extract_notes(content, "0.1.0"), "- Current change")

    def test_last_release_omits_link_definitions(self):
        content = "## [0.1.0] - 2026-10-02\n\n- Change\n\n---\n\n[0.1.0]: https://example.com\n"
        self.assertEqual(release.extract_notes(content, "0.1.0"), "- Change")

    def test_missing_empty_duplicate_or_invalid_date_notes_are_rejected(self):
        heading = "## [0.1.0] - 2026-10-02\n"
        for content in ["## [Unreleased]\n- Change\n", heading + "### Added\n", heading + "- Change\n" + heading + "- Duplicate\n", "## [0.1.0] - 2026-02-30\n- Change\n"]:
            with self.subTest(content=content), self.assertRaises(ValueError):
                release.extract_notes(content, "0.1.0")

    def test_older_release_cannot_replace_latest_image(self):
        self.assertFalse(release.should_publish_latest("0.2.0", [{"tagName": "server-v0.10.0"}]))
        self.assertTrue(release.should_publish_latest("0.10.0", [{"tagName": "server-v0.2.0"}]))
        self.assertTrue(release.should_publish_latest("0.2.0", [{"tagName": "server-v0.2.0"}]))

    def test_latest_comparison_ignores_android_drafts_and_prereleases(self):
        releases = [{"tagName": "v9.0.0"}, {"tagName": "server-v9.0.0", "isDraft": True},
                    {"tagName": "server-v9.0.0", "isPrerelease": True}]
        self.assertTrue(release.should_publish_latest("0.1.0", releases))


if __name__ == "__main__":
    unittest.main()
