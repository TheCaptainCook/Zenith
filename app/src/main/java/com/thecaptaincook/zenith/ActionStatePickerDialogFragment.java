package com.thecaptaincook.zenith;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class ActionStatePickerDialogFragment extends DialogFragment {

    public interface ActionStatePickerListener {
        void onStatePicked(String fullActionString, String param);
    }

    private ActionStatePickerListener listener;
    private String actionName;
    private int actionIcon;

    public void setListener(ActionStatePickerListener listener) {
        this.listener = listener;
    }

    public void setActionContext(String actionName, int actionIcon) {
        this.actionName = actionName;
        this.actionIcon = actionIcon;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Zenith);
    }

    private boolean needsTextInput() {
        return "Speak Text (TTS)".equals(actionName) || "Launch App".equals(actionName)
                || "Send SMS".equals(actionName) || "Open URL in Browser".equals(actionName)
                || "Play Sound / Ringtone".equals(actionName) || "Show Notification".equals(actionName)
                || "Send HTTP GET Request".equals(actionName) || "Send Webhook".equals(actionName)
                || "Show Dialog / Popup".equals(actionName)
                || "Show Toast Message".equals(actionName) || "Connect to Wi-Fi Network".equals(actionName)
                || "Make Phone Call".equals(actionName) || "Open Dialer with Number".equals(actionName)
                || "Write to File".equals(actionName) || "Set Variable".equals(actionName)
                || "Rename File".equals(actionName) || "Run Shell Command".equals(actionName)
                || "Run JavaScript".equals(actionName) || "Copy to Clipboard".equals(actionName)
                || "Send Email".equals(actionName) || "Reply to SMS Automatically".equals(actionName)
                || "Forward SMS".equals(actionName) || "Send WhatsApp Message".equals(actionName)
                || "Post to Social Media".equals(actionName) || "Connect to Bluetooth Device".equals(actionName)
                || "Change Network APN".equals(actionName) || "Copy File".equals(actionName)
                || "Move File".equals(actionName) || "Delete File".equals(actionName)
                || "Create Folder".equals(actionName) || "Extract Archive".equals(actionName)
                || "Compress Files".equals(actionName) || "Read File".equals(actionName)
                || "Download File".equals(actionName) || "Check Website Status".equals(actionName)
                || "Upload File to Server".equals(actionName) || "Control Philips Hue".equals(actionName)
                || "Control LIFX".equals(actionName) || "Control Roomba".equals(actionName)
                || "Create Calendar Event".equals(actionName) || "Add To-Do Task".equals(actionName)
                || "Log to Spreadsheet".equals(actionName) || "Start Timer".equals(actionName)
                || "Clear App Data".equals(actionName) || "Uninstall App".equals(actionName)
                || "Disable App".equals(actionName) || "Send Alert SMS with Location".equals(actionName)
                || "Enable/Disable Another Rule".equals(actionName) || "Goto Label".equals(actionName)
                || "Repeat Action".equals(actionName) || "Run Tasker Task".equals(actionName)
                || "Math Operation".equals(actionName) || "Compare Values".equals(actionName)
                || "Concatenate Text".equals(actionName);
    }
    
    private boolean needsSlider() {
        return actionName.contains("Volume") || actionName.contains("Brightness");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (needsSlider()) {
            return setupSliderView(inflater, container);
        } else if (needsTextInput()) {
            return setupInputView(inflater, container);
        } else {
            return setupListView(inflater, container);
        }
    }

    private View setupSliderView(LayoutInflater inflater, ViewGroup container) {
        View view = inflater.inflate(R.layout.fragment_parameter_slider, container, false);
        Toolbar toolbar = view.findViewById(R.id.toolbar_picker);
        toolbar.setTitle("Configure Action");
        toolbar.setNavigationOnClickListener(v -> dismiss());

        TextView txtTitle = view.findViewById(R.id.text_param_title);
        txtTitle.setText(actionName);

        Slider slider = view.findViewById(R.id.slider_param);
        TextView txtSliderValue = view.findViewById(R.id.text_slider_value);
        
        slider.addOnChangeListener((slider1, value, fromUser) -> {
            txtSliderValue.setText((int) value + "%");
        });

        MaterialButton btnSave = view.findViewById(R.id.btn_save_param);
        btnSave.setOnClickListener(v -> {
            if (listener != null) {
                listener.onStatePicked(actionName, (int) slider.getValue() + "%");
            }
            dismiss();
        });

        return view;
    }

    private View setupInputView(LayoutInflater inflater, ViewGroup container) {
        View view = inflater.inflate(R.layout.fragment_parameter_input, container, false);
        Toolbar toolbar = view.findViewById(R.id.toolbar_picker);
        toolbar.setTitle("Configure Action");
        toolbar.setNavigationOnClickListener(v -> dismiss());

        TextView txtTitle = view.findViewById(R.id.text_param_title);
        txtTitle.setText(actionName);

        TextInputLayout til = view.findViewById(R.id.til_param_input);
        TextInputEditText editInput = view.findViewById(R.id.edit_param_input);
        
        if (actionName.contains("App")) {
            til.setHint("Package name (e.g. com.spotify.music)");
        } else if (actionName.contains("SMS") || actionName.contains("Phone")) {
            til.setHint("Phone Number (or Contact)");
        } else if (actionName.contains("URL") || actionName.contains("HTTP") || actionName.contains("Webhook")) {
            til.setHint("URL Address");
        } else if (actionName.contains("File") || actionName.contains("Folder")) {
            til.setHint("File Path");
        } else {
            til.setHint("Enter parameter");
        }

        MaterialButton btnSave = view.findViewById(R.id.btn_save_param);
        btnSave.setOnClickListener(v -> {
            String input = editInput.getText() != null ? editInput.getText().toString().trim() : "";
            if (input.isEmpty()) {
                Toast.makeText(getContext(), "Parameter cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (listener != null) {
                listener.onStatePicked(actionName, input);
            }
            dismiss();
        });

        return view;
    }

    private View setupListView(LayoutInflater inflater, ViewGroup container) {
        View view = inflater.inflate(R.layout.fragment_trigger_picker, container, false);
        Toolbar toolbar = view.findViewById(R.id.toolbar_picker);
        toolbar.setTitle("Configure Action");
        toolbar.setNavigationOnClickListener(v -> dismiss());

        View searchBox = view.findViewById(R.id.til_search);
        if (searchBox != null) {
            searchBox.setVisibility(View.GONE);
        }

        RecyclerView recycler = view.findViewById(R.id.recycler_triggers);
        
        ActionPickerAdapter adapter = new ActionPickerAdapter(getStateOptions(), option -> {
            if (listener != null) {
                String param = "Execute".equals(option.name) ? null : option.name;
                listener.onStatePicked(actionName, param);
            }
            dismiss();
        });

        androidx.recyclerview.widget.GridLayoutManager gridLayoutManager = new androidx.recyclerview.widget.GridLayoutManager(getContext(), 2);
        gridLayoutManager.setSpanSizeLookup(new androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getItemViewType(position) == ActionOption.TYPE_HEADER ? 2 : 1;
            }
        });
        recycler.setLayoutManager(gridLayoutManager);
        recycler.setAdapter(adapter);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private List<ActionOption> getStateOptions() {
        List<ActionOption> list = new ArrayList<>();
        list.add(new ActionOption("Available Options"));

        if (actionName.contains("Toggle") || actionName.contains("Enable/Disable")) {
            list.add(new ActionOption("Turn On", "Activate the feature", actionIcon, "State"));
            list.add(new ActionOption("Turn Off", "Deactivate the feature", actionIcon, "State"));
            list.add(new ActionOption("Toggle", "Switch between On and Off", actionIcon, "State"));
        } else if ("Set Preferred Network Type".equals(actionName)) {
            list.add(new ActionOption("5G", "Prefer 5G networks", actionIcon, "State"));
            list.add(new ActionOption("4G", "Prefer LTE networks", actionIcon, "State"));
            list.add(new ActionOption("3G", "Prefer 3G networks", actionIcon, "State"));
            list.add(new ActionOption("2G", "Prefer 2G networks", actionIcon, "State"));
        } else if ("Change Language".equals(actionName)) {
            list.add(new ActionOption("English", "", actionIcon, "State"));
            list.add(new ActionOption("Spanish", "", actionIcon, "State"));
            list.add(new ActionOption("French", "", actionIcon, "State"));
            list.add(new ActionOption("German", "", actionIcon, "State"));
        } else if ("Open Specific Settings Page".equals(actionName)) {
            list.add(new ActionOption("Wi-Fi Settings", "", actionIcon, "State"));
            list.add(new ActionOption("Bluetooth Settings", "", actionIcon, "State"));
            list.add(new ActionOption("Display Settings", "", actionIcon, "State"));
            list.add(new ActionOption("Battery Settings", "", actionIcon, "State"));
        } else if ("Set Notification LED Color".equals(actionName) || "Change Accent Color".equals(actionName)) {
            list.add(new ActionOption("Red", "", actionIcon, "State"));
            list.add(new ActionOption("Green", "", actionIcon, "State"));
            list.add(new ActionOption("Blue", "", actionIcon, "State"));
            list.add(new ActionOption("Yellow", "", actionIcon, "State"));
        } else if ("Press Volume Key".equals(actionName)) {
            list.add(new ActionOption("Volume Up", "", actionIcon, "State"));
            list.add(new ActionOption("Volume Down", "", actionIcon, "State"));
        } else if ("Wait / Delay".equals(actionName)) {
            list.add(new ActionOption("1 Second", "", actionIcon, "State"));
            list.add(new ActionOption("5 Seconds", "", actionIcon, "State"));
            list.add(new ActionOption("10 Seconds", "", actionIcon, "State"));
            list.add(new ActionOption("30 Seconds", "", actionIcon, "State"));
            list.add(new ActionOption("1 Minute", "", actionIcon, "State"));
            list.add(new ActionOption("5 Minutes", "", actionIcon, "State"));
        } else {
            list.add(new ActionOption("Execute", "Run this action", actionIcon, "State"));
        }
        
        return list;
    }
}
