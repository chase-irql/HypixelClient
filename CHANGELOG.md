# Changelog

All notable changes to Strata are documented here. Versions follow semantic versioning.

## [1.0.0] - 2026-08-18

### Added

- Public release under the Strata name
- Entity overlays, targeting filters, snaplines, and FOV visualization
- Aim, combat, CPS, and click-graph tools
- Hideonleaf hunting and shard-session tracking
- Beachball and fishing state machines
- Optional chat, shutdown, and forced-stop event forwarding
- In-game tabbed configuration screen
- Public documentation, contribution guidance, security policy, and CI build

### Changed

- Renamed packages, resources, configuration, and runtime identifiers from EnemyBoxes to Strata
- Added automatic migration from `enemyboxes.json` to `strata.json`
- Made all third-party event forwarding explicit opt-in and removed the bundled service URL

[1.0.0]: https://github.com/chase-irql/Strata/releases/tag/v1.0.0
