import re

with open('internal/server/server.go', 'r') as f:
    content = f.read()

cases = """	case MsgTypeClientCapabilities:
		s.handleClientCapabilities(c, payloadBytes)
	case MsgTypeAuthenticate:
		s.handleAuthenticate(c, payloadBytes)
	case MsgTypeAddFriend:
		s.handleAddFriend(c, payloadBytes)"""

content = content.replace("	case MsgTypeClientCapabilities:\n		s.handleClientCapabilities(c, payloadBytes)", cases)


funcs = """func (s *Server) handleAuthenticate(c *Client, payload []byte) {
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

func (s *Server) closeAllClients() {"""

content = content.replace("func (s *Server) closeAllClients() {", funcs)

with open('internal/server/server.go', 'w') as f:
    f.write(content)
