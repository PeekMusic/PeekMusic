#!/usr/bin/env python3
# PeekMusic, a fork of Metrolist (C) 2026
# Licensed under GPL-3.0 | See git history for contributors
"""Rebrand a checkout of upstream's Windows manager (MetrolistGroup/MetrolistManager) as PeekMusic.

The manager is upstream's own Go repository. Instead of keeping a fork, the desktop workflow checks out the pinned
upstream commit and runs this script on it, so the installer matches what our app and updater expect:
`PeekMusic.exe`, app id `com.peekmusic.desktop`, `Software\\PeekMusic\\Desktop`, `PeekMusicManager.exe`,
`%LOCALAPPDATA%\\Programs\\PeekMusic` and the `peekmusic` data folders. The Go module path stays upstream's.
"""

import argparse
import re
import sys
from pathlib import Path

MODULE = "github.com/MetrolistGroup/MetrolistManager"
PLACEHOLDER = "\0PEEK_MODULE\0"

# Order matters: specific forms first, then the plain names.
REPLACEMENTS = [
    ("com.metrolist.desktop", "com.peekmusic.desktop"),
    ("MetrolistGroup", "PeekMusic"),
    ("Metrolist", "PeekMusic"),
    ("metrolist", "peekmusic"),
]


def rebrand_text(text: str) -> str:
    text = text.replace(MODULE, PLACEHOLDER)
    for old, new in REPLACEMENTS:
        text = text.replace(old, new)
    return text.replace(PLACEHOLDER, MODULE)


def rebrand(root: Path) -> list[Path]:
    changed = []
    files = [*root.rglob("*.go"), *root.glob("*.manifest")]
    for path in sorted(files):
        if ".git" in path.parts:
            continue
        original = path.read_text(encoding="utf-8")
        updated = rebrand_text(original)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed.append(path)
    return changed


def leftovers(root: Path) -> list[str]:
    found = []
    pattern = re.compile("metrolist", re.IGNORECASE)
    for path in sorted([*root.rglob("*.go"), *root.glob("*.manifest")]):
        if ".git" in path.parts:
            continue
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if pattern.search(line.replace(MODULE, "")) and "METROLIST_" not in line:
                found.append(f"{path.relative_to(root)}:{number}: {line.strip()}")
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("root", type=Path, help="checkout of MetrolistGroup/MetrolistManager")
    args = parser.parse_args()
    if not (args.root / "go.mod").is_file():
        print(f"{args.root} is not a Go module", file=sys.stderr)
        return 1
    changed = rebrand(args.root)
    rest = leftovers(args.root)
    if rest:
        print("Metrolist names left after rebranding:", *rest, sep="\n  ", file=sys.stderr)
        return 1
    print(f"Rebranded {len(changed)} files as PeekMusic")
    return 0


if __name__ == "__main__":
    sys.exit(main())
