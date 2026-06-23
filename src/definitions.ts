import type { PluginListenerHandle } from '@capacitor/core';

/**
 * Individual integrity checks exposed by the plugin.
 */
export type LocationCheckId =
  | 'system_mock_flag'
  | 'developer_options'
  | 'developer_mode_indicators'
  | 'mock_location_app'
  | 'adb_enabled'
  | 'mock_provider_settings'
  | 'location_anomaly'
  | 'motion_correlation'
  | 'simulator';

export type LocationIntegrityPlatform = 'ios' | 'android' | 'web';
export type LocationIntegrityConfidence = 'none' | 'low' | 'medium' | 'high';

export interface LocationCheckResult {
  id: LocationCheckId;
  name: string;
  platform: LocationIntegrityPlatform;
  available: boolean;
  detected: boolean;
  message?: string;
  metadata?: { [key: string]: unknown };
}

export interface DeveloperModeResult {
  detected: boolean;
  canDetectDeveloperMode: boolean;
  checks: LocationCheckResult[];
}

export interface LocationSample {
  latitude: number;
  longitude: number;
  accuracy?: number;
  altitude?: number;
  speed?: number;
  timestamp: number;
}

export interface LocationIntegrityResult {
  isSimulated: boolean;
  confidence: LocationIntegrityConfidence;
  riskScore: number;
  platform: LocationIntegrityPlatform;
  checks: LocationCheckResult[];
  developerMode: DeveloperModeResult;
  locationSample?: LocationSample | null;
  recommendation?: string;
}

export interface MockLocationDetectorCapabilities {
  platform: LocationIntegrityPlatform;
  availableChecks: LocationCheckId[];
  supportsMonitoring: boolean;
  canOpenDeveloperSettings: boolean;
}

export interface AnalyzeOptions {
  checks?: LocationCheckId[];
  requestLocationSample?: boolean;
  locationTimeoutMs?: number;
  minDetectedChecks?: number;
  additionalMockAppPackages?: string[];
  additionalMockAppUrlSchemes?: string[];
}

export interface RunCheckOptions extends AnalyzeOptions {
  check: LocationCheckId;
}

export interface MonitoringOptions extends AnalyzeOptions {
  intervalMs?: number;
}

export interface LocationIntegrityChangedEvent extends LocationIntegrityResult {
  reason: 'interval' | 'location_update' | 'manual';
}

export interface PluginVersionResult {
  version: string;
}

/**
 * Detect simulated GPS locations and developer tooling that commonly enables spoofing apps.
 *
 * This plugin combines multiple independent checks because no single OS flag is reliable against
 * tools such as PoKeep or iMyFone AnyTo. On iOS, Apple does not provide a public API to read the
 * Developer Mode toggle directly; the plugin uses App Store-safe heuristics instead.
 *
 * Apps cannot programmatically disable Developer Mode or mock location settings. Use
 * {@link MockLocationDetectorPlugin.openDeveloperSettings} to guide users to the relevant settings.
 */
export interface MockLocationDetectorPlugin {
  getCapabilities(): Promise<MockLocationDetectorCapabilities>;
  analyze(options?: AnalyzeOptions): Promise<LocationIntegrityResult>;
  runCheck(options: RunCheckOptions): Promise<LocationCheckResult>;
  openDeveloperSettings(): Promise<void>;
  startMonitoring(options?: MonitoringOptions): Promise<void>;
  stopMonitoring(): Promise<void>;
  addListener(
    eventName: 'locationIntegrityChanged',
    listenerFunc: (event: LocationIntegrityChangedEvent) => void,
  ): Promise<PluginListenerHandle>;
  getPluginVersion(): Promise<PluginVersionResult>;
}
