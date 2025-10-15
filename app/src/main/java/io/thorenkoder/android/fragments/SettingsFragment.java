package io.thorenkoder.android.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.material.transition.MaterialSharedAxis;
import io.thorenkoder.android.R;
import io.thorenkoder.android.SharedPreferenceKeys;
import io.thorenkoder.android.AboutActivity;
import io.thorenkoder.android.util.PreferencesUtils;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = SettingsFragment.class.getSimpleName();
    private MainFragment.SVM svm;
    private Preference preferenceDownloadDir;

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        svm = new ViewModelProvider(requireActivity()).get(MainFragment.SVM.class);
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Y, false));
        setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.Y, true));
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey);

        Preference preferenceTheme = findPreference(SharedPreferenceKeys.KEY_APP_THEME);
        if (preferenceTheme != null) {
            preferenceTheme.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof String) {
                    int newTheme = PreferencesUtils.getCurrentTheme((String) newValue);
                    AppCompatDelegate.setDefaultNightMode(newTheme);
                    return true;
                }
                return false;
            });
        }

        preferenceDownloadDir = findPreference(SharedPreferenceKeys.KEY_LIBRARY_MANAGER);
        updateDownloadDirSummary();
        if (preferenceDownloadDir != null) {
            preferenceDownloadDir.setOnPreferenceClickListener(preference -> {
                if (MainFragment.isPermissionGranted(requireActivity())) {
                    svm.setCanSelectFolder(true);
                } else {
                    svm.setCanRequestStoragePermissionState(true);
                }
                return true;
            });
        }

        Preference appVersionPref = findPreference("pref_app_version");
        if (appVersionPref != null) {
            appVersionPref.setSummary(getAppVersion());
        }

        Preference aboutPref = findPreference("pref_about");
        if (aboutPref != null) {
            aboutPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), AboutActivity.class);
                startActivity(intent);
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getParentFragment() instanceof MainFragment) {
            MainFragment mainFragment = (MainFragment) getParentFragment();
            mainFragment.setToolbarTitle("Settings");
            if (mainFragment.getToolbar() != null) {
                mainFragment.getToolbar().getMenu().clear();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (SharedPreferenceKeys.KEY_LIBRARY_MANAGER.equals(key)) {
            updateDownloadDirSummary();
        }
    }

    private void updateDownloadDirSummary() {
        if (preferenceDownloadDir != null) {
            String libPref = getPreferenceManager().getSharedPreferences()
                    .getString(SharedPreferenceKeys.KEY_LIBRARY_MANAGER, "");
            if (libPref != null && !libPref.isEmpty()) {
                preferenceDownloadDir.setSummary(libPref);
            } else {
                preferenceDownloadDir.setSummary(R.string.pref_download_dir_not_set);
            }
        }
    }

    private String getAppVersion() {
        try {
            return requireActivity()
                    .getPackageManager()
                    .getPackageInfo(requireActivity().getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "Not available";
        }
    }
}