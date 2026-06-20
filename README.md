# Llama Club Plugin

A RuneLite plugin for the [Llama Club](https://llamaclub.co.uk) OSRS clan. Syncs player progress to the clan website and sends real-time event notifications.

## Features

### Sidepanel UI

A dedicated **Llama Club** sidebar panel for status, settings, manual sync, and a link to the website. The main RuneLite Configuration page only shows the plugin on/off toggle.

### Player Data Sync

- **Combat Achievements** — task completion, points, and tier progress
- **Collection Log** — obtained items and category progress
- **Achievement Diaries** — all regions and difficulty tiers
- **Quest Completion** — full quest state tracking
- **Boss Kill Counts** — synced alongside other player data
- **Player Metadata** — account type, combat level, total level, and skills

Syncs automatically on logout, or manually via **Sync now** in the side panel.

### Real-Time Event Notifications

- **Loot Drops** — valuable loot with GE values and item rarity
- **Pet Drops** — pet acquisitions with milestone tracking
- **Level Ups** — skill level achievements
- **Experience Gains** — batched XP notifications
- **Quest Completions**
- **Boss Kills** — KC milestones, fight times, and combat stats
- **Clue Scrolls** — completions with tier filtering
- **Achievement Diaries** — tier completions
- **Combat Achievements** — task completions
- **Collection Log** — new item acquisitions
- **Player Deaths** — death info, location, and lost items

## Configuration

All settings live in the **Llama Club** side panel:

- **Connection** — plugin token
- **Loot filters** — item whitelist and untradeable handling
- **Clue scroll filters** — per-tier toggles
- **Event notifications** — toggle each event type individually
- **Screenshot settings** — sidebar and private message visibility
- **Event codeword** — overlay text, timestamp, and colours

## Support

For bug reports and feature requests, open an issue on the [GitHub repository](https://github.com/HypeHypest/llamaclub-cc-plugin/issues).

## License

This project is licensed under the BSD 2-Clause License — see [LICENSE](LICENSE) for details.

## Version

Current version: **1.0.0**

## Acknowledgments

Portions of this plugin were inspired by or derived from [Dink](https://github.com/pajlads/DinkPlugin), [reval-cc](https://github.com/revalOSRS/reval-cc-plugin), [Wise Old Man](https://github.com/wise-old-man/wiseoldman-runelite-plugin), and [RuneLite](https://github.com/runelite/runelite). See [LICENSES/](LICENSES/) for third-party license details.
