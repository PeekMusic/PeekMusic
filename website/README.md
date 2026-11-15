# peekmusic.org

Static site served by Caddy on the PeekParty server (`peekserver`, 2.31.21.150) from `/var/www/peekmusic`.

- `index.html`: landing page
- `privacy/index.html`: privacy policy (German and English). The app links to it through `PeekFork.PRIVACY_URL`.
- `style.css`: shared Material 3 Expressive styles, colors derived from the app's indigo (#1F1E73), light and dark.
- `icons/`, `favicon.ico`, `site.webmanifest`: the app icon on the indigo background.
- `fonts/roboto-flex-latin.woff2`: Roboto Flex (SIL Open Font License), self-hosted so visitors never load anything from Google.

The site loads nothing from other hosts. Keep it that way; the privacy policy depends on it.

Update the privacy policy whenever the app starts or stops talking to a service, or the server changes what it stores or logs.

## Deploy

```sh
cd website && scp -r index.html style.css favicon.ico site.webmanifest fonts icons privacy peekmusic:/var/www/peekmusic/
```

Caddy block in `/etc/caddy/Caddyfile`:

```
peekmusic.org, www.peekmusic.org {
	root * /var/www/peekmusic
	file_server
}
```
