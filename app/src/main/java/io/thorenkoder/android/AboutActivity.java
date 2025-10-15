package io.thorenkoder.android;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textview.MaterialTextView;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_about);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        MaterialTextView versionText = findViewById(R.id.text_version_value);
        versionText.setText(getAppVersion());

        MaterialTextView githubLink = findViewById(R.id.link_github);
        MaterialTextView youtubeLink = findViewById(R.id.link_youtube);
        MaterialTextView emailText = findViewById(R.id.text_email);
        MaterialTextView whatsappText = findViewById(R.id.text_whatsapp);

        githubLink.setOnClickListener(v -> openLink("https://github.com/ThorenKoder"));
        youtubeLink.setOnClickListener(v -> openLink("https://youtube.com/@ThorenKoder"));
        emailText.setOnClickListener(v -> openEmail());
        whatsappText.setOnClickListener(v -> openWhatsApp());
    }

    private String getAppVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return "Version " + info.versionName;
        } catch (Exception e) {
            return "Version not available";
        }
    }

    private void openLink(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void openEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:ameermuawiya604@gmail.com"));
        intent.putExtra(Intent.EXTRA_SUBJECT, "Feedback for LibVault App");
        startActivity(Intent.createChooser(intent, "Send Email"));
    }

    private void openWhatsApp() {
        String phone = "+923423071137";
        String url = "https://wa.me/" + phone.replace("+", "");
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }
}