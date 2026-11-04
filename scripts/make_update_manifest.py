#!/usr/bin/env python3
"""Build/merge the update manifest read by appupdatechecker2 (AppVersionChecker).

Format:
{
  "package": {
    "downloadUrlList_<abi>": ["https://.../app_<abi>.apk"],
    "downloadUrlList": ["https://.../app_universal.apk"]
  },
  "<versionName>": {"versionCode": 123, "changelog": ["..."]},
  ...
}
Only versionCode is compared by the app. Older entries are kept so users
skipping versions still see every changelog.
"""
import argparse
import json
import os
import sys

MAX_VERSIONS = 30


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--previous", help="existing manifest (optional, may not exist)")
    parser.add_argument("--out", required=True)
    parser.add_argument("--apk-dir", required=True)
    parser.add_argument("--base-url", required=True, help="https://github.com/<owner>/<repo>/releases/download/<tag>")
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--changelog", default="", help="entries separated by ';'")
    args = parser.parse_args()

    manifest = {}
    if args.previous and os.path.isfile(args.previous):
        with open(args.previous, encoding="utf-8") as f:
            manifest = json.load(f)

    for name, entry in manifest.items():
        if name != "package" and entry.get("versionCode", 0) >= args.version_code:
            sys.exit(f"versionCode {args.version_code} is not newer than {name} ({entry['versionCode']}). Bump filterPatch.")

    package = {}
    for apk in sorted(os.listdir(args.apk_dir)):
        if not apk.endswith(".apk"):
            continue
        abi = apk[:-len(".apk")].rsplit("_", 1)[-1]  # BlockTube_stable_<version>_<abi>.apk
        url = f"{args.base_url}/{apk}"
        if abi == "universal":
            package["downloadUrlList"] = [url]
        else:
            package[f"downloadUrlList_{abi}"] = [url]

    if "downloadUrlList" not in package:
        sys.exit("universal APK not found in " + args.apk_dir)

    changelog = [line.strip() for line in args.changelog.split(";") if line.strip()]

    versions = {k: v for k, v in manifest.items() if k != "package"}
    versions[args.version_name] = {"versionCode": args.version_code, "changelog": changelog}
    newest = sorted(versions.items(), key=lambda kv: kv[1]["versionCode"], reverse=True)[:MAX_VERSIONS]

    result = {"package": package}
    result.update(newest)

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
