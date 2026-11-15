# peekmusic.org

Static site served by Caddy on the PeekParty server (`peekserver`, 2.31.21.150) from `/var/www/peekmusic`.

- `index.html`: landing page
- `privacy/index.html`: privacy policy (German and English). The app links to it through `PeekFork.PRIVACY_URL`.

Update the privacy policy whenever the app starts or stops talking to a service, or the server changes what it stores or logs.

## Deploy

```sh
scp -r website/index.html website/privacy peekmusic:/var/www/peekmusic/
```

Caddy block in `/etc/caddy/Caddyfile`:

```
peekmusic.org, www.peekmusic.org {
	root * /var/www/peekmusic
	file_server
}
```
