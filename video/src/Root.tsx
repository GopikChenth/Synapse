import {Composition, Folder} from 'remotion';
import {Chunking} from './scenes/Chunking';
import {Conflicts} from './scenes/Conflicts';
import {Encrypted} from './scenes/Encrypted';
import {Everywhere} from './scenes/Everywhere';
import {Intro} from './scenes/Intro';
import {Outro} from './scenes/Outro';
import {PeerToPeer} from './scenes/PeerToPeer';
import {Showcase} from './Showcase';

// Total = 34.5s of scenes − (15 + 18 + 18 + 18 + 15 + 15) transition frames = 1035 − 99 = 936 frames
export const RemotionRoot: React.FC = () => {
	return (
		<>
			<Composition
				id="SynapseShowcase"
				component={Showcase}
				durationInFrames={936}
				fps={30}
				width={1920}
				height={1080}
			/>
			<Folder name="Scenes">
				<Composition
					id="Intro"
					component={Intro}
					durationInFrames={120}
					fps={30}
					width={1920}
					height={1080}
					defaultProps={{style: {flexDirection: 'column', justifyContent: 'center', alignItems: 'center'}}}
				/>
				<Composition
					id="PeerToPeer"
					component={PeerToPeer}
					durationInFrames={165}
					fps={30}
					width={1920}
					height={1080}
					defaultProps={{
						eyebrow: 'Peer-to-peer',
						title: 'No cloud in between',
						subtitle: 'Folders sync directly between your devices, on your network or across the internet.',
						style: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: '0 140px'},
					}}
				/>
				<Composition
					id="Encrypted"
					component={Encrypted}
					durationInFrames={150}
					fps={30}
					width={1920}
					height={1080}
					defaultProps={{
						eyebrow: 'Private by design',
						title: 'Encrypted end to end',
						subtitle: 'Every connection is mutually authenticated. Only devices you pair can join.',
						style: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: '0 140px'},
					}}
				/>
				<Composition
					id="Chunking"
					component={Chunking}
					durationInFrames={165}
					fps={30}
					width={1920}
					height={1080}
					defaultProps={{
						eyebrow: 'Smart transfers',
						title: 'Only what changed',
						subtitle: 'Synapse splits files into blocks and sends just the ones you edited.',
						style: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: '0 140px'},
					}}
				/>
				<Composition
					id="Conflicts"
					component={Conflicts}
					durationInFrames={165}
					fps={30}
					width={1920}
					height={1080}
					defaultProps={{
						eyebrow: 'Conflict handling',
						title: 'Both edits survive',
						subtitle: 'A three-way merge combines changes made on two devices at once.',
						style: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: '0 140px'},
					}}
				/>
				<Composition
					id="Everywhere"
					component={Everywhere}
					durationInFrames={150}
					fps={30}
					width={1920}
					height={1080}
					defaultProps={{
						title: 'One app, every screen',
						subtitle: 'Material 3 on Android, Windows, Linux and the web.',
						style: {flexDirection: 'column', justifyContent: 'center', alignItems: 'center'},
					}}
				/>
				<Composition
					id="Outro"
					component={Outro}
					durationInFrames={120}
					fps={30}
					width={1920}
					height={1080}
					defaultProps={{
						tagline: 'Sync without the cloud.',
						platforms: 'Android  ·  Windows  ·  Linux  ·  Web',
						style: {flexDirection: 'column', justifyContent: 'center', alignItems: 'center'},
					}}
				/>
			</Folder>
		</>
	);
};
