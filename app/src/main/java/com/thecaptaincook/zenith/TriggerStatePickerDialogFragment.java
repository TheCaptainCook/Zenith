package com.thecaptaincook.zenith;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.TextView;

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

public class TriggerStatePickerDialogFragment extends DialogFragment {

    public interface TriggerStatePickerListener {
        void onStatePicked(String fullTriggerString);
    }

    private TriggerStatePickerListener listener;
    private String triggerName;
    private int triggerIcon;

    public void setListener(TriggerStatePickerListener listener) {
        this.listener = listener;
    }

    public void setTriggerContext(String triggerName, int triggerIcon) {
        this.triggerName = triggerName;
        this.triggerIcon = triggerIcon;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Zenith);
    }

    private boolean needsTextInput() {
        return "SMS Received".equals(triggerName) || "Notification Received".equals(triggerName)
                || "Application Launched / Closed".equals(triggerName) || "NFC Tag Detected".equals(triggerName)
                || "Cell Tower Connected".equals(triggerName) || "Time of Day".equals(triggerName)
                || "Geofence".equals(triggerName);
    }

    private boolean needsSlider() {
        return "Battery Level".equals(triggerName);
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
        toolbar.setTitle("Configure Trigger");
        toolbar.setNavigationOnClickListener(v -> dismiss());

        TextView txtTitle = view.findViewById(R.id.text_param_title);
        txtTitle.setText(triggerName);

        Slider slider = view.findViewById(R.id.slider_param);
        TextView txtSliderValue = view.findViewById(R.id.text_slider_value);
        
        slider.addOnChangeListener((slider1, value, fromUser) -> {
            txtSliderValue.setText((int) value + "%");
        });

        MaterialButton btnSave = view.findViewById(R.id.btn_save_param);
        btnSave.setOnClickListener(v -> {
            if (listener != null) {
                listener.onStatePicked(triggerName + "|" + (int) slider.getValue() + "%");
            }
            dismiss();
        });

        return view;
    }

    private View setupInputView(LayoutInflater inflater, ViewGroup container) {
        View view = inflater.inflate(R.layout.fragment_parameter_input, container, false);
        Toolbar toolbar = view.findViewById(R.id.toolbar_picker);
        toolbar.setTitle("Configure Trigger");
        toolbar.setNavigationOnClickListener(v -> dismiss());

        TextView txtTitle = view.findViewById(R.id.text_param_title);
        txtTitle.setText(triggerName);

        TextInputLayout til = view.findViewById(R.id.til_param_input);
        TextInputEditText editInput = view.findViewById(R.id.edit_param_input);
        
        if ("SMS Received".equals(triggerName)) {
            til.setHint("Phone Number or Keyword");
        } else if ("Notification Received".equals(triggerName) || "Application Launched / Closed".equals(triggerName)) {
            til.setHint("App Package Name (e.g. com.whatsapp)");
        } else if ("Time of Day".equals(triggerName)) {
            til.setHint("Time (e.g. 08:00 AM)");
        } else if ("Geofence".equals(triggerName)) {
            til.setHint("Location Name or Coordinates");
        } else {
            til.setHint("Enter parameter");
        }

        MaterialButton btnSave = view.findViewById(R.id.btn_save_param);
        btnSave.setOnClickListener(v -> {
            String input = editInput.getText() != null ? editInput.getText().toString().trim() : "";
            if (input.isEmpty()) {
                ThemePrompt.show(getView(), "Parameter cannot be empty");
                return;
            }
            if (listener != null) {
                listener.onStatePicked(triggerName + "|" + input);
            }
            dismiss();
        });

        return view;
    }

    private View setupListView(LayoutInflater inflater, ViewGroup container) {
        View view = inflater.inflate(R.layout.fragment_trigger_picker, container, false);
        Toolbar toolbar = view.findViewById(R.id.toolbar_picker);
        toolbar.setTitle("Select State: " + triggerName);
        toolbar.setNavigationOnClickListener(v -> dismiss());

        RecyclerView recycler = view.findViewById(R.id.recycler_triggers);
        
        TriggerPickerAdapter adapter = new TriggerPickerAdapter(getStateOptions(), option -> {
            if (listener != null) {
                String param = "Execute".equals(option.name) ? "" : "|" + option.name;
                listener.onStatePicked(triggerName + param);
            }
            dismiss();
        });

        androidx.recyclerview.widget.GridLayoutManager gridLayoutManager = new androidx.recyclerview.widget.GridLayoutManager(getContext(), 2);
        gridLayoutManager.setSpanSizeLookup(new androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getItemViewType(position) == TriggerOption.TYPE_HEADER ? 2 : 1;
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

    private List<TriggerOption> getStateOptions() {
        List<TriggerOption> list = new ArrayList<>();
        list.add(new TriggerOption("Available States"));
        
        if ("Bluetooth State Changed".equals(triggerName) || "Airplane Mode Changed".equals(triggerName) || "Mobile Data State Changed".equals(triggerName)) {
            list.add(new TriggerOption("On", "Triggers when turned ON", triggerIcon, "State"));
            list.add(new TriggerOption("Off", "Triggers when turned OFF", triggerIcon, "State"));
        } else if ("Wi-Fi Connected / Disconnected".equals(triggerName) || "Power Connected / Disconnected".equals(triggerName) || "Bluetooth Device".equals(triggerName) || "USB Connected".equals(triggerName) || "Headset Plugged".equals(triggerName)) {
            list.add(new TriggerOption("Connected", "Triggers when connected", triggerIcon, "State"));
            list.add(new TriggerOption("Disconnected", "Triggers when disconnected", triggerIcon, "State"));
        } else if ("Screen On / Off / Unlocked".equals(triggerName)) {
            list.add(new TriggerOption("Screen On", "When screen turns on", triggerIcon, "State"));
            list.add(new TriggerOption("Screen Off", "When screen turns off", triggerIcon, "State"));
            list.add(new TriggerOption("User Present", "When device is unlocked", triggerIcon, "State"));
        } else if ("Docked / Undocked".equals(triggerName)) {
            list.add(new TriggerOption("Docked", "When device is placed in dock", triggerIcon, "State"));
            list.add(new TriggerOption("Undocked", "When device is removed from dock", triggerIcon, "State"));
        } else if ("Call State".equals(triggerName)) {
            list.add(new TriggerOption("Incoming Call", "", triggerIcon, "State"));
            list.add(new TriggerOption("Outgoing Call", "", triggerIcon, "State"));
            list.add(new TriggerOption("Missed Call", "", triggerIcon, "State"));
        } else if ("Device Shake".equals(triggerName)) {
            list.add(new TriggerOption("Light Shake", "", triggerIcon, "State"));
            list.add(new TriggerOption("Normal Shake", "", triggerIcon, "State"));
            list.add(new TriggerOption("Hard Shake", "", triggerIcon, "State"));
        } else if ("Device Orientation".equals(triggerName)) {
            list.add(new TriggerOption("Portrait", "", triggerIcon, "State"));
            list.add(new TriggerOption("Landscape", "", triggerIcon, "State"));
            list.add(new TriggerOption("Face Up", "", triggerIcon, "State"));
            list.add(new TriggerOption("Face Down", "", triggerIcon, "State"));
        } else if ("Weather Condition".equals(triggerName)) {
            list.add(new TriggerOption("Clear", "", triggerIcon, "State"));
            list.add(new TriggerOption("Rain", "", triggerIcon, "State"));
            list.add(new TriggerOption("Snow", "", triggerIcon, "State"));
            list.add(new TriggerOption("Cloudy", "", triggerIcon, "State"));
        } else if ("Activity Recognition".equals(triggerName)) {
            list.add(new TriggerOption("Walking", "", triggerIcon, "State"));
            list.add(new TriggerOption("Running", "", triggerIcon, "State"));
            list.add(new TriggerOption("Cycling", "", triggerIcon, "State"));
            list.add(new TriggerOption("Driving", "", triggerIcon, "State"));
        } else if ("Gesture".equals(triggerName)) {
            list.add(new TriggerOption("Swipe Up", "", triggerIcon, "State"));
            list.add(new TriggerOption("Swipe Down", "", triggerIcon, "State"));
            list.add(new TriggerOption("Double Tap", "", triggerIcon, "State"));
        } else if ("Hardware Button Press".equals(triggerName)) {
            list.add(new TriggerOption("Volume Up", "", triggerIcon, "State"));
            list.add(new TriggerOption("Volume Down", "", triggerIcon, "State"));
        } else {
            list.add(new TriggerOption("Execute", "Run this trigger", triggerIcon, "State"));
        }
        
        return list;
    }
}
