import { WebPlugin } from '@capacitor/core';

import type {
  AnalyzeOptions,
  LocationCheckId,
  LocationCheckResult,
  LocationIntegrityResult,
  MockLocationDetectorCapabilities,
  MockLocationDetectorPlugin,
  MonitoringOptions,
  PluginVersionResult,
  RunCheckOptions,
} from './definitions';

const WEB_CHECKS: LocationCheckId[] = ['simulator'];

function webCapabilities(): MockLocationDetectorCapabilities {
  return {
    platform: 'web',
    availableChecks: WEB_CHECKS,
    supportsMonitoring: false,
    canOpenDeveloperSettings: false,
  };
}

function webCheck(id: LocationCheckId): LocationCheckResult {
  if (id === 'simulator') {
    return {
      id,
      name: 'Browser environment',
      platform: 'web',
      available: true,
      detected: false,
      message: 'Web runtimes cannot access native GPS spoofing signals.',
      metadata: { userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : 'unknown' },
    };
  }

  return {
    id,
    name: id,
    platform: 'web',
    available: false,
    detected: false,
    message: 'Check is not available on web.',
  };
}

function webAnalyze(options?: AnalyzeOptions): LocationIntegrityResult {
  const requested = options?.checks ?? WEB_CHECKS;
  const checks = requested.map((check) => webCheck(check));

  return {
    isSimulated: false,
    confidence: 'none',
    riskScore: 0,
    platform: 'web',
    checks,
    developerMode: {
      detected: false,
      canDetectDeveloperMode: false,
      checks: [],
    },
    locationSample: null,
    recommendation: 'Use a native iOS or Android build to evaluate GPS spoofing signals.',
  };
}

export class MockLocationDetectorWeb extends WebPlugin implements MockLocationDetectorPlugin {
  async getCapabilities(): Promise<MockLocationDetectorCapabilities> {
    return webCapabilities();
  }

  async analyze(options?: AnalyzeOptions): Promise<LocationIntegrityResult> {
    return webAnalyze(options);
  }

  async runCheck(options: RunCheckOptions): Promise<LocationCheckResult> {
    return webCheck(options.check);
  }

  async openDeveloperSettings(): Promise<void> {
    throw this.unimplemented('openDeveloperSettings is not available on web.');
  }

  async startMonitoring(options?: MonitoringOptions): Promise<void> {
    void options;
    throw this.unimplemented('startMonitoring is not available on web.');
  }

  async stopMonitoring(): Promise<void> {
    throw this.unimplemented('stopMonitoring is not available on web.');
  }

  async getPluginVersion(): Promise<PluginVersionResult> {
    return { version: 'web' };
  }
}
