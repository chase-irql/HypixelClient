# Configuration and privacy

Strata stores settings in `config/strata.json` below the active Minecraft directory. The file is
created when settings are saved. If `config/enemyboxes.json` exists and the Strata file does not,
Strata imports it and writes a new `strata.json`; the legacy file is left untouched.

## Safety defaults

On a fresh install:

- Gameplay automation is disabled.
- Chat mention, server shutdown, beachball stop, and fishing stop alerts are disabled.
- The alert server URL and credentials are empty.
- Visual entity boxes and the FOV circle are enabled.

## Alert service

The in-game screen exposes alert toggles, but the endpoint must be set manually with
`alertServerUrl`. Strata derives two endpoints from that base:

- `POST /auth/session` authenticates the current Minecraft session.
- `POST /events` accepts a short-lived bearer token and the event payload.

During authentication, Strata sends the configured service the player's Minecraft name, UUID,
and a random server ID. The Minecraft access token is sent only to Mojang's session service as part
of the standard join flow; it is never included in the request to the configured alert server.
The returned bearer token and UUID are cached in `strata.json`.

Depending on which toggles are enabled, event payloads can include:

- Minecraft player name and UUID-derived authentication
- Chat sender, chat text, and whether a message was direct
- Dimension identifier
- Beachball bounce count and macro state
- Fishing state
- A forced-stop or shutdown reason

The config file may contain `alertServerSecret` and `alertAuthToken` in plain text. Treat it like a
credential file: do not commit it, paste it into issues, or share an unredacted copy.

## Other network access

When the shard tracker is enabled, Strata fetches Bazaar prices from
`https://api.hypixel.net/skyblock/bazaar`. The request is a public GET and does not include an API
key or Strata identifier.

Strata does not include telemetry, advertising, analytics, or an automatic update checker.
