import {useVideoConfig} from 'remotion';
import {body, c, display} from '../theme';
import {Reveal} from './Reveal';

/** Eyebrow + headline + supporting line used on the left of feature scenes. */
export const SceneText = ({
	eyebrow,
	title,
	subtitle,
	accent = c.primary,
}: {
	readonly eyebrow: string;
	readonly title: string;
	readonly subtitle: string;
	readonly accent?: string;
}) => {
	const {fps} = useVideoConfig();
	return (
		<div style={{position: 'relative', width: 780, display: 'flex', flexDirection: 'column', gap: 28}}>
			<Reveal
				name="Eyebrow"
				style={{
					fontFamily: body,
					fontWeight: 600,
					fontSize: 30,
					letterSpacing: 6,
					textTransform: 'uppercase',
					color: accent,
				}}
			>
				{eyebrow}
			</Reveal>
			<Reveal
				name="Headline"
				from={0.15 * fps}
				style={{
					fontFamily: display,
					fontWeight: 800,
					fontSize: 104,
					lineHeight: 1.02,
					letterSpacing: -2,
					color: c.onSurface,
				}}
			>
				{title}
			</Reveal>
			<Reveal
				name="Supporting text"
				from={0.35 * fps}
				style={{
					fontFamily: body,
					fontSize: 44,
					lineHeight: 1.35,
					color: c.onSurfaceVariant,
				}}
			>
				{subtitle}
			</Reveal>
		</div>
	);
};
