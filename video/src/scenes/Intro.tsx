import {
	Easing,
	Img,
	Interactive,
	interpolate,
	staticFile,
	useCurrentFrame,
	useVideoConfig,
} from 'remotion';
import {Backdrop} from '../components/Backdrop';
import {Reveal} from '../components/Reveal';
import {body, c, display} from '../theme';

const LogoMark = () => {
	const frame = useCurrentFrame();
	return (
		<div
			style={{
				width: 260,
				height: 260,
				borderRadius: 64,
				background: c.surface,
				border: `2px solid ${c.outline}`,
				display: 'flex',
				alignItems: 'center',
				justifyContent: 'center',
				boxShadow: `0 0 ${interpolate(frame, [0, 40, 90], [0, 140, 90], {
					extrapolateRight: 'clamp',
				})}px ${c.seed}88`,
				scale: interpolate(frame, [0, 30], [0.6, 1], {
					extrapolateLeft: 'clamp',
					extrapolateRight: 'clamp',
					easing: Easing.spring({damping: 12}),
					output: 'perceptual-scale',
				}),
				opacity: interpolate(frame, [0, 12], [0, 1], {
					extrapolateRight: 'clamp',
				}),
				marginBottom: 56,
				position: 'relative',
			}}
		>
			<Img src={staticFile('logo.png')} style={{width: 200, height: 200, borderRadius: 48}} />
		</div>
	);
};

const IntroInner = () => {
	const {fps} = useVideoConfig();
	return (
		<>
			<Backdrop />
			<LogoMark />
			<Reveal
				name="Wordmark"
				from={0.4 * fps}
				style={{
					fontFamily: display,
					fontWeight: 800,
					fontSize: 180,
					letterSpacing: -4,
					color: c.onSurface,
					lineHeight: 1,
				}}
			>
				Synapse
			</Reveal>
			<Reveal
				name="Tagline"
				from={0.9 * fps}
				style={{
					fontFamily: body,
					fontSize: 52,
					color: c.onSurfaceVariant,
					marginTop: 32,
				}}
			>
				Your files, on every device. No cloud.
			</Reveal>
		</>
	);
};

export const Intro = Interactive.create({
	Component: IntroInner,
	layout: 'absolute-fill',
	schema: {...Interactive.backgroundSchema},
});
