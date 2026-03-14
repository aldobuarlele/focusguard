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

  // MARK: - App Fetcher Methods (Phase 2)

  /**
   * Get all installed launchable apps on the device
   * Returns array of objects with packageName and appName
   * Excludes system apps without launcher intent
   * @returns {Promise<Array<{packageName: string, appName: string}>>}
   */
  async getInstalledApps() {
    if (Platform.OS !== 'android') {
      console.warn('FocusGuard only works on Android');
      return [];
    }

    try {
      const apps = await NativeModule.getInstalledApps();
      console.log(`Retrieved ${apps.length} installed apps`);
      return apps;
    } catch (error) {
      console.error('Failed to get installed apps:', error);
      throw error;
    }
  }

  // MARK: - Database Methods (Phase 2)

  /**
   * Update or create a blocking rule for an app
   * @param {string} packageName - The package name of the app
   * @param {number} level - Block level (0=none, 1=nudge, 2=challenge, 3=hard block)
   * @returns {Promise<boolean>} - True if successful
   */
  async updateAppRule(packageName, level) {
    if (Platform.OS !== 'android') {
      console.warn('FocusGuard only works on Android');
      return false;
    }

    try {
      const result = await NativeModule.updateAppRule(packageName, level);
      console.log(`Updated rule for ${packageName}: level=${level}`);
      return result;
    } catch (error) {
      console.error(`Failed to update rule for ${packageName}:`, error);
      throw error;
    }
  }

  /**
   * Get all blocking rules from the native SQLite database
   * @returns {Promise<Array<{packageName: string, blockLevel: number}>>}
   */
  async getAllAppRules() {
    if (Platform.OS !== 'android') {
      console.warn('FocusGuard only works on Android');
      return [];
    }

    try {
      const rules = await NativeModule.getAllAppRules();
      console.log(`Retrieved ${rules.length} app rules from database`);
      return rules;
    } catch (error) {
      console.error('Failed to get app rules:', error);
      throw error;
    }
  }

  /**
   * Get the block level for a specific app
   * @param {string} packageName - The package name to check
   * @returns {Promise<number>} - Block level (0 if not blocked)
   */
  async getAppRuleLevel(packageName) {
    if (Platform.OS !== 'android') {
      return 0;
    }

    try {
      const level = await NativeModule.getAppRuleLevel(packageName);
      console.log(`Rule level for ${packageName}: ${level}`);
      return level;
    } catch (error) {
      console.error(`Failed to get rule level for ${packageName}:`, error);
      throw error;
    }
  }

  // MARK: - Bypass Duration Methods (Phase 3)

  /**
   * Set bypass duration for a specific level
   * @param {number} level - The bypass level (1 or 2)
   * @param {number} durationMinutes - Duration in minutes
   * @returns {Promise<boolean>} - True if successful
   */
  async setBypassDuration(level, durationMinutes) {
    if (Platform.OS !== 'android') {
      console.warn('FocusGuard only works on Android');
      return false;
    }

    try {
      const result = await NativeModule.setBypassDuration(level, durationMinutes);
      console.log(`Set bypass duration for level ${level}: ${durationMinutes} minutes`);
      return result;
    } catch (error) {
      console.error(`Failed to set bypass duration for level ${level}:`, error);
      throw error;
    }
  }

  /**
   * Get bypass durations for both levels
   * @returns {Promise<{level1_duration: number, level2_duration: number}>}
   */
  async getBypassDurations() {
    if (Platform.OS !== 'android') {
      console.warn('FocusGuard only works on Android');
      return { level1_duration: 5, level2_duration: 15 };
    }

    try {
      const durations = await NativeModule.getBypassDurations();
      console.log(`Bypass durations: level1=${durations.level1_duration}, level2=${durations.level2_duration}`);
      return durations;
    } catch (error) {
      console.error('Failed to get bypass durations:', error);
      throw error;
    }
  }

  /**
   * Get all active bypasses (non-expired)
   * @returns {Promise<Object>} - Map of packageName -> expiryTimestamp (milliseconds)
   */
  async getActiveBypasses() {
    if (Platform.OS !== 'android') {
      console.warn('FocusGuard only works on Android');
      return {};
    }

    try {
      const bypasses = await NativeModule.getActiveBypasses();
      console.log(`Retrieved ${Object.keys(bypasses).length} active bypasses`);
      return bypasses;
    } catch (error) {
      console.error('Failed to get active bypasses:', error);
      throw error;
    }
  }
}

// Create and export singleton instance
const FocusGuardNative = new FocusGuardNativeModule();
export default FocusGuardNative;
