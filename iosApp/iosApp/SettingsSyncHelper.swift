import Foundation

/// Helper to sync settings to App Group for widget access
class SettingsSyncHelper {
    static let appGroupIdentifier = "group.org.androdevlinux.utxo"
    
    /// Sync settings file to App Group container
    /// Call this whenever settings are updated in the app
    static func syncSettingsToAppGroup() {
        guard let appGroupURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupIdentifier) else {
            print("SettingsSyncHelper: App Group not configured: \(appGroupIdentifier)")
            return
        }
        
        // Get the main app's cache directory
        guard let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else {
            print("SettingsSyncHelper: Could not find cache directory")
            return
        }
        
        let sourceFile = cacheDir.appendingPathComponent("settings.json")
        let destinationFile = appGroupURL.appendingPathComponent("settings.json")
        
        // Check if source file exists
        guard FileManager.default.fileExists(atPath: sourceFile.path) else {
            print("SettingsSyncHelper: Source settings file does not exist at \(sourceFile.path)")
            return
        }
        
        do {
            // Copy settings file to App Group container
            try FileManager.default.copyItem(at: sourceFile, to: destinationFile)
            print("SettingsSyncHelper: Successfully synced settings to App Group at \(destinationFile.path)")
        } catch {
            // If file exists, try removing and copying again
            if FileManager.default.fileExists(atPath: destinationFile.path) {
                do {
                    try FileManager.default.removeItem(at: destinationFile)
                    try FileManager.default.copyItem(at: sourceFile, to: destinationFile)
                    print("SettingsSyncHelper: Successfully updated settings in App Group")
                } catch {
                    print("SettingsSyncHelper: Failed to update settings in App Group: \(error)")
                }
            } else {
                print("SettingsSyncHelper: Failed to copy settings to App Group: \(error)")
            }
        }
    }
    
    /// Sync favorites to UserDefaults (simpler alternative)
    static func syncFavoritesToUserDefaults(favPairs: [String], selectedTradingPair: String, appTheme: String?) {
        guard let sharedDefaults = UserDefaults(suiteName: appGroupIdentifier) else {
            print("SettingsSyncHelper: App Group UserDefaults not available")
            return
        }
        
        if let favPairsData = try? JSONEncoder().encode(favPairs) {
            sharedDefaults.set(favPairsData, forKey: "favPairs")
            sharedDefaults.set(selectedTradingPair, forKey: "selectedTradingPair")
            if let appTheme = appTheme {
                sharedDefaults.set(appTheme, forKey: "appTheme")
            }
            sharedDefaults.synchronize()
            print("SettingsSyncHelper: Synced favorites to UserDefaults: \(favPairs)")
        }
    }
}

