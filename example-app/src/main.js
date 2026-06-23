import './style.css';

import { Capacitor } from '@capacitor/core';
import { MockLocationDetector } from '@capgo/capacitor-mock-location-detector';
import { CapacitorUpdater } from '@capgo/capacitor-updater';

const output = document.getElementById('plugin-output');
const analyzeButton = document.getElementById('run-analyze');
const monitorButton = document.getElementById('toggle-monitor');
const settingsButton = document.getElementById('open-settings');
const versionButton = document.getElementById('get-version');

let monitoring = false;

const setOutput = (value) => {
  output.textContent = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
};

if (Capacitor.isNativePlatform()) {
  void CapacitorUpdater.notifyAppReady().catch((error) => {
    console.error('CapacitorUpdater.notifyAppReady failed', error);
  });

  void MockLocationDetector.addListener('locationIntegrityChanged', (event) => {
    setOutput({ event: 'locationIntegrityChanged', ...event });
  });
}

analyzeButton.addEventListener('click', async () => {
  try {
    const result = await MockLocationDetector.analyze({
      requestLocationSample: true,
      minDetectedChecks: 1,
    });
    setOutput(result);
  } catch (error) {
    setOutput(`Error: ${error?.message ?? error}`);
  }
});

monitorButton.addEventListener('click', async () => {
  try {
    if (monitoring) {
      await MockLocationDetector.stopMonitoring();
      monitoring = false;
      monitorButton.textContent = 'Start monitoring';
      setOutput('Monitoring stopped');
      return;
    }

    await MockLocationDetector.startMonitoring({ intervalMs: 15000 });
    monitoring = true;
    monitorButton.textContent = 'Stop monitoring';
    setOutput('Monitoring started');
  } catch (error) {
    setOutput(`Error: ${error?.message ?? error}`);
  }
});

settingsButton.addEventListener('click', async () => {
  try {
    await MockLocationDetector.openDeveloperSettings();
    setOutput('Opened best-effort developer/settings screen');
  } catch (error) {
    setOutput(`Error: ${error?.message ?? error}`);
  }
});

versionButton.addEventListener('click', async () => {
  try {
    const result = await MockLocationDetector.getPluginVersion();
    setOutput(result);
  } catch (error) {
    setOutput(`Error: ${error?.message ?? error}`);
  }
});
