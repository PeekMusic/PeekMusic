/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.ui.screens.AboutAvatarShape
import com.metrolist.shared.ui.screens.AboutContributor
import com.metrolist.shared.ui.screens.AboutContributorRole

/** PeekMusic's entry on the About page, shown above Metrolist's team. */
internal object PeekAbout {
    val developer =
        AboutContributor(
            name = "Endrit",
            role = AboutContributorRole.PEEKMUSIC_DEVELOPER,
            githubHandle = "PeekMusic",
            avatarShape = AboutAvatarShape.COOKIE_9_SIDED,
        )
}
