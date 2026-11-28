# Synapse showcase video

A 31-second feature video for Synapse, built with [Remotion](https://www.remotion.dev) (React).

| Setting | Value |
| :-- | :-- |
| Composition | `SynapseShowcase` |
| Size | 1920 × 1080 |
| Frame rate | 30 fps |
| Length | 936 frames (31.2 s) |
| Audio | None yet |

## Scenes

| # | Scene | Shows | File |
| :-- | :-- | :-- | :-- |
| 1 | Intro | Logo, wordmark, "Your files, on every device. No cloud." | [`Intro.tsx`](src/scenes/Intro.tsx) |
| 2 | Peer-to-peer | Phone, laptop and desktop syncing directly, crossed-out cloud | [`PeerToPeer.tsx`](src/scenes/PeerToPeer.tsx) |
| 3 | Encrypted | Lock closing; Mutual TLS, QUIC, paired devices only | [`Encrypted.tsx`](src/scenes/Encrypted.tsx) |
| 4 | Chunking | Only the edited blocks of a file are sent (3/10) | [`Chunking.tsx`](src/scenes/Chunking.tsx) |
| 5 | Conflicts | Two edits merge into one file; unclear changes go to Quarantine | [`Conflicts.tsx`](src/scenes/Conflicts.tsx) |
| 6 | Everywhere | Android, Desktop and Web mockups of the Folders screen | [`Everywhere.tsx`](src/scenes/Everywhere.tsx) |
| 7 | Outro | Logo, "Sync without the cloud.", platform list | [`Outro.tsx`](src/scenes/Outro.tsx) |

Every scene is also registered on its own under the **Scenes** folder in Studio, so you can preview or render one at a time.

## Project layout

```
video/
├── public/logo.png        App logo (copied from app/shared/.../drawable/logo.png)
├── src/
│   ├── Root.tsx           Registers the full video and each scene
│   ├── Showcase.tsx       Scene order, lengths and transitions
│   ├── theme.ts           Colors (Synapse Purple M3 dark + emerald/amber) and fonts (Outfit, Inter)
│   ├── components/        Shared pieces: backdrop glow, text reveal, scene text, device icons
│   └── scenes/            One file per scene
└── out/                   Render output (ignored by Git)
```

## Commands

Run these from the `video/` folder.

Install dependencies:

```console
npm i
```

Open Remotion Studio to preview and edit:

```console
npm run dev
```

Render the full video:

```console
npx remotion render SynapseShowcase out/synapse-showcase.mp4
```

Render a single scene, for example the intro:

```console
npx remotion render Intro out/intro.mp4
```

Save one frame as an image:

```console
npx remotion still SynapseShowcase out/frame.png --frame=240
```

## Editing

- **Text:** Scene headlines and supporting lines are props in [`Showcase.tsx`](src/Showcase.tsx). You can also edit them in Studio's properties panel.
- **Timing:** Each scene's `durationInFrames` is set in [`Showcase.tsx`](src/Showcase.tsx). Transitions overlap neighbouring scenes, so the total is shorter than the scene lengths added together. If you change a length, update `durationInFrames` on `SynapseShowcase` in [`Root.tsx`](src/Root.tsx).
- **Colors and fonts:** [`theme.ts`](src/theme.ts). The colors follow the app's design spec in [`design.md`](../design.md).
- **Logo:** Replace `public/logo.png`.

## License

Remotion is free for individuals and teams of up to 3. Larger companies need a [company license](https://www.remotion.dev/docs/license).
