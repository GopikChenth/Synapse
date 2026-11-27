import type React from 'react';
import {Easing, Interactive, interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {Backdrop} from '../components/Backdrop';
import {Reveal} from '../components/Reveal';
import {body, c, display} from '../theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

const ROWS: {name: string; status: 'Synced' | 'Syncing' | 'Paused'}[] = [
	{name: 'Photos', status: 'Synced'},
	{name: 'Documents', status: 'Syncing'},
	{name: 'Music', status: 'Synced'},
	{name: 'Projects', status: 'Paused'},
];
const statusColor = {Synced: c.emerald, Syncing: c.primary, Paused: c.amber};

/** Miniature Folders screen in the app's M3 style. */
const FolderList = ({scale}: {readonly scale: number}) => (
	<div style={{padding: 22 * scale, display: 'flex', flexDirection: 'column', gap: 14 * scale}}>
		<div style={{fontFamily: display, fontWeight: 700, fontSize: 34 * scale, color: c.onSurface, marginBottom: 6 * scale}}>
			Folders
		</div>
		{ROWS.map((r) => (
			<div key={r.name} style={{display: 'flex', alignItems: 'center', gap: 14 * scale}}>
				<div
					style={{
						width: 44 * scale,
						height: 44 * scale,
						borderRadius: 12 * scale,
						background: c.primaryContainer,
						flexShrink: 0,
					}}
				/>
				<div style={{flex: 1, fontFamily: body, fontSize: 24 * scale, color: c.onSurface}}>{r.name}</div>
				<div
					style={{
						fontFamily: body,
						fontWeight: 600,
						fontSize: 17 * scale,
						color: statusColor[r.status],
						background: `${statusColor[r.status]}22`,
						padding: `${5 * scale}px ${12 * scale}px`,
						borderRadius: 999,
					}}
				>
					{r.status}
				</div>
			</div>
		))}
	</div>
);

const Pop = ({delay, label, children}: {readonly delay: number; readonly label: string; readonly children: React.ReactNode}) => {
	const frame = useCurrentFrame();
	return (
		<div
			style={{
				display: 'flex',
				flexDirection: 'column',
				alignItems: 'center',
				gap: 28,
				opacity: interpolate(frame, [delay, delay + 12], [0, 1], clamp),
				translate: interpolate(frame, [delay, delay + 26], ['0px 80px', '0px 0px'], {
					...clamp,
					easing: Easing.bezier(0.16, 1, 0.3, 1),
				}),
			}}
		>
			{children}
			<div style={{fontFamily: body, fontWeight: 600, fontSize: 40, color: c.onSurface}}>{label}</div>
		</div>
	);
};

const frameStyle: React.CSSProperties = {
	background: c.surface,
	border: `3px solid ${c.outline}`,
	overflow: 'hidden',
	boxShadow: '0 30px 80px #00000088',
};

const Dots = () => (
	<div style={{display: 'flex', gap: 8, padding: '12px 16px', background: c.container}}>
		{['#FF5F57', '#FEBC2E', '#28C840'].map((col) => (
			<span key={col} style={{width: 14, height: 14, borderRadius: 7, background: col}} />
		))}
	</div>
);

const Devices = () => (
	<div style={{position: 'relative', display: 'flex', alignItems: 'flex-end', gap: 70, marginTop: 70}}>
		<Pop delay={14} label="Android">
			<div style={{...frameStyle, width: 290, height: 560, borderRadius: 44, paddingTop: 30}}>
				<FolderList scale={0.82} />
			</div>
		</Pop>
		<Pop delay={22} label="Desktop">
			<div style={{...frameStyle, width: 680, height: 440, borderRadius: 18, display: 'flex', flexDirection: 'column'}}>
				<Dots />
				<div style={{display: 'flex', flex: 1}}>
					<div style={{width: 90, background: c.container, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 22, paddingTop: 24}}>
						{[0, 1, 2, 3].map((i) => (
							<span key={i} style={{width: 40, height: 28, borderRadius: 14, background: i === 1 ? c.primaryContainer : c.containerHigh}} />
						))}
					</div>
					<div style={{flex: 1}}>
						<FolderList scale={1} />
					</div>
				</div>
			</div>
		</Pop>
		<Pop delay={30} label="Web">
			<div style={{...frameStyle, width: 440, height: 380, borderRadius: 18}}>
				<div style={{display: 'flex', alignItems: 'center', gap: 12, background: c.container, paddingRight: 16}}>
					<Dots />
					<div style={{flex: 1, height: 26, borderRadius: 13, background: c.containerHigh}} />
				</div>
				<FolderList scale={0.78} />
			</div>
		</Pop>
	</div>
);

const EverywhereInner = ({title, subtitle}: {readonly title: string; readonly subtitle: string}) => {
	const {fps} = useVideoConfig();
	return (
		<>
			<Backdrop accent={c.cyan} />
			<Reveal
				name="Headline"
				style={{fontFamily: display, fontWeight: 800, fontSize: 104, letterSpacing: -2, color: c.onSurface, lineHeight: 1}}
			>
				{title}
			</Reveal>
			<Reveal
				name="Supporting text"
				from={0.2 * fps}
				style={{fontFamily: body, fontSize: 44, color: c.onSurfaceVariant, marginTop: 24}}
			>
				{subtitle}
			</Reveal>
			<Devices />
		</>
	);
};

export const Everywhere = Interactive.create({
	Component: EverywhereInner,
	layout: 'absolute-fill',
	schema: {
		...Interactive.backgroundSchema,
		title: {type: 'string', default: '', description: 'Headline'},
		subtitle: {type: 'string', default: '', description: 'Supporting text'},
	},
});
