package io.thorenkoder.android.fragments;

import static eup.dependency.haven.repository.Repository.Manager;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.transition.MaterialSharedAxis;
import eup.dependency.haven.api.CachedLibrary;
import eup.dependency.haven.callback.DependencyResolutionCallback;
import eup.dependency.haven.callback.DownloadCallback;
import eup.dependency.haven.model.Coordinates;
import eup.dependency.haven.model.Dependency;
import eup.dependency.haven.model.Pom;
import eup.dependency.haven.parser.PomParser;
import eup.dependency.haven.repository.LocalStorageFactory;
import eup.dependency.haven.repository.RemoteRepository;
import eup.dependency.haven.resolver.DependencyResolver;
import io.thorenkoder.android.SharedPreferenceKeys;
import io.thorenkoder.android.api.library.LocalLibraryManager;
import io.thorenkoder.android.databinding.FragmentDependencyManagerBinding;
import io.thorenkoder.android.logging.LogAdapter;
import io.thorenkoder.android.logging.LogViewModel;
import io.thorenkoder.android.logging.Logger;
import io.thorenkoder.android.util.BaseUtil.Path;
import io.thorenkoder.android.util.PreferencesUtils;
// Add these imports for network state checking
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;
import io.thorenkoder.android.R;
// Replace the old import for LogItem with this one
import io.thorenkoder.android.logging.Log;

public class DependencyManagerFragment extends Fragment
    implements SharedPreferences.OnSharedPreferenceChangeListener {

  public static final String TAG = DependencyManagerFragment.class.getSimpleName();
  private FragmentDependencyManagerBinding binding;
  private Logger logger;
  private LogAdapter logAdapter;
  private LogViewModel model;
  private Coordinates coordinates = null;
  private LocalStorageFactory storageFactory;
  private LocalLibraryManager libraryManager;
  private DependencyResolver resolver;

  public static DependencyManagerFragment newInstance() {
    return new DependencyManagerFragment();
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    PreferencesUtils.getDefaultPreferences().registerOnSharedPreferenceChangeListener(this);
    PreferencesUtils.getPrivatePreferences().registerOnSharedPreferenceChangeListener(this);
    setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Y, false));
    setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.Y, true));
    model = new ViewModelProvider(this).get(LogViewModel.class);
  }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup viewgroup, Bundle savedInstanceState) {
    binding = FragmentDependencyManagerBinding.inflate(inflater, viewgroup, false);
    setupRecyclerView();
    updateSwitch();
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    // Listener for the new switch ID
    binding.transitiveDependenciesSwitch.setOnCheckedChangeListener(
        (button, isChecked) -> updateSkipDependencyPreferences(isChecked));

    binding.downloadButton.setOnClickListener(
        v -> {
          // ADDED: Internet availability check
          if (!isNetworkAvailable()) {
            logger.e(
                "Network Error",
                "Internet connection is not available. Please check your connection and try again.");
            return; // Stop further execution if no internet
          }

          // Existing logic now runs only if internet is available
          clearLogs();
          setDownloadingUIState(true);
          resolveDependencies();
        });

    // Listener for the new Stop Button
    binding.stopButton.setOnClickListener(
        v -> {
          if (resolver != null) {
            // IMPORTANT: You need to implement a cancel() method in your DependencyResolver class
            resolver.cancel();
          }
          logger.e("CANCELLED", "Download cancelled by user.");
          setDownloadingUIState(false); // Reset UI
        });

    // Listener for the new Copy Logs Button
    binding.copyLogsButton.setOnClickListener(
        v -> {
          copyLogsToClipboard();
        });
  }
    

@Override
public void onResume() {
    super.onResume();
    
    // Set the toolbar title and clear any previous menus for this screen
    if (getParentFragment() instanceof MainFragment) {
        MainFragment mainFragment = (MainFragment) getParentFragment();
        
        // Set the title for this fragment
        mainFragment.setToolbarTitle("Dependency Manager");
        
        // Clear any menu items from the previous fragment to ensure a clean state
        if (mainFragment.getToolbar() != null) {
            mainFragment.getToolbar().getMenu().clear();
        }
    }
}

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    this.binding = null;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    PreferencesUtils.getDefaultPreferences().unregisterOnSharedPreferenceChangeListener(this);
    PreferencesUtils.getPrivatePreferences().unregisterOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
    switch (key) {
      case SharedPreferenceKeys.KEY_SKIP_SUB_DEPENDENCIES:
        updateSwitch();
        break;
    }
  }

  private void updateSwitch() {
    boolean isChecked =
        PreferencesUtils.getPrivatePreferences()
            .getBoolean(SharedPreferenceKeys.KEY_SKIP_SUB_DEPENDENCIES, false);
    // Using the new Switch ID
    binding.transitiveDependenciesSwitch.setChecked(isChecked);
  }

  private void updateSkipDependencyPreferences(boolean isChecked) {
    PreferencesUtils.getPrivatePreferences()
        .edit()
        .putBoolean(SharedPreferenceKeys.KEY_SKIP_SUB_DEPENDENCIES, isChecked)
        .apply();
  }

  private void setupRecyclerView() {
    logAdapter = new LogAdapter();
    storageFactory = new LocalStorageFactory();
    File cacheDirectory = requireContext().getExternalFilesDir("cache");
    storageFactory.setCacheDirectory(cacheDirectory);

    String libSaveDir =
        PreferencesUtils.getDefaultPreferences()
            .getString(SharedPreferenceKeys.KEY_LIBRARY_MANAGER, "");

    libraryManager = new LocalLibraryManager(storageFactory, new File(libSaveDir));

    // --- START: THE FINAL FIX ---
    // The "BaseUtil." prefix is removed to match the import statement. This will fix the build error.
    libraryManager.setCompileResourcesClassPath(Path.ANDROID_JAR, Path.CORE_LAMDA_STUBS);
    // --- END: THE FINAL FIX ---

    logger = new Logger();
    logger.attach(this);

    binding.logRecyclerview.setLayoutManager(new LinearLayoutManager(requireActivity()));
    binding.logRecyclerview.setAdapter(logAdapter);

    model
        .getLogs()
        .observe(
            getViewLifecycleOwner(),
            data -> {
                logAdapter.submitList(data);
                scrollToLastItem();
            });
}

  private void clearLogs() {
    if (logger != null) {
      logger.clear();
      logAdapter.notifyDataSetChanged();
    }
  }

  private void scrollToLastItem() {
    int itemCount = logAdapter.getItemCount();
    if (itemCount > 0) {
      binding.logRecyclerview.scrollToPosition(itemCount - 1);
    }
  }

 private void resolveDependencies() {
    // Input Validation
    try {
        String dependencyString = binding.dependencyEditText.getText().toString();
        if (dependencyString.isEmpty()) {
            logger.e("Input Error", "Please enter a dependency to download.");
            setDownloadingUIState(false);
            return;
        }
        coordinates = Coordinates.valueOf(dependencyString);
    } catch (IllegalArgumentException e) {
        logger.e("Format Error", "The dependency format is invalid. Example: com.google.android:material:1.0.0");
        setDownloadingUIState(false);
        return;
    } catch (Exception e) {
        logger.e("Unknown Error", "An unexpected error occurred while parsing the dependency: " + e.getMessage());
        setDownloadingUIState(false);
        return;
    }

    resolver = new DependencyResolver(storageFactory, coordinates);
    storageFactory.attach(resolver);
    configureRepositories(resolver, logger);

    boolean includeTransitive = binding.transitiveDependenciesSwitch.isChecked();
    resolver.skipInnerDependencies(!includeTransitive);

    if (libraryManager != null) {
        libraryManager.setTaskListener(
            new LocalLibraryManager.TaskListener() {
                @Override
                public void info(String message) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> logger.p("INFO", message));
                    }
                }

                @Override
                public void error(String message) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> logger.e("ERROR", message));
                    }
                }
            });
    }

    storageFactory.setDownloadCallback(
    new DownloadCallback() {
        @Override
        public void info(String message) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> logger.p("INFO", message));
        }

        @Override
        public void error(String message) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> logger.e("ERROR", message));
        }

        @Override
        public void warning(String message) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> logger.w("WARNING", message));
        }

        @Override
        public void done(List<CachedLibrary> cachedLibraryList) {
            if (getActivity() == null) return;
            
            if (cachedLibraryList == null || cachedLibraryList.isEmpty()) {
                getActivity().runOnUiThread(() -> {
                    logger.e("FAILURE", "Download finished, but no library files were successfully retrieved.");
                    setDownloadingUIState(false);
                });
                return;
            }

            new Thread(() -> {
                // This block runs in the background.
                final boolean success = libraryManager.copyCachedLibrary(cachedLibraryList);

                // After the background task is finished, update the UI on the main thread.
                getActivity().runOnUiThread(() -> {
                    if (success) {
                        String savePath =
                            PreferencesUtils.getDefaultPreferences()
                                .getString(
                                    SharedPreferenceKeys.KEY_LIBRARY_MANAGER, "Unknown location");
                        
                        String successMessage =
                            "\n========================================\n"
                                + "✅ DOWNLOAD & COMPILE SUCCESSFUL!\n"
                                + "Total Libraries Processed: " + cachedLibraryList.size() + "\n"
                                + "Saved in: "
                                + savePath
                                + "\n========================================";
                        logger.p("COMPLETE", successMessage);
                    } else {
                        String failureMessage =
                            "\n========================================\n"
                                + "❌ PROCESS FAILED\n"
                                + "Libraries were downloaded, but one or more failed during processing (copying, unzipping, or dexing).\n"
                                + "Check the logs above for specific errors."
                                + "\n========================================";
                        logger.e("FAILURE", failureMessage);
                    }
                    setDownloadingUIState(false);
                });
            }).start();
        }
    });

    resolver.resolve(
        new DependencyResolutionCallback() {
            // ... The rest of this callback remains unchanged ...
            @Override
            public void onDependenciesResolved(
                String message, List<Dependency> resolvedDependencies, long totalTime) {
                if (getActivity() == null) return;
                getActivity()
                    .runOnUiThread(
                        () -> {
                            int resolutionSize =
                                (resolvedDependencies != null) ? resolvedDependencies.size() : 0;
                            
                            StringBuilder sb = new StringBuilder();
                            sb.append("[");
                            if(resolutionSize > 0) {
                                resolvedDependencies.forEach(
                                    dependency -> {
                                        if (dependency != null) { 
                                            String artifact = dependency.toString();
                                            sb.append("\n  > ");
                                            sb.append(artifact);
                                        }
                                    });
                                sb.append("\n");
                            }
                            sb.append("]");
                            logger.p("Resolved Dependencies", sb.toString());

                            StringBuilder sbt = new StringBuilder();
                            sbt.append("\n");
                            double totalTimeSeconds = totalTime / 1000.0;
                            sbt.append(
                                "Resolution finished in "
                                    + String.format("%.3f", totalTimeSeconds)
                                    + "s");
                            sbt.append("\n");
                            String pluraled = (resolutionSize == 1) ? " dependency" : " dependencies";
                            sbt.append("Found " + resolutionSize + pluraled);
                            logger.p("SUMMARY", sbt.toString());

                            if (resolutionSize == 0) {
                                logger.w("Incomplete", "Resolution was successful but found no dependencies to download.");
                                setDownloadingUIState(false);
                                return;
                            }

                            new Thread(
                                () -> {
                                    Pom resolvedPom = new Pom();
                                    resolvedPom.setDependencies(resolvedDependencies);
                                    storageFactory.downloadLibraries(resolvedPom);
                                })
                            .start();
                        });
            }

            @Override
            public void onDependencyNotResolved(
                String message, List<Dependency> unresolvedDependencies) {
                if (getActivity() == null) return;
                getActivity()
                    .runOnUiThread(
                        () -> {
                            String failureMessage =
                                "\n========================================\n"
                                    + "❌ DOWNLOAD FAILED\n"
                                    + "Details: "
                                    + message
                                    + "\nCould not resolve the main dependency. Please check the name and version."
                                    + "\n========================================";
                            logger.e("FAILED", failureMessage);
                            setDownloadingUIState(false);
                        });
            }
            
            @Override
            public void error(String message) {
                if (getActivity() == null) return;
                getActivity()
                    .runOnUiThread(
                        () -> {
                            logger.e("ERROR", message);
                            setDownloadingUIState(false);
                        });
            }

            @Override
            public void info(String message) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> logger.p("INFO", message));
            }

            @Override
            public void warning(String message) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> logger.w("WARNING", message));
            }

            @Override
            public void verbose(String message) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> logger.d("VERBOSE", message));
            }
        });
}

  @VisibleForTesting
  private void parsePom() {
    PomParser parser = new PomParser();
    InputStream inputStream = getPomStream(binding.tilDepName.getEditText().getText().toString());
    Pom parsedPom = new Pom();
    try {
      parsedPom = parser.parse(inputStream);
      if (parsedPom == null) {
        String ne =
            "Failed to parse POM for "
                + parsedPom.getCoordinates().toString()
                + " because parsedPom is null";
        logger.e("ERROR", ne);
      } else {
        logParsedPom(parsedPom);
      }
      inputStream.close();
    } catch (Exception e) {
      String pe =
          "Failed to parse POM for "
              + parsedPom.getCoordinates().toString()
              + " due to "
              + e.getMessage();
      logger.e("ERROR", pe);
    }
  }

  public static InputStream getPomStream(String dirPath) {
    try {
      File localFile = new File(dirPath);
      return new FileInputStream(localFile);
    } catch (IOException e) {
      throw new IllegalArgumentException("Error occured :" + e.getMessage());
    }
  }

  private void logParsedPom(Pom parsedPom) {
    String parsedPomText =
        "Coordinates: "
            + parsedPom.getCoordinates()
            + "\n"
            + "Dependencies: "
            + parsedPom.getDependencies()
            + "\n"
            + "Excludes: "
            + parsedPom.getExclusions()
            + "\n"
            + "Managed Deps: "
            + parsedPom.getManagedDependencies()
            + "\n"
            + "Pom Parent: "
            + parsedPom.getParent()
            + "\n"
            + "Parsed POM in "
            + PomParser.getParsingDuration();
    logger.p("INFO", parsedPomText);
  }

  private void configureRepositories(DependencyResolver resolver, Logger logger) {

    boolean useDefault = false;

    if (Path.REPOSITORIES_JSON.exists()) {
      try {
        List<RemoteRepository> repositories =
            Manager.readRemoteRepositoryConfig(Path.REPOSITORIES_JSON, false);
        for (RemoteRepository repository : repositories) {
          resolver.addRepository(repository);
        }
      } catch (IOException | JSONException ignored) {
        useDefault = true;
      }
    } else {
      useDefault = true;
    }

    if (useDefault) {
      for (RemoteRepository repository : Manager.DEFAULT_REMOTE_REPOSITORIES) {
        resolver.addRepository(repository);
      }
      logger.d(
          "INFO",
          "Custom Repositories configuration file couldn't be read from. Using default repositories for now");
      try {
        // write default to file
        FileUtils.write(Path.REPOSITORIES_JSON, Manager.generateJSON(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        logger.e("ERROR", "Failed to create " + Path.REPOSITORIES_JSON.getName() + e.getMessage());
      }
    }
  }

  /**
   * Toggles the UI state between idle and downloading.
   *
   * @param isDownloading True to enter downloading state, false to return to idle.
   */
  private void setDownloadingUIState(boolean isDownloading) {
    if (binding == null) return; // Avoid crash if fragment is destroyed

    if (isDownloading) {
      // --- Downloading State ---
      binding.downloadButton.setVisibility(View.GONE);
      binding.stopButton.setVisibility(View.VISIBLE);
      binding.downloadProgressIndicator.setVisibility(View.VISIBLE);

      // Disable all inputs
      binding.dependencyEditText.setEnabled(false);
      binding.transitiveDependenciesSwitch.setEnabled(false);

    } else {
      // --- Idle State (Process Stopped/Finished) ---
      binding.downloadButton.setVisibility(View.VISIBLE);
      binding.downloadButton.setEnabled(true);
      binding.stopButton.setVisibility(View.GONE);
      binding.downloadProgressIndicator.setVisibility(View.GONE);

      // Re-enable all inputs
      binding.dependencyEditText.setEnabled(true);
      binding.transitiveDependenciesSwitch.setEnabled(true);
    }
  }

  /** Gathers all log messages from the adapter and copies them to the clipboard. */
  /** Gathers all log messages from the adapter and copies them to the clipboard. */
  private void copyLogsToClipboard() {
    if (logAdapter.getItemCount() == 0) {
      Toast.makeText(getContext(), "No logs to copy", Toast.LENGTH_SHORT).show();
      return;
    }

    // Use the new method to get the list of logs
    List<Log> currentLogs = logAdapter.getCurrentLogs();
    StringBuilder logsBuilder = new StringBuilder();

    // Use the correct 'Log' class and methods
    for (Log item : currentLogs) {
      logsBuilder.append(item.getTag()).append(": ").append(item.getMessage()).append("\n");
    }

    ClipboardManager clipboard =
        (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = ClipData.newPlainText("DependencyLogs", logsBuilder.toString());
    clipboard.setPrimaryClip(clip);

    Toast.makeText(getContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
  }

  /**
   * Checks if a network connection is available. Requires the ACCESS_NETWORK_STATE permission.
   *
   * @return true if connected to the internet, false otherwise.
   */
  private boolean isNetworkAvailable() {
    ConnectivityManager connectivityManager =
        (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    if (connectivityManager == null) {
      return false;
    }
    Network network = connectivityManager.getActiveNetwork();
    if (network == null) {
      return false;
    }
    NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
    return capabilities != null
        && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
  }
}
