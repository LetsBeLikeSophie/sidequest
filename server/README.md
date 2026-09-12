# Side Quest reaction proxy

Zero-dependency Node server for `POST /api/react` — see `server.js` for why
this exists (the Anthropic key must never ship inside the app itself).

## Local dev

```
npm start   # node --env-file=../.env server.js
```

## Production

Deployed on the existing Oracle Cloud VM (168.110.25.49 / itssophie.dev):

- Code: `~/sidequest-server/` (server.js, package.json)
- Key: `~/.env` (`ANTHROPIC_API_KEY=...`, chmod 600, not inside the repo)
- Process: `systemd` unit `sidequest-server.service` (same pattern as the
  existing `newstrend-api` service on that box) — `sudo systemctl status
  sidequest-server`
- Public URL: `https://itssophie.dev/sidequest/api/react`, proxied by the
  existing `itssophie.dev` nginx site (see the `/sidequest/` location block
  in `/etc/nginx/sites-available/itssophie.dev`) straight to
  `127.0.0.1:8787` — reuses the existing Let's Encrypt cert, no new
  subdomain/DNS needed.

To redeploy after a code change:

```
scp server.js ubuntu@168.110.25.49:~/sidequest-server/server.js
ssh ubuntu@168.110.25.49 "sudo systemctl restart sidequest-server"
```
