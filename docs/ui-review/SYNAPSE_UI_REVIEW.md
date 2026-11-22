# Synapse — UI/UX Review

| | |
|---|---|
| **Date** | 22 November 2026 |
| **Build reviewed** | Desktop app 1.0.6, the packaged `Synapse.jar` from `packaging/cachyos` |
| **How** | Ran the real app on Linux at 1280×820 (plus the default 800×600) against a live Synapse Core engine, seeded with 3 folders, 2 devices, 2 conflicts and 1 pairing request. Every screen was clicked through and captured. Network calls were logged and text contrast was measured from the screenshots. |
| **Not covered** | The Android app (no emulator here) and the Kotlin source (not readable from this session). Mobile-specific findings are inferred from the shared code structure. |
| **Overall rating** | **4 / 10**. It *looks* like a 7 but *works* like a 3. |

Screenshots referenced below are in this folder (`docs/ui-review/`).

---

## 1. Verdict in one paragraph

The visual direction is good: a coherent dark Material 3 theme, a restrained purple accent, clear green/amber/red status colours, and a dashboard that reads well at a glance. It already looks like a real product. The problem is that **what the screens say often isn't true**:
- The conflict counter keeps going up (2 real conflicts showed as 4, 22, then 38).
- The share switches in Edit Folder show the wrong state.
- Devices and folders are reported as "Connected" or "Up to Date" when nothing is connected.
- Several numbers are hardcoded placeholders.

On top of that, the headline feature, **inspecting a conflict diff, crashes the app**, and one screen shows a raw Ktor exception to the user. For a sync tool, trust in the status display *is* the product, so fix accuracy and stability before any visual polish.

---

## 2. Scorecard

| Area | Score | Summary |
|---|---|---|
| Visual design and aesthetics | **7/10** | Cohesive dark M3, good palette and status colours, tidy cards. Let down by every button having the same emphasis, a low-res logo, and inconsistent page headers and margins. |
| Layout and information architecture | **5/10** | Sensible nav sections. But only the selected nav item has a label, Recent Changes uses different chrome, Folders and Devices have no page title, Add Device is a full-screen mobile sheet on desktop, and list rows stretch 1,100 px wide. |
| Data accuracy and trust | **3/10** | Conflict count accumulates, share toggles are wrong, status words are misleading, and the version, Listeners and Discovery values are placeholders. |
| Stability and error handling | **2/10** | Inspect Diff crashes and the window vanishes, Recent Changes shows a raw exception, and Settings says "Daemon is not running" while connected. |
| Core flow: conflict resolution | **3/10** | The diff can't be opened. There's no side-by-side or merge editor, no who/when context, and the "Ignore" button is broken. |
| Copy and terminology | **4/10** | Leftover Syncthing text, internal jargon ("Virtual Quarantine", "zero disk pollution", "Listeners", "Introducer") with no explanations, and folder IDs shown instead of names. |
| Accessibility (partial check) | **4/10** | Icon-only navigation, small grey captions below WCAG AA contrast, letter-by-letter wrapping. A screen reader wasn't tested. |
| Desktop fit and responsiveness | **5/10** | The default 800×600 window clips the dashboard. Dialogs are mobile-style full-screen sheets, and there's no width cap on content. |
| Performance and efficiency | **4/10** | About 8 API requests per second while idle, with one extra request per folder every cycle. |
| **Overall** | **4/10** | |

---

## 3. Top issues, in priority order

| # | Severity | Issue | Screen | Evidence |
|---|---|---|---|---|
| 1 | 🔴 Blocker | **"Inspect Diff" crashes the app**, and the window disappears while the process keeps running | Conflicts | `05-inspect-diff-crash.png`. Compose throws *"Vertically scrollable component was measured with an infinity maximum height constraints"*: a `LazyColumn` or `verticalScroll` nested inside another scrolling container in the conflict dialog. |
| 2 | 🔴 Blocker | **Edit Folder shows share switches as off for devices it *is* shared with.** Saving would silently unshare the folder. | Edit Folder | `07-edit-folder.png`: "obsidian" is shared with Pixel 8 (Devices screen: *Folders: obsidian*), but the Pixel 8 switch is off. The share switches in Add Device are all off too. |
| 3 | 🔴 Blocker | **Conflict counter accumulates on every poll** | Dashboard | 2 real conflicts. The dashboard showed **4**, then **22**, then **38** over about 2 minutes (`01-dashboard.png`, `02-pairing-request.png`). The Quarantine dialog correctly says *2 Conflicts*, so the two disagree. Likely `count += new` instead of `count = new`. |
| 4 | 🟠 Major | **A raw exception is shown to users** | Recent Changes | `09-recent-changes-error.png`: *"Expected response body of the type 'interface java.util.List…' … 400 Bad Request … ktor.io/docs/faq.html"*. The screen calls `GET /rest/events?since=0&limit=1000` (Syncthing long-poll), but Synapse Core serves `/rest/events` as a **WebSocket** only. |
| 5 | 🟠 Major | **False "Daemon is not running" error.** The device ID is stuck on "Loading…". | Settings | `13-settings-false-daemon-error.png`. It appears whenever the native client has no token (see §7). The dashboard simultaneously says *Connected*. |
| 6 | 🟠 Major | **Status labels don't reflect reality** | Dashboard, Folders, Devices | All folders say **"Up to Date"** (dashboard) or **"Active"** (Folders screen), two different words for the same thing, while every remote device is offline. Pixel 8 showed **"Connected"** in one run and **"Disconnected"** in another with nothing changed; the answer depends on which API the screen reads. |
| 7 | 🟠 Major | **Placeholder data shown as fact** | Dashboard | "Version **v2.1.1**" is a hardcoded fallback (the engine is 1.0.0). "Listeners 3/3" and "Discovery 3/5" come from a Syncthing-shaped model that Synapse Core never fills. "Local State (Total) **0 0 ~0 B**" has no labels. |
| 8 | 🟠 Major | **Polling storm**: about 245 requests per 30 s while idle | All | `/system/status` 40×, `/connections` 35×, `/rest/db/status` 30×, and `/folders/{id}/devices` once per folder every cycle (N+1). The engine already offers a WebSocket event stream; use it and poll rarely. On Android this drains battery. |
| 9 | 🟡 Minor | **The "Ignore" button wraps one letter per line** | Conflicts | `04-conflicts.png`: *Ig / n / or / e*. The button row doesn't fit the fixed dialog width. |
| 10 | 🟡 Minor | **The device ID is regrouped incorrectly** in the pairing dialog | Pairing | `02-pairing-request.png`: `7QXJ 2KD- LRMB V4A- …` instead of `7QXJ2KD-LRMBV4A-…`. The ID is split every 4 characters while keeping the dashes. Users compare these IDs visually to verify a device, so this is a security-relevant usability bug. |

---

## 4. Screen-by-screen

### Dashboard (`01-dashboard.png`)
**Works well**
- Two-column card layout with clear sections: Folders, This Device, Remote Devices.
- The "Connected" pill in the header.
- A red banner for conflicts with a "Review & Resolve →" call to action, which is exactly the right pattern.
- Coloured status words.

**Problems**
- **Repeated heading:** "This Device / **This Device**". The subtitle should be the device name.
- **Run-together label:** "Identification**d0a0844**" has no space, and the copy icon is 8 px.
- **Inconsistent units:** "0 B/s" on the charts vs "0 bps (0 B)" in the stats.
- **Every button looks the same.** Pause All, Rescan All, Add Folder, Pause All Devices and Add Device are all filled lavender pills. The primary action (Add Folder or Add Device) should be filled and the rest tonal or text buttons. "Pause All" is destructive-ish and shouldn't look like "Add".
- **Folder rows** show the folder **ID** ("obsidian"), not a label or path, and no size, file count or last-sync time.
- **Unbalanced layout:** a large dead area under the Folders card while the right column scrolls.
- **Empty charts:** when there's no traffic, show "No transfers yet" instead of flat lines.

### Pairing request (`02-pairing-request.png`)
**Works well:** it interrupts the user, which is right for a security decision, and offers clear options: Dismiss, Quick Accept & Pair, Configure & Add.

**Problems:**
- The device ID is regrouped incorrectly (issue #10).
- "Quick Accept & Pair" is the most prominent button. For a security prompt, the safe option should lead, along with a hint like *"Only accept if this ID matches the one shown on your other device."*
- "Dismiss" is styled red like a destructive action, and it's unclear whether it rejects the device or only hides the prompt.

### Add Device (`03-add-device.png`)
- On desktop it's a **full-screen sheet that hides the nav rail**, a mobile pattern stretched to 1,280 px with 1,200 px-wide text fields. Use a centred dialog capped at about 560 px on desktop.
- **The known address is lost.** The pending request had `192.168.1.42:22000`, but the form pre-fills `dynamic`.
- **No explanations** for **Introducer** and **Auto Accept**, which have real security implications. "Pause Device" uses a ⚠️ warning icon, which is semantically odd.
- **Save is an unlabeled ✓ icon** in the top-right corner. Use a labeled "Add device" button.
- Folder share switches all start off. That's fine for a new device, but see issue #2 for edits.

### Conflicts, "Virtual Quarantine" (`04-conflicts.png`, `05-inspect-diff-crash.png`)
This is Synapse's differentiator, and it's the weakest screen.
- **Inspect Diff crashes** (#1). Even when it works, the diff is hidden behind a secondary button.
- **No context:** who made each change, on which device, and when. "Keep Local" and "Keep Remote" are meaningless without that.
- **No merge option.** The engine supports custom merged content (`CUSTOM_MERGE`) but the UI offers no editor.
- **Unclear default:** "Keep Both" is highlighted as primary, but its result isn't explained (Where does the second copy go? Under what name?).
- **No undo** after resolving.
- **Jargon:** "Virtual Quarantine" and "zero disk pollution" are internal terms. Something like *"2 files were edited on two devices at once"* says it in user language.
- **Wasted space:** the dialog is narrow (about 545 px) while the window is 1,280 px.

### Folders (`06-folders.png`)
- **No page title.** Dashboard and Settings have one; Folders and Devices don't.
- **Rows carry too little.** Each row stretches the full width but shows only name, path and "Active". It's missing sync progress, size, file count, shared devices, last scan, and errors.
- **Status words don't match** the dashboard ("Active" vs "Up to Date").
- **Empty space.** With 3 folders, 70% of the screen is empty. Consider a table or denser rows with more columns on desktop.
- **No search, sort or filter.**

### Edit Folder (`07-edit-folder.png`)
- **Share switches show the wrong state** (#2).
- **The conflict policy isn't exposed** (Three-way auto-merge / Last write wins / Device authority / Manual). That's the setting that makes Synapse different, and it's missing from the UI.
- **No ignore-patterns editor**, though the engine has the endpoint.
- **Delete sits right next to Save Changes**, with similar weight. Move it to a separate "Danger zone" and require confirmation by typing the folder name.
- **Folder ID looks editable**; the disabled styling is too subtle.

### Devices (`08-devices.png`)
- **No page title, and the margins differ** from Folders (cards start at x = 121 vs 129, top 14 vs 16 px).
- **Always-visible delete icons:** a red trash can on every row invites misclicks. Move it into an overflow menu or the edit dialog.
- **Missing details:** device ID (short form), last seen, address, connection type, and transfer rate.
- **Pairing requests aren't shown here.** The only place they appear is a modal on the dashboard.

### Recent Changes (`09-recent-changes-error.png`)
- **Raw exception text** (#4).
- **Inconsistent chrome:** a top app bar with an ✕ "close" button inside a nav-rail destination; no other section has this.
- **The rail label overflows.** "Recent Changes" is wider than the rail and touches both edges; use "Activity" or "History".
- **Empty even when the call succeeds.** It said "No recent changes detected" after 34 files were indexed and 2 conflicts were created.

### Settings (`10-settings.png`, `11-…`, `13-…`)
**Works well:** the QR code with Copy ID, the Light/Dark/System segmented control, the theme picker, and section grouping.

**Problems:**
- **Leftover Syncthing settings that don't apply to Synapse Core:**
  - "Allow anonymous usage data to be sent to the *Syncthing developers*"
  - "Send crash reports to help improve *Syncthing*", which is **on by default**
  - "Open the official *Syncthing* Web GUI"
  - "GUI Username / Leave empty for no authentication"
  - "Raw JSON configuration of your *Syncthing* instance"
- **Switches that do nothing:** NAT Traversal, Relaying and Global Discovery suggest features the engine doesn't run yet.
- **Device-name field is blank** although the engine runs as "vm".
- **The device ID is a 64-character hex hash**, while Add Device expects the `XXXXXXX-XXXXXXX-…` format. Users can't pair by copying this ID into another device.
- **Raw JSON config editor** is exposed on the main settings page. It belongs behind an "Advanced" toggle.
- **"Save Settings"** sits in the page header with no indication of unsaved changes.

### Default window (`12-default-window-800x600.png`)
- The app opens at **800×600**, which clips the dashboard mid-card. Open at around 1,200×800 or remember the last size, and set a sensible minimum.

---

## 5. Design-system adherence (vs `design.md`)

| Rule in `design.md` | Followed? |
|---|---|
| Colours from `MaterialTheme.colorScheme`, status colours emerald/amber/crimson | ✅ Mostly |
| Typography tokens only | 🟡 Page titles (Dashboard, Settings) are much larger than the 22 sp `titleLarge` token, and screens without a title (Folders, Devices) break the pattern |
| 16/12/8/4 dp spacing grid | 🟡 Page margins vary between screens (14/16/24 px) |
| Lists as flat rows, no heavy card outlines | ❌ Folders and Devices use filled card rows; the dashboard uses flat rows |
| Cards 12 dp corners, dialogs 28 dp | ✅ |
| Buttons as capsules | ✅, but emphasis levels aren't defined (see Dashboard) |
| Nav drawer at 65% width | n/a on desktop (rail used instead) |

**Missing from the design system:**
- button emphasis rules (filled, tonal, outlined, text, destructive)
- empty, loading and error state patterns
- page-header pattern
- desktop dialog sizes
- content max-width
- data formatting rules (bytes, rates, relative time, device-ID grouping)

---

## 6. What the UI is missing

### Core experience
- **A working conflict resolver:** side-by-side and inline diff, with *who / which device / when* for each side, a merge editor (the engine supports `CUSTOM_MERGE`), a preview of the result, and undo.
- **Per-folder conflict policy** controls, Synapse's key feature.
- **Real sync progress:** per-folder percentage, files and bytes remaining, current file, ETA, and per-device completion.
- **A Folder detail page:** files, recent changes, ignore patterns, versioning, shared devices, and errors.
- **A Device detail page:** last seen, addresses, connection type, shared folders, and transfer rates.
- **Version history browser.** The engine versions files, but there's no UI to restore an old version.
- **An activity feed that works** (Recent Changes).

### Onboarding and pairing
- **First-run onboarding:** what Synapse is, naming your device, adding a first folder, pairing a second device.
- **Guided pairing:** show *my* ID and QR code next to the "add device" form, and visually confirm that the IDs match.
- **Pairing requests listed** on the Devices screen and as a badge, not only as a modal.

### States and feedback
- **Consistent empty, loading and error states**, including friendly messages, retry, and "copy details for support".
- **Toasts or snackbars** confirming actions (folder added, device paused, conflict resolved), with undo where possible.
- **Confirmation for destructive actions** (delete folder or device, Pause All).
- **Offline and engine-down banners** that are accurate.

### Platform and polish
- **Notifications** for conflicts, new pairing requests, and sync errors. Tray support exists, but nothing appeared in this test (tray unsupported here).
- **Keyboard shortcuts and focus order** on desktop; tooltips on icon-only buttons.
- **Remembered window size and position**, plus a minimum window size.
- **Localization:** strings appear hardcoded.
- **Accessibility pass:**
  - Content descriptions on every icon.
  - Captions at or above 4.5:1 contrast. Measured: "Total: 0 B" is **3.19:1** and "Download Rate" **4.19:1**, both below the minimum.
  - Visible labels on all nav items.
  - Scalable text.
- **A high-resolution logo:** the rail logo is a blurry bitmap of a small figure on black.

---

## 7. Found along the way: app-side security and wiring

These were found while running the app, and they matter for the engine security fix applied the same day (see `SYNAPSE_AUDIT.md` §0).

1. **The API token is hardcoded in the app**: `MANAGED_API_KEY = "synapse-managed-api-key-2025"` in `DesktopDaemonManager` (and in both API clients). The desktop app starts the engine with `--token synapse-managed-api-key-2025`. Anyone can read that string from the jar, so the token protects nothing until it's replaced with a **random per-install token** (for example, let the engine generate `<home>/api.token` and have the app read it).
2. **The native client doesn't use that token on fresh installs.** In `CoreDiModule`, `SynapseCoreClientImpl` gets its token from `preferencesHelper.getApiKey()`, which is empty until the user saves one. `SyncthingApiClientImpl` correctly uses the `ApiKeyProvider`.
   - Before the engine fix this didn't matter, because the engine ignored tokens.
   - **With the fix, fresh installs lose conflicts, pairing requests, version and Settings data** (verified: 145 unauthenticated `/api/v1/system/status` calls in 25 s).
   - **Fix** (one line in `CoreDiModule.kt`): build `SynapseCoreClientImpl` with `tokenProvider = { apiKeyProvider.getApiKey() ?: preferencesHelper.getApiKey() }`, using the same source as the other client.
   - Do this **before** shipping a rebuilt engine binary. The existing desktop install works because the engine binary it bundles predates the fix.

---

## 8. Recommended order of work

| Phase | Work | Effort |
|---|---|---|
| **1. Stop the bleeding** | Fix the diff crash (#1), share-switch state (#2), conflict counter (#3), Recent Changes on WebSocket with a friendly error (#4), the native client token (§7.2), and the Settings false error (#5) | 2–4 days |
| **2. Make status honest** | One status vocabulary (Up to date / Syncing x% / Out of sync / Paused / Offline / Error). Remove placeholders (version, Listeners, Discovery). Remove Syncthing-only settings. Correct device-ID formatting. | 2–3 days |
| **3. Cut the noise** | Event-driven updates via `/api/v1/events`; polls at 10–30 s as a fallback; batch per-folder data. | 2 days |
| **4. Make conflicts great** | Side-by-side diff, merge editor, who/when context, undo, conflict-policy picker | 1–2 weeks |
| **5. Desktop polish** | Button emphasis rules, page headers, desktop dialog sizes, content max-width, window size and minimum, nav labels and tooltips, contrast fixes, new logo | 1 week |
| **6. Fill the gaps** | Onboarding, folder and device detail pages, version history, notifications, localization | 2–4 weeks |

---

## Appendix A: Measurements

**API traffic while idle on the dashboard (30 s, 3 folders, 2 devices):**
```
 40  GET /api/v1/system/status
 35  GET /api/v1/connections
 30  GET /rest/db/status
 20  GET /api/v1/folders/photos/devices
 20  GET /api/v1/folders/obsidian/devices
 20  GET /api/v1/folders/documents/devices
 20  GET /api/v1/folders
 20  GET /api/v1/devices
 20  GET /api/v1/cluster/pending/devices
 10  GET /api/v1/conflicts
 10  GET /api/v1/cluster/pending/folders
245  total ≈ 8 requests/second
```

**Contrast (sampled from screenshots; WCAG AA needs 4.5:1 for text and 3:1 for icons):**

| Element | Foreground | Background | Ratio | Result |
|---|---|---|---|---|
| Card caption "Total: 0 B" | #7A767F | #2C2A30 | 3.19:1 | ❌ |
| "Download Rate" label | #87838C | #252328 | 4.19:1 | ❌ |
| Unselected nav icon | #A49FAA | #49454F | 3.61:1 | ✅ (icon) / ❌ if treated as text |
| Button text "Pause All" | #381E72 | #D0BCFF | 7.71:1 | ✅ |
| "Listeners" caption | #CAC4D0 | #252328 | 9.13:1 | ✅ |

**Crash trace (Inspect Diff):**
```
Exception in thread "AWT-EventQueue-0" java.lang.IllegalStateException:
Vertically scrollable component was measured with an infinity maximum height constraints, which is disallowed.
```

## Appendix B: Screenshot index

| File | Shows |
|---|---|
| `01-dashboard.png` | Dashboard with conflict banner (count already inflated) |
| `02-pairing-request.png` | Incoming pairing modal, with the mangled device ID |
| `03-add-device.png` | Add Device full-screen sheet |
| `04-conflicts.png` | Virtual Quarantine dialog, with the broken "Ignore" button |
| `05-inspect-diff-crash.png` | Crash dialog after clicking Inspect Diff |
| `06-folders.png` | Folders list |
| `07-edit-folder.png` | Edit Folder, with the wrong share-switch state |
| `08-devices.png` | Devices list |
| `09-recent-changes-error.png` | Raw exception on Recent Changes |
| `10-settings.png` | Settings: identity, general |
| `11-settings-syncthing-leftovers.png` | Settings: Syncthing-specific options |
| `12-default-window-800x600.png` | Default window size clipping the dashboard |
| `13-settings-false-daemon-error.png` | "Daemon is not running" while connected |
