import {interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {c} from '../theme';

/** Slow drifting purple/emerald glow, shared by every scene. */
export const Backdrop = ({accent = c.seed}: {readonly accent?: string}) => {
	const frame = useCurrentFrame();
	const {durationInFrames} = useVideoConfig();
	const drift = interpolate(frame, [0, durationInFrames], [0, 6]);
	return (
		<div
			style={{
				position: 'absolute',
				inset: 0,
				background: `radial-gradient(circle at ${20 + drift}% ${28 - drift}%, ${accent}33 0%, transparent 45%),
          radial-gradient(circle at ${82 - drift}% ${74 + drift}%, ${c.emerald}22 0%, transparent 50%),
          linear-gradient(135deg, #070A13 0%, #0F1423 50%, #15132E 100%)`,
			}}
		/>
	);
};
