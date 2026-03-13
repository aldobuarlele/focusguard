import { StatusBar } from 'expo-status-bar';
import { StyleSheet, Text, View, Button, Alert, ScrollView } from 'react-native';
import FocusGuardNative from './src/native';

export default function App() {
  
  const handleCheckPermissions = async () => {
    try {
      const [hasOverlay, hasAccessibility] = await Promise.all([
        FocusGuardNative.hasOverlayPermission(),
        FocusGuardNative.isAccessibilityServiceEnabled()
      ]);
      
      Alert.alert(
        'Permissions Status',
        `Overlay Permission: ${hasOverlay ? 'Granted' : 'Not Granted'}\n` +
        `Accessibility Service: ${hasAccessibility ? 'Enabled' : 'Not Enabled'}`
      );
    } catch (error) {
      Alert.alert('Error', `Failed to check permissions: ${error.message}`);
    }
  };

  const handleOpenOverlaySettings = async () => {
    try {
      await FocusGuardNative.openOverlayPermissionSettings();
    } catch (error) {
      Alert.alert('Error', `Failed to open overlay settings: ${error.message}`);
    }
  };

  const handleOpenAccessibilitySettings = async () => {
    try {
      await FocusGuardNative.openAccessibilitySettings();
    } catch (error) {
      Alert.alert('Error', `Failed to open accessibility settings: ${error.message}`);
    }
  };

  const handleStartServices = async () => {
    try {
      const [overlayResult, accessibilityResult] = await Promise.all([
        FocusGuardNative.startOverlayService(),
        FocusGuardNative.startAccessibilityService()
      ]);
      
      Alert.alert(
        'Services Started',
        `Overlay Service: ${overlayResult ? 'Started' : 'Failed'}\n` +
        `Accessibility Service: ${accessibilityResult ? 'Started' : 'Failed'}`
      );
    } catch (error) {
      Alert.alert('Error', `Failed to start services: ${error.message}`);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>FocusGuard - Phase 1 Test</Text>
      <Text style={styles.subtitle}>OS Foundation & Native Bridge</Text>
      
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Permission Setup</Text>
        <Button title="Check Permissions Status" onPress={handleCheckPermissions} />
        <View style={styles.buttonSpacer} />
        <Button title="Open Overlay Permission Settings" onPress={handleOpenOverlaySettings} />
        <View style={styles.buttonSpacer} />
        <Button title="Open Accessibility Settings" onPress={handleOpenAccessibilitySettings} />
        <View style={styles.buttonSpacer} />
        <Button title="Start All Services" onPress={handleStartServices} />
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Phase 1 Architecture</Text>
        <Text style={styles.note}>
          • Target Apps: Settings (com.android.settings) and Chrome (com.android.chrome){'\n'}
          • Direct Service-to-Service Communication: AccessibilityService → OverlayService via Intent{'\n'}
          • No React Native Bridge latency for detection-to-overlay trigger{'\n'}
          • Overlay shows "PHASE 1: [APP NAME] BLOCKED" in red{'\n'}
          • No overlays shown on permission grant or service start
        </Text>
        <Text style={styles.architectureText}>
          • Kotlin Accessibility Service (detects foreground apps){'\n'}
          • Kotlin Overlay Service (shows blocking UI){'\n'}
          • React Native Bridge Module (service control only){'\n'}
          • SYSTEM_ALERT_WINDOW permission (overlay display){'\n'}
          • BIND_ACCESSIBILITY_SERVICE permission (app detection)
        </Text>
      </View>

      <StatusBar style="auto" />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    backgroundColor: '#f5f5f5',
    alignItems: 'center',
    padding: 20,
    paddingTop: 60,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 8,
    color: '#333',
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    marginBottom: 30,
  },
  section: {
    width: '100%',
    backgroundColor: 'white',
    borderRadius: 10,
    padding: 20,
    marginBottom: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 15,
    color: '#444',
  },
  note: {
    fontSize: 14,
    color: '#666',
    fontStyle: 'italic',
    marginBottom: 15,
    lineHeight: 20,
  },
  architectureText: {
    fontSize: 14,
    color: '#555',
    lineHeight: 22,
  },
  buttonSpacer: {
    height: 10,
  },
});
