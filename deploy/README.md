# Deploying the demo

What is here is the exact configuration behind
[demo2.mariaftaieh.com](https://demo2.mariaftaieh.com), so the deployment is reproducible
rather than something that lives only on one machine.

| | |
|---|---|
| `demo.env.example` | copy to `.env` beside `docker-compose.yml` |
| `demo2.mariaftaieh.com.caddyfile` | the TLS terminator in front |
| `demo-reset.sh` | rebuild the demo from nothing |
| `demo-reset.{service,timer}` | run that nightly |

## Why a public demo is configured differently

The application is single user and has no authentication. That is correct for something
self-hosted and completely wrong on the open internet, so a public deployment turns on
`LEDGER_DEMO_READ_ONLY=true` and everything below follows from not trusting anyone who can
reach it:

- **Nothing can be changed.** Every state-changing request is refused with 403 by a servlet
  filter, so an endpoint added later is covered without anybody remembering to cover it.
- **Uploads are parsed and discarded.** Somebody will drop a real bank statement onto a
  public URL. The parser runs and reports honestly what it found — that is the demo — and
  then nothing is written, so their data never reaches the database or another visitor.
- **Rate limits per visitor.** 120 requests a minute for the API and 6 for uploads, since
  uploading runs a PDF parser over bytes a stranger chose. nginx reads the real address
  from `X-Forwarded-For`; without that every visitor would share one bucket.
- **Bounded containers.** Memory and CPU limits on all three, so nothing here can take the
  host down with it.
- **Loopback only.** Every published port binds to `127.0.0.1`. The database is not
  reachable from outside the host at all.
- **No error detail.** Stack traces and exception messages are never returned; the API's own
  handler returns curated messages instead.

## Setting it up

```bash
git clone https://github.com/Maria-Ftaieh/personal-finance-ledger.git /root/ledger
cd /root/ledger
cp deploy/demo.env.example .env
docker compose up -d --build

sudo cp deploy/demo2.mariaftaieh.com.caddyfile /etc/caddy/Caddyfile.d/
sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
sudo systemctl reload caddy

sudo cp deploy/demo-reset.{service,timer} /etc/systemd/system/
sudo systemctl enable --now demo-reset.timer
sudo systemctl start demo-reset.service   # seed it now rather than waiting for 04:00
```

If you run `caddy validate` as root before Caddy has created its own log file, the file
ends up owned by root and the service — which runs as `caddy` — cannot write to it, and
the reload fails. `chown caddy:caddy /var/log/caddy/<site>.log` if that happens.

## The reset

`demo-reset.sh` drops the database, replays every migration from nothing, extends the price
index from TCMB, reseeds, and locks read-only again — verifying at the end that writes are
actually refused rather than assuming it.

Nightly, for two reasons. The seeded data is generated relative to today, so a demo left
alone slides into the past until the year-on-year comparison has no current month to
compare — the one thing the application exists to show. And it is the backstop for
everything else: read-only mode *should* mean nothing changes, but a demo that rebuilds
itself does not have to depend on that.

Seeding writes through the ordinary API, so the script unlocks for exactly as long as that
takes. It does this through the shell environment rather than by editing `.env`, so a reset
that dies half way cannot leave the demo writable — the next `up` reads `.env` again.
