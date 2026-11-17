#!/usr/bin/env python3
import json
import tempfile
import unittest
from pathlib import Path
from apk_metadata import artifact_metadata

class MetadataTest(unittest.TestCase):
    def fixture(self, root, version="2.7.3", code=27, output="app-optimized.apk"):
        (root/"app-optimized.apk").touch()
        path=root/"output-metadata.json"
        path.write_text(json.dumps({"elements":[{"versionName":version,"versionCode":code,"outputFile":output}]}))
        return path
    def test_version_follows_built_apk(self):
        with tempfile.TemporaryDirectory() as directory:
            value=artifact_metadata(self.fixture(Path(directory)))
            self.assertEqual(value["apk_name"],"xview-2.7.3.apk")
            self.assertEqual(value["artifact_name"],"xview-2.7.3")
    def test_prerelease_label(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(artifact_metadata(self.fixture(Path(directory),"4.0.0-beta.2"))["version_name"],"4.0.0-beta.2")
    def test_reject_path_escape(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):artifact_metadata(self.fixture(Path(directory),output="../bad.apk"))
    def test_reject_missing_version(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):artifact_metadata(self.fixture(Path(directory),version=""))

if __name__=="__main__":unittest.main()
