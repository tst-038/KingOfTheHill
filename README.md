# KingOfTheHill

King of the Hill for [Paper](https://papermc.io), in a world of its own. On a schedule, one hill opens in the KOTH
world. Whoever stands on it alone for a few seconds takes it and earns points for as long as they hold it. Spend the
points in a shop (`/koth shop`) on items, saved items such as crate keys, or console commands such as a permission.

It is a plugin of its own that builds on [MiniGami](https://github.com/tst-038/MiniGami): kills, deaths, captures and points show up in
MiniGami's stats, leaderboards and placeholders under the game `koth`.

## Contents

- [Requirements and installation](#requirements-and-installation)
- [Setting it up](#setting-it-up)
- [config.yml](#configyml)
- [Schedule examples](#schedule-examples)
- [shop.yml](#shopyml)
- [Messages](#messages)
- [Commands](#commands)
- [Permissions](#permissions)
- [Placeholders](#placeholders)
- [Building](#building)

## Requirements and installation

| | |
|---|---|
| Server | Paper **26.3**, Java **25** |
| Required | MiniGami **1.6** or later, which KingOfTheHill loads after and uses for stats, saved items and to know who is in a match |
| Optional | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/), for `%koth_...%` |
| Optional | [WorldEdit](https://enginehub.org/worldedit) or [FastAsyncWorldEdit](https://www.spigotmc.org/resources/fastasyncworldedit.13932/), to set the hill and the colour box by command |

1. Put `MiniGami-<version>.jar` and `KingOfTheHill-<version>.jar` in `plugins/`.
2. Start the server. KingOfTheHill creates `plugins/KingOfTheHill/` with `config.yml`, `shop.yml` and `messages/en.yml`.
3. Set up the world and the hill (below), then run `/kotha reload`.

Players who are in a MiniGami match can't join KOTH, shop or receive purchases; the match comes first.

## Setting it up

1. Have a world folder for KOTH, for example `koth`, next to your other worlds. KingOfTheHill loads it if it isn't
   loaded already. It never generates a world: a name with no folder is reported as a problem.
2. In `config.yml`, set `world.name` and run `/kotha reload`. Problems are listed in chat and in the log. A file with
   problems isn't used, and the previous one stays in use.
3. In the KOTH world, select the hill with WorldEdit and run `/kotha setup point set`; likewise `/kotha setup colours set`
   for the blocks that change colour, if you want them. Stand where `/koth join` should put players and run
   `/kotha setup spawn`. Each command writes `config.yml`, keeping its comments, and reloads. A change that would give
   the file problems, such as a hill that is too big, isn't written. You can also set `point.region`,
   `point.colour-blocks` and `world.spawn` in the file by hand and run `/kotha reload`.
4. Try it with `/kotha start 5` (a five minute round, whatever the timetable and restrictions say), and `/kotha stop`.

## config.yml

| Section | What it sets |
|---|---|
| `world` | The `name` of the world, the `spawn` for `/koth join` (left out: the world's spawn), and `respawn-here`: players who die in the world come back at that spawn |
| `schedule` | When it runs, as `cron` or `cycle` (see below), and `warn-minutes`: chat warnings that long before a start |
| `point` | The hill's `region`, `capture-seconds` to take it, and optional `colour-blocks` that change colour on each capture |
| `scoring` | `points` for the holder every `every-seconds`, wherever they are in the world |
| `restrictions` | `min-online`: a window waits, with an announcement, while fewer players are online, and starts as soon as there are enough; one that ends first is skipped. A forced start ignores it |
| `sidebar` | The title and lines shown to players in the KOTH world, for when it runs, when it waits for players and when it doesn't. It goes through MiniGami's boards, so a match's sidebar comes first and players get their own board back on leaving the world |

A holder who leaves the world or the server drops the hill. The round that is running keeps the config it started
with, so a reload changes the next round.

## Schedule examples

Three times a week for an hour, in server time (`minute hour day-of-month month day-of-week`):

```yaml
schedule:
  type: cron
  when:
    - "0 20 * * 1"   # Mondays 20:00
    - "0 20 * * 3"   # Wednesdays 20:00
    - "0 18 * * 6"   # Saturdays 18:00
  length-minutes: 60
  warn-minutes: [60, 15, 2]
```

A line can have a start and an end of its own, as `{ start: "<line>", end: "<line>" }`: a window starts at each time
of the start line and ends at the first time of the end line after it, at most 7 days later. A plain line lasts
`length-minutes`. A window that runs past midnight needs the end line's own weekday:

```yaml
schedule:
  type: cron
  when:
    - { start: "0 20 * * 1-4", end: "0 21 * * 1-4" }   # Monday to Thursday 20:00-21:00
    - { start: "0 20 * * 5", end: "0 22 * * 5" }       # Friday 20:00-22:00
    - { start: "0 23 * * 6", end: "0 1 * * 0" }        # Saturday 23:00 to Sunday 1:00
    - "0 18 * * 3"                                     # Wednesday 18:00, length-minutes long
  length-minutes: 60
```

Around the clock, 15 minutes on and 5 minutes off, counted from midnight:

```yaml
schedule:
  type: cycle
  active-minutes: 15
  break-minutes: 5
  warn-minutes: [2]
```

Times are the server's local wall-clock time, like MiniGami's own cron schedules. When the clocks change for
daylight saving time, a warning can be given twice or skipped. To avoid that, run the server in a time zone without
daylight saving time, such as UTC.

## shop.yml

The shop is a chest menu. Each entry has a `slot`, a `cost` in points, an `icon`, and `rewards`, all of which are
given on purchase:

- `{ item: diamond, amount: 8 }`, an item;
- `{ saved: <name>, amount, name }`, an item saved in MiniGami with `/mga loot save <name>`, such as a crate key;
- `{ command: "...", name: "..." }`, run from the console, with `%player%` being the buyer.

The `icon` is either `{ item: <item>, name, lore }`, with the `name` required, or `{ saved: <name>, name, lore }`, an
item saved in MiniGami with `/mga loot save <name>`. A saved icon shows the item as saved; give a `name` or `lore` to
replace the saved ones. Give one of `item` and `saved`, not both. The shop adds the cost line, and the owned line, under
the item's own lore. If the saved item is deleted after the shop was read, the entry shows a barrier and a warning is
logged each time the shop is opened.

An entry with `owned-permission` can't be bought again by someone who has that permission. Points are taken first,
and given back if the rewards can't be delivered. Run `/kotha reload` after editing.

The `/kotha shop` commands edit the file for you, keeping its comments, and reload. `/kotha shop add <id> <cost>` sells
the item you hold: it is saved in MiniGami as `koth-<id>`, and the entry gets the first free slot, that item as its icon,
named as the item is, and the item, as many as you hold, as its reward. `/kotha shop command <id> <cost> <command...>`
sells a command instead, with the held item saved as the icon. Change the rest, such as `lore` or `owned-permission`, in
the file.

## Messages

Every text is in `plugins/KingOfTheHill/messages/en.yml`, in [MiniMessage](https://docs.advntr.dev/minimessage/) format.
Keys you remove fall back to the bundled text. Use `%prefix%` for the shared prefix.
In the announcements, `<join>...</join>` makes the text a link that sends whoever clicks it to the KOTH world at once
(they need `koth.play`), without the client asking them to confirm a command.

## Commands

For players, with `koth.play`:

| Command | |
|---|---|
| `/koth` | Whether KOTH is running, who holds the hill and how long is left, that it is waiting for players, or when it next starts |
| `/koth shop` | Open the shop |
| `/koth balance` | Your points |
| `/koth join` | Go to the KOTH world's spawn; not while in a MiniGami match |

For staff, with `koth.admin`:

| Command | |
|---|---|
| `/kotha reload` | Read `config.yml`, `shop.yml` and the messages again |
| `/kotha start [minutes]` | Start a round now, for the given length or the timetable's, whatever the restrictions say |
| `/kotha stop` | End the running round, or stop waiting for players in this window |
| `/kotha status` | State (running, waiting or idle), holder, who is taking the hill, next start, players online, and whether the config and shop are loaded |
| `/kotha points show <player>` | A player's points |
| `/kotha points give <player> <amount>` | Give points |
| `/kotha points take <player> <amount>` | Take points, down to zero at the lowest |
| `/kotha setup point set\|select\|show` | Set the hill to your WorldEdit selection, select it with WorldEdit, or outline it in particles for a few seconds |
| `/kotha setup colours set\|select\|show` | The same for the box of blocks that change colour |
| `/kotha setup spawn` | Set the spawn of `/koth join` to where you stand |
| `/kotha shop add <id> <cost>` | Sell the item you hold, saved in MiniGami as `koth-<id>` |
| `/kotha shop command <id> <cost> <command...>` | Sell a command, run from the console with `%player%` being the buyer; the item you hold is its icon and gives it its name |
| `/kotha shop cost <id> <cost>` | Change what an entry costs |
| `/kotha shop slot <id> <slot>` | Move an entry to another free slot of the menu |
| `/kotha shop remove <id>` | Remove an entry; its saved item stays, for `/mga loot delete koth-<id>` |
| `/kotha shop list` | List the entries of the shop in use |

For `points`, the player must have been on the server before; names are looked up from the server's user cache.
`setup` is done in the KOTH world, and `set` and `select` need WorldEdit or FastAsyncWorldEdit. An entry `id` is
lower-case letters, digits, `_` and `-`.

## Permissions

| Permission | Default | |
|---|---|---|
| `koth.play` | everyone | `/koth`, the shop, your points and going to the KOTH world |
| `koth.admin` | op | `/kotha`: reload, start and stop, giving and taking points, setting up, and editing the shop |

## Placeholders

With PlaceholderAPI installed, KingOfTheHill's own:

| Placeholder | |
|---|---|
| `%koth_active%` | `true` while a round runs, otherwise `false` |
| `%koth_holder%` | Name of the holder, empty if none |
| `%koth_waiting%` | `true` while a window is open but waiting for enough players, otherwise `false` |
| `%koth_time_left%` | Time left in the round, or in the window while it waits; empty otherwise |
| `%koth_next_start%` | When it next starts, empty if nothing is planned |
| `%koth_next_in%` | How long until then |
| `%koth_balance%` | The viewer's points to spend |

KOTH's stats are `points` (everything earned on the hill, spent or not), `kills`, `deaths` and `captures`. They live
in MiniGami, so use its placeholders with the game `koth`:

| Placeholder | |
|---|---|
| `%mg_stat_koth_<stat>%` | The viewer's own amount |
| `%mg_weekly_stat_koth_<stat>%` / `%mg_monthly_stat_koth_<stat>%` | The viewer's amount this week (from Monday) / this month |
| `%mg_top_koth_<stat>_<place>_name%` / `…_value%` | A place on the leaderboard |
| `%mg_weekly_top_koth_<stat>_<place>_name%` / `…_value%` | A place on the leaderboard of this week |
| `%mg_monthly_top_koth_<stat>_<place>_name%` / `…_value%` | A place on the leaderboard of this month |

For example `%mg_weekly_top_koth_captures_1_name%` is who took the hill most often this week.

## Building

KingOfTheHill compiles against MiniGami's api. Every MiniGami release is on GitHub Packages; reading it needs a
GitHub token with `read:packages`, in `~/.gradle/gradle.properties`:

```properties
GitHubPackagesUsername=<your GitHub name>
GitHubPackagesPassword=<the token>
```

To build against a MiniGami that isn't released yet, run `./gradlew publishToMavenLocal` in the MiniGami repo
first; a local copy wins. The MiniGami version is set in `gradle/libs.versions.toml`.

`./gradlew build` makes `build/libs/KingOfTheHill-<version>.jar`, and `./gradlew runServer` starts a test server
with that MiniGami and PlaceholderAPI.
