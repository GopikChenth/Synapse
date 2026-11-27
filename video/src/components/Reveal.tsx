import type React from 'react';
import {Easing, Interactive, interpolate, useCurrentFrame} from 'remotion';

const RevealInner = ({
	children,
	style,
}: {
	readonly children: React.ReactNode;
	readonly style?: React.CSSProperties;
}) => {
	const frame = useCurrentFrame();
	return (
		<div
			style={{
				margin: 0,
				...style,
				opacity: interpolate(frame, [0, 18], [0, 1], {
					extrapolateLeft: 'clamp',
					extrapolateRight: 'clamp',
				}),
				translate: interpolate(frame, [0, 24], ['0px 40px', '0px 0px'], {
					extrapolateLeft: 'clamp',
					extrapolateRight: 'clamp',
					easing: Easing.bezier(0.16, 1, 0.3, 1),
				}),
			}}
		>
			{children}
		</div>
	);
};

/** Text block that fades and rises in. Use `from` to stagger it. */
export const Reveal = Interactive.create({
	Component: RevealInner,
	layout: 'none',
	schema: {
		...Interactive.textSchema,
		...Interactive.childrenSchema,
	},
});
