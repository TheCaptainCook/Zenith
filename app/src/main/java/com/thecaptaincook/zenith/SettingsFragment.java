package com.thecaptaincook.zenith;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Spinner;

import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {

    // ── Icon alias component names (one per app theme) ─────────────────────
    private static final String ALIAS_GLASS  = ".IconGlass";
    private static final String ALIAS_AMOLED = ".IconAmoled";
    private static final String ALIAS_OCEAN  = ".IconOcean";
    private static final String ALIAS_SUNSET = ".IconSunset";
    private static final String[] ALL_ALIASES = { ALIAS_GLASS, ALIAS_AMOLED, ALIAS_OCEAN, ALIAS_SUNSET };

    private SharedPreferences prefs;
    private AppDatabase db;
    private ExecutorService executorService;

    private ActivityResultLauncher<String> backupLauncher;
    private ActivityResultLauncher<String[]> restoreLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        backupLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri != null) {
                performBackupToUri(uri);
            }
        });

        restoreLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                performRestoreFromUri(uri);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        prefs = requireActivity().getSharedPreferences("zenith_prefs", Context.MODE_PRIVATE);
        db = AppDatabase.getDatabase(requireContext());
        executorService = Executors.newSingleThreadExecutor();

        // ── Sync icon with current theme on fragment open ──────────────────
        syncIconWithTheme(prefs.getInt("theme", 0));


        Spinner spinnerTheme = view.findViewById(R.id.spinner_theme);
        Spinner spinnerFont = view.findViewById(R.id.spinner_font);
        MaterialSwitch switchNotif = view.findViewById(R.id.switch_notifications);
        MaterialSwitch switchBg = view.findViewById(R.id.switch_background);
        Spinner spinnerRetent = view.findViewById(R.id.spinner_retention);
        Spinner spinnerDelete = view.findViewById(R.id.spinner_delete);
        com.google.android.material.slider.Slider sliderPitch = view.findViewById(R.id.slider_tts_pitch);
        com.google.android.material.slider.Slider sliderRate = view.findViewById(R.id.slider_tts_rate);

        spinnerTheme.setSelection(prefs.getInt("theme", 0));
        spinnerFont.setSelection(prefs.getInt("font", 0));
        switchNotif.setChecked(prefs.getBoolean("notif", true));
        switchBg.setChecked(prefs.getBoolean("bg", true));
        spinnerRetent.setSelection(prefs.getInt("retention", 2));
        spinnerDelete.setSelection(prefs.getInt("delete_method", 0));
        sliderPitch.setValue(prefs.getFloat("tts_pitch", 1.0f));
        sliderRate.setValue(prefs.getFloat("tts_rate", 1.0f));

        switchNotif.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("notif", isChecked).apply());
        switchBg.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("bg", isChecked).apply());

        sliderPitch.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) prefs.edit().putFloat("tts_pitch", value).apply();
        });
        
        sliderRate.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) prefs.edit().putFloat("tts_rate", value).apply();
        });
        
        spinnerRetent.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt("retention", position).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerDelete.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt("delete_method", position).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerTheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int currentTheme = prefs.getInt("theme", 0);
                if (currentTheme != position) {
                    prefs.edit().putInt("theme", position).apply();
                    // Auto-switch the launcher icon to match the new theme
                    switchIcon(themeToAlias(position));
                    applyTheme(position);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerFont.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int currentFont = prefs.getInt("font", 0);
                if (currentFont != position) {
                    prefs.edit().putInt("font", position).apply();
                    applyTheme(position);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        view.findViewById(R.id.btn_backup).setOnClickListener(v -> backupLauncher.launch("zenith_backup.json"));
        view.findViewById(R.id.btn_restore).setOnClickListener(v -> restoreLauncher.launch(new String[]{"application/json", "*/*"}));
        view.findViewById(R.id.btn_reset).setOnClickListener(v -> resetApp());

        return view;
    }

    private void applyTheme(int position) {
        // Recreate the activity so MainActivity.onCreate() applies the new theme style
        requireActivity().recreate();
    }

    // ── Icon switching ─────────────────────────────────────────────────────

    /**
     * Maps a theme position (from the Theme spinner) to its activity-alias name.
     *   0 = Glassmorphic Dark  → .IconGlass
     *   1 = Amoled Black       → .IconAmoled
     *   2 = Ocean Deep         → .IconOcean
     *   3 = Sunset Glow        → .IconSunset
     */
    private String themeToAlias(int themePosition) {
        switch (themePosition) {
            case 1:  return ALIAS_AMOLED;
            case 2:  return ALIAS_OCEAN;
            case 3:  return ALIAS_SUNSET;
            default: return ALIAS_GLASS;
        }
    }

    /**
     * Silently syncs the launcher icon alias to match the active theme.
     * Called on fragment open so icon stays consistent after manual APK updates.
     */
    private void syncIconWithTheme(int themePosition) {
        String expected = themeToAlias(themePosition);
        String current  = prefs.getString("app_icon", ALIAS_GLASS);
        if (!expected.equals(current)) {
            switchIcon(expected);
        }
    }

    /**
     * Enables the target activity-alias and disables all others.
     * The launcher reflects the icon change within a few seconds — no reboot needed.
     */
    private void switchIcon(String targetAlias) {
        PackageManager pm  = requireContext().getPackageManager();
        String         pkg = requireContext().getPackageName();

        for (String alias : ALL_ALIASES) {
            int state = alias.equals(targetAlias)
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            pm.setComponentEnabledSetting(
                    new ComponentName(pkg, pkg + alias),
                    state,
                    PackageManager.DONT_KILL_APP
            );
        }

        prefs.edit().putString("app_icon", targetAlias).apply();
    }

    private void performBackupToUri(Uri uri) {
        executorService.execute(() -> {
            try {
                List<RuleEntity> rules = db.ruleDao().getAllRules();
                JSONArray jsonArray = new JSONArray();
                for (RuleEntity rule : rules) {
                    JSONObject obj = new JSONObject();
                    obj.put("ruleName", rule.ruleName);
                    obj.put("folderName", rule.folderName);
                    obj.put("triggersJson", rule.triggersJson);
                    obj.put("triggerLogic", rule.triggerLogic);
                    obj.put("actionsJson", rule.actionsJson);
                    obj.put("isEnabled", rule.isEnabled);
                    jsonArray.put(obj);
                }

                OutputStream out = requireContext().getContentResolver().openOutputStream(uri);
                if (out != null) {
                    out.write(jsonArray.toString(4).getBytes());
                    out.flush();
                    out.close();
                    requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Backup Successful!", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Backup Failed", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void performRestoreFromUri(Uri uri) {
        executorService.execute(() -> {
            try {
                InputStream in = requireContext().getContentResolver().openInputStream(uri);
                if (in != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    in.close();

                    JSONArray jsonArray = new JSONArray(sb.toString());
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        RuleEntity rule = new RuleEntity();
                        rule.ruleName = obj.optString("ruleName", "Restored Rule");
                        rule.folderName = obj.optString("folderName", "Default");
                        rule.triggersJson = obj.optString("triggersJson", "[\"Screen Turned On\"]");
                        rule.triggerLogic = obj.optString("triggerLogic", "ANY");
                        rule.actionsJson = obj.optString("actionsJson", "[]");
                        rule.isEnabled = obj.optBoolean("isEnabled", false);
                        db.ruleDao().insert(rule);
                    }
                    
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Restore Successful!", Toast.LENGTH_SHORT).show();
                        Intent serviceIntent = new Intent(getContext(), AutomationService.class);
                        serviceIntent.setAction("RELOAD_RULES");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            requireContext().startForegroundService(serviceIntent);
                        } else {
                            requireContext().startService(serviceIntent);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Restore Failed: Invalid File", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void resetApp() {
        new AlertDialog.Builder(requireContext())
            .setTitle("Reset App")
            .setMessage("Are you sure? This will wipe all rules, logs, and preferences. This action cannot be undone.")
            .setPositiveButton("WIPE EVERYTHING", (dialog, which) -> {
                prefs.edit().clear().apply();
                // Reset icon back to default (Glassmorphic / theme 0)
                switchIcon(ALIAS_GLASS);
                executorService.execute(() -> {
                    db.clearAllTables();
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "App reset successfully.", Toast.LENGTH_LONG).show();
                        applyTheme(0);
                        Intent serviceIntent = new Intent(getContext(), AutomationService.class);
                        serviceIntent.setAction("RELOAD_RULES");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            requireContext().startForegroundService(serviceIntent);
                        } else {
                            requireContext().startService(serviceIntent);
                        }
                    });
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
