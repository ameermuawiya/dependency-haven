package io.thorenkoder.android.fragments;

import static io.thorenkoder.android.util.BaseUtil.Path;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.documentfile.provider.DocumentFile;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.annotation.SuppressLint;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
// --- ADD THIS IMPORT ---
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import io.thorenkoder.android.R;
import io.thorenkoder.android.SharedPreferenceKeys;
import io.thorenkoder.android.adapter.PagerAdapter;
import io.thorenkoder.android.databinding.FragmentMainBinding;
import io.thorenkoder.android.util.BaseUtil;
import io.thorenkoder.android.util.Constants;
import io.thorenkoder.android.util.PreferencesUtils;
import io.thorenkoder.android.util.SDKUtil;
import io.thorenkoder.android.util.SDKUtil.API;
import android.content.SharedPreferences;
import java.io.File;
import mod.agus.jcoderz.lib.FileUtil;

public class MainFragment extends Fragment {

  public static final String TAG = MainFragment.class.getSimpleName();
  private PagerAdapter adapter;
  private MenuItem trackableItem;
  private FragmentMainBinding binding;
  private SVM sVM;
  private ActivityResultLauncher<Intent> pickFolderResult;
  private MaterialToolbar toolbar;

  private ActivityResultLauncher<String[]> mPermissionLauncher;
  private final ActivityResultContracts.RequestMultiplePermissions mPermissionsContract =
      new ActivityResultContracts.RequestMultiplePermissions();

  public static MainFragment newInstance() {
    return new MainFragment();
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    sVM = new ViewModelProvider(requireActivity()).get(SVM.class);

    mPermissionLauncher =
        registerForActivityResult(
            mPermissionsContract,
            isGranted -> {
              if (isGranted.containsValue(true)) {
                String libPref =
                    PreferencesUtils.getDefaultPreferences()
                        .getString(SharedPreferenceKeys.KEY_LIBRARY_MANAGER, "");
                if (libPref == null || (libPref != null && !new File(libPref).exists())) {
                  new MaterialAlertDialogBuilder(requireActivity())
                      .setTitle(R.string.select_download_dir_title)
                      .setMessage(R.string.select_download_dir_msg)
                      .setPositiveButton(
                          R.string.pick_folder,
                          (d, which) -> {
                            selectFolder();
                          })
                      .setNegativeButton(R.string.cancel, null)
                      .setCancelable(false)
                      .show();
                }
              } else {
                new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.storage_permission_denied)
                    .setMessage(R.string.storage_permission_denial_prompt)
                    .setPositiveButton(
                        R.string.storage_permission_request_again,
                        (d, which) -> {
                          requestPermission();
                        })
                    .setNegativeButton(
                        R.string.exit,
                        (d, which) -> {
                          requireActivity().finishAffinity();
                          System.exit(0);
                        })
                    .setCancelable(false)
                    .show();
              }
            });

    pickFolderResult =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == Activity.RESULT_OK) {
                Intent intent = result.getData();
                if (intent != null) {
                  Uri folderUri = intent.getData();
                  if (folderUri != null) {
                    onFolderSelected(folderUri);
                  }
                }
              }
            });

    // CHANGE: The old checkDexingRes() call is replaced with the new intelligent method.
    if (!isPermissionGranted(requireActivity())) {
      requestPermission();
    } else {
      initializeDexingResources();
    }
  }

  // UPDATED METHOD
  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    binding = FragmentMainBinding.inflate(inflater, container, false);
    toolbar = binding.toolbar;
    configurePages();
    configureBottomNavigation();
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    sVM.canRequestStoragePermission()
        .observe(
            getViewLifecycleOwner(),
            canRequest -> {
              if (canRequest) {
                requestPermission();
              }
            });

    sVM.canSelectFolder()
        .observe(
            getViewLifecycleOwner(),
            canSelectFolder -> {
              if (canSelectFolder) {
                selectFolder();
              }
            });
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    this.binding = null;
  }

  /**
   * Sets the title of the shared Toolbar. Can be called from any child fragment.
   *
   * @param title The new title to display.
   */
  public void setToolbarTitle(String title) {
    if (toolbar != null) {
      toolbar.setTitle(title);
    }
  }

  /**
   * Sets the menu for the shared Toolbar and handles its clicks. Can be called from any child
   * fragment to show context-specific options.
   *
   * @param menuResId The resource ID of the menu (e.g., R.menu.downloads_menu).
   * @param listener The listener to handle menu item clicks.
   */
  public void setToolbarMenu(int menuResId, Toolbar.OnMenuItemClickListener listener) {
    if (toolbar != null) {
      toolbar.getMenu().clear(); // Remove old menu items
      toolbar.inflateMenu(menuResId); // Add new menu items
      toolbar.setOnMenuItemClickListener(listener);
    }
  }

  /**
   * Provides direct access to the Toolbar for more advanced control.
   *
   * @return The MaterialToolbar instance.
   */
  public MaterialToolbar getToolbar() {
    return toolbar;
  }

  private void configurePages() {
    adapter = new PagerAdapter(getChildFragmentManager(), getLifecycle());
    adapter.addFragment(DependencyManagerFragment.newInstance());
    adapter.addFragment(DownloadsFragment.newInstance());
    adapter.addFragment(SettingsFragment.newInstance());
    binding.pager.setOffscreenPageLimit(3);
    binding.pager.setUserInputEnabled(false);
    binding.pager.setAdapter(adapter);
  }

  // NEW METHOD to replace configureNavigationRail()
  private void configureBottomNavigation() {
    // Get the BottomNavigationView from the binding
    BottomNavigationView bottomNav = binding.bottomNavigation;

    // Set up the listener for item selection
    bottomNav.setOnItemSelectedListener(
        item -> {
          int itemId = item.getItemId();

          if (itemId == R.id.nav_dependency) { // Use the ID from your menu file
            binding.pager.setCurrentItem(0, false);
          } else if (itemId == R.id.nav_downloads) { // Use the ID from your menu file
            binding.pager.setCurrentItem(1, false);
          } else if (itemId == R.id.nav_settings) { // Use the ID from your menu file
            binding.pager.setCurrentItem(2, false);
          }

          return true;
        });
  }

  private void displayOpenYtChannelDialog() {
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
    builder.setTitle(getContext().getString(R.string.msg_title_yt));
    builder.setMessage(getContext().getString(R.string.msg_yt_body));
    builder.setPositiveButton(
        getContext().getString(R.string.dialog_yt_positive_botton),
        (dialog, which) -> openYtChannel());
    builder.setNegativeButton(
        getContext().getString(R.string.dialog_yt_negative_botton),
        (dialog, which) -> dialog.dismiss());
    builder.setCancelable(false);
    builder.show();
  }

  private void openYtChannel() {
    String url = Constants.YOUTUBE_CHANNEL_LINK;
    Intent i = new Intent(Intent.ACTION_VIEW);
    i.setData(Uri.parse(url));
    startActivity(i);
  }

  private void onFolderSelected(Uri uri) {
    try {
      DocumentFile pickedDir = DocumentFile.fromTreeUri(requireContext(), uri);
      File directory =
          new File(FileUtil.convertUriToFilePath(requireActivity(), pickedDir.getUri()));
      String folderPath = directory.getAbsolutePath();
      if (folderPath != null) {
        sVM.setCanSelectFolder(false);
        // save currently selected folder for library downloads
        PreferencesUtils.getDefaultPreferences()
            .edit()
            .putString(SharedPreferenceKeys.KEY_LIBRARY_MANAGER, directory.getAbsolutePath())
            .apply();
      }
    } catch (Exception e) {
      e.printStackTrace();
      BaseUtil.showToast(e.getMessage());
    }
  }

  private void selectFolder() {
    pickFolderResult.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE));
  }

  private void checkDownloadDir() {
    String path =
        PreferencesUtils.getDefaultPreferences()
            .getString(SharedPreferenceKeys.KEY_LIBRARY_MANAGER, null);
    if (path == null) {
      if (isPermissionGranted(requireActivity())) {
        selectFolder();
      } else {
        requestPermission();
      }
    }
  }

  private void requestPermission() {
    if (SDKUtil.isAtLeast(API.ANDROID_11)) {
      Intent intent = new Intent();
      intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
      Uri uri = Uri.fromParts("package", getActivity().getPackageName(), null);
      intent.setData(uri);
      getActivity().startActivity(intent);
      sVM.setCanRequestStoragePermissionState(false);
    } else {
      sVM.setCanRequestStoragePermissionState(false);
      mPermissionLauncher.launch(
          new String[] {
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE
          });
    }
  }

  @SuppressLint("NewApi")
  public static boolean isPermissionGranted(Context context) {
    if (SDKUtil.isAtLeast(API.ANDROID_11)) {
      return Environment.isExternalStorageManager();
    } else {
      return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
              == PackageManager.PERMISSION_GRANTED
          && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
              == PackageManager.PERMISSION_GRANTED;
    }
  }

  private void checkDexingRes() {
    if (!Path.ANDROID_JAR.exists()) {
      BaseUtil.unzipFromAssets("dexing-res/android.zip", Path.RESOURCE_FOLDER.getAbsolutePath());
    }
    if (!Path.CORE_LAMDA_STUBS.exists()) {
      BaseUtil.unzipFromAssets(
          "dexing-res/core-lambda-stubs.zip", Path.RESOURCE_FOLDER.getAbsolutePath());
    }
  }

  public static class SVM extends ViewModel {

    private MutableLiveData<Boolean> canRequestStoragePermission = new MutableLiveData<>(false);

    private MutableLiveData<Boolean> canSelectFolder = new MutableLiveData<>(false);

    public LiveData<Boolean> canRequestStoragePermission() {
      return this.canRequestStoragePermission;
    }

    public void setCanRequestStoragePermissionState(boolean enabled) {
      this.canRequestStoragePermission.setValue(enabled);
    }

    public LiveData<Boolean> canSelectFolder() {
      return this.canSelectFolder;
    }

    public void setCanSelectFolder(boolean enabled) {
      this.canSelectFolder.setValue(enabled);
    }
  }

  /**
   * Checks if dexing resources are initialized, and if not, runs the extraction on a background
   * thread. This is a one-time operation.
   */
  private void initializeDexingResources() {
    SharedPreferences prefs = PreferencesUtils.getPrivatePreferences();
    boolean areResourcesInitialized = prefs.getBoolean("dex_resources_initialized", false);

    // If already initialized, do nothing.
    if (areResourcesInitialized) {
      return;
    }

    // Run the heavy task on a background thread to not block the UI.
    new Thread(
            () -> {
              checkDexingRes();
              // After the task is done, save the flag so it doesn't run again.
              prefs.edit().putBoolean("dex_resources_initialized", true).apply();
            })
        .start();
  }
}
