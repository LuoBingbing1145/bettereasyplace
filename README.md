# BetterEasyPlace

English | [中文](README_ZH_CN.md)

A Fabric mod that improves Litematica's Easy Place functionality.

While Easy Place is active, Litematica blocks all right-click actions — including eating and using firework rockets (elytra boosting). This mod adds exceptions for those actions and more.

## Features

- **Allow Eating** — hold food and eat while Easy Place is active
- **Allow Firework** — use firework rockets while flying with elytra
- **Allow Shulker Box** — open placed shulker boxes and place shulker boxes normally while Easy Place is active
- **Blocking Blacklist** — blacklisted blocks will not be blocked by Easy Place (e.g. `minecraft:chest`)
- **Allow Liquid Placement** *[Experimental]* — auto-pick bucket and precisely place liquid source blocks from the schematic; prevents empty buckets from accidentally picking up schematic liquids
- **Allow Waterlogged Placement** *[Experimental]* — auto-handle waterlogged blocks (slabs, stairs, etc.) by placing water first then the block, or waterlogging existing blocks. Requires "Allow Liquid Placement" to also be enabled.
- Configurable via ModMenu or the in-game config GUI (default hotkey: `B+C`)
- Each feature toggle has its own configurable hotkey for quick toggling

## Supported Versions

| Branch  | Minecraft        |
|---------|------------------|
| 1.20.1  | 1.20.1 – 1.20.4  |
| 1.20.5  | 1.20.5 - 1.20.6  |
| 1.21    | 1.21 - 1.21.8    |
| 1.21.9  | 1.21.9 - 1.21.10 |
| 1.21.11 | 1.21.11          |

## Requirements

- Fabric Loader
- [Litematica](https://modrinth.com/mod/litematica)
- [MaLiLib](https://modrinth.com/mod/malilib)
- [ModMenu](https://modrinth.com/mod/modmenu) (optional, for config screen)

## License

MIT
