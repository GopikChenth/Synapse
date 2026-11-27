import {linearTiming, springTiming, TransitionSeries} from '@remotion/transitions';
import {fade} from '@remotion/transitions/fade';
import {slide} from '@remotion/transitions/slide';
import {useVideoConfig} from 'remotion';
import {Chunking} from './scenes/Chunking';
import {Conflicts} from './scenes/Conflicts';
import {Encrypted} from './scenes/Encrypted';
import {Everywhere} from './scenes/Everywhere';
import {Intro} from './scenes/Intro';
import {Outro} from './scenes/Outro';
import {PeerToPeer} from './scenes/PeerToPeer';

export const Showcase = () => {
	const {fps} = useVideoConfig();
	return (
		<TransitionSeries name="Scenes">
			<TransitionSeries.Sequence name="Intro" durationInFrames={4 * fps}>
				<Intro
					name="Intro"
					style={{flexDirection: 'column', justifyContent: 'center', alignItems: 'center'}}
				/>
			</TransitionSeries.Sequence>
			<TransitionSeries.Transition presentation={fade()} timing={linearTiming({durationInFrames: 15})} />
			<TransitionSeries.Sequence name="Peer-to-peer" durationInFrames={5.5 * fps}>
				<PeerToPeer
					name="Peer-to-peer"
					eyebrow="Peer-to-peer"
					title="No cloud in between"
					subtitle="Folders sync directly between your devices, on your network or across the internet."
					style={{flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: '0 140px'}}
				/>
			</TransitionSeries.Sequence>
			<TransitionSeries.Transition
				presentation={slide({direction: 'from-right'})}
				timing={springTiming({config: {damping: 200}, durationInFrames: 18})}
			/>
			<TransitionSeries.Sequence name="Encrypted" durationInFrames={5 * fps}>
				<Encrypted
					name="Encrypted"
					eyebrow="Private by design"
					title="Encrypted end to end"
					subtitle="Every connection is mutually authenticated. Only devices you pair can join."
					style={{flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: '0 140px'}}
				/>
			</TransitionSeries.Sequence>
			<TransitionSeries.Transition
				presentation={slide({direction: 'from-right'})}
				timing={springTiming({config: {damping: 200}, durationInFrames: 18})}
			/>
			<TransitionSeries.Sequence name="Chunking" durationInFrames={5.5 * fps}>
				<Chunking
					name="Chunking"
					eyebrow="Smart transfers"
					title="Only what changed"
					subtitle="Synapse splits files into blocks and sends just the ones you edited."
					style={{flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: '0 140px'}}
				/>
			</TransitionSeries.Sequence>
			<TransitionSeries.Transition
				presentation={slide({direction: 'from-right'})}
				timing={springTiming({config: {damping: 200}, durationInFrames: 18})}
			/>
			<TransitionSeries.Sequence name="Conflicts" durationInFrames={5.5 * fps}>
				<Conflicts
					name="Conflicts"
					eyebrow="Conflict handling"
					title="Both edits survive"
					subtitle="A three-way merge combines changes made on two devices at once."
					style={{flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: '0 140px'}}
				/>
			</TransitionSeries.Sequence>
			<TransitionSeries.Transition presentation={fade()} timing={linearTiming({durationInFrames: 15})} />
			<TransitionSeries.Sequence name="Everywhere" durationInFrames={5 * fps}>
				<Everywhere
					name="Everywhere"
					title="One app, every screen"
					subtitle="Material 3 on Android, Windows, Linux and the web."
					style={{flexDirection: 'column', justifyContent: 'center', alignItems: 'center'}}
				/>
			</TransitionSeries.Sequence>
			<TransitionSeries.Transition presentation={fade()} timing={linearTiming({durationInFrames: 15})} />
			<TransitionSeries.Sequence name="Outro" durationInFrames={4 * fps}>
				<Outro
					name="Outro"
					tagline="Sync without the cloud."
					platforms="Android  ·  Windows  ·  Linux  ·  Web"
					style={{flexDirection: 'column', justifyContent: 'center', alignItems: 'center'}}
				/>
			</TransitionSeries.Sequence>
		</TransitionSeries>
	);
};
