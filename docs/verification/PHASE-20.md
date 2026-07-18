# Phase 20 interface rehaul verification record

Status: automated beta checkpoint complete. Physical rendering and player input approval are required before promotion to `main` or creation of the `phase-20` tag.

Phase 20 replaces the baseline interface with Minecraft advancement styled windows, tabs, node frames, item icons, stone backgrounds, consistent utility and Studio panels, and one correctly ordered blur pass. It also replaces the old ability `Screen` with a hold to open radial HUD overlay that leaves the gameplay screen unset so movement and world ticking continue.

The supplied Phase 19 crash is fixed at both relevant boundaries. The Progression Ability page does not create mutation controls for an unowned ability, and a stale or invalid client action is rejected without allowing an exception to escape the input callback.

## Automated gate

| Check | Status | Evidence |
| --- | --- | --- |
| Clean build | pass | `./gradlew clean build verifySchemaArtifacts --no-daemon --stacktrace` completed with no failure. |
| Unit and property tests | pass | 380 tests completed with zero failures and zero errors. |
| Phase 20 regression tests | pass | Eight direction radial selection, center dead zone, invalid action containment, and Safe Retry behavior passed. |
| Architecture and schema | pass | Physical side boundaries passed and both generated schema artifacts match fresh generation byte for byte. |
| NeoForge GameTest | pass | All 2 required cumulative GameTests passed. |
| Dedicated server | pass | Dedicated server startup and forbidden error scan passed. |
| Physical client | pass | Headless client reached the title screen and passed the forbidden error scan. |
| Release JAR | pass | `progressiveskills-phase-20.jar`, 2102326 bytes, SHA 256 `b66f8a32faad5eb6d7bcb4b342b72f385687fc30ca32c29d31ac70bc88fd46d4`. Archive verification passed. |

## Focused player interface checklist

Use only `releases/phase-20/progressiveskills-phase-20.jar`. Back up the test world and remove every older ProgressiveSkills JAR before starting. Keep Menu Background Blur enabled for the first pass.

1. Join a cheats enabled test world. Run `/pskills network status` and confirm the client is active before opening any interface.
2. Press `P`. Confirm the world behind the interface is blurred while the advancement window, tab sprites, item icons, text, buttons, and tooltips remain sharp.
3. Open every Progression tab around the window. Confirm the selected tab is visually distinct, hovering a row shows its detail, paging works, searching works, and each page retains its existing action.
4. On the Abilities tab, select an ability the player does not own. Confirm Assign, Unassign, Toggle, and Activate are absent. Click every action that is present and confirm no crash or reported exception occurs.
5. Assign an owned active ability with `/pskills ability assign progressiveskills:second_wind 1`. Return to the Abilities tab and confirm the owned ability exposes only valid actions.
6. Press `K`. Confirm the Tree screen uses advancement root tabs, item framed nodes, prerequisite connectors, selected detail, cost, rank, and owned, available, locked, or suspended text.
7. Drag empty tree space in every direction and use the mouse wheel. Confirm panning is smooth and bounded. Change trees with tabs and the left and right arrow keys, pan each differently, then return and confirm each tree remembered its position.
8. Hover every visible tree node. Confirm the tooltip names the node, state, cost, currency, and description. Buy an available node, preview an owned node refund, and confirm only valid contextual buttons appear.
9. Hold Left Alt while walking forward, sprinting, jumping, and sneaking. Confirm the radial wheel appears only while the key is held and none of those movement inputs stop.
10. Move the mouse through all eight directions. Confirm equal radial slots, item icons, empty slot frames, selected slot highlight, center dead zone, ability name, type, charge count, cooldown, and readiness are legible.
11. Release Left Alt over an assigned slot. Confirm the wheel closes and changes the selected slot exactly once without activating the ability. Press `R` separately and confirm only that key activates the selected ability.
12. Open the wheel and release it in the center, over an empty slot, after opening another screen, after tabbing away from the game, and while disconnecting. Confirm the previous selection is preserved and no activation or duplicate selection occurs.
13. Open the command palette, HUD editor, Studio, Studio graph, and Studio curve preview. Confirm each uses the new Minecraft styled shell, keeps every control reachable, and returns to its parent screen correctly.
14. Repeat the interface pass with Menu Background Blur disabled, every GUI scale, a resized window, high contrast, compact layout, each text size, reduced motion, keyboard only navigation, and the narrator enabled.
15. Close and reopen the world. Confirm the Progression screen, Tree screen, selected ability HUD, wheel, commands, and saved progression still work. Send the latest log and a screenshot of any clipped, blurred, unreachable, misleading, or visually inconsistent state.

After this focused Phase 20 pass succeeds, resume the cumulative `/pskills check` flow from the Phase 6 point where testing previously stopped. Phase 20 does not replace the Phase 6 through Phase 19 gameplay mass check.
