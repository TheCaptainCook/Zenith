package com.thecaptaincook.zenith;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ── Apply custom theme BEFORE super.onCreate() ──────────────
        android.content.SharedPreferences prefs =
                getSharedPreferences("zenith_prefs", android.content.Context.MODE_PRIVATE);
        int themeIndex = prefs.getInt("theme", 0);
        switch (themeIndex) {
            case 1: setTheme(R.style.Theme_Zenith_Amoled); break;
            case 2: setTheme(R.style.Theme_Zenith_Ocean);  break;
            case 3: setTheme(R.style.Theme_Zenith_Sunset); break;
            default: setTheme(R.style.Theme_Zenith_Glass); break;
        }

        int fontIndex = prefs.getInt("font", 0);
        switch (fontIndex) {
            case 1: getTheme().applyStyle(R.style.FontStyle_Serif, true); break;
            case 2: getTheme().applyStyle(R.style.FontStyle_Monospace, true); break;
            case 3: getTheme().applyStyle(R.style.FontStyle_SansSerifCondensed, true); break;
            case 4: getTheme().applyStyle(R.style.FontStyle_Cursive, true); break;
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView navView = findViewById(R.id.nav_view);

        // Load the default fragment (Config)
        if (savedInstanceState == null) {
            loadFragment(new ConfigFragment());
        }

        startAutomationService();
        scheduleLogCleanup();

        navView.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int itemId = item.getItemId();
            
            if (itemId == R.id.navigation_config) {
                fragment = new ConfigFragment();
            } else if (itemId == R.id.navigation_saved) {
                fragment = new SavedFragment();
            } else if (itemId == R.id.navigation_logs) {
                fragment = new LogsFragment();
            } else if (itemId == R.id.navigation_settings) {
                fragment = new SettingsFragment();
            } else if (itemId == R.id.navigation_about) {
                fragment = new AboutFragment();
            }

            if (fragment != null) {
                loadFragment(fragment);
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .commit();
    }

    public void navigateToConfigEdit(int ruleId) {
        BottomNavigationView navView = findViewById(R.id.nav_view);
        navView.setSelectedItemId(R.id.navigation_config);
        
        ConfigFragment configFragment = new ConfigFragment();
        Bundle args = new Bundle();
        args.putInt("RULE_ID", ruleId);
        configFragment.setArguments(args);
        
        loadFragment(configFragment);
    }

    public void navigateToSavedRules() {
        BottomNavigationView navView = findViewById(R.id.nav_view);
        navView.setSelectedItemId(R.id.navigation_saved);
    }

    private void startAutomationService() {
        android.content.SharedPreferences prefs = getSharedPreferences("zenith_prefs", android.content.Context.MODE_PRIVATE);
        boolean bgEnabled = prefs.getBoolean("bg", true);
        if (bgEnabled) {
            android.content.Intent serviceIntent = new android.content.Intent(this, AutomationService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
    }

    private void scheduleLogCleanup() {
        androidx.work.PeriodicWorkRequest cleanupWorkRequest =
                new androidx.work.PeriodicWorkRequest.Builder(
                        LogCleanupWorker.class, 
                        24, java.util.concurrent.TimeUnit.HOURS)
                .build();
        
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "log_cleanup_work",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                cleanupWorkRequest
        );
    }
}
