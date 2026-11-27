# Synapse Design Specification — Material 3

Synapse uses **Material 3 only**: M3 components, the M3 color system, the
unmodified M3 type and shape scales, Material Symbols icons, and M3 motion
tokens. There is no Material 2 code or dependency in the shared UI.

Everything below lives in `app/shared/src/commonMain/.../core/designsystem/`.

---

## 1. Color

All UI colors come from `MaterialTheme.colorScheme` (plus the status roles
below). **No hex colors or alpha-tinted colors in UI code.**

### Themes (`Theme.kt`)

Every scheme is generated with the Material Color Utilities algorithm (the
engine behind Material Theme Builder and Android dynamic color), so all M3
roles — including `surfaceContainerLowest…Highest`, `surfaceDim/Bright`,
`inverse*` and `outlineVariant` — exist for light **and** dark.

| Key | Name in Settings | Seed | Variant |
| :-- | :-- | :-- | :-- |
| `Dynamic` | Dynamic color (wallpaper) | wallpaper | Android 12+ only |
| `Default` | Synapse Purple | `#6750A4` | Tonal spot (M3 baseline) |
| `MidnightGreen` | Midnight Green | `#52A435` | Fidelity |
| `DeepSpace` | Deep Space | `#00E5FF` | Fidelity |
| `TacticalHUD` | Tactical HUD | `#24FCDE` | Fidelity |

All themes support Light, Dark and System. To change a theme, change its seed
and regenerate with `docs/material3/genscheme.py` — don't hand-edit
individual roles.

### Common role usage

| Use | Role |
| :-- | :-- |
| Screen / scaffold background | `surface` (= `background`) |
| Top app bar | default (`surface`, `surfaceContainer` when scrolled) |
| Navigation bar / rail / drawer | component defaults |
| Filled card | default (`surfaceContainerHighest`); dashboard tiles use `surfaceContainer` |
| Dialog container | `surfaceContainerHigh` (AlertDialog default) |
| Primary text / secondary text | `onSurface` / `onSurfaceVariant` |
| Dividers, outlines on cards | `outlineVariant` |
| List leading icon | `secondaryContainer` / `onSecondaryContainer` |
| Destructive action text | `error` |

### Status colors (`Color.kt`, `components/CommonComponents.kt`)

M3 has no success/warning roles, so Synapse defines them as **M3 custom
colors**: the brand hues (emerald `#10B981`, amber `#F59E0B`) are harmonized
toward each theme's seed and expanded into the standard four tones.

| State | Color | Container / on-container |
| :-- | :-- | :-- |
| Connected / synced | `SynapseStatusColors.Connected` | `ConnectedContainer` / `OnConnectedContainer` |
| Paused | `SynapseStatusColors.Paused` | `PausedContainer` / `OnPausedContainer` |
| Disconnected / error | `SynapseStatusColors.Disconnected` (= `error`) | `errorContainer` / `onErrorContainer` |

Use `SyncStateBadge(SyncState.X)` or `StatusBadge(...)` for status labels.
The one intentional exception to theme colors is QR codes, which are always
black on white so they scan reliably.

---

## 2. Typography (`Typography.kt`)

The **default M3 type scale** (`Typography()`), unmodified. Never set
`fontSize` in UI code.

| Role | Used for |
| :-- | :-- |
| `headlineMedium` | Desktop page titles |
| `headlineSmall` | Dialog headlines (AlertDialog default) |
| `titleLarge` | Top app bar titles (default) |
| `titleMedium` | Card titles |
| `titleSmall` (in `primary`) | Section headers / list subheaders |
| `bodyLarge` | List-item headlines, control labels |
| `bodyMedium` | Supporting text, dialog body |
| `bodySmall` | Paths, timestamps, metadata |
| `labelLarge` | Buttons (default) |
| `labelSmall` | Badges |

---

## 3. Shape (`Shapes.kt`)

The **default M3 shape scale**: extraSmall 4dp, small 8dp, medium 12dp,
large 16dp, extraLarge 28dp. Use `MaterialTheme.shapes.*` or the component
default. Buttons, chips and badges are fully rounded (component defaults).

---

## 4. Layout

- 4dp grid. 16dp page margins on mobile, 24dp on desktop.
- Lists are M3 `ListItem`s (leading icon, headline, supporting text,
  trailing badge/menu) — not cards.
- Settings rows are `ListItem`s with a trailing `Switch`; the whole row toggles.
- Multi-select (share folder with devices, share device with folders) uses
  `Checkbox`.
- Desktop pages use `DesktopPage`: headline + actions, content capped at
  840–960dp wide and centered.
- Navigation: mobile uses a `NavigationBar` + `ModalNavigationDrawer`;
  desktop uses a `NavigationRail` with labels. Selected destinations show the
  filled icon, others the outlined icon.
- Extended FAB for the primary action on Folders / Devices; list content has
  88dp bottom padding so the last row clears it.

---

## 5. Components (`components/`)

| Component | Purpose |
| :-- | :-- |
| `SettingsGroup`, `SettingsSectionHeader`, `SettingsSwitchItem`, `SettingsTextField`, `SettingsThemeItem`, `SettingsThemeModeItem` | Settings screens (mobile + desktop) |
| `StatusBadge`, `SyncStateBadge`, `StatusDot`, `LeadingIcon` | Status and list decoration |
| `EmptyState`, `ErrorState` | Empty / error screens with an action |
| `DesktopPage` | Desktop page layout |
| `FullScreenOverlay` | M3 full-screen dialog container + motion |
| `ConfirmDialog`, `PairingRequestDialog`, `FolderOfferDialog` (`ui/SystemDialogs.kt`) | M3 basic dialogs with icon, text-button actions |

Dialogs: actions are text buttons (dismiss left, confirm right); destructive
confirms use `error` text color, never a filled red button.

---

## 6. Icons (`Icons.kt`)

Material Symbols, Outlined, weight 400, 24dp (`SynapseIcons.*`), generated
from `@material-symbols/svg-400`. Navigation destinations have a `*Filled`
variant for the selected state.

---

## 7. Motion (`Motion.kt`)

M3 easing and duration tokens (`SynapseMotion`):

- Full-screen dialogs/pages: enter = slide up 10% + fade, 400ms emphasized
  decelerate; exit = 200ms emphasized accelerate.
- FAB show/hide: scale + fade, 300ms / 200ms.
- Everything else uses component defaults (ripples, switch thumbs, segmented
  buttons, menus).
