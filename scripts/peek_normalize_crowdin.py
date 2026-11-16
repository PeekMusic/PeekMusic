#!/usr/bin/env python3
# PeekMusic, a fork of Metrolist (C) 2026
# Licensed under GPL-3.0 | See git history for contributors
"""Undo Crowdin's Android escaping in peek_strings.xml.

Crowdin exports these Compose Multiplatform catalogs as Android XML (see .github/workflows/peek-crowdin.yml) and writes \\' \\" \\@ \\? into translations.
Compose shows those backslashes literally, so they are removed here; \\n, \\t and \\u stay.
Run with no arguments to fix every locale's peek_strings.xml in place.
"""

import re
import sys
from pathlib import Path

RESOURCES = Path(__file__).resolve().parents[1] / "shared/src/commonMain/composeResources"
ANDROID_ESCAPE = re.compile(r"\\(['\"@?])")


def normalize(text: str) -> str:
    return ANDROID_ESCAPE.sub(r"\1", text)


def main() -> int:
    changed = 0
    for path in sorted(RESOURCES.glob("values-*/peek_strings.xml")):
        original = path.read_text(encoding="utf-8")
        fixed = normalize(original)
        if fixed != original:
            path.write_text(fixed, encoding="utf-8")
            changed += 1
            print(f"normalized {path.relative_to(RESOURCES)}")
    print(f"{changed} file(s) changed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
