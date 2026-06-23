import { registerPlugin } from '@capacitor/core';

import type { MockLocationDetectorPlugin } from './definitions';

const MockLocationDetector = registerPlugin<MockLocationDetectorPlugin>('MockLocationDetector', {
  web: () => import('./web').then((m) => new m.MockLocationDetectorWeb()),
});

export * from './definitions';
export { MockLocationDetector };
