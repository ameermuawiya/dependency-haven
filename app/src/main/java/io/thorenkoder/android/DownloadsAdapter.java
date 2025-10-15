// File: app/src/main/java/io/thorenkoder/android/adapter/DownloadsAdapter.java
package io.thorenkoder.android.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.R; // Use com.google.android.material.R for color attributes

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.thorenkoder.android.databinding.ListItemDownloadBinding;

public class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.DownloadViewHolder> {

  private final List<File> libraries;
  private final ItemClickListener clickListener;
  private final List<Integer> selectedItems = new ArrayList<>();

  public DownloadsAdapter(List<File> libraries, ItemClickListener clickListener) {
    this.libraries = libraries;
    this.clickListener = clickListener;
  }

  @NonNull
  @Override
  public DownloadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    ListItemDownloadBinding binding =
        ListItemDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
    return new DownloadViewHolder(binding);
  }

  @Override
  public void onBindViewHolder(@NonNull DownloadViewHolder holder, int position) {
    holder.bind(libraries.get(position));

    // A better way to highlight items that respects the app's theme
    if (selectedItems.contains(position)) {
      // Use a theme attribute for a subtle highlight color
      int color =
          getColorFromAttr(
              holder.itemView.getContext(),
              com.google.android.material.R.attr.colorSurfaceContainerHighest);
      holder.itemView.setBackgroundColor(color);
    } else {
      // Use transparent for default state to allow the ripple effect
      holder.itemView.setBackgroundColor(Color.TRANSPARENT);
    }
  }

  @Override
  public int getItemCount() {
    return libraries.size();
  }

  // --- Selection Logic ---

  public void toggleSelection(int position) {
    if (selectedItems.contains(position)) {
      selectedItems.remove(Integer.valueOf(position));
    } else {
      selectedItems.add(position);
    }
    notifyItemChanged(position);
  }

  public void clearSelection() {
    // A more efficient way to clear selection
    List<Integer> oldSelection = new ArrayList<>(selectedItems);
    selectedItems.clear();
    for (int position : oldSelection) {
      notifyItemChanged(position);
    }
  }

  public int getSelectedItemCount() {
    return selectedItems.size();
  }

  public List<File> getSelectedItems() {
    List<File> selectedFiles = new ArrayList<>();
    for (int position : selectedItems) {
      selectedFiles.add(libraries.get(position));
    }
    return selectedFiles;
  }

  // --- ViewHolder and Click Listener ---

  public class DownloadViewHolder extends RecyclerView.ViewHolder {
    private final ListItemDownloadBinding binding;

    // Replace the entire DownloadViewHolder constructor with this corrected version

    public DownloadViewHolder(ListItemDownloadBinding binding) {
      super(binding.getRoot());
      this.binding = binding;

      // Listener for the whole item view
      itemView.setOnClickListener(
          v -> {
            if (clickListener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
              clickListener.onItemClick(getAdapterPosition());
            }
          });

      // Listener for long clicks on the whole item view
      itemView.setOnLongClickListener(
          v -> {
            if (clickListener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
              clickListener.onItemLongClick(getAdapterPosition());
            }
            return true;
          });

      // --- FIXED: Using the correct ID "more_options_button" for the menu click ---
      binding.moreOptionsButton.setOnClickListener(
          v -> {
            if (clickListener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
              clickListener.onItemMenuClick(v, getAdapterPosition());
            }
          });
    }

    public void bind(File libraryFile) {
      binding.libraryNameTextView.setText(libraryFile.getName());
    }
  }

  // Interface for click events
  public interface ItemClickListener {
    void onItemClick(int position);

    void onItemLongClick(int position);

    void onItemMenuClick(View view, int position); // This method is new
  }

  // Add this new helper method anywhere inside your DownloadsAdapter class
  private int getColorFromAttr(android.content.Context context, int attr) {
    android.util.TypedValue typedValue = new android.util.TypedValue();
    android.content.res.Resources.Theme theme = context.getTheme();
    theme.resolveAttribute(attr, typedValue, true);
    return typedValue.data;
  }
}
