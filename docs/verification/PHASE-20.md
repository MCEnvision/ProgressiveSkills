# Phase 20 interface rehaul verification record

Status: automated beta checkpoint complete. Physical rendering and player input approval are required before promotion to `main` or creation of the `phase-20` tag.

Phase 20 Option B replaces the baseline interface with larger responsive Minecraft advancement inspired workbenches, customizable navigation, dense icon cards, a separate tree graph and detail rail, item framed nodes, safe formatted tooltips, consistent utility and Studio panels, and one correctly ordered blur pass. It also replaces the old ability `Screen` with a hold to open radial HUD overlay that leaves the gameplay screen unset so movement and world ticking continue.

The supplied Phase 19 crash is fixed at both relevant boundaries. The Progression Ability page does not create mutation controls for an unowned ability, and a stale or invalid client action is rejected without allowing an exception to escape the input callback.

## Automated gate

| Check | Status | Evidence |
| --- | --- | --- |
| Clean build | pass | `./gradlew clean build verifySchemaArtifacts --no-daemon --stacktrace` completed with no failure. |
| Unit and property tests | pass | 385 tests completed with zero failures and zero errors. |
| Phase 20 regression tests | pass | Eight direction radial selection, center dead zone, invalid action containment, Safe Retry behavior, compact and maximum frame bounds, legacy formatting, ampersand escaping, reset formatting, and stable id prettifying passed. |
| Architecture and schema | pass | Physical side boundaries passed and both generated schema artifacts match fresh generation byte for byte. |
| NeoForge GameTest | pass | All 2 required cumulative GameTests passed. |
| Dedicated server | pass | Dedicated server startup and forbidden error scan passed. |
| Physical client | pass | Headless client reached the title screen and passed the forbidden error scan. |
| Release JAR | pass | `progressiveskills-phase-20.jar`, 2118459 bytes, SHA 256 `7dc604555f35f9e4843805218a33040700d8628ef555a28d8aa97cd04cfcd823`. Archive verification passed. |

## Focused player interface checklist

Use only `releases/phase-20/progressiveskills-phase-20.jar`. Back up the test world and remove every older ProgressiveSkills JAR before starting. Keep Menu Background Blur enabled for the first pass.

1. Join a cheats enabled test world. Run `/pskills network status` and confirm the client is active before opening any interface.
2. Press `P`. Confirm the world behind the interface is blurred while the larger responsive workbench, tab sprites, item icons, text, buttons, and tooltips remain sharp.
3. Open every Progression tab around the window. Confirm Skills, Trees, Classes, Abilities, Claims, Guide, Compare, Tests, Sync, and Studio have an item icon, the selected tab is visually distinct, and every page remains reachable.
4. On Skills, Trees, Classes, Abilities, and Guide, confirm entries appear as item icon cards. Hover each card and confirm the right detail rail follows the hovered entry. Select cards, search, page in both directions, resize the window, and confirm the layout changes column count without covering any required control.
5. On the Abilities tab, select an ability the player does not own. Confirm Assign, Unassign, Toggle, and Activate are absent. Click every action that is present and confirm no crash or reported exception occurs.
6. Assign an owned active ability with `/pskills ability assign progressiveskills:second_wind 1`. Return to the Abilities tab and confirm the owned ability exposes only valid actions.
7. Press `K`. Confirm the Tree screen is substantially larger, uses advancement root tabs, and has a clipped graph canvas on the left plus a separate selected node detail rail on the right.
8. Select the bottommost node and nodes near every canvas edge. Confirm node icons, rank text, connectors, selected details, cost, and status never overlap each other or cross into the detail rail.
9. Drag empty tree space in every direction. Use the mouse wheel over the pointer to zoom in and out. Press Space and confirm the tree recenters and fits safely. Confirm zoom remains between readable limits and panning remains bounded.
10. Change trees with tabs and the left and right arrow keys, give each tree a different pan and zoom, then return to each one and confirm both values were remembered.
11. Hover every visible tree node, especially Momentum. Confirm the tooltip has four default lines, bold red name, red state, `Cost 2 Global Points`, and the description. Confirm no raw `progressiveskills:global_points` appears when a readable definition or fallback is available.
12. Test the longest available localized node description at every GUI scale and near every screen edge. Confirm the tooltip wraps, moves to the other side of the pointer when necessary, stays within the screen, and uses a dark backing panel that separates it from the graph.
13. Copy the default `assets/progressiveskills/ui/progression.json` into a test resource pack. Change the Guide icon to `minecraft:diamond`, change the first tooltip line to `&l&a{name}`, reload resources, and reopen the screens. Confirm the Guide tab uses a diamond and the node name is bold green. Restore the resource pack after this check.
14. Temporarily set one tab icon to an invalid item id and make one tooltip description very long. Reload resources and confirm the invalid icon falls back safely and the long tooltip remains bounded. Confirm the log contains no crash.
15. Buy an available node, preview an owned node refund, and confirm only valid contextual buttons appear. Verify the separate detail rail updates immediately after the synchronized state changes.
16. Hold Left Alt while walking forward, sprinting, jumping, and sneaking. Confirm the radial wheel appears only while the key is held and none of those movement inputs stop.
17. Move the mouse through all eight directions. Confirm equal radial slots, item icons, empty slot frames, selected slot highlight, center dead zone, ability name, type, charge count, cooldown, and readiness are legible.
18. Release Left Alt over an assigned slot. Confirm the wheel closes and changes the selected slot exactly once without activating the ability. Press `R` separately and confirm only that key activates the selected ability.
19. Open the wheel and release it in the center, over an empty slot, after opening another screen, after tabbing away from the game, and while disconnecting. Confirm the previous selection is preserved and no activation or duplicate selection occurs.
20. Open the command palette, HUD editor, Studio, Studio graph, and Studio curve preview. Confirm each uses the new Minecraft styled shell, keeps every control reachable, and returns to its parent screen correctly.
21. Repeat the interface pass with Menu Background Blur disabled, every GUI scale, a resized window, high contrast, compact layout, each text size, reduced motion, keyboard only navigation, and the narrator enabled.
22. Close and reopen the world. Confirm the Progression screen, Tree screen, selected ability HUD, wheel, commands, and saved progression still work. Send the latest log and a screenshot of any clipped, blurred, unreachable, misleading, or visually inconsistent state.

After this focused Phase 20 pass succeeds, resume the cumulative `/pskills check` flow from the Phase 6 point where testing previously stopped. Phase 20 does not replace the Phase 6 through Phase 19 gameplay mass check.
