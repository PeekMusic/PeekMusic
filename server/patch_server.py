import re

with open('internal/server/server.go', 'r') as f:
    content = f.read()

content = content.replace("rooms           map[string]*Room", "rooms           map[string]*Room\n\tonlineFriends   map[string]*Client\n\tfriendsMu       sync.RWMutex")

new_server = """		rooms:    make(map[string]*Room),
		onlineFriends: make(map[string]*Client),"""
content = content.replace("rooms:    make(map[string]*Room),", new_server)

with open('internal/server/server.go', 'w') as f:
    f.write(content)

