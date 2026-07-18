# UI customization

Phase 20 Option B keeps gameplay authoritative while allowing resource packs to change client presentation. ProgressiveSkills ships an original advancement inspired shell and uses vanilla items as safe fallbacks.

## Progression theme file

Create this resource in a resource pack.

`assets/progressiveskills/ui/progression.json`

```json
{
  "tab_icons": {
    "skills": "minecraft:experience_bottle",
    "trees": "minecraft:oak_sapling",
    "classes": "minecraft:armor_stand",
    "abilities": "minecraft:blaze_powder",
    "claims": "minecraft:chest",
    "guide": "minecraft:knowledge_book",
    "compare": "minecraft:compass",
    "tests": "minecraft:writable_book",
    "sync": "minecraft:redstone",
    "studio": "minecraft:crafting_table"
  },
  "tree_tooltip": {
    "max_width": 220,
    "lines": [
      "&l&c{name}",
      "&c{state}",
      "Cost {cost} {currency}",
      "{description}"
    ]
  }
}
```

Each icon value must be a registered item id. An invalid item id affects only that entry and falls back to the built in icon. Missing keys inherit the defaults, so a resource pack may override only the pages it changes.

The four default tooltip lines produce a result such as:

```text
Momentum
Locked
Cost 2 Global Points
Carry either Discipline into faster movement.
```

The first line is bold red and the second is red because of their legacy formatting codes.

## Tooltip placeholders

| Placeholder | Value |
| --- | --- |
| `{name}` | The localized node display name. |
| `{state}` | The localized owned, available, suspended, or locked state. |
| `{cost}` | The node cost as an integer. |
| `{currency}` | The synchronized currency display name or a prettified stable id. |
| `{description}` | The localized node description. |
| `{tree}` | The localized tree display name. |
| `{node_id}` | The prettified stable node id. |

Use one JSON array entry per deliberate line. A `\n` inside an entry also starts a new line. Empty rendered lines are retained except for an empty description placeholder, which is omitted.

Supported legacy formatting codes are `&0` through `&9`, `&a` through `&f`, `&k`, `&l`, `&m`, `&n`, `&o`, and `&r`. Use `&&` for a literal ampersand. Localized Components remain canonical. Legacy formatting is a presentation shorthand for pack authors.

`max_width` is clamped to a safe client range. The renderer wraps longer text again when the current window is narrower and clamps the completed tooltip inside the screen.

## Definition icons and text

Skills, trees, classes, abilities, and tree nodes continue to use their definition `display`, `description`, and `icon` fields. The root tree tab uses the tree definition icon. The Progression cards use the matching definition icon. This keeps server supplied pack identity visible while the theme file controls only shared navigation.

The dedicated Skills dashboard uses each skill definition icon as its selector and its synchronized level as the badge. The dedicated Classes dashboard uses each class definition icon as its selector and each class slot definition icon in the slot rail. Changing these definition icons changes the dashboards without a client code change.

Class slot capacity and class slot weight are gameplay data rather than theme data. Their displayed values always come from the synchronized server projection and cannot be replaced by a resource pack.

When no synchronized display exists for a referenced id, the UI removes its namespace, replaces underscores, slashes, periods, and hyphens with spaces, and title cases the result. For example, `progressiveskills:global_points` becomes `Global Points`.

## Resource reload

Apply the resource pack and use the normal Minecraft resource reload. Reopen the Progression or Tree screen after the reload. Invalid theme JSON or missing items cannot block the screen. ProgressiveSkills logs the problem and continues with defaults.

## Accessibility contract

Theme replacements must preserve the meaning of text and must not rely on color alone. Item icons are accompanied by labels, selected states use borders as well as color, long content wraps, keyboard focus remains available, and the narrator receives the selected entry detail.
