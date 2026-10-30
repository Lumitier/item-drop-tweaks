# Item Drop Tweaks

Client-side [Fabric](https://fabricmc.net/) mod for Minecraft **1.21.11**.
Makes dropped items look the way you want — size, spin, bobbing, pile
spread, brightness — and lets a pile finally show **how much is actually in
it**.

## Why

Vanilla draws at most **5 copies** of an item on the ground, and stops
growing the pile at **48 items**:

```java
if (count <=  1) return 1;
if (count <= 16) return 2;
if (count <= 32) return 3;
if (count <= 48) return 4;
return 5;
```

Past that point every pile looks identical. With an item-merging mod a
single drop easily carries a thousand items — and looks exactly like a pile
of 49.

This mod reads the real stack size and makes it visible, through the number
of copies, through size, or both.

## Features

- **Scale, self-illumination and shadow scaling**
- **Spin speed and bobbing** — slow them down, speed them up, or freeze
  them entirely
- **Pile spread** — stack the copies on top of each other or scatter them
- **Copies and size by stack size** on a logarithmic scale
- **Hide dropped items** entirely
- Purely client-side: works on any server, no gameplay changes, no network
  packets

Every default matches vanilla exactly, so a fresh install changes nothing
until you move a slider.

## Configuration

In game via **Mods → Item Drop Tweaks → ⚙** (requires Mod Menu).
Every setting applies instantly, no restart needed.

| Tab | Group | Options |
|---|---|---|
| General | — | Mod on/off |
| Item Drops | General Settings | Render dropped items, self-illumination |
| | Scale Settings | Scale, scale shadow |
| | Animation Settings | Spin speed, bob speed, bob height |
| | Pile Settings | Copy spread |
| | Stack Size Settings | Copies by stack size, max copies, scale by stack size, max scale, max stack size |

Available in **English** and **German** — the screen follows your game
language.

Settings live in `config/itemdroptweaks.json`. Invalid values are clamped
to valid ranges on load, and a broken file falls back to the defaults.

## A note on stack sizes

Without an item-merging mod, a dropped item never holds more than one full
stack, so vanilla's steps already cover the whole realistic range and the
cap at 48 only costs you 48–64.

The stack-size options come into their own with a merging mod such as
[Item Clumps](https://modrinth.com/mod/item-clumps) or Drop Stacker, which
put four-digit counts into a single entity.

Set **Max Stack Size** to the largest pile you actually expect — the scale
is logarithmic, and leaving it at a value far above your real counts
flattens the effect.

## Requirements

- Minecraft **1.21.11**
- **Fabric Loader 0.20.1** or newer
- [YetAnotherConfigLib](https://modrinth.com/mod/yacl)
- [Mod Menu](https://modrinth.com/mod/modmenu) *(optional — needed for the
  settings button)*

## License

[Apache License 2.0](LICENSE). The source is open so server admins can
verify the mod does nothing beyond rendering.
