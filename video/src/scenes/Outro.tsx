import {Easing, Img, Interactive, interpolate, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
import {Backdrop} from '../components/Backdrop';
import {Reveal} from '../components/Reveal';
import {body, c, display} from '../theme';

const Mark = () => {
	const frame = useCurrentFrame();
	return (
		<div
			style={{
				position: 'relative',
				display: 'flex',
				alignItems: 'center',
				gap: 44,
				opacity: interpolate(frame, [0, 14], [0, 1], {extrapolateRight: 'clamp'}),
				scale: interpolate(frame, [0, 30], [0.85, 1], {
					extrapolateLeft: 'clamp',
					extrapolateRight: 'clamp',
					easing: Easing.spring({damping: 14}),
					output: 'perceptual-scale',
				}),
			}}
		>
			<div
				style={{
					width: 180,
					height: 180,
					borderRadius: 44,
					background: c.surface,
					border: `2px solid ${c.outline}`,
					display: 'flex',
					alignItems: 'center',
					justifyContent: 'center',
					boxShadow: `0 0 90px ${c.seed}77`,
				}}
			>
				<Img src={staticFile('logo.png')} style={{width: 140, height: 140, borderRadius: 34}} />
			</div>
			<div style={{fontFamily: display, fontWeight: 800, fontSize: 160, letterSpacing: -4, color: c.onSurface}}>Synapse</div>
		</div>
	);
};

const OutroInner = ({tagline, platforms}: {readonly tagline: string; readonly platforms: string}) => {
	const {fps} = useVideoConfig();
	return (
		<>
			<Backdrop />
			<Mark />
			<Reveal
				name="Tagline"
				from={0.5 * fps}
				style={{fontFamily: display, fontWeight: 500, fontSize: 64, color: c.primary, marginTop: 40}}
			>
				{tagline}
			</Reveal>
			<Reveal
				name="Platforms"
				from={0.9 * fps}
				style={{fontFamily: body, fontSize: 40, letterSpacing: 2, color: c.onSurfaceVariant, marginTop: 28}}
			>
				{platforms}
			</Reveal>
		</>
	);
};

export const Outro = Interactive.create({
	Component: OutroInner,
	layout: 'absolute-fill',
	schema: {
		...Interactive.backgroundSchema,
		tagline: {type: 'string', default: '', description: 'Tagline'},
		platforms: {type: 'string', default: '', description: 'Platforms line'},
	},
});
