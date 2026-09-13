# Prison Gems

Prison Gems is a Paper plugin for a Minehut prison mine. Players use `/prison <area>` to teleport to a configured prison area. Blocks mined inside an area award Gems, stored in the server scoreboard. When the number of non-air blocks falls below the configured percentage of the captured mine template, the mine restores itself.

## Requirements

- A Paper server running Minecraft 1.21 or newer
- Java 21 or newer for building

## Install on Minehut

1. Set the server type to **Paper** and choose a compatible server version.
2. Upload `PrisonGems-1.0.7.jar` to the server's `plugins` folder using Minehut's File Manager.
3. Start the server once, then stop it. This creates `plugins/PrisonGems/config.yml`.
4. Start the server and create a mine from inside the game using the commands below.

## Commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/prison <area>` | `prisongems.use` | Teleport to an area spawn. |
| `/prison list` | `prisongems.use` | List available areas. |
| `/prison status <area>` | `prisongems.use` | Show remaining mine integrity and reset state. |
| `/prison create <name> <width> <height> <depth> [block] [terraform]` | `prisongems.admin` | Create a player-centred area and set its spawn to your current location. Fill with one block or a percentage palette; `true` carves a natural-looking surface using the same generated blocks. |
| `/prison setspawn <area>` | `prisongems.admin` | Set an existing area's spawn to your current location. |
| `/prison capture <area>` | `prisongems.admin` | Save the area’s current blocks as its reset template. Run this after building the mine. |
| `/prison reload` | `prisongems.admin` | Reload configuration and areas. |

## Recommended setup sequence

1. Stand at the centre of the proposed mine and run `/prison create startermine <width> <height> <depth> [block] [terraform]`. Dimensions include the centre block; for even dimensions, the area extends one extra block toward negative X, Y, and Z. The optional block accepts `stone` or a comma-separated palette such as `20%andesite,40%stone,40%cobblestone`; percentage entries must total 100. Set terraform to `true` after a fill palette to carve broad, repeatable hills and valleys up to three blocks deep while preserving the same generated palette.
2. Build or adjust the mine. Players may place and remove blocks until the template is captured; these setup actions do not award Gems.
3. Run `/prison capture startermine` to save the mine's template and lock block placement.
4. Give players `prisongems.use`.
5. Test `/prison startermine`, mine enough blocks to drop below the `reset-threshold-percent`, and confirm the mine refills.

Snapshots are intentionally designed for small and medium prison mines. Avoid extremely large cuboids, because snapshotting and restoring every block of a large volume can cause lag and create large files.

## Configuration

`gems-per-block` is the fixed Gems reward for each mined block. `reset-threshold-percent: 40` means the mine begins restoring only when fewer than 40 percent of its originally non-air blocks remain. The reset is applied in batches each tick to reduce lag.
