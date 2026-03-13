import React, { useState, useEffect } from 'react';
import {
  StyleSheet,
  Text,
  View,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  ScrollView,
  StatusBar,
} from 'react-native';
import FocusGuardNative from './src/native';

export default function App() {
  const [apps, setApps] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [servicesStarted, setServicesStarted] = useState(false);

  // Fetch installed apps and app rules on mount
  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);
      
      // Fetch both installed apps and app rules in parallel
      const [installedApps, appRules] = await Promise.all([
        FocusGuardNative.getInstalledApps(),
        FocusGuardNative.getAllAppRules(),
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
    } catch (err) {
      console.error('Failed to fetch data:', err);
      setError(err.message || 'Failed to load apps and rules');
      Alert.alert('Error', 'Failed to load apps and rules. Please try again.');
    } finally {
      setLoading(false);
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

  const renderAppItem = ({ item }) => (
    <View style={styles.appItem}>
      <View style={styles.appInfo}>
        <Text style={styles.appName} numberOfLines={1}>
          {item.appName}
        </Text>
        <Text style={styles.packageName} numberOfLines={1}>
          {item.packageName}
        </Text>
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

  const renderHeader = () => (
    <View style={styles.header}>
      <Text style={styles.title}>FocusGuard - Phase 2 Dashboard</Text>
      <Text style={styles.subtitle}>Dynamic App Blocking Rules</Text>
      
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
          onPress={fetchData}
          disabled={loading}
        >
          <Text style={styles.controlButtonText}>
            {loading ? 'Refreshing...' : 'Refresh Apps'}
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
        {apps.length} {apps.length === 1 ? 'App' : 'Apps'} Loaded
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
      
      <FlatList
        data={apps}
        renderItem={renderAppItem}
        keyExtractor={item => item.packageName}
        ListHeaderComponent={renderHeader}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>No apps found</Text>
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
    marginBottom: 20,
  },
  controlSection: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    marginBottom: 20,
  },
  controlButton: {
    backgroundColor: '#6c757d',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 6,
    marginBottom: 10,
    flex: 1,
    marginHorizontal: 4,
    minWidth: '30%',
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
    textAlign: 'center',
  },
  legend: {
    backgroundColor: '#f8f9fa',
    padding: 12,
    borderRadius: 6,
    marginBottom: 16,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 6,
  },
  legendColor: {
    width: 16,
    height: 16,
    borderRadius: 3,
    marginRight: 8,
  },
  legendNone: {
    backgroundColor: '#e9ecef',
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
    fontSize: 13,
    color: '#495057',
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
    marginBottom: 2,
  },
  packageName: {
    fontSize: 12,
    color: '#7f8c8d',
  },
  levelButtons: {
    flexDirection: 'row',
  },
  levelButton: {
    width: 48,
    height: 36,
    borderRadius: 6,
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 4,
    borderWidth: 1,
    borderColor: '#e9ecef',
  },
  levelButtonUnselected: {
    opacity: 0.5,
  },
  levelButtonSelected: {
    borderWidth: 3,
    borderColor: '#2c3e50',
    opacity: 1,
  },
  level0Button: {
    backgroundColor: '#e9ecef',
  },
  level1Button: {
    backgroundColor: '#fff3cd',
  },
  level2Button: {
    backgroundColor: '#ffe5d0',
  },
  level3Button: {
    backgroundColor: '#f8d7da',
  },
  levelButtonText: {
    fontSize: 12,
    fontWeight: '500',
  },
  levelButtonTextUnselected: {
    color: '#6c757d',
    fontWeight: '400',
  },
  levelButtonTextSelected: {
    color: '#2c3e50',
    fontWeight: 'bold',
  },
  emptyContainer: {
    padding: 40,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 16,
    color: '#6c757d',
  },
});