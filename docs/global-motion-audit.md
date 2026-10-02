# Global Motion Audit

## Reference Review

InstallerX-Revived is GPL-3.0. This project uses it only as a behavioral reference; no source implementation, animation constants, or utility code is copied.

The reviewed public revision (`816ea541`, 2026-10-02) separates programmatic push/pop transitions from the predictive-back gesture path. Its public navigation-transition history emphasizes gesture-progress-driven movement, edge-aware transforms, and distinct commit/cancel settling. PR #648 documents a freeze caused by intercepting navigation back with a regular `BackHandler`, and switches back handling into the navigation event pipeline. The M3E settings screens use `LargeFlexibleTopAppBar` with `exitUntilCollapsedScrollBehavior`, keeping the large-to-small title morph coupled to the app-bar scroll state.

This reference was reviewed through public repository metadata, source structure, change history, and documentation. No Android device is attached in this environment, so the InstallerX binary and this app could not be interactively run here.

References:
- https://github.com/wxxsfxyzm/InstallerX-Revived
- https://github.com/wxxsfxyzm/InstallerX-Revived/pull/648
- https://github.com/wxxsfxyzm/InstallerX-Revived/commit/6642ca38d4f114aea96b7f376301cb0ca3cdcff1

## ColdFront Motion Map

| Interaction | Current problem/risk | Motion policy |
| --- | --- | --- |
| Home, Devices, Lighting, Settings tab changes | One generic route transition cannot distinguish sibling pages from a detail push | Small directional shared-axis movement; direction follows tab order; spatial and effects specs use the active `MotionScheme` |
| Scan/About detail push | Same shallow travel as top-level pages | Detail enters from farther along the horizontal axis while the parent shifts only slightly |
| Scan/About back | Pop motion was disabled when the predictive-back preference was off | Normal pop always uses the symmetric detail return; the preference only gates predictive gesture transforms |
| Predictive Back | Incoming page was static and outgoing page only scaled | Navigation Compose owns the gesture progress; the previous page reveals shallowly and the outgoing page follows the active edge with restrained scale |
| Bottom navigation | About removed the navigation surface immediately, changing the content viewport | Keep the global navigation surface present; animate its visibility for compact/rail layout changes and keep Settings selected while About is open |
| Large to small title | Page shell did not explicitly retain the top-app-bar state/behavior objects | Keep one `LargeFlexibleTopAppBar` title slot and its built-in position/type/height morph; remember the saveable app-bar state and behavior across recompositions |
| Dialogs, menus, switches, sliders | Material already owns motion; extra wrappers risk double feedback | Keep native Material motion; do not layer custom transitions |
| BLE values and sorted scan results | Frequent updates could continually retarget animations | Keep readings, slider drag values, and scan ordering immediate |
| Home connection / device empty state | Discrete content replacement can jump | Retain state-targeted `AnimatedContent`; its targets are coarse UI states, not high-frequency telemetry |

## Interruption and Lifecycle Rules

- All navigation stays in the existing `NavHost`; no custom `BackHandler` or parallel navigation state is introduced.
- Push, normal pop, and predictive pop remain separate Navigation Compose transition callbacks, so gesture cancellation and completion are resolved by the same back-stack transition owner.
- Normal pop motion is not coupled to the predictive-back preference. Turning off gesture preview must not turn off the completed back transition.
- Motion specs are selected by interaction scale from `MaterialTheme.motionScheme`; no per-page millisecond constants are introduced.
- Navigation content is keyed only by destination state. Device telemetry, profile refreshes, and scrolling do not participate in page-transition target identity.

## Verification

- Unit tests cover the pure route-layer transition classifier (root-to-root, root-to-secondary, secondary-to-root, secondary-to-secondary).
- The existing GitHub Actions workflow is the required compile, lint, and test gate.
- Manual swipe/scroll and rapid-navigation validation requires an Android device or emulator; none is connected in the current environment.
