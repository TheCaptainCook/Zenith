package com.thecaptaincook.zenith;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigFragment extends Fragment {

    private TextInputEditText editRuleName;
    private TextInputEditText editFolderName;
    private Spinner spinnerTriggerLogic;
    private LinearLayout containerTriggers;
    private LinearLayout containerActions;
    private Button btnAddTrigger;
    private Button btnAddAction;
    private Button btnSaveDraft;
    private Button btnSaveActive;
    private Button btnLoadTemplate;

    private AppDatabase db;
    private ExecutorService executorService;
    private int currentRuleId = -1;
    private RuleEntity existingRule = null;
    private boolean pendingSaveIsActive = true;

    private ActivityResultLauncher<String[]> requestPermissionsLauncher;
    private ActivityResultLauncher<Intent> requestExactAlarmLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestPermissionsLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean allGranted = true;
            for (Boolean granted : result.values()) {
                if (!granted) allGranted = false;
            }
            if (allGranted) {
                checkExactAlarmAndSave();
            } else {
                Toast.makeText(getContext(), "Required permissions were denied.", Toast.LENGTH_SHORT).show();
            }
        });

        requestExactAlarmLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            checkExactAlarmAndSave();
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_config, container, false);
        
        db = AppDatabase.getDatabase(requireContext());
        executorService = Executors.newSingleThreadExecutor();

        editRuleName = view.findViewById(R.id.edit_rule_name);
        editFolderName = view.findViewById(R.id.edit_folder_name);
        spinnerTriggerLogic = view.findViewById(R.id.spinner_trigger_logic);
        containerTriggers = view.findViewById(R.id.container_triggers);
        containerActions = view.findViewById(R.id.container_actions);
        btnAddTrigger = view.findViewById(R.id.btn_add_trigger);
        btnAddAction = view.findViewById(R.id.btn_add_action);
        btnSaveDraft = view.findViewById(R.id.btn_save_draft);
        btnSaveActive = view.findViewById(R.id.btn_save_active);
        btnLoadTemplate = view.findViewById(R.id.btn_load_template);

        btnAddTrigger.setOnClickListener(v -> openTriggerPicker());
        btnAddAction.setOnClickListener(v -> openActionPicker());
        btnSaveDraft.setOnClickListener(v -> showPreviewAndSave(false));
        btnSaveActive.setOnClickListener(v -> showPreviewAndSave(true));
        btnLoadTemplate.setOnClickListener(v -> showTemplateDialog());

        if (getArguments() != null) {
            currentRuleId = getArguments().getInt("RULE_ID", -1);
            if (currentRuleId != -1) {
                btnSaveActive.setText("Update Active");
                loadExistingRule(currentRuleId);
            }
        }

        return view;
    }

    private void showTemplateDialog() {
        String[] templates = {"Battery Saver", "Morning Routine", "Night Mode", "Headphone Mode"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Load Pre-built Template")
                .setItems(templates, (dialog, which) -> {
                    if (which == 0) {
                        editRuleName.setText("Battery Saver");
                        editFolderName.setText("System");
                        setSpinnerToValue(spinnerTriggerLogic, "ANY");
                        containerTriggers.removeAllViews();
                        addTriggerRow("Battery Below 20%");
                        containerActions.removeAllViews();
                        addActionRow("Mute / Unmute All Sound", null, android.R.drawable.ic_lock_silent_mode_off);
                        addActionRow("Speak Text (TTS)", "Battery is low, please charge your device", android.R.drawable.ic_btn_speak_now);
                    } else if (which == 1) {
                        editRuleName.setText("Morning Routine");
                        editFolderName.setText("Home");
                        setSpinnerToValue(spinnerTriggerLogic, "ANY");
                        containerTriggers.removeAllViews();
                        addTriggerRow("Time of Day");
                        containerActions.removeAllViews();
                        addActionRow("Play Sound", null, android.R.drawable.ic_media_play);
                        addActionRow("Speak Text (TTS)", "Good morning! Time to start your day!", android.R.drawable.ic_btn_speak_now);
                    } else if (which == 2) {
                        editRuleName.setText("Night Mode");
                        editFolderName.setText("Home");
                        setSpinnerToValue(spinnerTriggerLogic, "ANY");
                        containerTriggers.removeAllViews();
                        addTriggerRow("Time of Day");
                        containerActions.removeAllViews();
                        addActionRow("Mute / Unmute All Sound", null, android.R.drawable.ic_lock_silent_mode_off);
                        addActionRow("Toggle Wi-Fi", null, android.R.drawable.ic_menu_preferences);
                    } else if (which == 3) {
                        editRuleName.setText("Headphone Mode");
                        editFolderName.setText("System");
                        setSpinnerToValue(spinnerTriggerLogic, "ANY");
                        containerTriggers.removeAllViews();
                        addTriggerRow("Headset Plugged");
                        containerActions.removeAllViews();
                        addActionRow("Set Media Volume", "100", android.R.drawable.ic_media_play);
                        addActionRow("Play Sound", null, android.R.drawable.ic_media_play);
                    }
                })
                .show();
    }

    private void showPreviewAndSave(boolean isActive) {
        if (containerActions.getChildCount() == 0 || containerTriggers.getChildCount() == 0) {
            checkPermissionsAndSave(isActive);
            return;
        }

        StringBuilder preview = new StringBuilder();
        preview.append("When ");

        java.util.List<String> triggerList = new java.util.ArrayList<>();
        for (int i = 0; i < containerTriggers.getChildCount(); i++) {
            android.view.View row = containerTriggers.getChildAt(i);
            android.widget.TextView tv = row.findViewById(R.id.text_selected_trigger);
            if (tv != null) {
                if (tv.getTag() != null) {
                    triggerList.add(tv.getTag().toString());
                } else if (tv.getText() != null) {
                    triggerList.add(tv.getText().toString());
                }
            }
        }

        String logic = spinnerTriggerLogic.getSelectedItem() != null ? spinnerTriggerLogic.getSelectedItem().toString() : "ANY";
        String logicWord = "ANY".equals(logic) ? " OR " : " AND ";

        for (int i = 0; i < triggerList.size(); i++) {
            preview.append(triggerList.get(i));
            if (i < triggerList.size() - 2) {
                preview.append(", ");
            } else if (i == triggerList.size() - 2) {
                preview.append(logicWord);
            }
        }

        preview.append(", it triggers ");

        for (int i = 0; i < containerActions.getChildCount(); i++) {
            android.view.View row = containerActions.getChildAt(i);
            android.widget.TextView textAction = row.findViewById(R.id.text_selected_action);
            if (textAction == null || textAction.getText() == null) continue;
            
            String actionType = textAction.getText().toString();

            preview.append(actionType);

            if (i < containerActions.getChildCount() - 2) {
                preview.append(", ");
            } else if (i == containerActions.getChildCount() - 2) {
                preview.append(" AND ");
            }
        }
        preview.append(".");

        String ruleName = editRuleName.getText() != null && !editRuleName.getText().toString().trim().isEmpty() 
            ? editRuleName.getText().toString().trim() 
            : "Rule";

        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_rule_summary, null);
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.text_dialog_title);
        android.widget.TextView tvMessage = dialogView.findViewById(R.id.text_dialog_message);
        android.widget.Button btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);
        android.widget.Button btnSave = dialogView.findViewById(R.id.btn_dialog_save);

        tvTitle.setText(ruleName + " summary Preview");
        tvMessage.setText(preview.toString());

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            dialog.dismiss();
            checkPermissionsAndSave(isActive);
        });

        dialog.show();
    }

    private void checkPermissionsAndSave(boolean isActive) {
        this.pendingSaveIsActive = isActive;
        
        if (containerTriggers.getChildCount() == 0) {
            Toast.makeText(getContext(), "Please add at least one trigger", Toast.LENGTH_SHORT).show();
            return;
        }

        if (containerActions.getChildCount() == 0) {
            Toast.makeText(getContext(), "Please add at least one action", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<String> permissionsNeeded = new HashSet<>();
        String rationaleMessage = "";

        for (int i = 0; i < containerActions.getChildCount(); i++) {
            View row = containerActions.getChildAt(i);
            android.widget.TextView textAction = row.findViewById(R.id.text_selected_action);
            if (textAction == null || textAction.getText() == null) continue;
            String actionType = textAction.getText().toString();

            if ("Toggle Flashlight".equals(actionType)) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    if (!permissionsNeeded.contains(Manifest.permission.CAMERA)) {
                        permissionsNeeded.add(Manifest.permission.CAMERA);
                        rationaleMessage += "Zenith needs Camera access to control the Flashlight.\n\n";
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if ("Show Notification".equals(actionType)) {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        if (!permissionsNeeded.contains(Manifest.permission.POST_NOTIFICATIONS)) {
                            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
                            rationaleMessage += "Zenith needs Notification access to display your rule alerts.\n\n";
                        }
                    }
                }
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Permissions Required")
                    .setMessage(rationaleMessage.trim())
                    .setPositiveButton("Continue", (dialog, which) -> {
                        requestPermissionsLauncher.launch(permissionsNeeded.toArray(new String[0]));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            checkExactAlarmAndSave();
        }
    }

    private void checkExactAlarmAndSave() {
        boolean hasTimeTrigger = false;
        for (int i = 0; i < containerTriggers.getChildCount(); i++) {
            View row = containerTriggers.getChildAt(i);
            android.widget.TextView tv = row.findViewById(R.id.text_selected_trigger);
            if (tv != null && tv.getText() != null) {
                String triggerType = tv.getText().toString();
                if ("Time of Day".equals(triggerType)) {
                    hasTimeTrigger = true;
                    break;
                }
            }
        } if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasTimeTrigger) {
            AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Exact Alarm Required")
                            .setMessage("To trigger rules at a specific time, Zenith needs permission to set Exact Alarms. You will be redirected to Settings to enable this.")
                            .setPositiveButton("Open Settings", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                                requestExactAlarmLauncher.launch(intent);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return;
                }
            }

        saveRule(pendingSaveIsActive);
    }

    private void loadExistingRule(int id) {
        executorService.execute(() -> {
            existingRule = db.ruleDao().getRuleById(id);
            if (existingRule != null) {
                requireActivity().runOnUiThread(() -> {
                    editRuleName.setText(existingRule.ruleName);
                    if (existingRule.folderName != null) {
                        editFolderName.setText(existingRule.folderName);
                    }
                    setSpinnerToValue(spinnerTriggerLogic, existingRule.triggerLogic);
                    
                    try {
                        if (existingRule.triggersJson != null && !existingRule.triggersJson.isEmpty()) {
                            JSONArray triggersArray = new JSONArray(existingRule.triggersJson);
                            for (int i = 0; i < triggersArray.length(); i++) {
                                addTriggerRow(triggersArray.getString(i));
                            }
                        }
                    } catch (Exception e) { android.util.Log.e("Config", "Load triggers error", e); }
                    
                    try {
                        if (existingRule.actionsJson != null && !existingRule.actionsJson.isEmpty()) {
                            JSONArray actionsArray = new JSONArray(existingRule.actionsJson);
                            for (int i = 0; i < actionsArray.length(); i++) {
                                String fullAction = actionsArray.getString(i);
                                String[] parts = fullAction.split("\\|", 2);
                                addActionRow(parts[0], parts.length > 1 ? parts[1] : null, 0);
                            }
                        }
                    } catch (Exception e) { android.util.Log.e("ConfigFragment", "Load actions error", e); }
                });
            }
        });
    }

    private void setSpinnerToValue(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equals(value)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void openTriggerPicker() {
        TriggerPickerDialogFragment dialog = new TriggerPickerDialogFragment();
        dialog.setListener(trigger -> {
            openTriggerStatePicker(trigger.name, trigger.iconResId);
        });
        dialog.show(getChildFragmentManager(), "TriggerPicker");
    }

    private boolean triggerNeedsState(String triggerName) {
        return true; // All triggers now route through the state picker wizard!
    }

    private void openTriggerStatePicker(String triggerName, int iconResId) {
        TriggerStatePickerDialogFragment dialog = new TriggerStatePickerDialogFragment();
        dialog.setTriggerContext(triggerName, iconResId);
        dialog.setListener(fullTriggerString -> addTriggerRow(fullTriggerString));
        dialog.show(getChildFragmentManager(), "TriggerStatePicker");
    }

    private void addTriggerRow(String triggerType) {
        if (triggerType == null) return;
        View triggerView = getLayoutInflater().inflate(R.layout.item_trigger, containerTriggers, false);
        android.widget.TextView textTrigger = triggerView.findViewById(R.id.text_selected_trigger);
        android.widget.ImageButton btnRemove = triggerView.findViewById(R.id.btn_remove_trigger);

        textTrigger.setText(triggerType.replace("|", " ➔ "));
        textTrigger.setTag(triggerType);

        btnRemove.setOnClickListener(v -> containerTriggers.removeView(triggerView));
        containerTriggers.addView(triggerView);
    }

    private void openActionPicker() {
        ActionPickerDialogFragment dialog = new ActionPickerDialogFragment();
        dialog.setListener(action -> {
            ActionStatePickerDialogFragment stateDialog = new ActionStatePickerDialogFragment();
            stateDialog.setActionContext(action.name, action.iconResId);
            stateDialog.setListener((fullActionString, param) -> {
                addActionRow(fullActionString, param, action.iconResId);
            });
            stateDialog.show(getChildFragmentManager(), "ActionStatePicker");
        });
        dialog.show(getChildFragmentManager(), "ActionPicker");
    }

    private void addActionRow(String actionType, String param, int iconResId) {
        if (actionType == null) return;
        View actionView = getLayoutInflater().inflate(R.layout.item_action, containerActions, false);
        android.widget.TextView textAction = actionView.findViewById(R.id.text_selected_action);
        android.widget.ImageView iconAction = actionView.findViewById(R.id.icon_selected_action);
        android.widget.ImageButton btnRemove = actionView.findViewById(R.id.btn_remove_action);

        String displayText = actionType;
        String tagText = actionType;
        if (param != null && !param.isEmpty()) {
            displayText += " ➔ " + param;
            tagText += "|" + param;
        }

        textAction.setText(displayText);
        textAction.setTag(tagText);

        if (iconResId != 0) {
            iconAction.setImageResource(iconResId);
        }

        btnRemove.setOnClickListener(v -> containerActions.removeView(actionView));
        containerActions.addView(actionView);
    }

    // Conditions removed

    private void saveRule(boolean isActive) {
        String ruleName = editRuleName.getText() != null ? editRuleName.getText().toString() : "";
        if (ruleName.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a rule name", Toast.LENGTH_SHORT).show();
            return;
        }

        String folderName = editFolderName.getText() != null ? editFolderName.getText().toString().trim() : "";
        if (folderName.isEmpty() || !isActive) {
            folderName = isActive ? "Default" : "Drafts";
        }

        String triggerLogic = spinnerTriggerLogic.getSelectedItem().toString();
        
        org.json.JSONArray triggersArray = new org.json.JSONArray();
        for (int i = 0; i < containerTriggers.getChildCount(); i++) {
            android.view.View row = containerTriggers.getChildAt(i);
            android.widget.TextView tv = row.findViewById(R.id.text_selected_trigger);
            if (tv != null) {
                if (tv.getTag() != null) {
                    triggersArray.put(tv.getTag().toString());
                } else if (tv.getText() != null) {
                    triggersArray.put(tv.getText().toString());
                }
            }
        }

        org.json.JSONArray actionsArray = new org.json.JSONArray();
        for (int i = 0; i < containerActions.getChildCount(); i++) {
            android.view.View row = containerActions.getChildAt(i);
            android.widget.TextView textAction = row.findViewById(R.id.text_selected_action);
            if (textAction != null) {
                if (textAction.getTag() != null) {
                    actionsArray.put(textAction.getTag().toString());
                } else if (textAction.getText() != null) {
                    actionsArray.put(textAction.getText().toString());
                }
            }
        }
        
        final String finalFolderName = folderName;

        executorService.execute(() -> {
            if (existingRule != null) {
                existingRule.ruleName = ruleName;
                existingRule.folderName = finalFolderName;
                existingRule.triggersJson = triggersArray.toString();
                existingRule.triggerLogic = triggerLogic;
                existingRule.actionsJson = actionsArray.toString();
                if (!isActive) existingRule.isEnabled = false;
                db.ruleDao().update(existingRule);
            } else {
                RuleEntity newRule = new RuleEntity();
                newRule.ruleName = ruleName;
                newRule.folderName = finalFolderName;
                newRule.triggersJson = triggersArray.toString();
                newRule.triggerLogic = triggerLogic;
                newRule.actionsJson = actionsArray.toString();
                newRule.isEnabled = isActive;
                db.ruleDao().insert(newRule);
            }
            
            requireActivity().runOnUiThread(() -> {
                String msg = isActive ? "Rule saved!" : "Saved to Drafts!";
                Toast.makeText(getContext(), existingRule != null ? (isActive ? "Rule updated!" : "Updated and moved to Drafts!") : msg, Toast.LENGTH_SHORT).show();
                
                Intent serviceIntent = new Intent(getContext(), AutomationService.class);
                serviceIntent.setAction("RELOAD_RULES");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(serviceIntent);
                } else {
                    requireContext().startService(serviceIntent);
                }

                if (existingRule == null) {
                    editRuleName.setText("");
                    editFolderName.setText("Default");
                    containerTriggers.removeAllViews();
                    // trigger row not added automatically anymore
                    containerActions.removeAllViews();
                }

                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateToSavedRules();
                }
            });
        });
    }
}
