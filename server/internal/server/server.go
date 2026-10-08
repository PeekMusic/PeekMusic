package server

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	mathrand "math/rand"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"go.uber.org/zap"
)

type Session struct {
	UserID       string
	Username     string
	RoomCode     string
	IsHost       bool
	DisconnectAt time.Time
}

type Room struct {
	Code               string
	Host               *Client
	Clients            map[string]*Client
	PendingJoins       map[string]*Client     // Users waiting for approval
	PendingSuggestions map[string]*Suggestion // Track suggestions waiting for host action
	DisconnectedUsers  map[string]*Session    // Users temporarily disconnected
	State              *RoomState
	BufferingUsers     map[string]bool // Track which users are still buffering
	HostStartPosition  int64           // Host's position when buffering started
	HostDisconnectedAt *time.Time      // When the host disconnected (nil if connected)
	EmptySince         *time.Time      // When the room became empty (nil if not empty)
	syncMu             sync.Mutex      // Serializes playback snapshots and their delivery.
	mu                 sync.RWMutex
}

// Suggestion represents a track suggestion from a guest
type Suggestion struct {
	ID           string
	FromUserID   string
	FromUsername string
	Track        *TrackInfo
}

// Server is the main WebSocket server
type Server struct {
	rooms           map[string]*Room
	onlineFriends   map[string]*Client
	friendsMu       sync.RWMutex
	sessions        map[string]*Session // sessionToken -> Session
	clients         map[*Client]bool
	upgrader        websocket.Upgrader
	mu              sync.RWMutex
	rngMu           sync.Mutex
	logger          *zap.Logger
	uaPolicy        *uaPolicy
	database        *database
	connectionSlots chan struct{}
	rng             *mathrand.Rand
	startTime       time.Time // Track when server started for room retention logic
}

const (
	// Grace period for reconnection (increased from 5 to 15 minutes for better recovery)
	ReconnectGracePeriod = 15 * time.Minute
	// How often to clean up expired sessions
	SessionCleanupInterval = 1 * time.Minute
	// How long to keep empty rooms before deleting them
	EmptyRoomCleanupTimeout = 5 * time.Minute
	// How often to check for empty rooms
	EmptyRoomCleanupInterval = 30 * time.Second
	// Minimum time to keep empty rooms after server restart (for reconnection)
	MinRoomRetentionAfterRestart = 2 * time.Minute
	// Security limits
	MaxUsernameLength     = 50
	MaxRoomCodeLength     = 10
	MaxTrackTitleLength   = 200
	MaxTrackArtistLength  = 200
	MaxTrackURLLength     = 2048
	MaxTrackDuration      = 24 * 60 * 60 * 1000 // 24 hours in milliseconds
	MaxQueueSize          = 1000
	MaxPendingJoins       = 100
	MaxPendingSuggestions = 100
	// Connection limits
	MaxClients           = 10000
	MaxRooms             = 10000
	MaxClientsPerRoom    = 100
	MaxReadMessageSize   = 524288 // 512KB (reasonable for queue syncs)
	MaxHeaderBytes       = 65536
	ReadTimeout          = 60 * time.Second
	WriteTimeout         = 10 * time.Second
	PingInterval         = 30 * time.Second
	IdleTimeout          = 120 * time.Second
	ShutdownTimeout      = 10 * time.Second
	MessageRateWindow    = time.Second
	MaxMessagesPerWindow = 60
	SyncResponseInterval = time.Second
)

func NewServer(logger *zap.Logger) *Server {
	s := &Server{
				rooms:    make(map[string]*Room),
		onlineFriends: make(map[string]*Client),
		sessions: make(map[string]*Session),
		clients:  make(map[*Client]bool),
		upgrader: websocket.Upgrader{
			CheckOrigin: func(r *http.Request) bool {
				return true
			},
			ReadBufferSize:  4096,
			WriteBufferSize: 4096,
		},
		logger:          logger,
		uaPolicy:        defaultUAPolicy(),
		connectionSlots: make(chan struct{}, MaxClients),
		rng:             mathrand.New(mathrand.NewSource(time.Now().UnixNano())),
		startTime:       time.Now(),
	}

	// Start cleanup goroutines
	go s.cleanupExpiredSessions()
	go s.cleanupEmptyRooms()

	return s
}

func (s *Server) generateRoomCode() string {
	const chars = "1234567890QWERTYUPASDFGHJLKZXCVBNM"
	code := make([]byte, 8)
	s.rngMu.Lock()
	for i := range code {
		code[i] = chars[s.rng.Intn(len(chars))]
	}
	s.rngMu.Unlock()
	return string(code)
}

func (s *Server) generateUserID() string {
	s.rngMu.Lock()
	randNum := s.rng.Intn(10000)
	s.rngMu.Unlock()
	return fmt.Sprintf("user_%d_%d", time.Now().UnixNano(), randNum)
}

func (s *Server) generateSessionToken() string {
	// Use crypto/rand for secure token generation
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		s.logger.Error("Failed to generate secure token", zap.Error(err))
		// Fallback to less secure but functional token
		s.rngMu.Lock()
		tokenNum := s.rng.Intn(1000000)
		s.rngMu.Unlock()
		return fmt.Sprintf("token_%d_%d", time.Now().UnixNano(), tokenNum)
	}
	return hex.EncodeToString(b)
}

func (s *Server) tryAcquireConnectionSlot() bool {
	if s.connectionSlots == nil {
		return true
	}
	select {
	case s.connectionSlots <- struct{}{}:
		return true
	default:
		return false
	}
}

func (s *Server) releaseConnectionSlot() {
	if s.connectionSlots != nil {
		<-s.connectionSlots
	}
}

func (s *Server) handleWebSocket(w http.ResponseWriter, r *http.Request) {
	s.mu.RLock()
	clientCount := len(s.clients)
	s.mu.RUnlock()
	if clientCount >= MaxClients {
		http.Error(w, "server at connection capacity", http.StatusServiceUnavailable)
		return
	}
	if !s.tryAcquireConnectionSlot() {
		http.Error(w, "server at connection capacity", http.StatusServiceUnavailable)
		return
	}
	slotOwned := true
	defer func() {
		if slotOwned {
			s.releaseConnectionSlot()
		}
	}()

	conn, err := s.upgrader.Upgrade(w, r, nil)
	if err != nil {
		s.logger.Warn("WebSocket upgrade error", zap.Error(err))
		return
	}

	// Use Protobuf codec with compression enabled
	client := newClient(s.generateUserID(), conn, s)

	userAgent := r.Header.Get("User-Agent")
	tier := s.uaPolicy.resolve(userAgent)
	if err := s.database.recordUserAgent(userAgent); err != nil {
		s.logger.Warn("Failed to record User-Agent", zap.Error(err))
	}
	client.uaTier = tier
	client.policy = s.uaPolicy
	if tier == uaBlock {
		// Write synchronously so blocked connections cannot create untracked pumps.
		s.logger.Debug("Blocked client", zap.String("client_id", client.clientID()))
		message, encodeErr := client.codec.Encode(MsgTypeError, ErrorPayload{Code: "blocked_client", Message: BlockedClientMessage})
		if encodeErr != nil {
			s.logger.Error("Failed to encode blocked-client response", zap.Error(encodeErr))
		} else if err := conn.SetWriteDeadline(time.Now().Add(WriteTimeout)); err == nil {
			_ = conn.WriteMessage(websocket.BinaryMessage, message)
			_ = conn.WriteControl(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.ClosePolicyViolation, "blocked client"), time.Now().Add(WriteTimeout))
		}
		_ = conn.Close()
		return
	}

	s.mu.Lock()
	if len(s.clients) >= MaxClients {
		s.mu.Unlock()
		_ = conn.Close()
		return
	}
	s.clients[client] = true
	s.mu.Unlock()

	client.connectionSlot = true
	slotOwned = false
	go client.writePump(s.logger)
	go client.readPump(s)

	s.logger.Info("Client connected", zap.String("client_id", client.clientID()), zap.String("ua_tier", tier.String()))
}

func (s *Server) handleMessage(c *Client, data []byte) {
	// Decode message using protobuf codec
	msgType, payloadBytes, err := c.codec.Decode(data)
	if err != nil {
		s.logger.Debug("Invalid message received", zap.String("client_id", c.clientID()), zap.Error(err))
		c.sendError(s.logger, "invalid_message", "Invalid message format")
		return
	}

	if msgType == "" {
		c.sendError(s.logger, "invalid_message", "Message type is required")
		return
	}

	s.logger.Debug("Message received", zap.String("client_id", c.clientID()), zap.String("message_type", msgType), zap.String("format", "protobuf"))

	switch msgType {
	case MsgTypeCreateRoom:
		s.handleCreateRoom(c, payloadBytes)
	case MsgTypeJoinRoom:
		s.handleJoinRoom(c, payloadBytes)
	case MsgTypeLeaveRoom:
		s.leaveRoom(c)
	case MsgTypeApproveJoin:
		s.handleApproveJoin(c, payloadBytes)
	case MsgTypeRejectJoin:
		s.handleRejectJoin(c, payloadBytes)
	case MsgTypePlaybackAction:
		s.handlePlaybackAction(c, payloadBytes)
	case MsgTypeBufferReady:
		s.handleBufferReady(c, payloadBytes)
	case MsgTypeKickUser:
		s.handleKickUser(c, payloadBytes)
	case MsgTypeTransferHost:
		s.handleTransferHost(c, payloadBytes)
	case MsgTypePing:
		s.handlePing(c, payloadBytes)
	case MsgTypeRequestSync:
		s.handleRequestSync(c)
	case MsgTypeReconnect:
		s.handleReconnect(c, payloadBytes)
	case MsgTypeSuggestTrack:
		s.handleSuggestTrack(c, payloadBytes)
	case MsgTypeApproveSuggestion:
		s.handleApproveSuggestion(c, payloadBytes)
	case MsgTypeRejectSuggestion:
		s.handleRejectSuggestion(c, payloadBytes)
	case MsgTypeClientCapabilities:
		s.handleClientCapabilities(c, payloadBytes)
	case MsgTypeAuthenticate:
		s.handleAuthenticate(c, payloadBytes)
	case MsgTypeAddFriend:
		s.handleAddFriend(c, payloadBytes)
	case MsgTypeRemoveFriend:
		s.handleRemoveFriend(c, payloadBytes)
	default:
		c.sendError(s.logger, "unknown_message_type", fmt.Sprintf("Unknown message type: %s", msgType))
	}
}

func (s *Server) handlePing(c *Client, payload []byte) {
	receivedAt := time.Now().UnixMilli()
	p := PingPayload{}
	if len(payload) > 0 {
		if err := decodePayload(payload, MsgTypePing, &p); err != nil {
			c.sendError(s.logger, "invalid_payload", "Invalid ping payload")
			return
		}
	}

	c.sendMessage(s.logger, MsgTypePong, PongPayload{
		ClientTime:        p.ClientTime,
		ServerReceiveTime: receivedAt,
		ServerSendTime:    time.Now().UnixMilli(),
		Sequence:          p.Sequence,
	})
}

func (s *Server) handleClientCapabilities(c *Client, payload []byte) {
	var p ClientCapabilitiesPayload
	if err := decodePayload(payload, MsgTypeClientCapabilities, &p); err != nil {
		c.sendError(s.logger, "invalid_payload", "Invalid client capabilities payload")
		return
	}
	if !p.SupportsProtobuf {
		c.sendError(s.logger, "unsupported_client", "Protobuf support is required")
		return
	}
	c.negotiationMu.Lock()
	if c.affiliationStarted {
		c.negotiationMu.Unlock()
		c.sendError(s.logger, "capabilities_too_late", "Client capabilities must be sent before joining a room")
		return
	}
	if c.capabilitiesSet || c.codec == nil {
		c.negotiationMu.Unlock()
		c.sendError(s.logger, "capabilities_already_set", "Client capabilities have already been configured")
		return
	}
	c.capabilitiesSet = true
	c.codec.setCompressionEnabled(p.SupportsCompression)
	c.sendMessage(s.logger, MsgTypeServerCapabilities, ServerCapabilitiesPayload{
		SupportsProtobuf:    true,
		SupportsCompression: true,
		ServerVersion:       "1",
	})
	c.negotiationMu.Unlock()
}

func (s *Server) handleAuthenticate(c *Client, payload []byte) {
	var p AuthenticatePayload
	if err := decodePayload(payload, MsgTypeAuthenticate, &p); err != nil {
		c.sendError(s.logger, "invalid_payload", "Invalid authenticate payload")
		return
	}
	
	friendCode, err := s.authenticateFriend(p.AuthToken)
	if err != nil {
		c.sendError(s.logger, "auth_error", err.Error())
		return
	}
	
	c.setFriendCode(friendCode)
    if p.CurrentUsername != "" {
        c.setUsername(p.CurrentUsername)
        s.saveUsername(friendCode, p.CurrentUsername)
    }
	
	s.friendsMu.Lock()
	s.onlineFriends[friendCode] = c
	s.friendsMu.Unlock()
	
	c.sendMessage(s.logger, MsgTypeAuthSuccess, AuthSuccessPayload{FriendCode: friendCode})
	
	friendCodes, _ := s.getFriends(friendCode)
	friendsList := []FriendInfo{}
	
	s.friendsMu.RLock()
	for _, fcode := range friendCodes {
		isOnline := false
		activeRoom := ""
        uname := ""
		if fc, ok := s.onlineFriends[fcode]; ok && fc != nil {
			isOnline = true
            uname = fc.userName()
			if room := fc.currentRoom(); room != nil {
				activeRoom = room.Code
			}
		}
		friendsList = append(friendsList, FriendInfo{
			FriendCode: fcode,
            Username: uname,
			IsOnline: isOnline,
			ActiveRoomCode: activeRoom,
		})
	}
	s.friendsMu.RUnlock()
	
	c.sendMessage(s.logger, MsgTypeFriendsStatus, FriendsStatusPayload{Friends: friendsList})
    
    s.broadcastFriendPresence(c, friendCode)
}

func (s *Server) handleAddFriend(c *Client, payload []byte) {
	var p AddFriendPayload
	if err := decodePayload(payload, MsgTypeAddFriend, &p); err != nil {
		c.sendError(s.logger, "invalid_payload", "Invalid add friend payload")
		return
	}
	myCode := c.getFriendCode()
	if myCode == "" {
		c.sendError(s.logger, "not_authenticated", "Must authenticate first")
		return
	}
	err := s.addFriend(myCode, p.FriendCode)
	success := true
	errMsg := ""
	if err != nil {
		success = false
		errMsg = err.Error()
	}
	c.sendMessage(s.logger, MsgTypeFriendAdded, FriendAddedPayload{FriendCode: p.FriendCode, Success: success, ErrorMessage: errMsg})
    if success {
        s.broadcastFriendPresence(c, myCode)
        
        // Also send the new friend's presence back to the adder so their UI updates immediately
        s.friendsMu.RLock()
        isOnline := false
        activeRoom := ""
        uname := ""
        if fc, ok := s.onlineFriends[p.FriendCode]; ok && fc != nil {
            isOnline = true
            uname = fc.userName()
            if room := fc.currentRoom(); room != nil {
                activeRoom = room.Code
            }
        }
        s.friendsMu.RUnlock()
        
        c.sendMessage(s.logger, MsgTypeFriendPresenceUpdate, FriendPresenceUpdatePayload{
            Friend: &FriendInfo{
                FriendCode:     p.FriendCode,
                Username:       uname,
                IsOnline:       isOnline,
                ActiveRoomCode: activeRoom,
            },
        })
    }
}

func (s *Server) broadcastFriendPresence(c *Client, myCode string) {
    if myCode == "" {
        return
    }
    activeRoom := ""
    if room := c.currentRoom(); room != nil {
        activeRoom = room.Code
    }
    
    friends, _ := s.getFriends(myCode)
    
    s.friendsMu.RLock()
    defer s.friendsMu.RUnlock()
    for _, fcode := range friends {
        if fc, ok := s.onlineFriends[fcode]; ok && fc != nil {
            fc.sendMessage(s.logger, MsgTypeFriendPresenceUpdate, FriendPresenceUpdatePayload{
                Friend: &FriendInfo{
                    FriendCode: myCode,
                    Username: c.userName(),
                    IsOnline: true,
                    ActiveRoomCode: activeRoom,
                },
            })
        }
    }
}

func (s *Server) closeAllClients() {
	s.mu.RLock()
	clients := make([]*Client, 0, len(s.clients))
	for client := range s.clients {
		clients = append(clients, client)
	}
	s.mu.RUnlock()

	for _, client := range clients {
		if client == nil || client.Conn == nil {
			continue
		}
		_ = client.Conn.Close()
		client.closeSend()
	}
}

func (s *Server) broadcastFriendPresenceOffline(fcode string) {
    if fcode == "" {
        return
    }
    
    friends, _ := s.getFriends(fcode)
    lastUname := s.getUsername(fcode)
    
    s.friendsMu.RLock()
    defer s.friendsMu.RUnlock()
    for _, friendCode := range friends {
        if fc, ok := s.onlineFriends[friendCode]; ok && fc != nil {
            fc.sendMessage(s.logger, MsgTypeFriendPresenceUpdate, FriendPresenceUpdatePayload{
                Friend: &FriendInfo{
                    FriendCode:     fcode,
                    Username:       lastUname,
                    IsOnline:       false,
                    ActiveRoomCode: "",
                },
            })
        }
    }
}

func (s *Server) handleRemoveFriend(c *Client, payload []byte) {
	var p RemoveFriendPayload
	if err := decodePayload(payload, MsgTypeRemoveFriend, &p); err != nil {
		c.sendError(s.logger, "invalid_payload", "Invalid remove friend payload")
		return
	}
	myCode := c.getFriendCode()
	if myCode == "" {
		c.sendError(s.logger, "not_authenticated", "Must authenticate first")
		return
	}
	err := s.removeFriend(myCode, p.FriendCode)
	if err != nil {
		c.sendError(s.logger, "remove_friend_error", err.Error())
		return
	}
	
	// Notify the adder that the friend is removed
	c.sendMessage(s.logger, MsgTypeFriendRemoved, RemoveFriendPayload{FriendCode: p.FriendCode})
	
	// Notify the removed friend that we removed them
	s.friendsMu.RLock()
	if targetClient, ok := s.onlineFriends[p.FriendCode]; ok && targetClient != nil {
		targetClient.sendMessage(s.logger, MsgTypeFriendRemoved, RemoveFriendPayload{FriendCode: myCode})
	}
	s.friendsMu.RUnlock()
}
