#!/usr/bin/env python3
# PeekMusic, a fork of Metrolist (C) 2026
# Licensed under GPL-3.0 | See git history for contributors
"""Check that the Windows manager rebrand matches what the PeekMusic app and updater expect."""

import tempfile
import unittest
from pathlib import Path

import peek_rebrand_manager as rebrand


class RebrandManagerTest(unittest.TestCase):
    def test_names_match_the_app_and_updater(self):
        text = rebrand.rebrand_text(
            'import "github.com/MetrolistGroup/MetrolistManager/internal/payload"\n'
            'appRegistryPath = `Software\\Metrolist\\Desktop`\n'
            'ManagerExecutable = "MetrolistManager.exe"\n'
            'AppID: "com.metrolist.desktop"\n'
            'launcher := "Metrolist.exe"\n'
            'Publisher: "MetrolistGroup"\n'
            'filepath.Join(home, ".local", "share", "metrolist")\n'
            'env := "METROLIST_CLEANUP_PID"\n'
        )
        self.assertIn("github.com/MetrolistGroup/MetrolistManager/internal/payload", text)
        self.assertIn("`Software\\PeekMusic\\Desktop`", text)
        self.assertIn('"PeekMusicManager.exe"', text)
        self.assertIn('"com.peekmusic.desktop"', text)
        self.assertIn('"PeekMusic.exe"', text)
        self.assertIn('Publisher: "PeekMusic"', text)
        self.assertIn('"share", "peekmusic"', text)
        self.assertIn("METROLIST_CLEANUP_PID", text)

    def test_rebrand_leaves_only_the_module_path(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "go.mod").write_text("module github.com/MetrolistGroup/MetrolistManager\n")
            (root / "main.go").write_text('import "github.com/MetrolistGroup/MetrolistManager/x"\nvar n = "Metrolist Desktop"\n')
            (root / "MetrolistManager.exe.manifest").write_text('<assemblyIdentity name="MetrolistGroup.MetrolistManager"/>')
            changed = rebrand.rebrand(root)
            self.assertEqual(2, len(changed))
            self.assertEqual([], rebrand.leftovers(root))
            self.assertIn('"PeekMusic Desktop"', (root / "main.go").read_text())
            self.assertIn("PeekMusic.PeekMusicManager", (root / "MetrolistManager.exe.manifest").read_text())


if __name__ == "__main__":
    unittest.main()
