#!/usr/bin/env python3
"""Read the built APK metadata; never duplicate application versions in CI."""
import argparse
import json
import re
from pathlib import Path


def artifact_metadata(path):
    path = Path(path)
    data = json.loads(path.read_text())
    elements = data.get("elements", [])
    if len(elements) != 1:
        raise ValueError("Expected one universal APK")
    item = elements[0]
    version = item.get("versionName", "")
    code = item.get("versionCode")
    if not isinstance(version, str) or not version.strip() or not isinstance(code, int) or code < 1:
        raise ValueError("Missing APK version metadata")
    # Filenames and GITHUB_OUTPUT must never accept paths, newlines or shell syntax.
    label = re.sub(r"[^A-Za-z0-9._+-]", "-", version).strip(".-")
    if not label:
        raise ValueError("Invalid APK version label")
    apk = Path(item["outputFile"])
    if apk.name != str(apk) or apk.suffix != ".apk":
        raise ValueError("APK must be a file in the output directory")
    source = path.parent / apk
    if not source.is_file():
        raise ValueError("Built APK is missing")
    return {"version_name": label, "version_code": str(code),
            "apk_path": source.as_posix(), "apk_name": f"xview-{label}.apk",
            "artifact_name": f"xview-{label}"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("metadata", type=Path)
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()
    values = artifact_metadata(args.metadata)
    if args.github_output:
        with args.github_output.open("a") as output:
            for key, value in values.items():
                if "\n" in value or "\r" in value:
                    raise ValueError("Multiline output rejected")
                output.write(f"{key}={value}\n")
    print(json.dumps(values))


if __name__ == "__main__":
    main()
