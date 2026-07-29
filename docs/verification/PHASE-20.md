# Phase 20 interface rehaul verification record

Status: automated beta checkpoint complete. Physical rendering and player input approval are required before promotion to `main` or creation of the `phase-20` tag.

Phase 20 Option B replaces the baseline interface with larger responsive Minecraft advancement inspired workbenches, customizable navigation, a two panel Skills dossier and bound tree workspace, dedicated class selectors, dense icon cards for the remaining catalogs, a separate full tree graph and detail rail, item framed nodes, safe formatted tooltips, consistent utility and Studio panels, and one correctly ordered blur pass. It also replaces the old ability `Screen` with a hold to open radial HUD overlay that leaves the gameplay screen unset so movement and world ticking continue.

The supplied Phase 19 crash is fixed at both relevant boundaries. The Progression Ability page does not create mutation controls for an unowned ability, and a stale or invalid client action is rejected without allowing an exception to escape the input callback.

## Automated gate

| Check | Status | Evidence |
| --- | --- | --- |
| Clean build | pass | `./gradlew clean build verifySchemaArtifacts --no-daemon --console=plain` completed with no failure. |
| Unit and property tests | pass | 397 tests completed with zero failures and zero errors. |
| Phase 20 regression tests | pass | Eight direction radial selection, center dead zone, invalid action containment, Safe Retry behavior, compact and maximum frame bounds, dedicated selector region bounds and capacity, workbench panel separation, fitted node bounds, XP meter overflow safety, inspected node precedence, workbench color parsing, legacy formatting, ampersand escaping, reset formatting, and stable id prettifying passed. |
| Architecture and schema | pass | Physical side boundaries passed and both generated schema artifacts match fresh generation byte for byte. |
| NeoForge GameTest | pass | All 2 required cumulative GameTests passed. |
| Dedicated server | pass | Dedicated server startup and forbidden error scan passed. |
| Physical client | pass | Headless client reached the title screen and passed the forbidden error scan. |
| Release JAR | pass | `progressiveskills-phase-20.jar`, 2155993 bytes, SHA 256 `fffeeeee35cc5ff1d1cdd4d12b45fd7d0a9dc619c9e30bcd9e98b30161558c8e`. Archive verification passed. |

## Focused player interface checklist

Use only `releases/phase-20/progressiveskills-phase-20.jar`. Back up the test world and remove every older ProgressiveSkills JAR before starting. Keep Menu Background Blur enabled for the first pass.

1. Join a cheats enabled test world. Run `/pskills network status` and confirm the client is active before opening any interface.
2. Press `P`. Confirm the world behind the interface is blurred while the larger responsive workbench, tab sprites, item icons, text, buttons, and tooltips remain sharp.
3. Open every Progression tab around the window. Confirm Skills, Trees, Classes, Abilities, Claims, Guide, Compare, Tests, Sync, and Studio have an item icon, the selected tab is visually distinct, and every page remains reachable.
4. Open Skills. Confirm the default End advancement background remains behind the sharp workbench panels. Confirm the left dossier has the total and selected skill levels, Active and Banked XP meter, no more than three relevant unspent point balances with definition icons, and a large live player preview.
5. Confirm the right workspace has a vertical skill selector rail. Hover every skill icon and confirm the heading, dossier values, bound tree, and node card preview that skill without changing its selected border. Click an icon and confirm its selection and level badge remain stable after the pointer moves away. Page through a pack with more skills than the rail can display.
6. For a skill with a skill scoped tree `bind`, confirm the central graph uses the real node positions and icons. Confirm required paths, alternative choice paths, owned paths, owned nodes, available nodes, locked nodes, the major root diamond, hover outline, and pinned node outline remain distinct. Hover and click nodes. Confirm the separate card shows name, state, cost, currency icon, current balance, path type, and wrapped description without covering the graph. Use Open tree and confirm the exact bound tree opens. Inspect a skill without a bound tree and confirm the safe empty message appears.
7. Open Classes. Confirm the left rail contains the pack defined class slots and each used capacity badge. Select every slot and confirm the middle icon grid shows only classes assigned to that slot. Hover and keyboard focus the class icons and confirm the right detail rail previews the matching class without changing ownership.
8. Confirm each class detail shows selection state, slot weight, selection cost or free selection, requirement counts, and grant count. Select an eligible class, then preview and confirm its respec using the existing actions. Confirm slot capacity and the owned badge update only after the authoritative synchronized result.
9. Test a disabled, unaffordable, prerequisite blocked, and capacity blocked class. Confirm invalid selection controls are absent or the server rejects the request safely. Confirm no client-only preview changes the actual class selection. If the pack has more class slots or classes than one page can display, test both independent page controls.
10. In a test resource pack, change one skill icon, one class icon, and one class slot icon in their definitions. Reload and confirm all three selector icons change while class capacity, class weight, costs, and ownership remain authoritative. Restore the pack after this check.
11. On Trees, Abilities, and Guide, confirm entries appear as item icon cards. Hover each card and confirm the right detail rail follows the hovered entry. Select cards, search, page in both directions, resize the window, and confirm the layout changes column count without covering any required control.
12. On the Abilities tab, select an ability the player does not own. Confirm Assign, Unassign, Toggle, and Activate are absent. Click every action that is present and confirm no crash or reported exception occurs.
13. Assign an owned active ability with `/pskills ability assign progressiveskills:second_wind 1`. Return to the Abilities tab and confirm the owned ability exposes only valid actions.
14. Press `K`. Confirm the Tree screen is substantially larger, uses advancement root tabs, and has a clipped graph canvas on the left plus a separate selected node detail rail on the right.
15. Select the bottommost node and nodes near every canvas edge. Confirm node icons, rank text, connectors, selected details, cost, and status never overlap each other or cross into the detail rail.
16. Drag empty tree space in every direction. Use the mouse wheel over the pointer to zoom in and out. Press Space and confirm the tree recenters and fits safely. Confirm zoom remains between readable limits and panning remains bounded.
17. Change trees with tabs and the left and right arrow keys, give each tree a different pan and zoom, then return to each one and confirm both values were remembered.
18. Hover every visible tree node, especially Momentum. Confirm the tooltip has four default lines, bold red name, red state, `Cost 2 Global Points`, and the description. Confirm no raw `progressiveskills:global_points` appears when a readable definition or fallback is available.
19. Test the longest available localized node description at every GUI scale and near every screen edge. Confirm the tooltip wraps, moves to the other side of the pointer when necessary, stays within the screen, and uses a dark backing panel that separates it from the graph.
20. Copy the default `assets/progressiveskills/ui/progression.json` into a test resource pack. Change the Guide icon to `minecraft:diamond`, change the first tooltip line to `&l&a{name}`, change `selected_node` to `#ff55ff`, and set `max_point_balances` to `2`. Reload resources and reopen the screens. Confirm the Guide tab uses a diamond, the full tree node name is bold green, the Skills selected node uses magenta, and only two point balances appear. Restore the resource pack after this check.
21. Temporarily set one tab icon and the Skills background to invalid ids, set one workbench color to invalid text, and make one tooltip description very long. Reload resources and confirm the icon, background, and color use their local defaults while the tooltip remains bounded. Confirm the log contains no crash.
22. Buy an available node, preview an owned node refund, and confirm only valid contextual buttons appear. Verify the separate detail rail updates immediately after the synchronized state changes.
23. Hold Left Alt while walking forward, sprinting, jumping, and sneaking. Confirm the radial wheel appears only while the key is held and none of those movement inputs stop.
24. Move the mouse through all eight directions. Confirm each slot renders only its configured item or neutral empty placeholder with no advancement frame or token background. Confirm the pointed slot receives a gold circular outline, including an empty slot, while the synchronized selected slot keeps a quieter blue outline. Confirm slot numbers, ability name, type, charge count, cooldown, readiness, and the center dead zone remain legible.
25. Release Left Alt over an assigned slot. Confirm the wheel closes and changes the selected slot exactly once without activating the ability. Press `R` separately and confirm only that key activates the selected ability.
26. Open the wheel and release it in the center, over an empty slot, after opening another screen, after tabbing away from the game, and while disconnecting. Confirm the previous selection is preserved and no activation or duplicate selection occurs.
27. Open the command palette, HUD editor, Studio, Studio graph, and Studio curve preview. Confirm each uses the new Minecraft styled shell, keeps every control reachable, and returns to its parent screen correctly.
28. Repeat the interface pass with Menu Background Blur disabled, every GUI scale, a resized window, high contrast, compact layout, each text size, reduced motion, keyboard only navigation, and the narrator enabled.
29. Close and reopen the world. Confirm the Progression screen, Tree screen, selected ability HUD, wheel, commands, and saved progression still work. Send the latest log and a screenshot of any clipped, blurred, unreachable, misleading, or visually inconsistent state.

After this focused Phase 20 pass succeeds, resume the cumulative `/pskills check` flow from the Phase 6 point where testing previously stopped. Phase 20 does not replace the Phase 6 through Phase 19 gameplay mass check.
