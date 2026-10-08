import re

with open('internal/server/client.go', 'r') as f:
    content = f.read()

content = content.replace("codec              *MessageCodec // Message codec for encoding/decoding", "codec              *MessageCodec // Message codec for encoding/decoding\n\tserver             *Server")
content = content.replace("func newClient(id string, conn *websocket.Conn) *Client {", "func newClient(id string, conn *websocket.Conn, server *Server) *Client {")
content = content.replace("codec: NewMessageCodec(true),\n\t}", "codec: NewMessageCodec(true),\n\t\tserver: server,\n\t}")

setroom_orig = """func (c *Client) setRoom(room *Room) {
	if room != nil {
		c.markAffiliationStarted()
	}
	c.room.Store(room)
}"""
setroom_new = """func (c *Client) setRoom(room *Room) {
	if room != nil {
		c.markAffiliationStarted()
	}
	c.room.Store(room)
	if c.server != nil {
		c.server.broadcastFriendPresence(c, c.getFriendCode())
	}
}"""
content = content.replace(setroom_orig, setroom_new)

trysetroom_orig = """func (c *Client) trySetRoom(room *Room) bool {
	if room == nil {
		return false
	}
	c.markAffiliationStarted()
	return c.room.CompareAndSwap(nil, room)
}"""
trysetroom_new = """func (c *Client) trySetRoom(room *Room) bool {
	if room == nil {
		return false
	}
	c.markAffiliationStarted()
	ok := c.room.CompareAndSwap(nil, room)
	if ok && c.server != nil {
		c.server.broadcastFriendPresence(c, c.getFriendCode())
	}
	return ok
}"""
content = content.replace(trysetroom_orig, trysetroom_new)

clearroom_orig = """func (c *Client) clearRoom(room *Room) bool {
	return room != nil && c.room.CompareAndSwap(room, nil)
}"""
clearroom_new = """func (c *Client) clearRoom(room *Room) bool {
	ok := room != nil && c.room.CompareAndSwap(room, nil)
	if ok && c.server != nil {
		c.server.broadcastFriendPresence(c, c.getFriendCode())
	}
	return ok
}"""
content = content.replace(clearroom_orig, clearroom_new)

with open('internal/server/client.go', 'w') as f:
    f.write(content)

with open('internal/server/server.go', 'r') as f:
    server_content = f.read()

server_content = server_content.replace("client := newClient(s.generateUserID(), conn)", "client := newClient(s.generateUserID(), conn, s)")

with open('internal/server/server.go', 'w') as f:
    f.write(server_content)
