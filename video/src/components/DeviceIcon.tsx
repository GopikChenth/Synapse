import {c} from '../theme';

export type DeviceKind = 'phone' | 'laptop' | 'desktop';

/** Simple line-art device glyph drawn centered on (0,0) inside an SVG. */
export const DeviceGlyph = ({kind, color = c.onSurface}: {readonly kind: DeviceKind; readonly color?: string}) => {
	const stroke = {fill: 'none', stroke: color, strokeWidth: 5, strokeLinejoin: 'round' as const};
	if (kind === 'phone') {
		return (
			<g>
				<rect x={-26} y={-44} width={52} height={88} rx={10} {...stroke} />
				<line x1={-8} y1={32} x2={8} y2={32} {...stroke} strokeLinecap="round" />
			</g>
		);
	}
	if (kind === 'laptop') {
		return (
			<g>
				<rect x={-40} y={-34} width={80} height={52} rx={6} {...stroke} />
				<path d="M -54 30 L 54 30 L 46 18 L -46 18 Z" {...stroke} />
			</g>
		);
	}
	return (
		<g>
			<rect x={-46} y={-40} width={92} height={60} rx={6} {...stroke} />
			<line x1={0} y1={20} x2={0} y2={36} {...stroke} />
			<line x1={-22} y1={38} x2={22} y2={38} {...stroke} strokeLinecap="round" />
		</g>
	);
};
