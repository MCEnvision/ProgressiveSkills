# Phase 20 interface rehaul verification record

Status: automated beta checkpoint complete. Physical rendering and player input approval are required before promotion to `main` or creation of the `phase-20` tag.

Phase 20 Option B uses one responsive Progression workbench. Pressing `P` opens Main Menu. Main Menu, Skills, Classes, and Abilities are the only player pages. Skill trees, per skill progress, node details, purchases, refunds, panning, zooming, and recentering are contained in Skills. The separate Tree screen and `K` key mapping are removed.

The bundled starter pack now contains fifteen editable skills and fifteen bound trees. Every tree has at least ten functional nodes. Existing operator files are never overwritten by starter installation.

The ability wheel remains a hold to open radial HUD overlay. It leaves the gameplay screen unset so movement and world ticking continue.

## Automated gate

| Check | Status | Evidence |
| --- | --- | --- |
| Clean build | pass | `./gradlew clean build verifySchemaArtifacts --no-daemon --console=plain` completed successfully. |
| Unit and property tests | pass | All 399 tests passed with zero failures and zero errors. |
| Phase 20 focused tests | pass | Four page navigation, graph zoom anchoring, zoom bounds, authoritative next level curve progress, starter skill count, tree binding, ten node minimum, functional grants, and nonoverwriting installation passed. |
| Architecture and schema | pass | Physical side boundaries and generated schema artifacts passed. |
| NeoForge GameTest | pass | All two required GameTests passed. |
| Dedicated server | pass | Dedicated server startup and forbidden error scan passed. |
| Physical client | pass | Headless client title screen startup and forbidden error scan passed. |
| Release JAR | pass | `progressiveskills-phase-20.jar`, 2,167,908 bytes. SHA 256 `816cffcebd3889fa1a25bff116c616e85c78f216f99bc9c47a67e4bc137681ee`. |

## Focused player interface checklist

Use only `releases/phase-20/progressiveskills-phase-20.jar`. Back up the test world and remove every older ProgressiveSkills JAR before starting. Keep Menu Background Blur enabled for the first pass.

1. Join a cheats enabled test world. Run `/pskills network status` and confirm the client reports an active Protocol 7 session before opening any interface.
2. Press `P`. Confirm Main Menu opens. Confirm the world behind it is blurred while the window, panels, text, icons, buttons, and tooltips remain sharp.
3. Confirm only Main Menu, Skills, Classes, and Abilities appear around the window. Confirm no Trees, Claims, Guide, Compare, Tests, Sync, or Studio player tab appears.
4. Confirm Main Menu has large entry cards for Skills, Classes, and Abilities plus a readable progression summary. Open each card and return through the Main Menu tab.
5. Press `K` in gameplay. Confirm it has no ProgressiveSkills binding and does not open a second tree interface.
6. Open Skills. Confirm the left dossier shows total progression, the visible skill count, no more than three relevant point balances, and the live player model without overlapping the selector or graph.
7. Confirm the starter selector contains Physique, Builder, Combat, Miner, Woodcutting, Farming, Fishing, Hunting, Archery, Defense, Agility, Endurance, Exploration, Alchemy, and Enchanting. Page through the rail when all fifteen do not fit.
8. Hover every skill icon. Confirm its header, level, progress bar, tree, and node card preview without changing the pinned selector border. Click a skill and confirm the selection remains after the pointer moves away.
9. For a skill below maximum level, confirm the header shows current XP toward the next authoritative requirement and the bar advances after XP is granted. At maximum level, confirm it reads Maximum Level. Confirm the old Active and Banked XP share bar is not duplicated in the left dossier.
10. Inspect every starter skill. Confirm each has a bound tree with at least ten visible nodes. Confirm icons, required paths, alternative paths, owned paths, owned nodes, available nodes, locked nodes, hover outline, and pinned outline remain distinct.
11. Hover a node with a long description near each screen edge. Confirm the configurable tooltip wraps, stays inside the screen, uses its backing panel, formats color codes, and displays a readable currency such as Global Points.
12. Click a node. Confirm its separate detail card shows name, state, cost, currency icon, balance, path type, and description without covering the graph.
13. Drag empty graph space in every direction. Confirm only the graph pans and the surrounding page does not move.
14. Put the pointer inside the graph and use the mouse wheel. Confirm zoom centers on the pointer and remains between the readable limits. Move the pointer outside the graph and confirm the wheel no longer zooms the tree.
15. Put the pointer inside the graph and press Space. Confirm the current tree recenters and fits. Give two different skills different pan and zoom values, switch between them, and confirm each view is remembered while the screen remains open.
16. Buy an available node. Confirm only the Buy button appears before purchase and that the synchronized owned state appears after the server accepts it.
17. Select an owned node. Use Preview Refund, inspect the affected state, then use Confirm Refund. Confirm the confirmation is tied to the current node and preview digest and disappears after state changes.
18. Change player state between a preview and confirmation. Confirm the stale confirmation fails safely, requests fresh synchronization, and never crashes the render thread.
19. Open Classes. Confirm slot capacity, selectors, hover preview, selection state, costs, requirements, and select or respec actions remain readable and server authoritative.
20. Open Abilities. Select an unowned ability and confirm Assign, Unassign, Toggle, and Activate are absent. Select owned active and toggle abilities and confirm only valid actions appear.
21. Hold Left Alt while walking, sprinting, jumping, and sneaking. Confirm movement continues and the radial wheel exists only while the key is held.
22. Move through every wheel direction. Confirm slots show only their item or neutral empty placeholder, the hovered circle and selected circle remain distinct, and labels do not cover cooldown or charge information.
23. Release Left Alt over an assigned slot. Confirm selection changes exactly once without activation. Press `R` separately and confirm only `R` activates the selected ability.
24. Repeat the Progression pass with Menu Background Blur disabled, every GUI scale, a resized window, high contrast, compact layout, each text size, reduced motion, keyboard navigation, and narration.
25. Close and reopen the world. Confirm Main Menu, all three progression pages, embedded trees, selected ability HUD, wheel, commands, and saved progression still work. Send the latest log plus screenshots of any clipped, blurred, unreachable, misleading, or visually inconsistent state.

After this Phase 20 pass succeeds, resume the cumulative `/pskills check` flow from the Phase 6 point where gameplay testing previously stopped. Phase 20 does not replace the Phase 6 through Phase 19 gameplay mass check.
