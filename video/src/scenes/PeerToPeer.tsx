import {Easing, Interactive, interpolate, useCurrentFrame} from 'remotion';
import {Backdrop} from '../components/Backdrop';
import {DeviceGlyph, type DeviceKind} from '../components/DeviceIcon';
import {SceneText} from '../components/SceneText';
import {body, c} from '../theme';

const NODES: {kind: DeviceKind; label: string; x: number; y: number}[] = [
	{kind: 'phone', label: 'Phone', x: 430, y: 150},
	{kind: 'laptop', label: 'Laptop', x: 130, y: 640},
	{kind: 'desktop', label: 'Desktop', x: 730, y: 640},
];
const LINKS: [number, number][] = [
	[0, 1],
	[1, 2],
	[2, 0],
];

const Mesh = () => {
	const frame = useCurrentFrame();
	const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
	const noCloud = interpolate(frame, [70, 90], [0, 1], clamp);
	return (
		<svg viewBox="0 0 860 820" style={{position: 'relative', width: 860, height: 820, overflow: 'visible'}}>
			{LINKS.map(([a, b], i) => {
				const A = NODES[a];
				const B = NODES[b];
				const len = Math.hypot(B.x - A.x, B.y - A.y);
				const draw = interpolate(frame, [15 + i * 8, 45 + i * 8], [0, 1], {
					...clamp,
					easing: Easing.bezier(0.65, 0, 0.35, 1),
				});
				return (
					<g key={i}>
						<line
							x1={A.x}
							y1={A.y}
							x2={B.x}
							y2={B.y}
							stroke={c.primary}
							strokeOpacity={0.45}
							strokeWidth={4}
							strokeDasharray={len}
							strokeDashoffset={len * (1 - draw)}
						/>
						{[0, 0.5].map((offset) => {
							const t = (((frame - 45) / 40 + offset + i * 0.17) % 1 + 1) % 1;
							const forward = i % 2 === 0;
							const p = forward ? t : 1 - t;
							return (
								<circle
									key={offset}
									cx={A.x + (B.x - A.x) * p}
									cy={A.y + (B.y - A.y) * p}
									r={10}
									fill={c.emerald}
									opacity={frame > 45 ? Math.sin(t * Math.PI) : 0}
									style={{filter: `drop-shadow(0 0 12px ${c.emerald})`}}
								/>
							);
						})}
					</g>
				);
			})}
			{NODES.map((n, i) => {
				const pop = interpolate(frame, [i * 6, i * 6 + 22], [0, 1], {
					...clamp,
					easing: Easing.spring({damping: 14}),
				});
				return (
					<g key={n.label} transform={`translate(${n.x} ${n.y}) scale(${pop})`}>
						<circle r={96} fill={c.container} stroke={c.outline} strokeWidth={3} />
						<circle r={96} fill="none" stroke={c.emerald} strokeWidth={3} opacity={interpolate(frame, [45, 60], [0, 0.8], clamp)} />
						<DeviceGlyph kind={n.kind} />
						<text
							y={150}
							textAnchor="middle"
							fill={c.onSurfaceVariant}
							style={{fontFamily: body, fontSize: 34, fontWeight: 500}}
						>
							{n.label}
						</text>
					</g>
				);
			})}
			{/* crossed-out cloud in the middle of the mesh */}
			<g transform="translate(430 470)" opacity={noCloud}>
				<path
					d="M -60 20 a 34 34 0 0 1 8 -66 a 46 46 0 0 1 88 8 a 30 30 0 0 1 4 58 Z"
					fill="none"
					stroke={c.onSurfaceVariant}
					strokeWidth={5}
					strokeOpacity={0.7}
				/>
				<line
					x1={-70}
					y1={-60}
					x2={interpolate(noCloud, [0, 1], [-70, 70])}
					y2={interpolate(noCloud, [0, 1], [-60, 40])}
					stroke={c.amber}
					strokeWidth={7}
					strokeLinecap="round"
				/>
			</g>
		</svg>
	);
};

const PeerToPeerInner = ({
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
		<Mesh />
	</>
);

export const PeerToPeer = Interactive.create({
	Component: PeerToPeerInner,
	layout: 'absolute-fill',
	schema: {
		...Interactive.backgroundSchema,
		eyebrow: {type: 'string', default: '', description: 'Eyebrow'},
		title: {type: 'string', default: '', description: 'Headline'},
		subtitle: {type: 'string', default: '', description: 'Supporting text'},
	},
});
