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
  /**
   * How often to re-run checks while monitoring is active.
   *
   * Minimum 5000 ms. Defaults to 30000.
   *
   * @default 30000
   */
  intervalMs?: number;

  /**
   * When `true`, `locationIntegrityChanged` is emitted only when
   * `isSimulated`, `confidence`, `riskScore`, or triggered check IDs change.
   *
   * The first event after {@link MockLocationDetectorPlugin.startMonitoring} always fires.
   *
   * @default true
   */
  emitOnlyOnChange?: boolean;
}

/**
 * Payload emitted by {@link MockLocationDetectorPlugin.addListener} when monitoring detects
 * a new integrity snapshot.
 *
 * Register the listener before calling {@link MockLocationDetectorPlugin.startMonitoring}:
 *
 * @example
 * ```typescript
 * import { MockLocationDetector } from '@capgo/capacitor-mock-location-detector';
 *
 * const handle = await MockLocationDetector.addListener('locationIntegrityChanged', (event) => {
 *   if (event.isSimulated) {
 *     console.warn('Spoofing detected', event.riskScore, event.checks);
 *   }
 * });
 *
 * await MockLocationDetector.startMonitoring({ intervalMs: 30000 });
 *
 * // later
 * await handle.remove();
 * await MockLocationDetector.stopMonitoring();
 * ```
 */
export interface LocationIntegrityChangedEvent extends LocationIntegrityResult {
  /**
   * Why this snapshot was emitted.
   *
   * - `manual` — first snapshot right after monitoring starts
   * - `interval` — periodic re-check (`intervalMs`)
   * - `location_update` — device location changed while monitoring (native only)
   */
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

  /**
   * Start background integrity monitoring on native platforms.
   *
   * Pair with {@link MockLocationDetectorPlugin.addListener} to receive
   * `locationIntegrityChanged` events while the app is in the foreground.
   */
  startMonitoring(options?: MonitoringOptions): Promise<void>;

  /** Stop monitoring and release native location listeners. */
  stopMonitoring(): Promise<void>;

  /**
   * Listen for integrity updates while {@link MockLocationDetectorPlugin.startMonitoring} is active.
   *
   * @param eventName Must be `'locationIntegrityChanged'`.
   */
  addListener(
    eventName: 'locationIntegrityChanged',
    listenerFunc: (event: LocationIntegrityChangedEvent) => void,
  ): Promise<PluginListenerHandle>;
  getPluginVersion(): Promise<PluginVersionResult>;
}
