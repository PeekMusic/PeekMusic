// PeekMusic: PeekParty friends. Every account is a random token the app creates; the server gives it a
// friend code. Friends see each other's name, whether they are online and which party they are in.
//
// Kept in its own files; upstream code only calls handlePeekMessage, peekClientGone and passes the
// peek_friends_v2 feature through, so merging newer upstream metroserver stays easy.

package server

import (
	"errors"
	"sync"
	"time"

	pb "github.com/MetrolistGroup/metroserver/proto/peek"
	"go.uber.org/zap"
	"google.golang.org/protobuf/proto"
)

const (
	MsgTypeAuthenticate          = "authenticate"
	MsgTypeAuthSuccess           = "auth_success"
	MsgTypeAddFriend             = "add_friend"
	MsgTypeRemoveFriend          = "remove_friend"
	MsgTypeFriendRemoved         = "friend_removed"
	MsgTypeFriendsStatus         = "friends_status"
	MsgTypeFriendPresenceUpdate  = "friend_presence_update"
	MsgTypeFriendAdded           = "friend_added"
	MsgTypeAcceptFriendRequest   = "accept_friend_request"
	MsgTypeDeclineFriendRequest  = "decline_friend_request"
	MsgTypeFriendRequest         = "friend_request"
	MsgTypeInviteFriend          = "invite_friend"
	MsgTypeFriendInvite          = "friend_invite"
	MsgTypeDeleteFriendAccount   = "delete_friend_account"
	MsgTypeFriendAccountDeleted  = "friend_account_deleted"
	FeaturePeekFriends           = "peek_friends_v2" // friend requests, invites, account deletion
	peekPresenceInterval         = 2 * time.Second
	peekAddFriendPerMinute       = 10
	peekInvitesPerMinute         = 20
	peekRateWindow               = time.Minute
	peekFriendNotAuthenticated   = "not_authenticated"
	peekFriendErrorCode          = "friend_error"
	peekFriendInvalidPayloadCode = "invalid_payload"
)

// peekState is the in-memory side: who is online under which friend code. The zero value is ready to use.
type peekState struct {
	mu      sync.Mutex
	byCode  map[string]map[*Client]struct{}
	clients map[*Client]*peekClient
	once    sync.Once
	migrate sync.Once
}

type peekClient struct {
	code     string
	silent   bool   // presence_only: counts for presence, gets no friend messages
	presence string // last presence sent to friends: name + room
	adds     []time.Time
	invites  []time.Time
}

func (s *Server) handlePeekMessage(c *Client, msgType string, payload []byte) bool {
	switch msgType {
	case MsgTypeAuthenticate:
		s.peekAuthenticate(c, payload)
	case MsgTypeAddFriend:
		s.peekAddFriend(c, payload)
	case MsgTypeRemoveFriend, MsgTypeDeclineFriendRequest:
		s.peekRemove(c, payload)
	case MsgTypeAcceptFriendRequest:
		s.peekAccept(c, payload)
	case MsgTypeInviteFriend:
		s.peekInvite(c, payload)
	case MsgTypeDeleteFriendAccount:
		s.peekDeleteAccount(c)
	default:
		return false
	}
	return true
}

func (s *Server) peekReady() (*peekStore, bool) {
	store, err := s.peekStore()
	if err != nil {
		return nil, false
	}
	s.peek.migrate.Do(func() {
		moved, err := store.migrate()
		if err != nil {
			s.logger.Error("PeekParty friends migration failed", zap.Error(err))
			return
		}
		if moved > 0 {
			s.logger.Info("PeekParty auth tokens hashed", zap.Int("count", moved))
		}
	})
	s.peek.once.Do(func() { go s.peekPresenceLoop() })
	return store, true
}

func (s *Server) peekCode(c *Client) string {
	s.peek.mu.Lock()
	defer s.peek.mu.Unlock()
	if pc := s.peek.clients[c]; pc != nil {
		return pc.code
	}
	return ""
}

// peekOnline returns code's connections that take friend messages.
func (s *Server) peekOnline(code string) []*Client {
	return s.peekConnections(code, false)
}

func (s *Server) peekConnections(code string, includeSilent bool) []*Client {
	s.peek.mu.Lock()
	defer s.peek.mu.Unlock()
	out := make([]*Client, 0, len(s.peek.byCode[code]))
	for c := range s.peek.byCode[code] {
		if pc := s.peek.clients[c]; includeSilent || (pc != nil && !pc.silent) {
			out = append(out, c)
		}
	}
	return out
}

// peekInfo is code's presence as its friends see it: online if any of its devices is connected, and the
// party of the first device that is in one.
func (s *Server) peekInfo(store *peekStore, code string) *pb.FriendInfo {
	info := &pb.FriendInfo{FriendCode: code}
	for _, c := range s.peekConnections(code, true) {
		info.IsOnline = true
		if name := c.userName(); name != "" {
			info.Username = name
		}
		if room := c.currentRoom(); room != nil && info.ActiveRoomCode == "" {
			info.ActiveRoomCode = room.Code
		}
	}
	if info.Username == "" {
		info.Username = store.username(code)
	}
	return info
}

func sendToAll(s *Server, clients []*Client, msgType string, payload proto.Message) {
	for _, c := range clients {
		c.sendMessage(s.logger, msgType, payload)
	}
}

func allowRate(times []time.Time, limit int, now time.Time) ([]time.Time, bool) {
	kept := times[:0]
	for _, t := range times {
		if now.Sub(t) < peekRateWindow {
			kept = append(kept, t)
		}
	}
	if len(kept) >= limit {
		return kept, false
	}
	return append(kept, now), true
}

func (s *Server) peekAuthenticate(c *Client, payload []byte) {
	var p pb.AuthenticatePayload
	if proto.Unmarshal(payload, &p) != nil {
		c.sendError(s.logger, peekFriendInvalidPayloadCode, "Invalid authenticate payload")
		return
	}
	store, ok := s.peekReady()
	if !ok {
		c.sendError(s.logger, "auth_error", errPeekNoDatabase.Error())
		return
	}
	if n := len(p.AuthToken); n < peekMinAuthTokenLength || n > peekMaxAuthTokenLength {
		c.sendError(s.logger, "auth_error", "Invalid auth token")
		return
	}
	username := sanitizeString(p.CurrentUsername, MaxUsernameLength)
	v2 := c.hasFeature(FeaturePeekFriends)
	code, err := store.authenticate(p.AuthToken, username, v2)
	if err != nil {
		s.logger.Error("PeekParty authenticate failed", zap.Error(err))
		c.sendError(s.logger, "auth_error", "Could not sign in to friends")
		return
	}
	if username != "" && c.userName() == "" {
		c.setUsername(username)
	}

	s.peek.mu.Lock()
	if s.peek.clients == nil {
		s.peek.clients = map[*Client]*peekClient{}
		s.peek.byCode = map[string]map[*Client]struct{}{}
	}
	previous := s.peek.clients[c]
	if previous != nil && previous.code != code {
		delete(s.peek.byCode[previous.code], c)
	}
	if previous == nil || previous.code != code {
		s.peek.clients[c] = &peekClient{code: code}
	}
	s.peek.clients[c].silent = p.PresenceOnly
	if s.peek.byCode[code] == nil {
		s.peek.byCode[code] = map[*Client]struct{}{}
	}
	s.peek.byCode[code][c] = struct{}{}
	s.peek.mu.Unlock()
	if previous != nil && previous.code != code {
		s.peekBroadcastPresence(store, previous.code)
	}

	if !p.PresenceOnly {
		c.reply(s.logger, MsgTypeAuthSuccess, &pb.AuthSuccessPayload{FriendCode: code})
		s.peekSendStatus(store, c, code)
	}
	s.peekBroadcastPresence(store, code)
	s.logger.Info("PeekParty friends signed in", zap.String("client_id", c.clientID()), zap.Bool("v2", v2))
}

// peekSendStatus sends c its full friend list (and open requests, for apps that know them).
func (s *Server) peekSendStatus(store *peekStore, c *Client, code string) {
	snap, err := store.snapshot(code)
	if err != nil {
		s.logger.Error("PeekParty friend list failed", zap.Error(err))
		return
	}
	status := &pb.FriendsStatusPayload{Friends: []*pb.FriendInfo{}}
	for _, f := range snap.friends {
		status.Friends = append(status.Friends, s.peekInfo(store, f))
	}
	if c.hasFeature(FeaturePeekFriends) {
		for _, f := range snap.incoming {
			status.IncomingRequests = append(status.IncomingRequests, &pb.FriendInfo{FriendCode: f, Username: snap.usernames[f]})
		}
		for _, f := range snap.outgoing {
			status.OutgoingRequests = append(status.OutgoingRequests, &pb.FriendInfo{FriendCode: f, Username: snap.usernames[f]})
		}
	}
	c.sendMessage(s.logger, MsgTypeFriendsStatus, status)
}

// peekBroadcastPresence tells code's online friends how code looks now.
func (s *Server) peekBroadcastPresence(store *peekStore, code string) {
	update := &pb.FriendPresenceUpdatePayload{Friend: s.peekInfo(store, code)}
	for _, friend := range store.friends(code) {
		sendToAll(s, s.peekOnline(friend), MsgTypeFriendPresenceUpdate, update)
	}
}

// peekPresenceLoop notices name and party changes without hooks in upstream's room code.
func (s *Server) peekPresenceLoop() {
	ticker := time.NewTicker(peekPresenceInterval)
	defer ticker.Stop()
	for {
		select {
		case <-s.stop:
			return
		case <-ticker.C:
		}
		store, err := s.peekStore()
		if err != nil {
			continue
		}
		s.peekRefreshPresence(store)
	}
}

func (s *Server) peekRefreshPresence(store *peekStore) {
	s.peek.mu.Lock()
	codes := make([]string, 0, len(s.peek.byCode))
	for code := range s.peek.byCode {
		codes = append(codes, code)
	}
	s.peek.mu.Unlock()
	for _, code := range codes {
		info := s.peekInfo(store, code)
		signature := info.Username + "\x00" + info.ActiveRoomCode
		changed := false
		s.peek.mu.Lock()
		for c := range s.peek.byCode[code] {
			if pc := s.peek.clients[c]; pc != nil && pc.presence != signature {
				pc.presence = signature
				changed = true
			}
		}
		s.peek.mu.Unlock()
		if changed {
			update := &pb.FriendPresenceUpdatePayload{Friend: info}
			for _, friend := range store.friends(code) {
				sendToAll(s, s.peekOnline(friend), MsgTypeFriendPresenceUpdate, update)
			}
		}
	}
}

// peekClientGone runs when a connection closes.
func (s *Server) peekClientGone(c *Client) {
	s.peek.mu.Lock()
	pc := s.peek.clients[c]
	if pc != nil {
		delete(s.peek.clients, c)
		delete(s.peek.byCode[pc.code], c)
		if len(s.peek.byCode[pc.code]) == 0 {
			delete(s.peek.byCode, pc.code)
		}
	}
	s.peek.mu.Unlock()
	if pc == nil {
		return
	}
	if store, err := s.peekStore(); err == nil {
		s.peekBroadcastPresence(store, pc.code)
	}
}

func (s *Server) peekRequireCode(c *Client) (*peekStore, string, bool) {
	store, ok := s.peekReady()
	code := s.peekCode(c)
	s.peek.mu.Lock()
	silent := s.peek.clients[c] != nil && s.peek.clients[c].silent
	s.peek.mu.Unlock()
	if !ok || code == "" || silent {
		c.sendError(s.logger, peekFriendNotAuthenticated, "Must authenticate first")
		return nil, "", false
	}
	return store, code, true
}

func decodeFriendCode(payload []byte) (string, bool) {
	var p pb.FriendCodePayload
	if proto.Unmarshal(payload, &p) != nil {
		return "", false
	}
	code := normalizeFriendCode(p.FriendCode)
	return code, code != ""
}

func (s *Server) peekAddFriend(c *Client, payload []byte) {
	store, me, ok := s.peekRequireCode(c)
	if !ok {
		return
	}
	var raw pb.FriendCodePayload
	_ = proto.Unmarshal(payload, &raw)
	target := normalizeFriendCode(raw.FriendCode)
	fail := func(err error) {
		c.reply(s.logger, MsgTypeFriendAdded, &pb.FriendAddedPayload{FriendCode: raw.FriendCode, Success: false, ErrorMessage: err.Error()})
	}
	if target == "" {
		fail(errPeekUnknownCode)
		return
	}
	if target == me {
		fail(errPeekOwnCode)
		return
	}
	s.peek.mu.Lock()
	allowed := false
	if pc := s.peek.clients[c]; pc != nil {
		pc.adds, allowed = allowRate(pc.adds, peekAddFriendPerMinute, time.Now())
	}
	s.peek.mu.Unlock()
	if !allowed {
		fail(errors.New("too many attempts, try again in a minute"))
		return
	}

	result, err := store.addFriend(me, target)
	if err != nil {
		if !errors.Is(err, errPeekUnknownCode) && !errors.Is(err, errPeekTooManyOut) && !errors.Is(err, errPeekTooManyFriend) {
			s.logger.Error("PeekParty add friend failed", zap.Error(err))
			err = errors.New("could not add friend")
		}
		fail(err)
		return
	}
	targetName := store.username(target)
	switch result {
	case peekAddedRequest:
		if c.hasFeature(FeaturePeekFriends) {
			c.reply(s.logger, MsgTypeFriendAdded, &pb.FriendAddedPayload{FriendCode: target, Success: true, Pending: true, Username: targetName})
		} else {
			// PeekMusic 1.3.0 only knows success or an error text; the text explains the wait.
			c.reply(s.logger, MsgTypeFriendAdded, &pb.FriendAddedPayload{FriendCode: target, Success: false, ErrorMessage: "Friend request sent. You are friends once they accept it."})
		}
		request := &pb.FriendRequestPayload{From: &pb.FriendInfo{FriendCode: me, Username: s.peekInfo(store, me).Username}}
		for _, other := range s.peekOnline(target) {
			if other.hasFeature(FeaturePeekFriends) {
				other.sendMessage(s.logger, MsgTypeFriendRequest, request)
			}
		}
	default:
		c.reply(s.logger, MsgTypeFriendAdded, &pb.FriendAddedPayload{FriendCode: target, Success: true, Username: targetName})
		c.sendMessage(s.logger, MsgTypeFriendPresenceUpdate, &pb.FriendPresenceUpdatePayload{Friend: s.peekInfo(store, target)})
		sendToAll(s, s.peekOnline(target), MsgTypeFriendPresenceUpdate, &pb.FriendPresenceUpdatePayload{Friend: s.peekInfo(store, me)})
	}
}

func (s *Server) peekAccept(c *Client, payload []byte) {
	store, me, ok := s.peekRequireCode(c)
	if !ok {
		return
	}
	from, valid := decodeFriendCode(payload)
	if !valid {
		c.sendError(s.logger, peekFriendInvalidPayloadCode, "Invalid friend code")
		return
	}
	if err := store.acceptRequest(me, from); err != nil {
		if !errors.Is(err, errPeekNoRequest) && !errors.Is(err, errPeekTooManyFriend) {
			s.logger.Error("PeekParty accept failed", zap.Error(err))
			err = errors.New("could not accept the request")
		}
		c.sendError(s.logger, peekFriendErrorCode, err.Error())
		return
	}
	sendToAll(s, s.peekOnline(me), MsgTypeFriendPresenceUpdate, &pb.FriendPresenceUpdatePayload{Friend: s.peekInfo(store, from)})
	sendToAll(s, s.peekOnline(from), MsgTypeFriendPresenceUpdate, &pb.FriendPresenceUpdatePayload{Friend: s.peekInfo(store, me)})
}

// peekRemove ends a friendship, withdraws my request or declines theirs. Both sides hear friend_removed.
func (s *Server) peekRemove(c *Client, payload []byte) {
	store, me, ok := s.peekRequireCode(c)
	if !ok {
		return
	}
	other, valid := decodeFriendCode(payload)
	if !valid {
		c.sendError(s.logger, peekFriendInvalidPayloadCode, "Invalid friend code")
		return
	}
	if _, err := store.removeAny(me, other); err != nil {
		s.logger.Error("PeekParty remove friend failed", zap.Error(err))
		c.sendError(s.logger, "remove_friend_error", "Could not remove friend")
		return
	}
	sendToAll(s, s.peekOnline(me), MsgTypeFriendRemoved, &pb.FriendCodePayload{FriendCode: other})
	sendToAll(s, s.peekOnline(other), MsgTypeFriendRemoved, &pb.FriendCodePayload{FriendCode: me})
}

// peekInvite asks a friend to join the party c is in.
func (s *Server) peekInvite(c *Client, payload []byte) {
	store, me, ok := s.peekRequireCode(c)
	if !ok {
		return
	}
	friend, valid := decodeFriendCode(payload)
	if !valid || !store.areFriends(me, friend) {
		c.sendError(s.logger, peekFriendErrorCode, "You can only invite friends")
		return
	}
	room := c.currentRoom()
	if room == nil {
		c.sendError(s.logger, peekFriendErrorCode, "Start or join a party first")
		return
	}
	s.peek.mu.Lock()
	allowed := false
	if pc := s.peek.clients[c]; pc != nil {
		pc.invites, allowed = allowRate(pc.invites, peekInvitesPerMinute, time.Now())
	}
	s.peek.mu.Unlock()
	if !allowed {
		c.sendError(s.logger, peekFriendErrorCode, "Too many invites, try again in a minute")
		return
	}
	targets := s.peekOnline(friend)
	if len(targets) == 0 {
		c.sendError(s.logger, peekFriendErrorCode, "Your friend is offline")
		return
	}
	invite := &pb.FriendInvitePayload{From: s.peekInfo(store, me), RoomCode: room.Code}
	for _, t := range targets {
		if t.hasFeature(FeaturePeekFriends) {
			t.sendMessage(s.logger, MsgTypeFriendInvite, invite)
		}
	}
}

// peekDeleteAccount forgets c's account on request (privacy policy: "until you ask us to delete them").
func (s *Server) peekDeleteAccount(c *Client) {
	store, me, ok := s.peekRequireCode(c)
	if !ok {
		return
	}
	affected, err := store.deleteAccount(me)
	if err != nil {
		s.logger.Error("PeekParty delete account failed", zap.Error(err))
		c.sendError(s.logger, peekFriendErrorCode, "Could not delete your friends data")
		return
	}
	own := s.peekConnections(me, true)
	loud := s.peekOnline(me)
	s.peek.mu.Lock()
	for _, oc := range own {
		delete(s.peek.clients, oc)
	}
	delete(s.peek.byCode, me)
	s.peek.mu.Unlock()
	for _, other := range affected {
		sendToAll(s, s.peekOnline(other), MsgTypeFriendRemoved, &pb.FriendCodePayload{FriendCode: me})
	}
	c.reply(s.logger, MsgTypeFriendAccountDeleted, &pb.DeleteFriendAccountPayload{})
	for _, oc := range loud {
		if oc != c {
			oc.sendMessage(s.logger, MsgTypeFriendAccountDeleted, &pb.DeleteFriendAccountPayload{})
		}
	}
	s.logger.Info("PeekParty friends account deleted", zap.String("client_id", c.clientID()))
}
