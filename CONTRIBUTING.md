# Contributing

Thanks for improving HypixelClient. Small, focused pull requests are the easiest to review.

## Development setup

1. Install a Java 21 JDK.
2. Clone the repository.
3. Run `./gradlew build` (`./gradlew.bat build` on Windows).
4. Use `./gradlew runClient` for a local Fabric development client.

The checked-in wrapper selects the expected Gradle version. Do not commit files from `build/`,
`.gradle/`, `run/`, IDE metadata, local configuration, logs, access tokens, or server secrets.

## Pull requests

- Explain the user-visible behavior and why the change is needed.
- Keep Mixins narrow and move behavior into a feature, state, rendering, or integration class.
- Add comments for state-machine transitions or Minecraft behavior that is not obvious from code.
- Update the README, architecture guide, configuration reference, and changelog when applicable.
- Run the complete Gradle build before opening the pull request.
- Include screenshots for visible HUD or settings-screen changes.
- Never include credentials or unredacted `hypixelclient.json` contents in an issue, commit, or build log.

Bug reports should include the HypixelClient version, Minecraft version, Fabric Loader version, Fabric API
version, reproduction steps, and a redacted log excerpt.
