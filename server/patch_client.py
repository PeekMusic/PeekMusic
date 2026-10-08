import re

with open('internal/server/client.go', 'r') as f:
    content = f.read()

content = content.replace("sessionToken       atomic.Value // string", "sessionToken       atomic.Value // string\n\tfriendCode         atomic.Value // string")

funcs = """func (c *Client) getFriendCode() string {
	return loadAtomicString(&c.friendCode)
}

func (c *Client) setFriendCode(friendCode string) {
	c.friendCode.Store(friendCode)
}

func (c *Client) session() string {"""
content = content.replace("func (c *Client) session() string {", funcs)

with open('internal/server/client.go', 'w') as f:
    f.write(content)

