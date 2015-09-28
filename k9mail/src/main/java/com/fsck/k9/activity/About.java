package com.fsck.k9.activity;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;

import com.fsck.k9.K9;

import java.util.Calendar;

import de.fau.cs.mad.smile.android.R;

public class About extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.about_action) + " " + getString(R.string.app_name));
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();

        if(actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        initializeWebView();
    }

    public void initializeWebView() {
        String appName = getString(R.string.app_name);
        int year = Calendar.getInstance().get(Calendar.YEAR);
        WebView wv = (WebView) findViewById(R.id.webview);

        StringBuilder html = new StringBuilder()
                .append("<meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\" />")
                .append("<img src=\"file:///android_asset/icon.png\" alt=\"").append(appName).append("\"/>")
                .append("<h1>")
                .append(String.format(getString(R.string.about_title_fmt),
                        "<a href=\"" + getString(R.string.app_webpage_url)) + "\">")
                .append(appName)
                .append("</a>")
                .append("</h1><p>")
                .append(appName)
                .append(" ")
                .append(String.format(getString(R.string.debug_version_fmt), getVersionNumber()))
                .append("</p><p>")
                .append(String.format(getString(R.string.app_authors_fmt),
                        getString(R.string.app_authors)))
                .append("</p><p>")
                .append(String.format(getString(R.string.app_revision_fmt),
                        "<a href=\"" + getString(R.string.app_revision_url) + "\">" +
                                getString(R.string.app_revision_url) +
                                "</a>"))
                .append("</p><hr/><p>")
                .append(String.format(getString(R.string.app_copyright_fmt), year, year))
                .append("</p><hr/><p>")
                .append(getString(R.string.app_license))
                .append("</p><hr/><p>");

        StringBuilder libs = new StringBuilder().append("<ul>");
        for (String[] library : K9.USED_LIBRARIES) {
            libs.append("<li><a href=\"").append(library[1]).append("\">").append(library[0]).append("</a></li>");
        }
        libs.append("</ul>");

        html.append(String.format(getString(R.string.app_libraries), libs.toString()))
                .append("</p><hr/><p>")
                .append(String.format(getString(R.string.app_emoji_icons),
                        "<div>TypePad \u7d75\u6587\u5b57\u30a2\u30a4\u30b3\u30f3\u753b\u50cf " +
                                "(<a href=\"http://typepad.jp/\">Six Apart Ltd</a>) / " +
                                "<a href=\"http://creativecommons.org/licenses/by/2.1/jp/\">CC BY 2.1</a></div>"))
                .append("</p><hr/><p>")
                .append(getString(R.string.app_htmlcleaner_license));

        wv.loadDataWithBaseURL("file:///android_res/drawable/", html.toString(), "text/html", "utf-8", null);
    }

    /**
     * Get current version number.
     *
     * @return String version
     */
    private String getVersionNumber() {
        String version = "?";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            //Log.e(TAG, "Package name not found", e);
        }
        return version;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_about, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}