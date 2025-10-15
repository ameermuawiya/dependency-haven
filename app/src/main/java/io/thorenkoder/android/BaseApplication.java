package io.thorenkoder.android;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Process;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import io.thorenkoder.android.util.PreferencesUtils;
import io.thorenkoder.android.util.SingletonContext;
import java.io.File;

public class BaseApplication extends Application {

    private static BaseApplication instance;
    public static Context applicationContext;

    @Override
    public void onCreate() {
        super.onCreate();
        initialize();
        setupDefaultLibraryPath(); // Sets up the default library path on startup
        setPowerfulCrashHandler();
        applyAppTheme();
        useDynamicColor();
    }

    public static BaseApplication getInstance() {
        return instance;
    }

    private void setPowerfulCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler(
            (thread, throwable) -> handleUncaughtException(thread, throwable));
    }

    private void handleUncaughtException(Thread thread, Throwable throwable) {
        Intent intent = new Intent(getApplicationContext(), DebugActivity.class);
        intent.putExtra("error", Log.getStackTraceString(throwable));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        Process.killProcess(Process.myPid());
        System.exit(10);
    }

    private void initialize() {
        instance = this;
        applicationContext = this;
        SingletonContext.initialize(applicationContext);
    }

    /**
     * Checks if a library storage path is set. If not, creates and sets a default path.
     * This method is powerful and safe, enclosed in a try-catch block.
     */
    private void setupDefaultLibraryPath() {
        SharedPreferences prefs = PreferencesUtils.getDefaultPreferences();
        String currentPath = prefs.getString(SharedPreferenceKeys.KEY_LIBRARY_MANAGER, "");

        // Only proceed if the path is not already set by the user
        if (currentPath == null || currentPath.isEmpty()) {
            try {
                // Define the default directory within the public Downloads folder
                File defaultDir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "LibVault"
                );

                // Create the directory if it doesn't exist
                if (!defaultDir.exists()) {
                    if (!defaultDir.mkdirs()) {
                        // Log an error if directory creation fails
                        Log.e("BaseApplication", "Failed to create default library directory.");
                        return; // Stop if we can't create the folder
                    }
                }

                // Save the new default path to SharedPreferences
                prefs.edit()
                     .putString(SharedPreferenceKeys.KEY_LIBRARY_MANAGER, defaultDir.getAbsolutePath())
                     .apply();
                Log.i("BaseApplication", "Default library path set to: " + defaultDir.getAbsolutePath());

            } catch (Exception e) {
                // Catch any other potential exceptions during file operations
                Log.e("BaseApplication", "Error setting up default library path", e);
            }
        }
    }

    /**
     * Reads the saved theme from SharedPreferences and applies it.
     * If no theme is set, it defaults to 'Auto/System Default'.
     */
    public void applyAppTheme() {
        SharedPreferences prefs = PreferencesUtils.getDefaultPreferences();
        String themeValue = prefs.getString(SharedPreferenceKeys.KEY_APP_THEME, "3");

        int themeMode;
        switch (themeValue) {
            case "1":
                themeMode = AppCompatDelegate.MODE_NIGHT_NO; // Light
                break;
            case "2":
                themeMode = AppCompatDelegate.MODE_NIGHT_YES; // Dark
                break;
            default:
            case "3":
                themeMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; // Auto
                break;
        }
        AppCompatDelegate.setDefaultNightMode(themeMode);
    }

    private void useDynamicColor() {
        if (PreferencesUtils.useDynamicColors()) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }
    }
}