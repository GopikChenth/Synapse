import {loadFont as loadOutfit} from '@remotion/google-fonts/Outfit';
import {loadFont as loadInter} from '@remotion/google-fonts/Inter';

export const display = loadOutfit('normal', {
	weights: ['500', '700', '800'],
	subsets: ['latin'],
}).fontFamily;

export const body = loadInter('normal', {
	weights: ['400', '500', '600'],
	subsets: ['latin'],
}).fontFamily;

// Synapse Purple (M3 dark, seed #6750A4) + brand status hues from design.md
export const c = {
	bg: '#0B0A10',
	surface: '#141218',
	container: '#211F26',
	containerHigh: '#2B2930',
	outline: '#49454F',
	onSurface: '#E6E0E9',
	onSurfaceVariant: '#CAC4D0',
	primary: '#D0BCFF',
	primaryContainer: '#4F378B',
	seed: '#8B5CF6',
	emerald: '#10B981',
	amber: '#F59E0B',
	cyan: '#00E5FF',
};
