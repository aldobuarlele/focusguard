import React, { useState, useEffect, useRef } from 'react';
import {
  StyleSheet,
  Text,
  View,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  StatusBar,
  TextInput,
  PermissionsAndroid,
  Platform,
} from 'react-native';
import FocusGuardNative from './src/native';

export default function App() {
  const [apps, setApps] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [servicesStarted, setServicesStarted] = useState(false);
  
  // Feature A: Search Bar
  const [searchQuery, setSearchQuery] = useState('');
  
  // Feature C: Dynamic Durations Settings UI
  const [level1Duration, setLevel1Duration] = useState(5); // default 5 minutes
  const [level2Duration, setLevel2Duration] = useState(15); // default 15 minutes
  const [draftLevel1, setDraftLevel1] = useState(5); // draft for level 1
  const [draftLevel2, setDraftLevel2] = useState(15); // draft for level 2
  const [settingsExpanded, setSettingsExpanded] = useState(false);
  
  // Feature D: Active Bypass UI
  const [activeBypasses, setActiveBypasses] = useState({});
  
  // Ref for interval cleanup
  const intervalRef = useRef(null);

  // Fetch installed apps, app rules, and bypass durations on mount
  useEffect(() => {
    fetchData();
    fetchActiveBypasses();
    
    // Request notification permission for Android 13+
    if (Platform.OS === 'android' && Platform.Version >= 33) {
      PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
    }
    
    // Set up interval to refresh active bypasses every 10 seconds
    intervalRef.current = setInterval(() => {
      fetchActiveBypasses();
    }, 10000); // 10 seconds
    
    // Cleanup interval on unmount
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, []);

  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);
      
      // Fetch installed apps, app rules, and bypass durations in parallel
      const [installedApps, appRules, bypassDurations] = await Promise.all([
        FocusGuardNative.getInstalledApps(),
        FocusGuardNative.getAllAppRules(),
        FocusGuardNative.getBypassDurations(),
      ]);

      // Create a map of packageName -> blockLevel from appRules
      const rulesMap = {};
      appRules.forEach(rule => {
        rulesMap[rule.packageName] = rule.blockLevel;
      });

      // Merge installed apps with their block levels
      const mergedApps = installedApps.map(app => ({
        ...app,
        blockLevel: rulesMap[app.packageName] || 0,
      }));

      // Sort apps alphabetically by appName
      mergedApps.sort((a, b) => a.appName.localeCompare(b.appName));

      setApps(mergedApps);
      
      // Update duration states from native module
      setLevel1Duration(bypassDurations.level1_duration);
      setLevel2Duration(bypassDurations.level2_duration);
      // Also update draft states
      setDraftLevel1(bypassDurations.level1_duration);
      setDraftLevel2(bypassDurations.level2_duration);
      
    } catch (err) {
      console.error('Failed to fetch data:', err);
      setError(err.message || 'Failed to load apps and rules');
      Alert.alert('Error', 'Failed to load apps and rules. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const fetchActiveBypasses = async () => {
    try {
      const bypasses = await FocusGuardNative.getActiveBypasses();
      setActiveBypasses(bypasses);
    } catch (err) {
      console.error('Failed to fetch active bypasses:', err);
      // Don't show error alert for this background operation
    }
  };

  const handleUpdateBlockLevel = async (packageName, newLevel) => {
    try {
      // Optimistic update
      setApps(prevApps =>
        prevApps.map(app =>
          app.packageName === packageName
            ? { ...app, blockLevel: newLevel }
            : app
        )
      );

      // Call native module to update database
      await FocusGuardNative.updateAppRule(packageName, newLevel);
      
      console.log(`✅ SUCCESS: Updated ${packageName} to block level ${newLevel}`);
    } catch (err) {
      console.error(`❌ FAILURE: Failed to update ${packageName} to level ${newLevel}:`, err);
      // Revert optimistic update on error
      fetchData();
      Alert.alert('Error', `Failed to update rule: ${err.message}`);
    }
  };

  const handleUpdateBypassDuration = async (level, newDuration) => {
    try {
      // Validate duration
      if (newDuration < 1 || newDuration > 120) {
        Alert.alert('Invalid Duration', 'Duration must be between 1 and 120 minutes');
        return;
      }

      // Update local state optimistically
      if (level === 1) {
        setLevel1Duration(newDuration);
      } else if (level === 2) {
        setLevel2Duration(newDuration);
      }

      // Call native module
      await FocusGuardNative.setBypassDuration(level, newDuration);
      
      console.log(`✅ SUCCESS: Updated level ${level} bypass duration to ${newDuration} minutes`);
    } catch (err) {
      console.error(`❌ FAILURE: Failed to update level ${level} duration:`, err);
      // Revert by fetching fresh data
      const durations = await FocusGuardNative.getBypassDurations();
      setLevel1Duration(durations.level1_duration);
      setLevel2Duration(durations.level2_duration);
      Alert.alert('Error', `Failed to update bypass duration: ${err.message}`);
    }
  };

  const handleStartServices = async () => {
    try {
      const [overlayResult, accessibilityResult] = await Promise.all([
        FocusGuardNative.startOverlayService(),
        FocusGuardNative.startAccessibilityService(),
      ]);

      if (overlayResult && accessibilityResult) {
        setServicesStarted(true);
        Alert.alert('Success', 'All services started successfully!');
      } else {
        Alert.alert('Warning', 'Some services failed to start. Please check permissions.');
      }
    } catch (err) {
      console.error('Failed to start services:', err);
      Alert.alert('Error', `Failed to start services: ${err.message}`);
    }
  };

  const handleCheckPermissions = async () => {
    try {
      const [hasOverlay, hasAccessibility] = await Promise.all([
        FocusGuardNative.hasOverlayPermission(),
        FocusGuardNative.isAccessibilityServiceEnabled(),
      ]);

      Alert.alert(
        'Permissions Status',
        `Overlay Permission: ${hasOverlay ? 'Granted' : 'Not Granted'}\n` +
        `Accessibility Service: ${hasAccessibility ? 'Enabled' : 'Not Enabled'}`
      );
    } catch (err) {
      Alert.alert('Error', `Failed to check permissions: ${err.message}`);
    }
  };

  const handleApplyDraftChanges = async () => {
    try {
      // Validate durations
      if (draftLevel1 < 1 || draftLevel1 > 120 || draftLevel2 < 1 || draftLevel2 > 120) {
        Alert.alert('Invalid Duration', 'Duration must be between 1 and 120 minutes');
        return;
      }

      // Call native module to update both durations
      await Promise.all([
        FocusGuardNative.setBypassDuration(1, draftLevel1),
        FocusGuardNative.setBypassDuration(2, draftLevel2),
      ]);

      // Update actual states
      setLevel1Duration(draftLevel1);
      setLevel2Duration(draftLevel2);
      
      Alert.alert('Sukses', 'Durasi berhasil diperbarui!');
      console.log(`✅ SUCCESS: Updated durations to L1=${draftLevel1}min, L2=${draftLevel2}min`);
    } catch (err) {
      console.error('❌ FAILURE: Failed to update bypass durations:', err);
      Alert.alert('Error', `Failed to update bypass durations: ${err.message}`);
      // Revert by fetching fresh data
      const durations = await FocusGuardNative.getBypassDurations();
      setLevel1Duration(durations.level1_duration);
      setLevel2Duration(durations.level2_duration);
      setDraftLevel1(durations.level1_duration);
      setDraftLevel2(durations.level2_duration);
    }
  };

  // Filter apps based on search query
  const filteredApps = apps.filter(app => {
    if (!searchQuery.trim()) return true;
    
    const query = searchQuery.toLowerCase();
    return (
      app.appName.toLowerCase().includes(query) ||
      app.packageName.toLowerCase().includes(query)
    );
  });

  const renderAppItem = ({ item }) => {
    const bypassExpiry = activeBypasses[item.packageName];
    const isBypassActive = bypassExpiry && bypassExpiry > Date.now();
    const minutesRemaining = isBypassActive 
      ? Math.max(0, Math.ceil((bypassExpiry - Date.now()) / 60000))
      : 0;

    return (
      <View style={styles.appItem}>
        <View style={styles.appInfo}>
          <Text style={styles.appName} numberOfLines={1}>
            {item.appName}
          </Text>
          <Text style={styles.packageName} numberOfLines={1}>
            {item.packageName}
          </Text>
          
          {/* Feature D: Active Bypass Indicator */}
          {isBypassActive && (
            <View style={styles.bypassIndicator}>
              <Text style={styles.bypassText}>
                ✅ Bypass Active (Expires in {minutesRemaining} min{minutesRemaining !== 1 ? 's' : ''})
              </Text>
            </View>
          )}
        </View>
        
        <View style={styles.levelButtons}>
          {[0, 1, 2, 3].map(level => {
            const isSelected = Number(item.blockLevel) === level;
            return (
              <TouchableOpacity
                key={level}
                style={[
                  styles.levelButton,
                  level === 0 && styles.level0Button,
                  level === 1 && styles.level1Button,
                  level === 2 && styles.level2Button,
                  level === 3 && styles.level3Button,
                  !isSelected && styles.levelButtonUnselected,
                  isSelected && styles.levelButtonSelected,
                ]}
                onPress={() => handleUpdateBlockLevel(item.packageName, level)}
              >
                <Text
                  style={[
                    styles.levelButtonText,
                    !isSelected && styles.levelButtonTextUnselected,
                    isSelected && styles.levelButtonTextSelected,
                  ]}
                >
                  {level === 0 ? 'None' : `L${level}`}
                </Text>
              </TouchableOpacity>
            );
          })}
        </View>
      </View>
    );
  };

  const renderSettingsPanel = () => (
    <View style={styles.header}>
      <Text style={styles.title}>FocusGuard - Phase 3 Dashboard</Text>
      <Text style={styles.subtitle}>Dynamic App Blocking with Bypass Management</Text>
      
      {/* Feature C: Dynamic Durations Settings UI */}
      <TouchableOpacity
        style={styles.settingsToggle}
        onPress={() => setSettingsExpanded(!settingsExpanded)}
      >
        <Text style={styles.settingsToggleText}>
          {settingsExpanded ? '▼ Bypass Duration Settings' : '▶ Bypass Duration Settings'}
        </Text>
      </TouchableOpacity>
      
      {settingsExpanded && (
        <View style={styles.settingsContainer}>
          <View style={styles.durationSetting}>
            <Text style={styles.durationLabel}>Level 1 Bypass Duration:</Text>
            <View style={styles.durationControls}>
              <TouchableOpacity
                style={styles.durationButton}
                onPress={() => setDraftLevel1(prev => Math.max(1, prev - 1))}
                disabled={draftLevel1 <= 1}
              >
                <Text style={styles.durationButtonText}>-</Text>
              </TouchableOpacity>
              <Text style={styles.durationValue}>{draftLevel1} minutes</Text>
              <TouchableOpacity
                style={styles.durationButton}
                onPress={() => setDraftLevel1(prev => Math.min(120, prev + 1))}
                disabled={draftLevel1 >= 120}
              >
                <Text style={styles.durationButtonText}>+</Text>
              </TouchableOpacity>
            </View>
          </View>
          
          <View style={styles.durationSetting}>
            <Text style={styles.durationLabel}>Level 2 Bypass Duration:</Text>
            <View style={styles.durationControls}>
              <TouchableOpacity
                style={styles.durationButton}
                onPress={() => setDraftLevel2(prev => Math.max(1, prev - 1))}
                disabled={draftLevel2 <= 1}
              >
                <Text style={styles.durationButtonText}>-</Text>
              </TouchableOpacity>
              <Text style={styles.durationValue}>{draftLevel2} minutes</Text>
              <TouchableOpacity
                style={styles.durationButton}
                onPress={() => setDraftLevel2(prev => Math.min(120, prev + 1))}
                disabled={draftLevel2 >= 120}
              >
                <Text style={styles.durationButtonText}>+</Text>
              </TouchableOpacity>
            </View>
          </View>
          
          <TouchableOpacity
            style={styles.applyButton}
            onPress={handleApplyDraftChanges}
          >
            <Text style={styles.applyButtonText}>Terapkan Perubahan</Text>
          </TouchableOpacity>
          
          <Text style={styles.durationNote}>
            These durations apply when granting bypass from Level 1 or Level 2 overlays
          </Text>
        </View>
      )}
      
      <View style={styles.controlSection}>
        <TouchableOpacity
          style={styles.controlButton}
          onPress={handleCheckPermissions}
        >
          <Text style={styles.controlButtonText}>Check Permissions</Text>
        </TouchableOpacity>
        
        <TouchableOpacity
          style={[styles.controlButton, styles.startButton]}
          onPress={handleStartServices}
        >
          <Text style={styles.controlButtonText}>
            {servicesStarted ? 'Services Running' : 'Start Services'}
          </Text>
        </TouchableOpacity>
        
        <TouchableOpacity
          style={[styles.controlButton, styles.refreshButton]}
          onPress={() => {
            fetchData();
            fetchActiveBypasses();
          }}
          disabled={loading}
        >
          <Text style={styles.controlButtonText}>
            {loading ? 'Refreshing...' : 'Refresh All'}
          </Text>
        </TouchableOpacity>
      </View>

      <View style={styles.legend}>
        <View style={styles.legendItem}>
          <View style={[styles.legendColor, styles.legendNone]} />
          <Text style={styles.legendText}>Level 0: Not Blocked</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendColor, styles.legendNudge]} />
          <Text style={styles.legendText}>Level 1: Nudge/Awareness</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendColor, styles.legendChallenge]} />
          <Text style={styles.legendText}>Level 2: Challenge/Friction</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendColor, styles.legendHard]} />
          <Text style={styles.legendText}>Level 3: Hard Block</Text>
        </View>
      </View>

      <Text style={styles.appsCount}>
        {filteredApps.length} of {apps.length} {apps.length === 1 ? 'App' : 'Apps'} Shown
        {Object.keys(activeBypasses).length > 0 && ` • ${Object.keys(activeBypasses).length} Active Bypass${Object.keys(activeBypasses).length !== 1 ? 'es' : ''}`}
      </Text>
    </View>
  );

  if (loading && apps.length === 0) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color="#4A90E2" />
        <Text style={styles.loadingText}>Loading apps and rules...</Text>
      </View>
    );
  }

  if (error && apps.length === 0) {
    return (
      <View style={styles.centerContainer}>
        <Text style={styles.errorText}>Error: {error}</Text>
        <TouchableOpacity style={styles.retryButton} onPress={fetchData}>
          <Text style={styles.retryButtonText}>Retry</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <StatusBar backgroundColor="#4A90E2" barStyle="light-content" />
      
      {/* Feature A: Search Bar - EXTRACTED OUTSIDE FlatList */}
      <View style={styles.searchContainer}>
        <TextInput
          style={styles.searchInput}
          placeholder="🔍 Cari aplikasi..."
          value={searchQuery}
          onChangeText={setSearchQuery}
          clearButtonMode="while-editing"
        />
      </View>
      
      <FlatList
        data={filteredApps}
        renderItem={renderAppItem}
        keyExtractor={item => item.packageName}
        ListHeaderComponent={renderSettingsPanel}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>
              {searchQuery ? 'No apps match your search' : 'No apps found'}
            </Text>
          </View>
        }
        contentContainerStyle={styles.listContent}
        showsVerticalScrollIndicator={true}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8f9fa',
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f8f9fa',
    padding: 20,
  },
  loadingText: {
    marginTop: 20,
    fontSize: 16,
    color: '#666',
  },
  errorText: {
    fontSize: 16,
    color: '#e74c3c',
    textAlign: 'center',
    marginBottom: 20,
  },
  retryButton: {
    backgroundColor: '#4A90E2',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 6,
  },
  retryButtonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
  },
  listContent: {
    paddingBottom: 30,
  },
  header: {
    backgroundColor: 'white',
    padding: 20,
    borderBottomWidth: 1,
    borderBottomColor: '#e9ecef',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 16,
    color: '#7f8c8d',
    marginBottom: 16,
  },
  // Feature A: Search Bar Styles
  searchContainer: {
    backgroundColor: 'white',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#e9ecef',
  },
  searchInput: {
    backgroundColor: '#f8f9fa',
    borderWidth: 1,
    borderColor: '#dee2e6',
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 16,
    color: '#495057',
  },
  // Feature C: Settings Styles
  settingsToggle: {
    backgroundColor: '#f8f9fa',
    borderWidth: 1,
    borderColor: '#dee2e6',
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 12,
    marginBottom: 12,
  },
  settingsToggleText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#4A90E2',
  },
  settingsContainer: {
    backgroundColor: '#f8f9fa',
    borderWidth: 1,
    borderColor: '#dee2e6',
    borderRadius: 8,
    padding: 16,
    marginBottom: 16,
  },
  durationSetting: {
    marginBottom: 16,
  },
  durationLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#2c3e50',
    marginBottom: 8,
  },
  durationControls: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  durationButton: {
    backgroundColor: '#4A90E2',
    width: 40,
    height: 40,
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
  },
  durationButtonText: {
    color: 'white',
    fontSize: 20,
    fontWeight: 'bold',
  },
  durationValue: {
    fontSize: 18,
    fontWeight: '600',
    color: '#2c3e50',
  },
  durationNote: {
    fontSize: 14,
    color: '#7f8c8d',
    fontStyle: 'italic',
    marginTop: 8,
  },
  applyButton: {
    backgroundColor: '#28a745',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 6,
    alignItems: 'center',
    marginTop: 8,
    marginBottom: 8,
  },
  applyButtonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
  },
  controlSection: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 20,
  },
  controlButton: {
    backgroundColor: '#6c757d',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 6,
    flex: 1,
    marginHorizontal: 4,
    alignItems: 'center',
  },
  startButton: {
    backgroundColor: '#28a745',
  },
  refreshButton: {
    backgroundColor: '#17a2b8',
  },
  controlButtonText: {
    color: 'white',
    fontSize: 14,
    fontWeight: '600',
  },
  legend: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
    width: '48%',
  },
  legendColor: {
    width: 16,
    height: 16,
    borderRadius: 3,
    marginRight: 8,
  },
  legendNone: {
    backgroundColor: '#6c757d',
  },
  legendNudge: {
    backgroundColor: '#ffc107',
  },
  legendChallenge: {
    backgroundColor: '#fd7e14',
  },
  legendHard: {
    backgroundColor: '#dc3545',
  },
  legendText: {
    fontSize: 12,
    color: '#6c757d',
  },
  appsCount: {
    fontSize: 14,
    color: '#6c757d',
    textAlign: 'center',
    marginTop: 8,
  },
  appItem: {
    backgroundColor: 'white',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#e9ecef',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  appInfo: {
    flex: 1,
    marginRight: 12,
  },
  appName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#2c3e50',
    marginBottom: 4,
  },
  packageName: {
    fontSize: 14,
    color: '#6c757d',
    marginBottom: 4,
  },
  // Feature D: Active Bypass Indicator Styles
  bypassIndicator: {
    backgroundColor: '#d4edda',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
    marginTop: 4,
  },
  bypassText: {
    fontSize: 12,
    color: '#155724',
    fontWeight: '500',
  },
  levelButtons: {
    flexDirection: 'row',
  },
  levelButton: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 6,
    marginLeft: 4,
    minWidth: 50,
    alignItems: 'center',
  },
  level0Button: {
    backgroundColor: '#6c757d',
  },
  level1Button: {
    backgroundColor: '#ffc107',
  },
  level2Button: {
    backgroundColor: '#fd7e14',
  },
  level3Button: {
    backgroundColor: '#dc3545',
  },
  levelButtonUnselected: {
    opacity: 0.3,
  },
  levelButtonSelected: {
    opacity: 1,
  },
  levelButtonText: {
    fontSize: 14,
    fontWeight: '600',
  },
  levelButtonTextUnselected: {
    color: 'rgba(255, 255, 255, 0.7)',
  },
  levelButtonTextSelected: {
    color: 'white',
  },
  emptyContainer: {
    padding: 40,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 16,
    color: '#6c757d',
    textAlign: 'center',
  },
});
