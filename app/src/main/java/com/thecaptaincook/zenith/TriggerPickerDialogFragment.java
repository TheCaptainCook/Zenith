package com.thecaptaincook.zenith;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class TriggerPickerDialogFragment extends DialogFragment {

    public interface TriggerPickerListener {
        void onTriggerPicked(TriggerOption trigger);
    }

    private TriggerPickerListener listener;

    public void setListener(TriggerPickerListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Zenith); // use app theme for full screen
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_trigger_picker, container, false);

        Toolbar toolbar = view.findViewById(R.id.toolbar_picker);
        toolbar.setNavigationOnClickListener(v -> dismiss());

        RecyclerView recycler = view.findViewById(R.id.recycler_triggers);
        
        TriggerPickerAdapter adapter = new TriggerPickerAdapter(getTriggerOptions(), trigger -> {
            if (listener != null) {
                listener.onTriggerPicked(trigger);
            }
            dismiss();
        });

        com.google.android.material.textfield.TextInputEditText editSearch = view.findViewById(R.id.edit_search);
        editSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
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

    private List<TriggerOption> getTriggerOptions() {
        List<TriggerOption> list = new ArrayList<>();
        
        // Connectivity & Network
        list.add(new TriggerOption("Connectivity & Network"));
        list.add(new TriggerOption("Wi-Fi Connected / Disconnected", "Fires when you connect to or disconnect from a specific Wi-Fi network.", android.R.drawable.ic_menu_preferences, "Connectivity"));
        list.add(new TriggerOption("Bluetooth Device", "Fires when your phone connects to or disconnects from a specific Bluetooth device.", android.R.drawable.ic_menu_preferences, "Connectivity"));
        list.add(new TriggerOption("Bluetooth State Changed", "Fires when you manually turn Bluetooth on or off.", android.R.drawable.ic_menu_preferences, "Connectivity"));
        list.add(new TriggerOption("Airplane Mode Changed", "Fires when Airplane Mode is activated or deactivated.", android.R.drawable.ic_menu_preferences, "Connectivity"));
        list.add(new TriggerOption("Mobile Data State Changed", "Fires when the mobile data connection is turned on or off.", android.R.drawable.ic_menu_preferences, "Connectivity"));
        list.add(new TriggerOption("NFC Tag Detected", "Fires when you tap an NFC tag against your phone.", android.R.drawable.ic_menu_preferences, "Connectivity"));
        list.add(new TriggerOption("Cell Tower Connected", "Fires when your phone connects to or disconnects from a specific cellular tower.", android.R.drawable.ic_menu_preferences, "Connectivity"));

        // Communication & Notifications
        list.add(new TriggerOption("Communication & Notifications"));
        list.add(new TriggerOption("SMS Received", "Fires when a new SMS is received. Can be filtered by number or keyword.", android.R.drawable.ic_dialog_email, "Communication"));
        list.add(new TriggerOption("SMS Sent", "Fires after you send an SMS.", android.R.drawable.ic_dialog_email, "Communication"));
        list.add(new TriggerOption("Call State", "Fires when you receive, make, or miss a phone call.", android.R.drawable.ic_menu_call, "Communication"));
        list.add(new TriggerOption("Notification Received", "Fires when an app posts a new notification.", android.R.drawable.ic_dialog_info, "Communication"));

        // Device State & Sensors
        list.add(new TriggerOption("Device State & Sensors"));
        list.add(new TriggerOption("Battery Level", "Fires when the battery level goes above or below a set percentage.", android.R.drawable.ic_lock_idle_charging, "Device"));
        list.add(new TriggerOption("Power Connected / Disconnected", "Fires when you plug in or unplug your phone from a power source.", android.R.drawable.ic_lock_idle_charging, "Device"));
        list.add(new TriggerOption("Screen On / Off / Unlocked", "Fires when the screen state changes.", android.R.drawable.ic_menu_view, "Device"));
        list.add(new TriggerOption("Device Shake", "Fires when the device is shaken with a certain force.", android.R.drawable.ic_popup_sync, "Device"));
        list.add(new TriggerOption("Device Orientation", "Fires when the device's orientation changes.", android.R.drawable.ic_popup_sync, "Device"));
        list.add(new TriggerOption("Headset Plugged", "Fires when a wired headset is plugged in or removed.", android.R.drawable.ic_media_play, "Device"));
        list.add(new TriggerOption("Docked / Undocked", "Fires when the device is placed into or removed from a dock.", android.R.drawable.ic_menu_preferences, "Device"));
        list.add(new TriggerOption("USB Connected", "Fires when a USB cable is connected or disconnected.", android.R.drawable.ic_menu_preferences, "Device"));

        // Time, Location & Weather
        list.add(new TriggerOption("Time, Location & Weather"));
        list.add(new TriggerOption("Time of Day", "Fires at a specific time of day or at a recurring interval.", android.R.drawable.ic_menu_recent_history, "Time"));
        list.add(new TriggerOption("Sunrise / Sunset", "Fires at the calculated sunrise or sunset time.", android.R.drawable.ic_menu_recent_history, "Time"));
        list.add(new TriggerOption("Geofence", "Fires when you physically enter or exit a predefined area.", android.R.drawable.ic_menu_mylocation, "Time"));
        list.add(new TriggerOption("Weather Condition", "Fires based on a weather forecast.", android.R.drawable.ic_menu_mapmode, "Time"));

        // App & System Events
        list.add(new TriggerOption("App & System Events"));
        list.add(new TriggerOption("Application Launched / Closed", "Fires when a specific app is opened or closed.", android.R.drawable.ic_menu_manage, "System"));
        list.add(new TriggerOption("Device Boot Completed", "Fires when the device has finished booting up.", android.R.drawable.ic_menu_manage, "System"));
        list.add(new TriggerOption("Daydream / Screensaver", "Fires when the device's screensaver starts or stops.", android.R.drawable.ic_menu_manage, "System"));
        list.add(new TriggerOption("Wallpaper Changed", "Fires when the home screen or lock screen wallpaper is changed.", android.R.drawable.ic_menu_gallery, "System"));

        // Manual & Advanced
        list.add(new TriggerOption("Manual & Advanced Triggers"));
        list.add(new TriggerOption("Manual / Widget Trigger", "A trigger the user can activate manually via a homescreen shortcut.", android.R.drawable.ic_menu_add, "Advanced"));
        list.add(new TriggerOption("Activity Recognition", "Fires when the device detects you are in a specific motion state.", android.R.drawable.ic_menu_directions, "Advanced"));
        list.add(new TriggerOption("Gesture", "Fires when a specific touch gesture is performed on the screen.", android.R.drawable.ic_menu_edit, "Advanced"));
        list.add(new TriggerOption("Hardware Button Press", "Fires when a physical button is pressed.", android.R.drawable.ic_menu_agenda, "Advanced"));

        return list;
    }
}
