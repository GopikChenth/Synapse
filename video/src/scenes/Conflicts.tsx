import {Easing, Interactive, interpolate, useCurrentFrame} from 'remotion';
import {Backdrop} from '../components/Backdrop';
import {SceneText} from '../components/SceneText';
import {body, c, display} from '../theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const W = 860;
const CARD_W = 400;
const CARD_H = 240;

const Card = ({
	title,
	lines,
	accent,
	style,
}: {
	readonly title: string;
	readonly lines: string[];
	readonly accent: string;
	readonly style: React.CSSProperties;
}) => (
	<div
		style={{
			position: 'absolute',
			width: CARD_W,
			minHeight: CARD_H,
			borderRadius: 28,
			background: c.container,
			border: `2px solid ${accent}`,
			padding: 32,
			boxSizing: 'border-box',
			display: 'flex',
			flexDirection: 'column',
			gap: 18,
			...style,
		}}
	>
		<div style={{fontFamily: display, fontWeight: 700, fontSize: 40, color: c.onSurface}}>{title}</div>
		{lines.map((l) => (
			<div key={l} style={{fontFamily: body, fontSize: 30, color: c.onSurfaceVariant, display: 'flex', gap: 14, alignItems: 'center'}}>
				<span style={{width: 12, height: 12, borderRadius: 6, background: accent}} />
				{l}
			</div>
		))}
	</div>
);

const Merge = () => {
	const frame = useCurrentFrame();
	const together = interpolate(frame, [30, 60], [0, 1], {...clamp, easing: Easing.bezier(0.65, 0, 0.35, 1)});
	const merged = interpolate(frame, [58, 72], [0, 1], clamp);
	const quarantine = interpolate(frame, [90, 105], [0, 1], clamp);
	const top = 60;
	return (
		<div style={{position: 'relative', width: W, height: 640}}>
			<Card
				title="Laptop"
				lines={['Edited intro']}
				accent={c.primary}
				style={{
					top: top + interpolate(together, [0, 1], [0, 60]),
					left: interpolate(together, [0, 1], [0, (W - CARD_W) / 2]),
					rotate: `${interpolate(together, [0, 1], [-4, 0])}deg`,
					opacity: 1 - merged,
				}}
			/>
			<Card
				title="Phone"
				lines={['Added summary']}
				accent={c.cyan}
				style={{
					top: top + 120 - interpolate(together, [0, 1], [0, 60]),
					left: interpolate(together, [0, 1], [W - CARD_W, (W - CARD_W) / 2]),
					rotate: `${interpolate(together, [0, 1], [4, 0])}deg`,
					opacity: 1 - merged,
				}}
			/>
			<Card
				title="notes.md"
				lines={['Edited intro', 'Added summary', 'Nothing lost']}
				accent={c.emerald}
				style={{
					top: top + 30,
					left: (W - CARD_W) / 2,
					height: CARD_H + 60,
					opacity: merged,
					scale: interpolate(merged, [0, 1], [0.9, 1], {easing: Easing.spring({damping: 12})}),
					boxShadow: `0 0 80px ${c.emerald}55`,
				}}
			/>
			<div
				style={{
					position: 'absolute',
					top: top + 30 + CARD_H + 60 + 50,
					left: 0,
					width: W,
					display: 'flex',
					justifyContent: 'center',
					opacity: quarantine,
					translate: `0px ${interpolate(quarantine, [0, 1], [20, 0])}px`,
				}}
			>
				<div
					style={{
						fontFamily: body,
						fontWeight: 500,
						fontSize: 34,
						color: c.amber,
						padding: '18px 32px',
						borderRadius: 999,
						background: `${c.amber}1F`,
						border: `2px solid ${c.amber}`,
					}}
				>
					Unclear changes wait in Quarantine
				</div>
			</div>
		</div>
	);
};

const ConflictsInner = ({
	eyebrow,
	title,
	subtitle,
}: {
	readonly eyebrow: string;
	readonly title: string;
	readonly subtitle: string;
}) => (
	<>
		<Backdrop />
		<SceneText eyebrow={eyebrow} title={title} subtitle={subtitle} />
		<Merge />
	</>
);

export const Conflicts = Interactive.create({
	Component: ConflictsInner,
	layout: 'absolute-fill',
	schema: {
		...Interactive.backgroundSchema,
		eyebrow: {type: 'string', default: '', description: 'Eyebrow'},
		title: {type: 'string', default: '', description: 'Headline'},
		subtitle: {type: 'string', default: '', description: 'Supporting text'},
	},
});
