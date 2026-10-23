import { WebPlugin, PluginListenerHandle } from '@capacitor/core';
import type { ScreenPinningPlugin } from './ScreenPinning';

export class ScreenPinningWeb extends WebPlugin implements ScreenPinningPlugin {
  async openScreenPinningSettings(): Promise<{ success: boolean }> {
    console.warn('openScreenPinningSettings hanya tersedia di Android.');
    return { success: false };
  }

  async setAlarm(options: { timestamp?: number; delaySeconds?: number }): Promise<{ success: boolean; alarmTime?: number }> {
    console.log('Web mock setAlarm:', options);
    const delay = options.delaySeconds ? options.delaySeconds * 1000 : 5000;
    setTimeout(() => {
      this.notifyListeners('onAlarmTriggered', { isAlarmActive: true });
    }, delay);
    return { success: true, alarmTime: Date.now() + delay };
  }

  async cancelAlarm(): Promise<{ success: boolean }> {
    console.log('Web mock cancelAlarm');
    return { success: true };
  }

  async startPinning(): Promise<{ success: boolean }> {
    console.log('Web mock startPinning');
    return { success: true };
  }

  async stopPinning(): Promise<{ success: boolean }> {
    console.log('Web mock stopPinning');
    return { success: true };
  }

  async isPinningEnabled(): Promise<{ enabled: boolean }> {
    return { enabled: true };
  }

  async stopAlarm(): Promise<{ success: boolean }> {
    console.log('Web mock stopAlarm');
    return { success: true };
  }

  async checkAlarmState(): Promise<{ isAlarmActive: boolean }> {
    return { isAlarmActive: false };
  }

  async checkPermissions(): Promise<{
    pinningEnabled: boolean;
    canFullScreen: boolean;
    canOverlay: boolean;
    isDeviceOwner?: boolean;
    isLockTaskPermitted?: boolean;
  }> {
    return {
      pinningEnabled: true,
      canFullScreen: true,
      canOverlay: true,
      isDeviceOwner: true,
      isLockTaskPermitted: true,
    };
  }

  async enableKioskMode(): Promise<{ success: boolean }> {
    return { success: true };
  }

  async requestFullScreenPermission(): Promise<{ success: boolean }> {
    return { success: true };
  }

  async requestOverlayPermission(): Promise<{ success: boolean }> {
    return { success: true };
  }
}
