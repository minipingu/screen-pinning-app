import { registerPlugin, PluginListenerHandle } from '@capacitor/core';

export interface ScreenPinningPlugin {
  openScreenPinningSettings(): Promise<{ success: boolean }>;
  setAlarm(options: { timestamp?: number; delaySeconds?: number }): Promise<{ success: boolean; alarmTime?: number }>;
  cancelAlarm(): Promise<{ success: boolean }>;
  startPinning(): Promise<{ success: boolean }>;
  stopPinning(): Promise<{ success: boolean }>;
  isPinningEnabled(): Promise<{ enabled: boolean }>;
  stopAlarm(): Promise<{ success: boolean }>;
  checkAlarmState(): Promise<{ isAlarmActive: boolean }>;
  checkPermissions(): Promise<{
    pinningEnabled: boolean;
    canFullScreen: boolean;
    canOverlay: boolean;
    isDeviceOwner?: boolean;
    isLockTaskPermitted?: boolean;
  }>;
  enableKioskMode(): Promise<{ success: boolean }>;
  requestFullScreenPermission(): Promise<{ success: boolean }>;
  requestOverlayPermission(): Promise<{ success: boolean }>;
  addListener(
    eventName: 'onAlarmTriggered',
    listenerFunc: (data: { isAlarmActive: boolean }) => void
  ): Promise<PluginListenerHandle>;
}

const ScreenPinning = registerPlugin<ScreenPinningPlugin>('ScreenPinning', {
  web: () => import('./web').then((m) => new m.ScreenPinningWeb()),
});

export { ScreenPinning };
