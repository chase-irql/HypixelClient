# Architecture

HypixelClient is a client-only Fabric mod. It has no dedicated server component and does not add blocks,
items, or persistent world data. Fabric callbacks and focused Mixins feed Minecraft events into a
small set of static feature coordinators.

## Runtime flow

1. `HypixelClient` loads configuration and registers key, chat, render, network, and tick callbacks.
2. Each client tick advances enabled feature state machines such as targeting, Hideonleaf,
   beachball, and fishing.
3. Mixins expose narrowly scoped hooks that Fabric does not provide, including attack invocation,
   input replacement, packet observation, and world-render timing.
4. Renderers read current state and draw world boxes or HUD overlays. They do not own gameplay
   decisions.
5. `HypixelClientConfig` persists user choices. Transient locks, counters, and state-machine phases remain
   in memory.

## Source layout

| Package | Responsibility |
| --- | --- |
| `client` | Fabric entry point and top-level lifecycle wiring |
| `client.config` | JSON serialization and legacy config migration |
| `client.state` | Shared settings and transient runtime state |
| `client.combat` | CPS measurement, click scheduling, and attack dispatch |
| `client.feature.lockon` | Candidate selection, aim smoothing, and automated actions |
| `client.feature.hideonleaf` | Hideonleaf hunting and shard-session valuation |
| `client.feature.beachball` | Beachball state machine and movement decisions |
| `client.feature.fishing` | Fishing, packet diagnostics, and sea-creature combat |
| `client.feature.chat` | Mention and shutdown-message detection |
| `client.integration` | Opt-in event-service authentication and delivery |
| `client.render` | World and HUD rendering |
| `client.ui` | Settings screens and custom widgets |
| `client.mixin` | Accessors and injection points into Minecraft client behavior |

## State ownership

`HypixelClientState` is the canonical store for user-configurable values shared across features. Feature
classes may own private transient state when it is meaningful only to their state machine. A
feature that presses keys or changes movement must provide a reset path and call it when disabled,
disconnected, or interrupted.

`HypixelClientConfig.Data` mirrors only values intended to survive a restart. The runtime fields are
copied explicitly during load and save so adding a setting is a deliberate change. When adding a
new persistent option, update its default, the `Data` model, both copy directions, the UI, and
[the configuration reference](CONFIGURATION.md).

## Mixins

Mixin classes should stay thin: capture an event or expose an inaccessible method, then delegate
to a feature class. Avoid keeping feature state in a Mixin. All injected methods use the `hypixelclient$`
prefix to prevent name collisions.

The active Mixin list is in `src/client/resources/hypixelclient.client.mixins.json`. A missing or renamed
target fails startup because the configuration is marked `required` and uses `defaultRequire: 1`.

## External requests

`HideonleafShardTracker` performs an asynchronous, unauthenticated GET against Hypixel's public
Bazaar endpoint while the tracker is enabled. `BotEventClient` is separately gated behind an
enabled alert type and a user-supplied URL. See [Configuration and privacy](CONFIGURATION.md).
