import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

const { FocusGuardNative: NativeModule } = NativeModules;

class FocusGuardNativeModule {
  constructor() {
    if (Platform.OS === 'android') {
      this.eventEmitter = new NativeEventEmitter(NativeModule);
      this.setupEventListeners();
    }
  }

  setupEventListeners() {
    // Listen for native events
    this.eventEmitter.addListener('onAppDetected', (data) => {
      console.log('App detected:', data.packageName);
      // This will be used in Phase 2+ for more complex logic
    });

    this.eventEmitter.addListener('onOverlayShown', (data) => {
      console.log('Overlay shown for:', data.packageName);
    });

    this.eventEmitter.addListener('onOverlayHidden', () => {
      console.log('Overlay hidden');
    });
  }

  // MARK: - Service Control Methods

  async startAccessibilityService() {
    if (Platform.OS !== 'android') {
      console.warn('FocusGuard only works on Android');
      return false;
    }

    try {
      const result = await NativeModule.startAccessibilityService();
      console.log('Accessibility service started:', result);
      return result;
    } catch (error) {
      console.error('Failed to start accessibility service:', error);
      throw error;
    }
  }

  async stopAccessibilityService() {
    if (Platform.OS !== 'android') {
      return false;
    }

    try {
      const result = await NativeModule.stopAccessibilityService();
      console.log('Accessibility service stopped:', result);
      return result;
    } catch (error) {
      console.error('Failed to stop accessibility service:', error);
      throw error;
    }
  }

  async startOverlayService() {
    if (Platform.OS !== 'android') {
      return false;
    }

    try {
      const result = await NativeModule.startOverlayService();
      console.log('Overlay service started:', result);
      return result;
    } catch (error) {
      console.error('Failed to start overlay service:', error);
      throw error;
    }
  }

  async stopOverlayService() {
    if (Platform.OS !== 'android') {
      return false;
    }

    try {
      const result = await NativeModule.stopOverlayService();
      console.log('Overlay service stopped:', result);
      return result;
    } catch (error) {
      console.error('Failed to stop overlay service:', error);
      throw error;
    }
  }

  // MARK: - Overlay Control Methods

  async showOverlayForApp(packageName) {
    if (Platform.OS !== 'android') {
      return false;
    }

    try {
      const result = await NativeModule.showOverlayForApp(packageName);
      console.log(`Overlay shown for ${packageName}:`, result);
      return result;
    } catch (error) {
      console.error(`Failed to show overlay for ${packageName}:`, error);
      throw error;
    }
  }

  async hideOverlay() {
    if (Platform.OS !== 'android') {
      return false;
    }

    try {
      const result = await NativeModule.hideOverlay();
      console.log('Overlay hidden:', result);
      return result;
    } catch (error) {
      console.error('Failed to hide overlay:', error);
      throw error;
    }
  }

  // MARK: - Permission Check Methods

  async hasOverlayPermission() {
    if (Platform.OS !== 'android') {
      return false;
    }

    try {
      const result = await NativeModule.hasOverlayPermission();
      console.log('Has overlay permission:', result);
      return result;
    } catch (error) {
      console.error('Failed to check overlay permission:', error);
      return false;
    }
  }

  async isAccessibilityServiceEnabled() {
    if (Platform.OS !== 'android') {
      return false;
    }

    try {
      const result = await NativeModule.isAccessibilityServiceEnabled();
      console.log('Accessibility service enabled:', result);
      return result;
    } catch (error) {
      console.error('Failed to check accessibility service:', error);
      return false;
    }
  }

  // MARK: - Settings Methods

  async openOverlayPermissionSettings() {
    if (Platform.OS !== 'android') {
      return false;
    }

    try {
      const result = await NativeModule.openOverlayPermissionSettings();
      console.log('Opened overlay permission settings:', result);
      return result;
    } catch (error) {
      console.error('Failed to open overlay permission settings:', error);
      throw error;
    }
  }

  async openAccessibilitySettings() {
    if (Platform.OS !== 'android') {
      return false;
    }

    try {
      const result = await NativeModule.openAccessibilitySettings();
      console.log('Opened accessibility settings:', result);
      return result;
    } catch (error) {
      console.error('Failed to open accessibility settings:', error);
      throw error;
    }
  }

  // MARK: - Test Methods for Phase 1 (Removed - using direct service-to-service communication)
  // Note: In Phase 1, the AccessibilityDetectionService directly triggers OverlayService
  // via Intent when it detects target apps (Settings or Chrome)
}

// Create and export singleton instance
const FocusGuardNative = new FocusGuardNativeModule();
export default FocusGuardNative;