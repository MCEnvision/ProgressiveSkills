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
  },
  "skill_workbench": {
    "background": "minecraft:textures/gui/advancements/backgrounds/end.png",
    "max_point_balances": 3,
    "colors": {
      "meter_fill": "#78b84a",
      "point_text": "#ffd65c",
      "owned_node": "#5fa34a",
      "available_node": "#ffc55c",
      "locked_node": "#737373",
      "hovered_node": "#ffffff",
      "selected_node": "#80c8ff",
      "required_path": "#777777",
      "alternative_path": "#5d8fc7",
      "owned_path": "#63a84f"
    }
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

## Skills workbench

The Skills page uses a two panel workbench. The left dossier contains the synchronized selected skill level meter, up to three relevant tree currency balances, and the live local player preview. The right workspace contains a vertical skill selector rail, selected skill heading, fitted skill bound tree, and separate node detail card. A narrow window stacks the graph and detail card instead of allowing them to overlap.

`background` is a full texture resource id. The default is Minecraft's End advancement background. A missing or invalid replacement keeps the default. `max_point_balances` is clamped from one through five. It limits only the compact balance list and never changes the actual balances.

Each workbench color accepts `#RRGGBB` or `#AARRGGBB`. Six digit colors receive full opacity. An invalid value falls back only that color to its default. Node frames, icons, labels, and connector shapes remain present, so ownership and path meaning never rely on color alone.

The graph uses only synchronized skill scoped trees whose `bind` field matches the inspected skill. Node rows, columns, required prerequisites, alternative prerequisites, costs, currency, ownership, icons, display text, and descriptions remain authoritative. Required paths use the required path color. `requires_any` paths use the alternative path color and appear as alternative choice paths in the card. The client never invents mutually exclusive behavior when the definition does not provide it.

Hovering a skill or node changes the preview. Clicking pins local inspection. The Open tree action enters that exact full tree workspace when there is enough room for the contextual button. Compact layouts retain the normal Trees tab as the full mutation path. Buying, refunding, and respeccing never occur through the dashboard preview.

## Definition icons and text

Skills, trees, classes, abilities, and tree nodes continue to use their definition `display`, `description`, and `icon` fields. The root tree tab uses the tree definition icon. The Progression cards use the matching definition icon. This keeps server supplied pack identity visible while the theme file controls only shared navigation.

The dedicated Skills dashboard uses each skill definition icon as its vertical selector and its synchronized level as the badge. Its graph uses each bound tree node icon, and its point list uses the matching currency definition icon. The dedicated Classes dashboard uses each class definition icon as its selector and each class slot definition icon in the slot rail. Changing these definition icons changes the dashboards without a client code change.

Class slot capacity and class slot weight are gameplay data rather than theme data. Their displayed values always come from the synchronized server projection and cannot be replaced by a resource pack.

The ability wheel also uses each ability definition icon. It renders the item directly without an advancement frame or token background. The slot under the pointer receives a gold circular outline, and the synchronized selected slot uses a quieter blue outline. Empty slots show the built in neutral placeholder item and slot number. Resource packs can change assigned ability items through the existing definition icon field, but they cannot replace synchronized cooldown, charge, readiness, assignment, or selection state.

When no synchronized display exists for a referenced id, the UI removes its namespace, replaces underscores, slashes, periods, and hyphens with spaces, and title cases the result. For example, `progressiveskills:global_points` becomes `Global Points`.

## Resource reload

Apply the resource pack and use the normal Minecraft resource reload. Reopen the Progression or Tree screen after the reload. Invalid theme JSON, missing items, missing backgrounds, or invalid colors cannot block the screen. ProgressiveSkills logs structural theme errors and continues with defaults.

## Accessibility contract

Theme replacements must preserve the meaning of text and must not rely on color alone. Item icons are accompanied by labels, selected states use borders as well as color, long content wraps, keyboard focus remains available, and the narrator receives the selected entry detail.
