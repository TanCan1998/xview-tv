#!/usr/bin/env python3
"""Offline structural checks. Does not compile Kotlin or claim Android correctness."""
import hashlib
import re
import sys
import tomllib
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[2]
checks = 0

def check(value, message):
    global checks
    if not value:
        raise AssertionError(message)
    checks += 1

settings = (root / "settings.gradle.kts").read_text()
modules = re.findall(r'"(:[a-z][a-z:-]*)"', settings)
check(len(modules) == len(set(modules)) == 9, "Expected nine unique modules")
for module in modules:
    path = root / module.lstrip(":").replace(":", "/")
    check((path / "build.gradle.kts").is_file(), f"Missing module {module}")
    check((path / "src/main").is_dir(), f"Missing sources {module}")

catalog = tomllib.loads((root / "gradle/libs.versions.toml").read_text())
check(catalog["versions"]["android-min-sdk"] == "23", "Phone and TV minimum must remain API 23")
check(catalog["versions"]["android-compile-sdk"] == "37", "Unexpected compile SDK")
check(catalog["versions"]["android-target-sdk"] == "35", "Unexpected target SDK")
for kind in ["application", "library"]:
    convention = (root / f"build-logic/src/main/kotlin/xview.android.{kind}.gradle.kts").read_text()
    check('findVersion("android-min-sdk")' in convention, f"{kind} must use the shared TV minimum")
    check("isCoreLibraryDesugaringEnabled = true" in convention and
          'add("coreLibraryDesugaring", catalog.findLibrary("desugar-jdk-libs").get())' in convention,
          f"{kind} must backport supported Java APIs below API 26")
for section in ["libraries", "plugins"]:
    for alias, value in catalog[section].items():
        version = value.get("version", {})
        if isinstance(version, dict) and "ref" in version:
            check(version["ref"] in catalog["versions"], f"Undefined version for {alias}")

import subprocess


def source_files(pattern):
    # rg respects ignore rules, so generated build reports are not source inputs.
    result = subprocess.run(["rg", "--files", "-g", pattern, "-g", "!build/**", "-g", "!**/build/**"],
                            cwd=root, capture_output=True, text=True, check=True)
    return [root / name for name in result.stdout.splitlines()]


for path in source_files("*.xml"):
    ET.parse(path)
    checks += 1
for path in source_files("*.gradle.kts"):
    text = path.read_text()
    for dependency in re.findall(r'project\("(:[a-z:-]+)"\)', text):
        check(dependency in modules, f"Unknown dependency {dependency}")
    if "feature" in path.parts:
        check(not re.search(r'project\(":(?:provider|core:(?:data|network|database))', text), "Feature bypasses domain")

wrapper = root / "gradle/wrapper/gradle-wrapper.jar"
check(hashlib.sha256(wrapper.read_bytes()).hexdigest() ==
      "621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8", "Wrapper JAR checksum mismatch")
properties = (root / "gradle/wrapper/gradle-wrapper.properties").read_text()
check("distributionSha256Sum=d8a18a7a841dd5df825d35a685fad6f2d7f12ac0c280e57b67c27b4a520ba228" in properties,
      "Missing distribution checksum")
check((root / "gradlew").read_text().startswith("#!/bin/sh"), "Invalid Unix wrapper")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
check("LEANBACK_LAUNCHER" in manifest and "android.hardware.touchscreen" in manifest, "Missing TV manifest")
check("android.permission.INTERNET" in manifest, "Network diagnostics requires INTERNET")
check("productFlavors" not in (root / "app/build.gradle.kts").read_text(), "Use a single application, no Demo flavor")
for module in ["model", "domain", "provider", "data"]:
    for path in (root / "core" / module / "src").rglob("*.kt"):
        check(not re.search(r'^import (?:android|androidx)\.', path.read_text(), re.M), "Core contract depends on Android")

for workflow in (root / ".github/workflows").glob("*.yml"):
    for action in re.findall(r"uses:\s*(\S+)", workflow.read_text()):
        check(re.fullmatch(r"[^@]+@[0-9a-f]{40}", action) is not None, "Action must use full SHA")
try:
    import yaml
except ImportError:
    print("NOTE: PyYAML not installed; YAML syntax not parsed")
else:
    for path in (root / ".github").rglob("*.yml"):
        yaml.safe_load(path.read_text())
        checks += 1

for name in ["README.md", "README_zh-CN.md", "LICENSE", "NOTICE", "AGENTS.md", "docs/design/xview-tv.md"]:
    check((root / name).stat().st_size > 0, f"Missing {name}")
design = (root / "docs/design/xview-tv.md").read_text()
check("局域网" in design and "API23" in design and "设置" in design, "Missing current login/UI/storage design")
shell = (root / "app/src/main/kotlin/io/github/tancan1998/xviewtv/AppShell.kt").read_text()
player = (root / "app/src/main/kotlin/io/github/tancan1998/xviewtv/TvPlayerSurface.kt").read_text()
viewer = (root / "app/src/main/kotlin/io/github/tancan1998/xviewtv/MediaViewer.kt").read_text()
home = (root / "feature/home/src/main/kotlin/io/github/tancan1998/xviewtv/feature/home/HomeScreen.kt").read_text()
check("KEYCODE_MENU" in shell and "Key.DirectionUp -> { settingsVisible = true" not in shell, "TV Settings must use Menu, not global Up")
check("homeRegistry.restore()" in shell and "LocalTvHomeFocusRegistry" in shell, "TV Settings must restore the actual leaf focus target")
check("useController = !tv" in viewer and "FOCUS_BLOCK_DESCENDANTS" in viewer, "TV native transport controls must stay hidden and unfocusable")
check("TvPlaybackInputPolicy.shouldSeek" in player and "TvPlaybackInputPolicy.shouldToggle" in player, "Hidden TV controls require repeat-safe seek/OK")
check("TvPlaybackInputPolicy.CONTROLS_TIMEOUT_MS" in player, "TV controls must have a finite auto-hide timeout")
check("Modifier.height(32.dp)" in player and "vertical = 4.dp" in player, "TV external toolbar must stay compact")
check("Box(Modifier.weight(1f)) { header() }" in home and "else header()" in home, "TV refresh must share the header row")
check('name=".TvFocusFixtureActivity"' not in manifest, "Offline fixture must never be in the production manifest")
check("Apache License" in (root / "LICENSE").read_text(), "Missing Apache license")
print(f"PASS: {checks} offline structural checks; {len(modules)} modules")
print("Scope: source structure only; Gradle/Kotlin/JUnit/Lint/APK/TV/CI are not executed by this script")
