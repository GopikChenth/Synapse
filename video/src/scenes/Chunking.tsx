import {Easing, Interactive, interpolate, interpolateColors, useCurrentFrame} from 'remotion';
import {Backdrop} from '../components/Backdrop';
import {SceneText} from '../components/SceneText';
import {body, c, display} from '../theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const BLOCKS = 10;
const CHANGED = [2, 5, 6];
const SIZE = 66;
const GAP = 12;
const ROW_GAP = 220;

const Row = ({label, y}: {readonly label: string; readonly y: number}) => (
	<div
		style={{
			position: 'absolute',
			top: y - 56,
			left: 0,
			fontFamily: body,
			fontWeight: 500,
			fontSize: 32,
			color: c.onSurfaceVariant,
		}}
	>
		{label}
	</div>
);

const Blocks = () => {
	const frame = useCurrentFrame();
	const sent = CHANGED.filter((_, i) => frame > 62 + i * 10).length;
	return (
		<div style={{position: 'relative', width: BLOCKS * (SIZE + GAP), height: 520}}>
			<Row label="report.docx · Laptop" y={70} />
			<Row label="report.docx · Phone" y={70 + ROW_GAP} />
			{Array.from({length: BLOCKS}).map((_, i) => {
				const ci = CHANGED.indexOf(i);
				const edit = ci >= 0 ? interpolate(frame, [24 + ci * 5, 34 + ci * 5], [0, 1], clamp) : 0;
				const fly = ci >= 0 ? interpolate(frame, [50 + ci * 10, 70 + ci * 10], [0, 1], {...clamp, easing: Easing.bezier(0.65, 0, 0.35, 1)}) : 0;
				const x = i * (SIZE + GAP);
				const base = {position: 'absolute' as const, left: x, width: SIZE, height: SIZE, borderRadius: 14};
				return (
					<div key={i}>
						{/* source block */}
						<div
							style={{
								...base,
								top: 70,
								background: interpolateColors(edit, [0, 1], [c.containerHigh, c.amber]),
								border: `2px solid ${c.outline}`,
							}}
						/>
						{/* destination block */}
						<div
							style={{
								...base,
								top: 70 + ROW_GAP,
								background: ci >= 0 && fly >= 1 ? c.emerald : c.containerHigh,
								border: `2px solid ${c.outline}`,
							}}
						/>
						{/* travelling copy */}
						{ci >= 0 && fly > 0 && fly < 1 ? (
							<div
								style={{
									...base,
									top: 70 + fly * ROW_GAP,
									background: c.amber,
									boxShadow: `0 0 30px ${c.amber}`,
								}}
							/>
						) : null}
					</div>
				);
			})}
			<div
				style={{
					position: 'absolute',
					top: 70 + ROW_GAP + SIZE + 60,
					left: 0,
					display: 'flex',
					alignItems: 'baseline',
					gap: 20,
					opacity: interpolate(frame, [55, 70], [0, 1], clamp),
				}}
			>
				<span style={{fontFamily: display, fontWeight: 800, fontSize: 96, color: c.emerald}}>
					{sent}/{BLOCKS}
				</span>
				<span style={{fontFamily: body, fontSize: 40, color: c.onSurfaceVariant}}>blocks sent</span>
			</div>
		</div>
	);
};

const ChunkingInner = ({
	eyebrow,
	title,
	subtitle,
}: {
	readonly eyebrow: string;
	readonly title: string;
	readonly subtitle: string;
}) => (
	<>
		<Backdrop accent={c.amber} />
		<SceneText eyebrow={eyebrow} title={title} subtitle={subtitle} accent={c.amber} />
		<Blocks />
	</>
);

export const Chunking = Interactive.create({
	Component: ChunkingInner,
	layout: 'absolute-fill',
	schema: {
		...Interactive.backgroundSchema,
		eyebrow: {type: 'string', default: '', description: 'Eyebrow'},
		title: {type: 'string', default: '', description: 'Headline'},
		subtitle: {type: 'string', default: '', description: 'Supporting text'},
	},
});
