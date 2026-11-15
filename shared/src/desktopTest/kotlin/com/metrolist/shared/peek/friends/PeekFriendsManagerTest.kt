/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek.friends

import com.metrolist.shared.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeekFriendsManagerTest {
    private var savedToken = ""
    private var savedCache = ""

    @BeforeTest
    fun saveSettings() {
        savedToken = Settings.shared.listenTogetherAuthToken
        savedCache = Settings.shared.peekFriendsCache
        Settings.shared.peekFriendsCache = ""
    }

    @AfterTest
    fun restoreSettings() {
        Settings.shared.listenTogetherAuthToken = savedToken
        Settings.shared.peekFriendsCache = savedCache
    }

    private fun info(
        code: String,
        name: String,
        online: Boolean = false,
        room: String? = null,
    ) = PeekFriendInfo(code, name, online, room)

    @Test
    fun requestLifecycleAndCache() =
        runTest {
            val manager = PeekFriendsManager(backgroundScope, HttpClient(MockEngine { error("No network in this test") }))
            manager.handle(PeekFriendMessages.AUTH_SUCCESS, PeekAuthSuccess("AAA-AAA-AAA"))
            assertEquals("AAA-AAA-AAA", manager.friendCode.value)
            manager.handle(
                PeekFriendMessages.FRIENDS_STATUS,
                PeekFriendsStatus(
                    friends = listOf(info("ZZZ-ZZZ-ZZZ", "zoe"), info("BBB-BBB-BBB", "Ben", online = true, room = "ROOM1234")),
                    incomingRequests = listOf(info("CCC-CCC-CCC", "Cid")),
                    outgoingRequests = emptyList(),
                ),
            )
            assertEquals(listOf("BBB-BBB-BBB", "ZZZ-ZZZ-ZZZ"), manager.friends.value.map { it.code }, "online friends first")
            assertEquals(listOf("CCC-CCC-CCC"), manager.incomingRequests.value.map { it.code })

            // A pending request, then the other side accepts.
            val events = mutableListOf<PeekFriendsEvent>()
            val collector = backgroundScope.launch { manager.events.collect { events += it } }
            testScheduler.runCurrent()
            manager.handle(PeekFriendMessages.FRIEND_ADDED, PeekFriendAdded("DDD-DDD-DDD", true, null, true, "Dee"))
            assertEquals(listOf("DDD-DDD-DDD"), manager.outgoingRequests.value.map { it.code })
            manager.handle(PeekFriendMessages.FRIEND_PRESENCE_UPDATE, PeekFriendPresence(info("DDD-DDD-DDD", "Dee", online = true)))
            assertTrue(manager.outgoingRequests.value.isEmpty())
            assertTrue(manager.friends.value.any { it.code == "DDD-DDD-DDD" && it.online })
            testScheduler.runCurrent()
            assertEquals(listOf(PeekFriendsEvent.RequestSent("Dee"), PeekFriendsEvent.FriendAdded("Dee")), events)

            // The cache keeps names but never online state.
            val restored = PeekFriendsManager(backgroundScope, HttpClient(MockEngine { error("No network in this test") }))
            assertEquals(
                manager.friends.value
                    .map { it.code }
                    .toSet(),
                restored.friends.value
                    .map { it.code }
                    .toSet(),
            )
            assertTrue(restored.friends.value.none { it.online || it.roomCode != null })

            manager.handle(PeekFriendMessages.FRIEND_REMOVED, PeekFriendCode("BBB-BBB-BBB"))
            assertTrue(manager.friends.value.none { it.code == "BBB-BBB-BBB" })
            collector.cancel()
        }

    @Test
    fun accountDeletionClearsTokenAndLists() =
        runTest {
            Settings.shared.listenTogetherAuthToken = "legacy-1-3-0-token-uuid"
            assertEquals("legacy-1-3-0-token-uuid", PeekFriendsManager.authToken(), "1.3.0's token is kept")
            assertEquals(true, PeekFriendsManager.partySignIn()?.presenceOnly)

            val manager = PeekFriendsManager(backgroundScope, HttpClient(MockEngine { error("No network in this test") }))
            manager.handle(PeekFriendMessages.AUTH_SUCCESS, PeekAuthSuccess("AAA-AAA-AAA"))
            manager.handle(
                PeekFriendMessages.FRIENDS_STATUS,
                PeekFriendsStatus(listOf(info("BBB-BBB-BBB", "Ben")), emptyList(), emptyList()),
            )
            val event = backgroundScope.launch { manager.events.first { it == PeekFriendsEvent.AccountDeleted } }
            testScheduler.runCurrent()
            manager.handle(PeekFriendMessages.FRIEND_ACCOUNT_DELETED, null)
            testScheduler.runCurrent()
            assertTrue(event.isCompleted)
            assertNull(manager.friendCode.value)
            assertTrue(manager.friends.value.isEmpty())
            val fresh = PeekFriendsManager.authToken()
            assertTrue(fresh.length == 32 && fresh != "legacy-1-3-0-token-uuid", "a fresh token after deletion")
        }
}
