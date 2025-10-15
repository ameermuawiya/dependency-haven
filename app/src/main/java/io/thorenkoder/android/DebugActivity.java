package io.thorenkoder.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DebugActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Intent intent = getIntent();
        String errorMessage = intent.getStringExtra("error");
        
        if (errorMessage == null) {
            errorMessage = "No error log available.";
        }

        showErrorDialog(errorMessage);
    }

    private void showErrorDialog(final String errorLog) {
    // 1. First, create the builder and configure it
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.msg_app_crashed)
        .setMessage(getString(R.string.msg_app_crashed_error) + "\n\n" + errorLog)
        .setCancelable(false)
        .setPositiveButton(R.string.restart, (dialog, which) -> restartApp())
        .setNeutralButton("Copy & Exit", (dialog, which) -> copyAndExit(errorLog))
        .setNegativeButton(R.string.exit, (dialog, which) -> finishAffinity());

    // 2. Create the dialog object from the builder
    final AlertDialog dialog = builder.create();

    // 3. Set the listener on the created dialog object
    dialog.setOnShowListener(d -> {
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextIsSelectable(true);
        }
    });

    // 4. Finally, show the dialog
    dialog.show();
}

    /**
     * Copies the provided error log to the clipboard and then finishes the activity.
     * @param errorLog The error string to be copied.
     */
    private void copyAndExit(String errorLog) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("CrashLog", errorLog);
        clipboard.setPrimaryClip(clip);
        
        // Show a confirmation message
        Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show();
        
        // Close the entire app after copying
        finishAffinity();
    }

    private void restartApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
        // Forcefully close the crashed process to ensure a clean start
        Process.killProcess(Process.myPid());
        System.exit(1);
    }
}