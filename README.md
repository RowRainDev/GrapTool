# GrapTool Plugin

**GrapTool** is a Minecraft plugin that allows players to "grapple" each other — temporarily disabling the movement of a target, along with optional effects such as glow, sound, and titles.

> ✨ Created by **RowRain** - [discord.gg/raincloud](https://discord.gg/raincloud)

---

## 📦 Features

- Toggleable grapple mode with `/grap on` / `/grap off`
- Locks movement of the target player
- Configurable distances, glow effect, god mode, and sounds
- Optional title/subtitle messages
- Admin reload command `/grap reload`
- Sound effects and action bar messages
- Team-based glow system
- Auto-cleans sessions when players disconnect

---

## 📜 Commands

| Command              | Permission         | Description                     |
|----------------------|--------------------|---------------------------------|
| `/grap on`           | `graptool-use`     | Enables grapple mode            |
| `/grap off`          | `graptool-use`     | Disables grapple mode           |
| `/grap reload`       | `graptool.admin`   | Reloads configuration file      |

---

## 🔧 Configuration (`config.yml`)

```yaml
distance:
  default: 3.0
  minimum: 1.0
  maximum: 10.0
  step: 0.5

grapple-sound: true
glow-effect: true

title:
  enabled: true
  main-title: "&4&l{grapper} &r&4Tarafından Graplendin."
  subtitle: "&c&lHaraket edemezsin."

god-mode: true

sounds:
  grapple-start: "ENTITY_ENDER_DRAGON_FLAP"
  grapple-update: "BLOCK_PISTON_EXTEND"
  volume: 0.3
  pitch: 1.6
