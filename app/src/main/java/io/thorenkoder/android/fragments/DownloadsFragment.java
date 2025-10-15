package io.thorenkoder.android.fragments;

import android.content.SharedPreferences;
import io.thorenkoder.android.SharedPreferenceKeys;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.transition.MaterialSharedAxis;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.apache.commons.io.FileUtils;
import java.io.IOException;
import java.util.stream.Collectors;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.thorenkoder.android.R;
import io.thorenkoder.android.adapter.DownloadsAdapter;
import io.thorenkoder.android.databinding.FragmentDownloadsBinding;

public class DownloadsFragment extends Fragment implements DownloadsAdapter.ItemClickListener {

  public static final String TAG = DownloadsFragment.class.getSimpleName();
  private static final File SKETCHWARE_LIBS_DIR =
      new File(Environment.getExternalStorageDirectory(), "/.sketchware/libs/local_libs/");
  private FragmentDownloadsBinding binding;
  private DownloadsAdapter adapter;
  private final List<File> downloadedLibs = new ArrayList<>();
  private SharedPreferences prefs;

  // --- REMOVED: Old ActionMode variables are no longer needed ---

  public static DownloadsFragment newInstance() {
    return new DownloadsFragment();
  }

  private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener =
      (sharedPreferences, key) -> {
        if (key != null && key.equals(SharedPreferenceKeys.KEY_LIBRARY_MANAGER)) {
          loadDownloadedLibraries();
        }
      };

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Y, false));
    setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.Y, true));
    prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
  }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    binding = FragmentDownloadsBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    setupRecyclerView();
  }

  // Replace your existing onResume method with this one
  @Override
  public void onResume() {
    super.onResume();
    // Reload the list every time the fragment becomes visible
    loadDownloadedLibraries();
    // Set the default toolbar state
    updateToolbarState();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
    binding = null;
  }

  private void setupRecyclerView() {
    adapter = new DownloadsAdapter(downloadedLibs, this);
    binding.downloadsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    binding.downloadsRecyclerView.setAdapter(adapter);
  }

  // --- REMOVED: setupActionModeCallback() method is no longer needed ---

  private void loadDownloadedLibraries() {
    // This method remains the same
    showLoading(true);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Handler handler = new Handler(Looper.getMainLooper());
    executor.execute(
        () -> {
          String defaultPath =
              Environment.getExternalStorageDirectory().getPath() + "/LibVault/libs/";
          String path = prefs.getString(SharedPreferenceKeys.KEY_LIBRARY_MANAGER, defaultPath);
          File dir = new File(path);
          List<File> directories = new ArrayList<>();
          if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles(File::isDirectory);
            if (files != null) {
              directories.addAll(Arrays.asList(files));
            }
          }
          handler.post(
              () -> {
                showLoading(false);
                downloadedLibs.clear();
                downloadedLibs.addAll(directories);
                adapter.notifyDataSetChanged();
                updateUIState();
              });
        });
  }

  /**
   * Shows or hides the loading indicator and manages content visibility.
   *
   * @param isLoading True to show the progress bar, false otherwise.
   */
  private void showLoading(boolean isLoading) {
    if (binding != null) {
      binding.loadingProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
      // Hide other views while loading
      if (isLoading) {
        binding.downloadsRecyclerView.setVisibility(View.GONE);
        binding.emptyView.setVisibility(View.GONE);
      }
    }
  }

  /**
   * Updates the UI to show either the list of libraries or the empty state view. This should only
   * be called after loading is finished.
   */
  private void updateUIState() {
    if (binding != null) {
      boolean isEmpty = downloadedLibs.isEmpty();
      binding.downloadsRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
      binding.emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }
  }

  // --- MODIFIED: Item Click Listener Methods now control the main Toolbar ---

  @Override
  public void onItemClick(int position) {
    // If we are in selection mode, a single click should toggle selection
    if (adapter.getSelectedItemCount() > 0) {
      toggleSelection(position);
    } else {
      // Handle regular click (e.g., show library details)
      Toast.makeText(
              getContext(),
              "Clicked: " + downloadedLibs.get(position).getName(),
              Toast.LENGTH_SHORT)
          .show();
    }
  }

  @Override
  public void onItemLongClick(int position) {
    // A long click will always start or toggle selection
    toggleSelection(position);
  }

  // Add this method after the onItemLongClick method in DownloadsFragment.java

  @Override
  public void onItemMenuClick(View anchorView, int position) {
    PopupMenu popup = new PopupMenu(requireContext(), anchorView);
    popup.getMenuInflater().inflate(R.menu.item_popup_menu, popup.getMenu());

    popup.setOnMenuItemClickListener(
        item -> {
          List<File> singleItemList = new ArrayList<>();
          singleItemList.add(downloadedLibs.get(position));
          int itemId = item.getItemId();

          if (itemId == R.id.action_item_delete) {
            showDeleteConfirmationDialog(singleItemList);
            return true;
          } else if (itemId == R.id.action_item_copy) {
            handleFileCopy(singleItemList, false); // false for copy
            return true;
          } else if (itemId == R.id.action_item_move) {
            handleFileCopy(singleItemList, true); // true for move
            return true;
          }
          return false;
        });
    popup.show();
  }

  private void toggleSelection(int position) {
    adapter.toggleSelection(position);
    updateToolbarState(); // Update the toolbar every time selection changes
  }

  /**
   * --- NEW METHOD --- This method updates the MainFragment's Toolbar based on the current
   * selection state.
   */
  private void updateToolbarState() {
    if (getParentFragment() instanceof MainFragment) {
      MainFragment mainFragment = (MainFragment) getParentFragment();
      int selectedCount = adapter.getSelectedItemCount();

      if (selectedCount > 0) {
        mainFragment.setToolbarTitle(selectedCount + " selected");
        mainFragment.setToolbarMenu(
            R.menu.selection_toolbar_menu,
            item -> {
              int itemId = item.getItemId();
              if (itemId == R.id.action_delete) {
                deleteSelectedItems();
                return true;
              } else if (itemId == R.id.action_copy) {
                copySelectedItems();
                return true;
              } else if (itemId == R.id.action_move) {
                moveSelectedItems();
                return true;
              }
              return false;
            });
      } else {
        mainFragment.setToolbarTitle("Downloads");
        if (mainFragment.getToolbar() != null) {
          mainFragment.getToolbar().getMenu().clear();
        }
      }
    }
  }

  private void clearSelection() {
    if (adapter != null) {
      adapter.clearSelection();
    }
    updateToolbarState();
  }

  // Replace the old delete, copy, and move methods with these
  private void deleteSelectedItems() {
    showDeleteConfirmationDialog(adapter.getSelectedItems());
  }

  private void copySelectedItems() {
    handleFileCopy(adapter.getSelectedItems(), false); // false for copy
  }

  private void moveSelectedItems() {
    handleFileCopy(adapter.getSelectedItems(), true); // true for move
  }

  // Add all these new methods to your DownloadsFragment class

  /**
   * Shows a confirmation dialog before deleting files.
   *
   * @param filesToDelete List of files to be deleted.
   */
  private void showDeleteConfirmationDialog(List<File> filesToDelete) {
    if (filesToDelete.isEmpty()) return;

    new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Confirm Deletion")
        .setMessage(
            "Are you sure you want to delete "
                + filesToDelete.size()
                + " library/libraries? This action cannot be undone.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Delete",
            (dialog, which) -> {
              performDeletion(filesToDelete);
            })
        .show();
  }

  /**
   * Performs the actual file deletion on a background thread.
   *
   * @param filesToDelete List of files to delete.
   */
  private void performDeletion(List<File> filesToDelete) {
    Toast.makeText(getContext(), "Deleting...", Toast.LENGTH_SHORT).show();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Handler handler = new Handler(Looper.getMainLooper());

    executor.execute(
        () -> {
          try {
            for (File file : filesToDelete) {
              FileUtils.deleteDirectory(file);
            }
            handler.post(
                () -> {
                  Toast.makeText(getContext(), "Successfully deleted.", Toast.LENGTH_SHORT).show();
                  clearSelection();
                  loadDownloadedLibraries();
                });
          } catch (IOException e) {
            handler.post(
                () ->
                    Toast.makeText(
                            getContext(),
                            "Error deleting files: " + e.getMessage(),
                            Toast.LENGTH_LONG)
                        .show());
          }
        });
  }

  /**
   * Main handler for both copy and move operations.
   *
   * @param selectedFiles List of files to process.
   * @param isMove True if it's a move operation, false for copy.
   */
  private void handleFileCopy(List<File> selectedFiles, boolean isMove) {
    if (selectedFiles.isEmpty()) return;
    String action = isMove ? "move" : "copy";

    new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Confirm " + action)
        .setMessage(
            "You are about to "
                + action
                + " "
                + selectedFiles.size()
                + " library/libraries to the Sketchware Pro directory:\n\n"
                + SKETCHWARE_LIBS_DIR.getPath())
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            action.toUpperCase(),
            (dialog, which) -> {
              // Check for duplicates on a background thread
              checkDuplicatesAndProceed(selectedFiles, isMove);
            })
        .show();
  }

  /** Checks for duplicate libraries in the target directory on a background thread. */
  private void checkDuplicatesAndProceed(List<File> selectedFiles, boolean isMove) {
    Toast.makeText(getContext(), "Checking for duplicates...", Toast.LENGTH_SHORT).show();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Handler handler = new Handler(Looper.getMainLooper());

    executor.execute(
        () -> {
          List<File> duplicates = new ArrayList<>();
          List<File> nonDuplicates = new ArrayList<>();

          if (SKETCHWARE_LIBS_DIR.exists()) {
            for (File selected : selectedFiles) {
              File target = new File(SKETCHWARE_LIBS_DIR, selected.getName());
              if (target.exists()) {
                duplicates.add(selected);
              } else {
                nonDuplicates.add(selected);
              }
            }
          } else {
            nonDuplicates.addAll(selectedFiles);
          }

          handler.post(
              () -> {
                if (!duplicates.isEmpty()) {
                  showDuplicateDialog(duplicates, nonDuplicates, isMove);
                } else if (!nonDuplicates.isEmpty()) {
                  // No duplicates, proceed directly
                  performCopyOrMove(nonDuplicates, new ArrayList<>(), isMove, "REPLACE");
                } else {
                  Toast.makeText(
                          getContext(),
                          "No libraries to " + (isMove ? "move" : "copy") + ".",
                          Toast.LENGTH_SHORT)
                      .show();
                  clearSelection();
                }
              });
        });
  }

  /** Shows a dialog to the user on how to handle duplicate files. */
  private void showDuplicateDialog(
      List<File> duplicates, List<File> nonDuplicates, boolean isMove) {
    String action = isMove ? "move" : "copy";
    String[] options = {"Skip Duplicates", "Replace Existing", "Keep Both (Rename)"};

    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(duplicates.size() + " Duplicate Libraries Found")
        .setItems(
            options,
            (dialog, which) -> {
              switch (which) {
                case 0: // Skip
                  performCopyOrMove(nonDuplicates, new ArrayList<>(), isMove, "SKIP");
                  break;
                case 1: // Replace
                  performCopyOrMove(nonDuplicates, duplicates, isMove, "REPLACE");
                  break;
                case 2: // Keep Both
                  performCopyOrMove(nonDuplicates, duplicates, isMove, "KEEP_BOTH");
                  break;
              }
            })
        .show();
  }

  /** Performs the final copy or move operation on a background thread based on user's choice. */
  /** Performs the final copy or move operation on a background thread based on user's choice. */
  private void performCopyOrMove(
      List<File> nonDuplicates, List<File> duplicates, boolean isMove, String strategy) {
    String action = isMove ? "Moving" : "Copying";
    Toast.makeText(getContext(), action + " libraries...", Toast.LENGTH_LONG).show();

    List<File> filesToProcess = new ArrayList<>(nonDuplicates);
    if (!strategy.equals("SKIP")) {
      filesToProcess.addAll(duplicates);
    }

    if (filesToProcess.isEmpty()) {
      Toast.makeText(getContext(), "Nothing to " + (isMove ? "move" : "copy"), Toast.LENGTH_SHORT)
          .show();
      clearSelection();
      return;
    }

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Handler handler = new Handler(Looper.getMainLooper());

    executor.execute(
        () -> {
          try {
            if (!SKETCHWARE_LIBS_DIR.exists()) {
              SKETCHWARE_LIBS_DIR.mkdirs();
            }

            for (File sourceFile : filesToProcess) {
              File destFile = new File(SKETCHWARE_LIBS_DIR, sourceFile.getName());
              boolean isDuplicate = duplicates.contains(sourceFile);

              // This block contains the corrected logic for handling duplicates
              if (isDuplicate) {
                if (strategy.equals("REPLACE")) {
                  FileUtils.deleteDirectory(destFile);
                } else if (strategy.equals("KEEP_BOTH")) {
                  destFile = new File(SKETCHWARE_LIBS_DIR, sourceFile.getName() + " (1)");
                } else { // SKIP
                  continue; // Skip this file and go to the next one
                }
              }

              // Perform the file operation
              if (isMove) {
                FileUtils.moveDirectory(sourceFile, destFile);
              } else {
                FileUtils.copyDirectory(sourceFile, destFile);
              }
            }

            handler.post(
                () -> {
                  String finalAction = isMove ? "moved" : "copied";
                  Toast.makeText(
                          getContext(),
                          "Successfully "
                              + finalAction
                              + " "
                              + filesToProcess.size()
                              + " libraries.",
                          Toast.LENGTH_SHORT)
                      .show();
                  clearSelection();
                  if (isMove) {
                    loadDownloadedLibraries(); // Refresh list if files were moved
                  }
                });

          } catch (IOException e) {
            handler.post(
                () ->
                    Toast.makeText(
                            getContext(), "Operation failed: " + e.getMessage(), Toast.LENGTH_LONG)
                        .show());
          }
        });
  }
}
