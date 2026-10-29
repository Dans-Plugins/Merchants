# Merchants

## Description

Merchants is a Minecraft plugin that adds NPC shopkeepers who buy and sell goods for coin.

It is the trading half of [Medieval Economy](https://github.com/Dans-Plugins/Medieval-Economy). That plugin gives a server physical coins and a coinpurse; this one gives players somewhere to spend them, and gives a settlement a market that exists whether or not anyone is online to run a stall.

This plugin was proposed from a Kingdom: First Era player request on 2026-09-04 ("MPC's for buying and selling").

## Project Status

**Early implementation.** Operators can place, delete and list merchants with `/merchant` (see [COMMANDS.md](COMMANDS.md)). A merchant is a villager that stays where it is placed, is saved across restarts (in `plugins/Merchants/merchants.yml`), and cannot be damaged, struck into a witch or converted by a zombie. Stock lists, trading and `/merchant edit` are still to come. The scope of the first release is defined in [MVP.md](MVP.md).

## Planned Features

- Place a merchant in the world and give it a stock list
- Buy and sell against a Medieval Economy coinpurse
- Per-merchant pricing, stock limits and restocking
- Merchants that persist across restarts and cannot be killed by accident

## Installation

Not yet released. Once there is a release, installation will follow the usual DPC pattern: drop the jar in `plugins/` and restart. It will also be available through [Dan's Plugin Manager](https://github.com/Dans-Plugins/Dans-Plugin-Manager).

### Development builds

Every push to `main` that changes more than documentation republishes a rolling `dev` prerelease built from that commit (see [Releases](https://github.com/Dans-Plugins/Merchants/releases)). It is unreleased, unreviewed code; at the moment merchants can be placed but not traded with, as described under [Project Status](#project-status). To try it with Dan's Plugin Manager:

```
/dpm get merchants --experimental
```

Once a release has been published, `/dpm get merchants --stable` returns to it.

### Required Companion Plugins

- [Medieval Economy](https://github.com/Dans-Plugins/Medieval-Economy) — supplies the currency merchants trade in.

### Building from source

Building requires JDK 21 (the bytecode targets Java 17). Use the committed Gradle wrapper:

```bash
./gradlew clean build
```

This compiles the plugin, runs the unit tests and writes the jar to `build/libs/Merchants-<version>.jar`.

## Usage

### Documentation

- [MVP](MVP.md) – Scope of the first release, and its deliberate non-goals
- [Commands](COMMANDS.md) – Every command and permission

`USER_GUIDE.md`, `CONFIG.md` and `CHANGELOG.md` land with the first release, per [DOCUMENTATION_PRACTICES.md](https://github.com/Dans-Plugins/dpc-conventions/blob/main/docs/DOCUMENTATION_PRACTICES.md).

## Support

You can find the support Discord server [here](https://discord.gg/xXtuAQ2).

### Experiencing a bug?

Please fill out a bug report [here](https://github.com/Dans-Plugins/Merchants/issues/new).

## Contributing

This repository follows the standards in [dpc-conventions](https://github.com/Dans-Plugins/dpc-conventions). `CONTRIBUTING.md` has not been written yet; until it is, see [Building from source](#building-from-source).

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

See the [LICENSE](LICENSE) file for full details.
