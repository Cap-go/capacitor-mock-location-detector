# @capgo/capacitor-mock-location-detector

<a href="https://capgo.app/"><img src="https://capgo.app/readme-banner.svg?repo=Cap-go/capacitor-mock-location-detector" alt="Capgo - Instant updates for Capacitor" /></a>

<div align="center">
  <h2><a href="https://capgo.app/?ref=plugin_mock_location_detector"> ➡️ Get Instant updates for your App with Capgo</a></h2>
  <h2><a href="https://capgo.app/consulting/?ref=plugin_mock_location_detector"> Missing a feature? We’ll build the plugin for you 💪</a></h2>
</div>

Detect simulated GPS locations using layered, App Store-safe checks on iOS and Android.

## Snapshot

- **Plugin name:** `@capgo/capacitor-mock-location-detector`
- **One-line value:** Multi-layer GPS spoofing and developer tooling detection for Capacitor apps
- **Maintainer:** Capgo
- **Status:** beta

## Problem & Scope

### Why this plugin exists

Tools like PoKeep and iMyFone AnyTo can spoof device location. A single flag such as iOS `isSimulatedBySoftware` is not reliable against every spoofing method. This plugin combines multiple independent signals so your app can make better fraud-prevention decisions.

### What it does

- Runs layered checks: system mock flags, developer options, known mock apps, movement heuristics, and motion correlation
- Returns a scored `LocationIntegrityResult` with per-check details
- Supports continuous monitoring with `locationIntegrityChanged` events
- Opens the best-effort settings screen so users can disable developer/mock tooling themselves

### What it does not do

- Cannot programmatically disable Developer Mode or mock location settings (not allowed by iOS/Android)
- Cannot guarantee 100% detection against every future spoofing tool
- On iOS, Apple provides no public API to read the Developer Mode toggle directly

## Compatibility

| Plugin version | Capacitor compatibility | Maintained |
| -------------- | ----------------------- | ---------- |
| v8.\*.\*       | v8.\*.\*                | ✅          |
| v7.\*.\*       | v7.\*.\*                | On demand   |
| v6.\*.\*       | v6.\*.\*                | On demand   |

## Install

```bash
npm install @capgo/capacitor-mock-location-detector
npx cap sync
```

## Setup

### iOS

Add location usage descriptions to your app `Info.plist`:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>We verify your location has not been spoofed.</string>
```

Optional: declare URL schemes for companion spoof apps you want to detect:

```xml
<key>LSApplicationQueriesSchemes</key>
<array>
  <string>anyto</string>
  <string>fakegps</string>
</array>
```

### Android

Ensure your app requests runtime location permissions. The plugin declares coarse/fine location permissions in its manifest merge.

## Usage

```typescript
import { MockLocationDetector } from '@capgo/capacitor-mock-location-detector';

const result = await MockLocationDetector.analyze({
  requestLocationSample: true,
  minDetectedChecks: 1,
});

if (result.isSimulated) {
  console.warn('Possible GPS spoofing detected', result.checks);
}

// Guide the user — apps cannot disable developer mode automatically
await MockLocationDetector.openDeveloperSettings();
```

Run a single check layer:

```typescript
const mockFlag = await MockLocationDetector.runCheck({
  check: 'system_mock_flag',
});
```

Start monitoring:

```typescript
await MockLocationDetector.addListener('locationIntegrityChanged', (event) => {
  console.log('Integrity changed', event.riskScore, event.checks);
});

await MockLocationDetector.startMonitoring({ intervalMs: 30000 });
```

## Check layers

| Check ID | iOS | Android | Description |
| --- | --- | --- | --- |
| `system_mock_flag` | ✅ | ✅ | OS mock/simulation flag on the current location fix |
| `developer_options` | — | ✅ | Android developer options enabled |
| `developer_mode_indicators` | ✅ | ✅ | Indirect developer/debug build heuristics |
| `mock_location_app` | ✅ | ✅ | Known spoof app packages / URL schemes |
| `adb_enabled` | — | ✅ | USB debugging enabled |
| `mock_provider_settings` | — | ✅ | Apps granted mock-location permission |
| `location_anomaly` | ✅ | ✅ | Impossible movement speed / teleport heuristic |
| `motion_correlation` | ✅ | — | GPS movement without matching accelerometer activity |
| `simulator` | ✅ | ✅ | Simulator/emulator environment |

## Capgo Links

- **Plugin docs URL:** `https://capgo.app/docs/plugins/mock-location-detector/`
- **Website/docs repo:** `https://github.com/Cap-go/website`

<docgen-index>

* [`getCapabilities()`](#getcapabilities)
* [`analyze(...)`](#analyze)
* [`runCheck(...)`](#runcheck)
* [`openDeveloperSettings()`](#opendevelopersettings)
* [`startMonitoring(...)`](#startmonitoring)
* [`stopMonitoring()`](#stopmonitoring)
* [`addListener('locationIntegrityChanged', ...)`](#addlistenerlocationintegritychanged-)
* [`getPluginVersion()`](#getpluginversion)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

Detect simulated GPS locations and developer tooling that commonly enables spoofing apps.

This plugin combines multiple independent checks because no single OS flag is reliable against
tools such as PoKeep or iMyFone AnyTo. On iOS, Apple does not provide a public API to read the
Developer Mode toggle directly; the plugin uses App Store-safe heuristics instead.

Apps cannot programmatically disable Developer Mode or mock location settings. Use
{@link MockLocationDetectorPlugin.openDeveloperSettings} to guide users to the relevant settings.

### getCapabilities()

```typescript
getCapabilities() => Promise<MockLocationDetectorCapabilities>
```

**Returns:** <code>Promise&lt;<a href="#mocklocationdetectorcapabilities">MockLocationDetectorCapabilities</a>&gt;</code>

--------------------


### analyze(...)

```typescript
analyze(options?: AnalyzeOptions | undefined) => Promise<LocationIntegrityResult>
```

| Param         | Type                                                      |
| ------------- | --------------------------------------------------------- |
| **`options`** | <code><a href="#analyzeoptions">AnalyzeOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#locationintegrityresult">LocationIntegrityResult</a>&gt;</code>

--------------------


### runCheck(...)

```typescript
runCheck(options: RunCheckOptions) => Promise<LocationCheckResult>
```

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#runcheckoptions">RunCheckOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#locationcheckresult">LocationCheckResult</a>&gt;</code>

--------------------


### openDeveloperSettings()

```typescript
openDeveloperSettings() => Promise<void>
```

--------------------


### startMonitoring(...)

```typescript
startMonitoring(options?: MonitoringOptions | undefined) => Promise<void>
```

Start background integrity monitoring on native platforms.

Pair with {@link MockLocationDetectorPlugin.addListener} to receive
`locationIntegrityChanged` events while the app is in the foreground.

| Param         | Type                                                            |
| ------------- | --------------------------------------------------------------- |
| **`options`** | <code><a href="#monitoringoptions">MonitoringOptions</a></code> |

--------------------


### stopMonitoring()

```typescript
stopMonitoring() => Promise<void>
```

Stop monitoring and release native location listeners.

--------------------


### addListener('locationIntegrityChanged', ...)

```typescript
addListener(eventName: 'locationIntegrityChanged', listenerFunc: (event: LocationIntegrityChangedEvent) => void) => Promise<PluginListenerHandle>
```

Listen for integrity updates while {@link MockLocationDetectorPlugin.startMonitoring} is active.

| Param              | Type                                                                                                        | Description                           |
| ------------------ | ----------------------------------------------------------------------------------------------------------- | ------------------------------------- |
| **`eventName`**    | <code>'locationIntegrityChanged'</code>                                                                     | Must be `'locationIntegrityChanged'`. |
| **`listenerFunc`** | <code>(event: <a href="#locationintegritychangedevent">LocationIntegrityChangedEvent</a>) =&gt; void</code> |                                       |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### getPluginVersion()

```typescript
getPluginVersion() => Promise<PluginVersionResult>
```

**Returns:** <code>Promise&lt;<a href="#pluginversionresult">PluginVersionResult</a>&gt;</code>

--------------------


### Interfaces


#### MockLocationDetectorCapabilities

| Prop                           | Type                                                                            |
| ------------------------------ | ------------------------------------------------------------------------------- |
| **`platform`**                 | <code><a href="#locationintegrityplatform">LocationIntegrityPlatform</a></code> |
| **`availableChecks`**          | <code>LocationCheckId[]</code>                                                  |
| **`supportsMonitoring`**       | <code>boolean</code>                                                            |
| **`canOpenDeveloperSettings`** | <code>boolean</code>                                                            |


#### LocationIntegrityResult

| Prop                 | Type                                                                                |
| -------------------- | ----------------------------------------------------------------------------------- |
| **`isSimulated`**    | <code>boolean</code>                                                                |
| **`confidence`**     | <code><a href="#locationintegrityconfidence">LocationIntegrityConfidence</a></code> |
| **`riskScore`**      | <code>number</code>                                                                 |
| **`platform`**       | <code><a href="#locationintegrityplatform">LocationIntegrityPlatform</a></code>     |
| **`checks`**         | <code>LocationCheckResult[]</code>                                                  |
| **`developerMode`**  | <code><a href="#developermoderesult">DeveloperModeResult</a></code>                 |
| **`locationSample`** | <code><a href="#locationsample">LocationSample</a> \| null</code>                   |
| **`recommendation`** | <code>string</code>                                                                 |


#### LocationCheckResult

| Prop            | Type                                                                            |
| --------------- | ------------------------------------------------------------------------------- |
| **`id`**        | <code><a href="#locationcheckid">LocationCheckId</a></code>                     |
| **`name`**      | <code>string</code>                                                             |
| **`platform`**  | <code><a href="#locationintegrityplatform">LocationIntegrityPlatform</a></code> |
| **`available`** | <code>boolean</code>                                                            |
| **`detected`**  | <code>boolean</code>                                                            |
| **`message`**   | <code>string</code>                                                             |
| **`metadata`**  | <code>{ [key: string]: unknown; }</code>                                        |


#### DeveloperModeResult

| Prop                         | Type                               |
| ---------------------------- | ---------------------------------- |
| **`detected`**               | <code>boolean</code>               |
| **`canDetectDeveloperMode`** | <code>boolean</code>               |
| **`checks`**                 | <code>LocationCheckResult[]</code> |


#### LocationSample

| Prop            | Type                |
| --------------- | ------------------- |
| **`latitude`**  | <code>number</code> |
| **`longitude`** | <code>number</code> |
| **`accuracy`**  | <code>number</code> |
| **`altitude`**  | <code>number</code> |
| **`speed`**     | <code>number</code> |
| **`timestamp`** | <code>number</code> |


#### AnalyzeOptions

| Prop                              | Type                           |
| --------------------------------- | ------------------------------ |
| **`checks`**                      | <code>LocationCheckId[]</code> |
| **`requestLocationSample`**       | <code>boolean</code>           |
| **`locationTimeoutMs`**           | <code>number</code>            |
| **`minDetectedChecks`**           | <code>number</code>            |
| **`additionalMockAppPackages`**   | <code>string[]</code>          |
| **`additionalMockAppUrlSchemes`** | <code>string[]</code>          |


#### RunCheckOptions

| Prop        | Type                                                        |
| ----------- | ----------------------------------------------------------- |
| **`check`** | <code><a href="#locationcheckid">LocationCheckId</a></code> |


#### MonitoringOptions

| Prop                   | Type                 | Description                                                                                                                                                                                                                  | Default            |
| ---------------------- | -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------ |
| **`intervalMs`**       | <code>number</code>  | How often to re-run checks while monitoring is active. Minimum 5000 ms. Defaults to 30000.                                                                                                                                   | <code>30000</code> |
| **`emitOnlyOnChange`** | <code>boolean</code> | When `true`, `locationIntegrityChanged` is emitted only when `isSimulated`, `confidence`, `riskScore`, or triggered check IDs change. The first event after {@link MockLocationDetectorPlugin.startMonitoring} always fires. | <code>true</code>  |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### LocationIntegrityChangedEvent

Payload emitted by {@link MockLocationDetectorPlugin.addListener} when monitoring detects
a new integrity snapshot.

Register the listener before calling {@link MockLocationDetectorPlugin.startMonitoring}:

| Prop         | Type                                                     | Description                                                                                                                                                                                                           |
| ------------ | -------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`reason`** | <code>'interval' \| 'location_update' \| 'manual'</code> | Why this snapshot was emitted. - `manual` — first snapshot right after monitoring starts - `interval` — periodic re-check (`intervalMs`) - `location_update` — device location changed while monitoring (native only) |


#### PluginVersionResult

| Prop          | Type                |
| ------------- | ------------------- |
| **`version`** | <code>string</code> |


### Type Aliases


#### LocationIntegrityPlatform

<code>'ios' | 'android' | 'web'</code>


#### LocationCheckId

Individual integrity checks exposed by the plugin.

<code>'system_mock_flag' | 'developer_options' | 'developer_mode_indicators' | 'mock_location_app' | 'adb_enabled' | 'mock_provider_settings' | 'location_anomaly' | 'motion_correlation' | 'simulator'</code>


#### LocationIntegrityConfidence

<code>'none' | 'low' | 'medium' | 'high'</code>

</docgen-api>
