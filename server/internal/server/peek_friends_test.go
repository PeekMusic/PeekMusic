// PeekMusic: tests for PeekParty friends.

package server

import (
	"bytes"
	"encoding/json"
	"path/filepath"
	"slices"
	"testing"
	"time"

	pb "github.com/MetrolistGroup/metroserver/proto/peek"
	"go.etcd.io/bbolt"
	"google.golang.org/protobuf/proto"
)

func peekTestServer(t *testing.T) *Server {
	t.Helper()
	server := testServer()
	var err error
	server.database, err = openDatabase(filepath.Join(t.TempDir(), DefaultDatabaseFile))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		server.Close()
		_ = server.database.Close()
	})
	return server
}

func peekSend(t *testing.T, server *Server, client *Client, msgType string, payload proto.Message) {
	t.Helper()
	data, err := proto.Marshal(payload)
	if err != nil {
		t.Fatal(err)
	}
	if !server.handlePeekMessage(client, msgType, data) {
		t.Fatalf("%s was not handled", msgType)
	}
}

// peekNext returns the next message of type want queued for client, skipping others.
func peekNext(t *testing.T, client *Client, want string, target proto.Message) {
	t.Helper()
	for range 20 {
		var buf []byte
		select {
		case buf = <-client.Send:
		case <-time.After(time.Second):
			t.Fatalf("no %s message", want)
		}
		msgType, payload, err := client.codec.Decode(buf)
		if err != nil {
			t.Fatal(err)
		}
		if msgType == want {
			if target != nil {
				if err := proto.Unmarshal(payload, target); err != nil {
					t.Fatal(err)
				}
			}
			return
		}
	}
	t.Fatalf("no %s message", want)
}

func peekDrain(client *Client) {
	for len(client.Send) > 0 {
		<-client.Send
	}
}

func peekAuth(t *testing.T, server *Server, client *Client, token, name string) string {
	t.Helper()
	peekSend(t, server, client, MsgTypeAuthenticate, &pb.AuthenticatePayload{AuthToken: token, CurrentUsername: name})
	var success pb.AuthSuccessPayload
	peekNext(t, client, MsgTypeAuthSuccess, &success)
	peekDrain(client)
	return success.FriendCode
}

func TestNormalizeFriendCode(t *testing.T) {
	for in, want := range map[string]string{
		"a1b2c3d4e":     "A1B-2C3-D4E",
		"A1B-2C3-D4E":   "A1B-2C3-D4E",
		" a1b 2c3 d4e ": "A1B-2C3-D4E",
		"A1B-2C3":       "",
		"A1B-2C3-D4É":   "",
		"A1B-2C3-D4E-F": "",
	} {
		if got := normalizeFriendCode(in); got != want {
			t.Errorf("normalizeFriendCode(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestPeekLegacyDataSurvivesAndTokensAreHashed(t *testing.T) {
	server := peekTestServer(t)
	const token = "legacy-token-0123456789"
	err := server.database.db.Update(func(tx *bbolt.Tx) error {
		auth, _ := tx.CreateBucketIfNotExists(peekLegacyAuth)
		friends, _ := tx.CreateBucketIfNotExists(peekFriendsBucket)
		names, _ := tx.CreateBucketIfNotExists(peekUsernamesBucket)
		_ = auth.Put([]byte(token), []byte("AAA-BBB-CCC"))
		_ = friends.Put([]byte("AAA-BBB-CCC"), []byte(`["DDD-EEE-FFF"]`))
		_ = friends.Put([]byte("DDD-EEE-FFF"), []byte(`["AAA-BBB-CCC"]`))
		return names.Put([]byte("DDD-EEE-FFF"), []byte("Old friend"))
	})
	if err != nil {
		t.Fatal(err)
	}

	client := withFeatures(newClient("me", nil), FeaturePeekFriends)
	peekSend(t, server, client, MsgTypeAuthenticate, &pb.AuthenticatePayload{AuthToken: token, CurrentUsername: "Me"})
	var success pb.AuthSuccessPayload
	peekNext(t, client, MsgTypeAuthSuccess, &success)
	if success.FriendCode != "AAA-BBB-CCC" {
		t.Fatalf("friend code = %q, want the 1.3.0 one", success.FriendCode)
	}
	var status pb.FriendsStatusPayload
	peekNext(t, client, MsgTypeFriendsStatus, &status)
	if len(status.Friends) != 1 || status.Friends[0].FriendCode != "DDD-EEE-FFF" || status.Friends[0].Username != "Old friend" || status.Friends[0].IsOnline {
		t.Fatalf("friends = %v, want the offline 1.3.0 friend with their name", status.Friends)
	}

	_ = server.database.db.View(func(tx *bbolt.Tx) error {
		if tx.Bucket(peekLegacyAuth) != nil {
			t.Error("plaintext token bucket still exists")
		}
		return tx.ForEach(func(name []byte, b *bbolt.Bucket) error {
			return b.ForEach(func(k, v []byte) error {
				if bytes.Contains(k, []byte(token)) || bytes.Contains(v, []byte(token)) {
					t.Errorf("bucket %s still holds the plaintext token", name)
				}
				return nil
			})
		})
	})
}

func TestPeekFriendRequestFlow(t *testing.T) {
	server := peekTestServer(t)
	alice := withFeatures(newClient("alice", nil), FeaturePeekFriends)
	bob := withFeatures(newClient("bob", nil), FeaturePeekFriends)
	aliceCode := peekAuth(t, server, alice, "alice-token-0123456789", "Alice")
	bobCode := peekAuth(t, server, bob, "bob-token-01234567890", "Bob")

	peekSend(t, server, alice, MsgTypeAddFriend, &pb.FriendCodePayload{FriendCode: bobCode})
	var added pb.FriendAddedPayload
	peekNext(t, alice, MsgTypeFriendAdded, &added)
	if !added.Success || !added.Pending || added.Username != "Bob" {
		t.Fatalf("friend_added = %v, want a pending request to Bob", &added)
	}
	var request pb.FriendRequestPayload
	peekNext(t, bob, MsgTypeFriendRequest, &request)
	if request.From.GetFriendCode() != aliceCode || request.From.GetUsername() != "Alice" {
		t.Fatalf("friend_request = %v", &request)
	}
	if store, _ := server.peekStore(); store.areFriends(aliceCode, bobCode) {
		t.Fatal("friends before the request was accepted")
	}

	// A reconnect lists the open request on both sides.
	bob2 := withFeatures(newClient("bob2", nil), FeaturePeekFriends)
	peekSend(t, server, bob2, MsgTypeAuthenticate, &pb.AuthenticatePayload{AuthToken: "bob-token-01234567890"})
	var status pb.FriendsStatusPayload
	peekNext(t, bob2, MsgTypeFriendsStatus, &status)
	if len(status.IncomingRequests) != 1 || status.IncomingRequests[0].FriendCode != aliceCode {
		t.Fatalf("incoming = %v", status.IncomingRequests)
	}

	peekSend(t, server, bob, MsgTypeAcceptFriendRequest, &pb.FriendCodePayload{FriendCode: aliceCode})
	var presence pb.FriendPresenceUpdatePayload
	peekNext(t, alice, MsgTypeFriendPresenceUpdate, &presence)
	if presence.Friend.GetFriendCode() != bobCode || !presence.Friend.GetIsOnline() {
		t.Fatalf("alice presence = %v", &presence)
	}
	peekNext(t, bob, MsgTypeFriendPresenceUpdate, &presence)
	if presence.Friend.GetFriendCode() != aliceCode {
		t.Fatalf("bob presence = %v", &presence)
	}
	if store, _ := server.peekStore(); !store.areFriends(aliceCode, bobCode) || !store.areFriends(bobCode, aliceCode) {
		t.Fatal("not friends after accepting")
	}
	if snap, _ := (&peekStore{db: server.database.db}).snapshot(bobCode); len(snap.incoming) != 0 {
		t.Fatalf("request left after accepting: %v", snap.incoming)
	}

	// Accepting twice fails cleanly.
	peekDrain(bob)
	peekSend(t, server, bob, MsgTypeAcceptFriendRequest, &pb.FriendCodePayload{FriendCode: aliceCode})
	receiveLifecycleError(t, bob, peekFriendErrorCode)
}

func TestPeekDeclineAndMutualRequest(t *testing.T) {
	server := peekTestServer(t)
	alice := withFeatures(newClient("alice", nil), FeaturePeekFriends)
	bob := withFeatures(newClient("bob", nil), FeaturePeekFriends)
	aliceCode := peekAuth(t, server, alice, "alice-token-0123456789", "Alice")
	bobCode := peekAuth(t, server, bob, "bob-token-01234567890", "Bob")

	peekSend(t, server, alice, MsgTypeAddFriend, &pb.FriendCodePayload{FriendCode: bobCode})
	peekSend(t, server, bob, MsgTypeDeclineFriendRequest, &pb.FriendCodePayload{FriendCode: aliceCode})
	var removed pb.FriendCodePayload
	peekNext(t, alice, MsgTypeFriendRemoved, &removed)
	if removed.FriendCode != bobCode {
		t.Fatalf("friend_removed = %q", removed.FriendCode)
	}

	// Bob asking back while Alice's request is open makes them friends at once.
	peekSend(t, server, alice, MsgTypeAddFriend, &pb.FriendCodePayload{FriendCode: bobCode})
	peekDrain(bob)
	peekSend(t, server, bob, MsgTypeAddFriend, &pb.FriendCodePayload{FriendCode: aliceCode})
	var added pb.FriendAddedPayload
	peekNext(t, bob, MsgTypeFriendAdded, &added)
	if !added.Success || added.Pending {
		t.Fatalf("friend_added = %v, want friends at once", &added)
	}
	if store, _ := server.peekStore(); !store.areFriends(aliceCode, bobCode) {
		t.Fatal("not friends after asking back")
	}
}

func TestPeekLegacyAppsStillBefriendAtOnce(t *testing.T) {
	server := peekTestServer(t)
	alice := withFeatures(newClient("alice", nil), FeaturePeekFriends)
	legacy := newClient("legacy", nil) // PeekMusic 1.3.0: no feature negotiation
	aliceCode := peekAuth(t, server, alice, "alice-token-0123456789", "Alice")
	legacyCode := peekAuth(t, server, legacy, "legacy-token-01234567", "Legacy")

	peekSend(t, server, alice, MsgTypeAddFriend, &pb.FriendCodePayload{FriendCode: legacyCode})
	var added pb.FriendAddedPayload
	peekNext(t, alice, MsgTypeFriendAdded, &added)
	if !added.Success || added.Pending {
		t.Fatalf("friend_added = %v, want friends at once with a 1.3.0 app", &added)
	}
	var presence pb.FriendPresenceUpdatePayload
	peekNext(t, legacy, MsgTypeFriendPresenceUpdate, &presence)
	if presence.Friend.GetFriendCode() != aliceCode {
		t.Fatalf("legacy presence = %v", &presence)
	}

	// A 1.3.0 app adding someone on a current app gets the request explained as text.
	carol := withFeatures(newClient("carol", nil), FeaturePeekFriends)
	carolCode := peekAuth(t, server, carol, "carol-token-0123456789", "Carol")
	peekSend(t, server, legacy, MsgTypeAddFriend, &pb.FriendCodePayload{FriendCode: carolCode})
	peekNext(t, legacy, MsgTypeFriendAdded, &added)
	if added.Success || added.ErrorMessage == "" {
		t.Fatalf("friend_added = %v, want an explanation for 1.3.0", &added)
	}
	var request pb.FriendRequestPayload
	peekNext(t, carol, MsgTypeFriendRequest, &request)
}

func TestPeekAddFriendErrorsAndRateLimit(t *testing.T) {
	server := peekTestServer(t)
	alice := withFeatures(newClient("alice", nil), FeaturePeekFriends)
	aliceCode := peekAuth(t, server, alice, "alice-token-0123456789", "Alice")

	var added pb.FriendAddedPayload
	for _, code := range []string{aliceCode, "ZZZ-ZZZ-ZZZ", "nope"} {
		peekSend(t, server, alice, MsgTypeAddFriend, &pb.FriendCodePayload{FriendCode: code})
		peekNext(t, alice, MsgTypeFriendAdded, &added)
		if added.Success {
			t.Fatalf("adding %q succeeded", code)
		}
	}
	for range peekAddFriendPerMinute {
		peekSend(t, server, alice, MsgTypeAddFriend, &pb.FriendCodePayload{FriendCode: "ZZZ-ZZZ-ZZZ"})
		peekNext(t, alice, MsgTypeFriendAdded, &added)
	}
	if added.ErrorMessage != "too many attempts, try again in a minute" {
		t.Fatalf("error = %q, want the rate limit", added.ErrorMessage)
	}

	unauthenticated := newClient("anon", nil)
	peekSend(t, server, unauthenticated, MsgTypeAddFriend, &pb.FriendCodePayload{FriendCode: aliceCode})
	receiveLifecycleError(t, unauthenticated, peekFriendNotAuthenticated)

	short := newClient("short", nil)
	peekSend(t, server, short, MsgTypeAuthenticate, &pb.AuthenticatePayload{AuthToken: "short"})
	receiveLifecycleError(t, short, "auth_error")
}

func befriend(t *testing.T, server *Server, a, b string) {
	t.Helper()
	store, _ := server.peekStore()
	if err := store.db.Update(func(tx *bbolt.Tx) error { return linkFriendsTx(tx, a, b) }); err != nil {
		t.Fatal(err)
	}
}

func TestPeekPresenceAcrossDevicesAndRooms(t *testing.T) {
	server := peekTestServer(t)
	phone := withFeatures(newClient("phone", nil), FeaturePeekFriends)
	tablet := withFeatures(newClient("tablet", nil), FeaturePeekFriends)
	bob := withFeatures(newClient("bob", nil), FeaturePeekFriends)
	aliceCode := peekAuth(t, server, phone, "alice-token-0123456789", "Alice")
	peekAuth(t, server, tablet, "alice-token-0123456789", "Alice")
	bobCode := peekAuth(t, server, bob, "bob-token-01234567890", "Bob")
	befriend(t, server, aliceCode, bobCode)
	store, _ := server.peekStore()

	tablet.setRoom(&Room{Code: "ROOM1234"})
	server.peekRefreshPresence(store)
	var presence pb.FriendPresenceUpdatePayload
	peekNext(t, bob, MsgTypeFriendPresenceUpdate, &presence)
	if presence.Friend.GetActiveRoomCode() != "ROOM1234" {
		t.Fatalf("presence = %v, want Alice's party", &presence)
	}
	peekDrain(bob)
	server.peekRefreshPresence(store)
	if len(bob.Send) != 0 {
		t.Fatal("presence resent without a change")
	}

	// One device leaving keeps Alice online.
	server.peekClientGone(phone)
	peekNext(t, bob, MsgTypeFriendPresenceUpdate, &presence)
	if !presence.Friend.GetIsOnline() {
		t.Fatal("Alice shown offline while her tablet is connected")
	}
	server.peekClientGone(tablet)
	peekNext(t, bob, MsgTypeFriendPresenceUpdate, &presence)
	if presence.Friend.GetIsOnline() || presence.Friend.GetUsername() != "Alice" {
		t.Fatalf("presence = %v, want Alice offline with her name", &presence)
	}
}

func TestPeekInvite(t *testing.T) {
	server := peekTestServer(t)
	alice := withFeatures(newClient("alice", nil), FeaturePeekFriends)
	bob := withFeatures(newClient("bob", nil), FeaturePeekFriends)
	stranger := withFeatures(newClient("stranger", nil), FeaturePeekFriends)
	aliceCode := peekAuth(t, server, alice, "alice-token-0123456789", "Alice")
	bobCode := peekAuth(t, server, bob, "bob-token-01234567890", "Bob")
	strangerCode := peekAuth(t, server, stranger, "stranger-token-012345", "Stranger")
	befriend(t, server, aliceCode, bobCode)

	peekSend(t, server, alice, MsgTypeInviteFriend, &pb.FriendCodePayload{FriendCode: bobCode})
	receiveLifecycleError(t, alice, peekFriendErrorCode) // not in a party yet

	alice.setRoom(&Room{Code: "ROOM1234"})
	peekSend(t, server, alice, MsgTypeInviteFriend, &pb.FriendCodePayload{FriendCode: bobCode})
	var invite pb.FriendInvitePayload
	peekNext(t, bob, MsgTypeFriendInvite, &invite)
	if invite.RoomCode != "ROOM1234" || invite.From.GetFriendCode() != aliceCode {
		t.Fatalf("invite = %v", &invite)
	}

	peekSend(t, server, alice, MsgTypeInviteFriend, &pb.FriendCodePayload{FriendCode: strangerCode})
	receiveLifecycleError(t, alice, peekFriendErrorCode)
	if len(stranger.Send) != 0 {
		t.Fatal("a stranger got an invite")
	}
}

func TestPeekDeleteAccount(t *testing.T) {
	server := peekTestServer(t)
	alice := withFeatures(newClient("alice", nil), FeaturePeekFriends)
	bob := withFeatures(newClient("bob", nil), FeaturePeekFriends)
	aliceCode := peekAuth(t, server, alice, "alice-token-0123456789", "Alice")
	bobCode := peekAuth(t, server, bob, "bob-token-01234567890", "Bob")
	befriend(t, server, aliceCode, bobCode)

	peekSend(t, server, alice, MsgTypeDeleteFriendAccount, &pb.DeleteFriendAccountPayload{})
	peekNext(t, alice, MsgTypeFriendAccountDeleted, nil)
	var removed pb.FriendCodePayload
	peekNext(t, bob, MsgTypeFriendRemoved, &removed)
	if removed.FriendCode != aliceCode {
		t.Fatalf("friend_removed = %q", removed.FriendCode)
	}
	if server.peekCode(alice) != "" {
		t.Fatal("deleted client still signed in")
	}
	_ = server.database.db.View(func(tx *bbolt.Tx) error {
		var list []string
		_ = json.Unmarshal(tx.Bucket(peekFriendsBucket).Get([]byte(bobCode)), &list)
		if slices.Contains(list, aliceCode) {
			t.Error("Bob still lists Alice")
		}
		for _, b := range [][]byte{peekFriendsBucket, peekUsernamesBucket, peekMetaBucket} {
			if tx.Bucket(b).Get([]byte(aliceCode)) != nil {
				t.Errorf("%s still holds Alice", b)
			}
		}
		if tx.Bucket(peekAuthBucket).Stats().KeyN != 1 {
			t.Error("Alice's token hash was kept")
		}
		return nil
	})

	again := withFeatures(newClient("again", nil), FeaturePeekFriends)
	if code := peekAuth(t, server, again, "alice-token-0123456789", "Alice"); code == aliceCode {
		t.Fatal("the deleted friend code came back")
	}
}
