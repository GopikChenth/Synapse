import {Easing, Interactive, interpolate, useCurrentFrame} from 'remotion';
import {Backdrop} from '../components/Backdrop';
import {SceneText} from '../components/SceneText';
import {body, c} from '../theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

const Lock = () => {
	const frame = useCurrentFrame();
	const locked = interpolate(frame, [28, 40], [0, 1], {
		...clamp,
		easing: Easing.bezier(0.5, 0, 0.2, 1.4),
	});
	return (
		<svg viewBox="-300 -300 600 600" style={{width: 600, height: 600, overflow: 'visible'}}>
			{[0, 1, 2].map((i) => {
				const t = ((frame - 40 - i * 18) / 54) % 1;
				return (
					<circle
						key={i}
						r={150 + Math.max(0, t) * 150}
						fill="none"
						stroke={c.emerald}
						strokeWidth={3}
						opacity={frame < 40 + i * 18 ? 0 : (1 - t) * 0.6}
					/>
				);
			})}
			<circle r={170} fill={c.container} stroke={c.outline} strokeWidth={3} />
			<g transform={`translate(0 ${interpolate(locked, [0, 1], [-34, 0])})`}>
				<path
					d="M -46 -10 L -46 -52 A 46 46 0 0 1 46 -52 L 46 -10"
					fill="none"
					stroke={locked > 0.98 ? c.emerald : c.primary}
					strokeWidth={16}
					strokeLinecap="round"
				/>
			</g>
			<rect x={-72} y={-14} width={144} height={110} rx={20} fill={locked > 0.98 ? c.emerald : c.primary} />
			<circle cy={30} r={14} fill={c.container} />
			<rect x={-5} y={30} width={10} height={32} rx={5} fill={c.container} />
		</svg>
	);
};

const Chip = ({label, delay}: {readonly label: string; readonly delay: number}) => {
	const frame = useCurrentFrame();
	return (
		<div
			style={{
				fontFamily: body,
				fontWeight: 500,
				fontSize: 34,
				color: c.onSurface,
				padding: '16px 30px',
				borderRadius: 999,
				background: c.containerHigh,
				border: `2px solid ${c.outline}`,
				display: 'flex',
				alignItems: 'center',
				gap: 16,
				whiteSpace: 'nowrap',
				opacity: interpolate(frame, [delay, delay + 12], [0, 1], clamp),
				translate: interpolate(frame, [delay, delay + 18], ['0px 24px', '0px 0px'], {
					...clamp,
					easing: Easing.bezier(0.16, 1, 0.3, 1),
				}),
			}}
		>
			<span style={{width: 14, height: 14, borderRadius: 7, background: c.emerald}} />
			{label}
		</div>
	);
};

const EncryptedInner = ({
	eyebrow,
	title,
	subtitle,
}: {
	readonly eyebrow: string;
	readonly title: string;
	readonly subtitle: string;
}) => (
	<>
		<Backdrop accent={c.emerald} />
		<SceneText eyebrow={eyebrow} title={title} subtitle={subtitle} accent={c.emerald} />
		<div style={{position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 40}}>
			<Lock />
			<div style={{display: 'flex', gap: 20}}>
				<Chip label="Mutual TLS" delay={50} />
				<Chip label="QUIC" delay={58} />
				<Chip label="Paired devices only" delay={66} />
			</div>
		</div>
	</>
);

export const Encrypted = Interactive.create({
	Component: EncryptedInner,
	layout: 'absolute-fill',
	schema: {
		...Interactive.backgroundSchema,
		eyebrow: {type: 'string', default: '', description: 'Eyebrow'},
		title: {type: 'string', default: '', description: 'Headline'},
		subtitle: {type: 'string', default: '', description: 'Supporting text'},
	},
});
