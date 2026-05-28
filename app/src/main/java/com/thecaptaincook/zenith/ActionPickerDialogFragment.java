package com.thecaptaincook.zenith;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ActionPickerDialogFragment extends DialogFragment {

    public interface OnActionPickedListener {
        void onActionPicked(ActionOption action);
    }

    private OnActionPickedListener listener;

    public void setListener(OnActionPickedListener listener) {
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
        toolbar.setTitle("Select Action");
        toolbar.setNavigationOnClickListener(v -> dismiss());

        RecyclerView recycler = view.findViewById(R.id.recycler_triggers);
        
        ActionPickerAdapter adapter = new ActionPickerAdapter(getActionOptions(), action -> {
            if (listener != null) {
                listener.onActionPicked(action);
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

    private List<ActionOption> getActionOptions() {
        List<ActionOption> list = new ArrayList<>();
        list.add(new ActionOption("Audio & Volume Control"));
        list.add(new ActionOption("Set Ringtone Volume", "Change volume level for incoming calls (0-100%)", android.R.drawable.ic_lock_silent_mode, "Audio & Volume Control"));
        list.add(new ActionOption("Set Media Volume", "Change volume for music, videos, and games", android.R.drawable.ic_lock_silent_mode, "Audio & Volume Control"));
        list.add(new ActionOption("Set Alarm Volume", "Change volume for alarms", android.R.drawable.ic_lock_silent_mode, "Audio & Volume Control"));
        list.add(new ActionOption("Set Notification Volume", "Change volume for app notifications", android.R.drawable.ic_lock_silent_mode, "Audio & Volume Control"));
        list.add(new ActionOption("Set Call Volume", "Change in-call audio volume", android.R.drawable.ic_lock_silent_mode, "Audio & Volume Control"));
        list.add(new ActionOption("Mute / Unmute All Sound", "Completely silence or restore all audio", android.R.drawable.ic_lock_silent_mode, "Audio & Volume Control"));
        list.add(new ActionOption("Vibrate Mode On/Off", "Toggle vibration for calls and notifications", android.R.drawable.ic_lock_silent_mode, "Audio & Volume Control"));
        list.add(new ActionOption("Play Sound / Ringtone", "Play a custom sound file, ringtone, or alarm tone", android.R.drawable.ic_lock_silent_mode, "Audio & Volume Control"));
        list.add(new ActionOption("Speak Text (TTS)", "Use Text-to-Speech to read a message aloud", android.R.drawable.ic_lock_silent_mode, "Audio & Volume Control"));
        list.add(new ActionOption("Increase Volume Gradually", "Slowly ramp up volume over a set time period", android.R.drawable.ic_lock_silent_mode, "Audio & Volume Control"));
        list.add(new ActionOption("Voice Announcement", "Announce caller name, message sender, or battery level", android.R.drawable.ic_lock_silent_mode, "Audio & Volume Control"));
        list.add(new ActionOption("Device Hardware & System"));
        list.add(new ActionOption("Toggle Flashlight / Torch", "Turn the camera LED flash on or off", android.R.drawable.ic_menu_camera, "Device Hardware & System"));
        list.add(new ActionOption("Set Screen Brightness", "Adjust display brightness (0-100% or auto)", android.R.drawable.ic_menu_camera, "Device Hardware & System"));
        list.add(new ActionOption("Set Screen Timeout", "Change how long until screen turns off", android.R.drawable.ic_menu_camera, "Device Hardware & System"));
        list.add(new ActionOption("Lock Screen", "Immediately turn off the screen and lock the device", android.R.drawable.ic_menu_camera, "Device Hardware & System"));
        list.add(new ActionOption("Toggle Always-On Display", "Enable or disable always-on screen mode", android.R.drawable.ic_menu_camera, "Device Hardware & System"));
        list.add(new ActionOption("Restart Device", "Reboot the phone (may require root)", android.R.drawable.ic_menu_camera, "Device Hardware & System"));
        list.add(new ActionOption("Shutdown Device", "Power off the phone (may require root)", android.R.drawable.ic_menu_camera, "Device Hardware & System"));
        list.add(new ActionOption("Set Wallpaper", "Change home screen or lock screen wallpaper", android.R.drawable.ic_menu_camera, "Device Hardware & System"));
        list.add(new ActionOption("Take Screenshot", "Automatically capture a screenshot", android.R.drawable.ic_menu_camera, "Device Hardware & System"));
        list.add(new ActionOption("Keep Screen On", "Prevent screen from timing out (temporarily)", android.R.drawable.ic_menu_camera, "Device Hardware & System"));
        list.add(new ActionOption("Toggle Auto-Rotate", "Enable or disable screen rotation", android.R.drawable.ic_menu_camera, "Device Hardware & System"));
        list.add(new ActionOption("Set Font Size", "Change system font scaling", android.R.drawable.ic_menu_camera, "Device Hardware & System"));
        list.add(new ActionOption("Connectivity & Network"));
        list.add(new ActionOption("Toggle Wi-Fi", "Turn Wi-Fi on or off", android.R.drawable.ic_menu_share, "Connectivity & Network"));
        list.add(new ActionOption("Connect to Wi-Fi Network", "Automatically connect to a specific SSID", android.R.drawable.ic_menu_share, "Connectivity & Network"));
        list.add(new ActionOption("Toggle Bluetooth", "Turn Bluetooth on or off", android.R.drawable.ic_menu_share, "Connectivity & Network"));
        list.add(new ActionOption("Connect to Bluetooth Device", "Pair or connect to a specific Bluetooth device", android.R.drawable.ic_menu_share, "Connectivity & Network"));
        list.add(new ActionOption("Toggle Mobile Data", "Turn cellular data on or off", android.R.drawable.ic_menu_share, "Connectivity & Network"));
        list.add(new ActionOption("Toggle Airplane Mode", "Enable or disable Airplane Mode", android.R.drawable.ic_menu_share, "Connectivity & Network"));
        list.add(new ActionOption("Toggle Hotspot", "Turn personal Wi-Fi hotspot on or off", android.R.drawable.ic_menu_share, "Connectivity & Network"));
        list.add(new ActionOption("Toggle NFC", "Enable or disable NFC", android.R.drawable.ic_menu_share, "Connectivity & Network"));
        list.add(new ActionOption("Toggle VPN", "Connect or disconnect a VPN", android.R.drawable.ic_menu_share, "Connectivity & Network"));
        list.add(new ActionOption("Change Network APN", "Modify Access Point Name settings", android.R.drawable.ic_menu_share, "Connectivity & Network"));
        list.add(new ActionOption("Set Preferred Network Type", "Switch between 5G/4G/3G/2G", android.R.drawable.ic_menu_share, "Connectivity & Network"));
        list.add(new ActionOption("System Settings & Modes"));
        list.add(new ActionOption("Enable/Disable Do Not Disturb", "Turn DND mode on or off", android.R.drawable.ic_menu_manage, "System Settings & Modes"));
        list.add(new ActionOption("Set DND Priority Settings", "Configure which notifications can bypass DND", android.R.drawable.ic_menu_manage, "System Settings & Modes"));
        list.add(new ActionOption("Toggle Battery Saver", "Turn power saving mode on or off", android.R.drawable.ic_menu_manage, "System Settings & Modes"));
        list.add(new ActionOption("Toggle Dark Mode", "Switch between light and dark theme", android.R.drawable.ic_menu_manage, "System Settings & Modes"));
        list.add(new ActionOption("Toggle Location Services", "Turn GPS/location on or off", android.R.drawable.ic_menu_manage, "System Settings & Modes"));
        list.add(new ActionOption("Toggle Auto-Sync", "Enable or disable background data sync for apps", android.R.drawable.ic_menu_manage, "System Settings & Modes"));
        list.add(new ActionOption("Toggle Developer Options", "Enable/disable developer settings", android.R.drawable.ic_menu_manage, "System Settings & Modes"));
        list.add(new ActionOption("Change Language", "Switch system language", android.R.drawable.ic_menu_manage, "System Settings & Modes"));
        list.add(new ActionOption("Open Specific Settings Page", "Launch Wi-Fi, Bluetooth, or battery settings", android.R.drawable.ic_menu_manage, "System Settings & Modes"));
        list.add(new ActionOption("Communication & Messaging"));
        list.add(new ActionOption("Send SMS", "Send a text message to a specific number", android.R.drawable.ic_dialog_email, "Communication & Messaging"));
        list.add(new ActionOption("Send MMS", "Send a picture or video message", android.R.drawable.ic_dialog_email, "Communication & Messaging"));
        list.add(new ActionOption("Make Phone Call", "Directly call a number (automatic, no dialer)", android.R.drawable.ic_dialog_email, "Communication & Messaging"));
        list.add(new ActionOption("Open Dialer with Number", "Open phone app with number pre-filled", android.R.drawable.ic_dialog_email, "Communication & Messaging"));
        list.add(new ActionOption("Send Email", "Compose and send email via Gmail/Outlook", android.R.drawable.ic_dialog_email, "Communication & Messaging"));
        list.add(new ActionOption("Reply to SMS Automatically", "Send auto-reply when receiving messages", android.R.drawable.ic_dialog_email, "Communication & Messaging"));
        list.add(new ActionOption("Forward SMS", "Forward incoming SMS to another number", android.R.drawable.ic_dialog_email, "Communication & Messaging"));
        list.add(new ActionOption("Send WhatsApp Message", "Send message via WhatsApp (requires app)", android.R.drawable.ic_dialog_email, "Communication & Messaging"));
        list.add(new ActionOption("Send Telegram Message", "Send message via Telegram (requires app)", android.R.drawable.ic_dialog_email, "Communication & Messaging"));
        list.add(new ActionOption("Post to Social Media", "Share text/image to Twitter, Facebook, etc.", android.R.drawable.ic_dialog_email, "Communication & Messaging"));
        list.add(new ActionOption("Notifications & Alerts"));
        list.add(new ActionOption("Show Notification", "Display a custom notification in the status bar", android.R.drawable.ic_dialog_info, "Notifications & Alerts"));
        list.add(new ActionOption("Show Dialog / Popup", "Display a full-screen alert dialog", android.R.drawable.ic_dialog_info, "Notifications & Alerts"));
        list.add(new ActionOption("Show Toast Message", "Show a brief popup message", android.R.drawable.ic_dialog_info, "Notifications & Alerts"));
        list.add(new ActionOption("Announce with TTS", "Speak text aloud through the speaker", android.R.drawable.ic_dialog_info, "Notifications & Alerts"));
        list.add(new ActionOption("Cancel All Notifications", "Clear all notifications from the status bar", android.R.drawable.ic_dialog_info, "Notifications & Alerts"));
        list.add(new ActionOption("Dismiss Specific Notification", "Remove a specific app's notification", android.R.drawable.ic_dialog_info, "Notifications & Alerts"));
        list.add(new ActionOption("Vibrate", "Trigger a custom vibration pattern", android.R.drawable.ic_dialog_info, "Notifications & Alerts"));
        list.add(new ActionOption("Flash LED", "Blink the camera flash or notification LED", android.R.drawable.ic_dialog_info, "Notifications & Alerts"));
        list.add(new ActionOption("Set Notification LED Color", "Change the LED color (on phones that support it)", android.R.drawable.ic_dialog_info, "Notifications & Alerts"));
        list.add(new ActionOption("Blink Screen", "Flash the screen on/off for alerts", android.R.drawable.ic_dialog_info, "Notifications & Alerts"));
        list.add(new ActionOption("File & Storage Management"));
        list.add(new ActionOption("Copy File", "Duplicate a file to another location", android.R.drawable.ic_menu_save, "File & Storage Management"));
        list.add(new ActionOption("Move File", "Relocate a file from one folder to another", android.R.drawable.ic_menu_save, "File & Storage Management"));
        list.add(new ActionOption("Delete File", "Permanently remove a file", android.R.drawable.ic_menu_save, "File & Storage Management"));
        list.add(new ActionOption("Rename File", "Change a file's name", android.R.drawable.ic_menu_save, "File & Storage Management"));
        list.add(new ActionOption("Create Folder", "Create a new directory", android.R.drawable.ic_menu_save, "File & Storage Management"));
        list.add(new ActionOption("Extract Archive", "Unzip a .zip or .rar file", android.R.drawable.ic_menu_save, "File & Storage Management"));
        list.add(new ActionOption("Compress Files", "Create a .zip archive", android.R.drawable.ic_menu_save, "File & Storage Management"));
        list.add(new ActionOption("Write to File", "Append or write text to a .txt file", android.R.drawable.ic_menu_save, "File & Storage Management"));
        list.add(new ActionOption("Read File", "Read content from a file and use as variable", android.R.drawable.ic_menu_save, "File & Storage Management"));
        list.add(new ActionOption("Save Image to Gallery", "Download and save an image to DCIM or Pictures", android.R.drawable.ic_menu_save, "File & Storage Management"));
        list.add(new ActionOption("Backup File", "Create a copy with timestamp in filename", android.R.drawable.ic_menu_save, "File & Storage Management"));
        list.add(new ActionOption("Camera & Media"));
        list.add(new ActionOption("Take Photo (Front/Rear)", "Capture a photo automatically (no viewfinder)", android.R.drawable.ic_menu_gallery, "Camera & Media"));
        list.add(new ActionOption("Record Video", "Start/stop video recording", android.R.drawable.ic_menu_gallery, "Camera & Media"));
        list.add(new ActionOption("Record Audio", "Capture microphone input to an audio file", android.R.drawable.ic_menu_gallery, "Camera & Media"));
        list.add(new ActionOption("Take Timelapse", "Capture series of photos at intervals", android.R.drawable.ic_menu_gallery, "Camera & Media"));
        list.add(new ActionOption("Play Media File", "Play audio or video file", android.R.drawable.ic_menu_gallery, "Camera & Media"));
        list.add(new ActionOption("Pause/Resume Media", "Control currently playing media", android.R.drawable.ic_menu_gallery, "Camera & Media"));
        list.add(new ActionOption("Skip to Next/Previous Track", "Control music playback", android.R.drawable.ic_menu_gallery, "Camera & Media"));
        list.add(new ActionOption("Increase/Decrease Playback Speed", "Change speed of media", android.R.drawable.ic_menu_gallery, "Camera & Media"));
        list.add(new ActionOption("Set Media Player Volume", "Adjust volume of specific media player", android.R.drawable.ic_menu_gallery, "Camera & Media"));
        list.add(new ActionOption("Web & Internet"));
        list.add(new ActionOption("Open URL in Browser", "Launch a specific website", android.R.drawable.ic_menu_search, "Web & Internet"));
        list.add(new ActionOption("Download File", "Download a file from a direct URL", android.R.drawable.ic_menu_search, "Web & Internet"));
        list.add(new ActionOption("Send HTTP GET Request", "Call a web API endpoint", android.R.drawable.ic_menu_search, "Web & Internet"));
        list.add(new ActionOption("Send HTTP POST Request", "Submit data to a web server", android.R.drawable.ic_menu_search, "Web & Internet"));
        list.add(new ActionOption("Send Webhook", "Trigger a webhook URL (IFTTT, Zapier, etc.)", android.R.drawable.ic_menu_search, "Web & Internet"));
        list.add(new ActionOption("Fetch RSS Feed", "Read and parse RSS feed content", android.R.drawable.ic_menu_search, "Web & Internet"));
        list.add(new ActionOption("Check Website Status", "Ping a URL to see if it's online", android.R.drawable.ic_menu_search, "Web & Internet"));
        list.add(new ActionOption("Upload File to Server", "FTP or HTTP upload", android.R.drawable.ic_menu_search, "Web & Internet"));

        list.add(new ActionOption("Calendar & Productivity"));
        list.add(new ActionOption("Create Calendar Event", "Add event to Google Calendar", android.R.drawable.ic_menu_today, "Calendar & Productivity"));
        list.add(new ActionOption("Delete Calendar Event", "Remove an event by title/time", android.R.drawable.ic_menu_today, "Calendar & Productivity"));
        list.add(new ActionOption("Add Reminder", "Create a reminder in Google Tasks/Keep", android.R.drawable.ic_menu_today, "Calendar & Productivity"));
        list.add(new ActionOption("Create Note", "Add note to Google Keep, OneNote, etc.", android.R.drawable.ic_menu_today, "Calendar & Productivity"));
        list.add(new ActionOption("Add To-Do Task", "Create task in Todoist, TickTick, etc.", android.R.drawable.ic_menu_today, "Calendar & Productivity"));
        list.add(new ActionOption("Log to Spreadsheet", "Append row to Google Sheets", android.R.drawable.ic_menu_today, "Calendar & Productivity"));
        list.add(new ActionOption("Start Timer", "Begin a countdown timer", android.R.drawable.ic_menu_today, "Calendar & Productivity"));
        list.add(new ActionOption("Start Stopwatch", "Begin tracking elapsed time", android.R.drawable.ic_menu_today, "Calendar & Productivity"));
        list.add(new ActionOption("App & UI Control"));
        list.add(new ActionOption("Launch App", "Open any installed application", android.R.drawable.ic_menu_manage, "App & UI Control"));
        list.add(new ActionOption("Close App", "Force-stop a running application", android.R.drawable.ic_menu_manage, "App & UI Control"));
        list.add(new ActionOption("Open App Specific Page", "Launch deep link into app section", android.R.drawable.ic_menu_manage, "App & UI Control"));
        list.add(new ActionOption("Uninstall App", "Remove an app (may require confirmation)", android.R.drawable.ic_menu_manage, "App & UI Control"));
        list.add(new ActionOption("Install APK", "Silently install an app (requires root)", android.R.drawable.ic_menu_manage, "App & UI Control"));
        list.add(new ActionOption("Clear App Data", "Delete app cache or stored data", android.R.drawable.ic_menu_manage, "App & UI Control"));
        list.add(new ActionOption("Disable App", "Disable system or user app (requires root)", android.R.drawable.ic_menu_manage, "App & UI Control"));
        list.add(new ActionOption("Enable App", "Re-enable a disabled app", android.R.drawable.ic_menu_manage, "App & UI Control"));
        list.add(new ActionOption("Go to Home Screen", "Simulate pressing the home button", android.R.drawable.ic_menu_manage, "App & UI Control"));
        list.add(new ActionOption("Go Back", "Simulate pressing the back button", android.R.drawable.ic_menu_manage, "App & UI Control"));
        list.add(new ActionOption("Open Recent Apps", "Show the app switcher", android.R.drawable.ic_menu_manage, "App & UI Control"));
        list.add(new ActionOption("Press Volume Key", "Simulate volume up/down button", android.R.drawable.ic_menu_manage, "App & UI Control"));
        list.add(new ActionOption("Press Power Button", "Turn screen off (simulate power press)", android.R.drawable.ic_menu_manage, "App & UI Control"));
        list.add(new ActionOption("Security & Privacy"));
        list.add(new ActionOption("Lock Device", "Immediately lock screen (requires PIN/password)", android.R.drawable.ic_lock_lock, "Security & Privacy"));
        list.add(new ActionOption("Enable Lockdown Mode", "Disable biometric unlock temporarily", android.R.drawable.ic_lock_lock, "Security & Privacy"));
        list.add(new ActionOption("Disable USB Debugging", "Turn off developer USB access", android.R.drawable.ic_lock_lock, "Security & Privacy"));
        list.add(new ActionOption("Enable Encryption", "Force device encryption (requires setup)", android.R.drawable.ic_lock_lock, "Security & Privacy"));
        list.add(new ActionOption("Take Photo of User", "Capture front camera photo on failed unlock", android.R.drawable.ic_lock_lock, "Security & Privacy"));
        list.add(new ActionOption("Log Last Location", "Record GPS coordinates to file", android.R.drawable.ic_lock_lock, "Security & Privacy"));
        list.add(new ActionOption("Send Alert SMS with Location", "Text current location to emergency contact", android.R.drawable.ic_lock_lock, "Security & Privacy"));
        list.add(new ActionOption("Wipe Device", "Factory reset (requires confirmation)", android.R.drawable.ic_lock_lock, "Security & Privacy"));
        list.add(new ActionOption("Logging & Data Recording"));
        list.add(new ActionOption("Write to Log File", "Append timestamp + data to a .log file", android.R.drawable.ic_menu_info_details, "Logging & Data Recording"));
        list.add(new ActionOption("Log Battery Level", "Record battery percentage to CSV", android.R.drawable.ic_menu_info_details, "Logging & Data Recording"));
        list.add(new ActionOption("Log Location History", "Save GPS coordinates periodically", android.R.drawable.ic_menu_info_details, "Logging & Data Recording"));
        list.add(new ActionOption("Log Screen Time", "Track how long screen has been on", android.R.drawable.ic_menu_info_details, "Logging & Data Recording"));
        list.add(new ActionOption("Log App Usage", "Record which apps were opened and when", android.R.drawable.ic_menu_info_details, "Logging & Data Recording"));
        list.add(new ActionOption("Export Database", "Backup trigger/action history", android.R.drawable.ic_menu_info_details, "Logging & Data Recording"));
        list.add(new ActionOption("Send Log via Email", "Email log files to yourself", android.R.drawable.ic_menu_info_details, "Logging & Data Recording"));
        list.add(new ActionOption("Delay & Control Flow"));
        list.add(new ActionOption("Wait / Delay", "Pause execution for X seconds before next action", android.R.drawable.ic_menu_recent_history, "Delay & Control Flow"));
        list.add(new ActionOption("Repeat Action", "Loop an action X times or until condition met", android.R.drawable.ic_menu_recent_history, "Delay & Control Flow"));
        list.add(new ActionOption("Stop Current Automation", "Halt execution of this rule", android.R.drawable.ic_menu_recent_history, "Delay & Control Flow"));
        list.add(new ActionOption("Enable/Disable Another Rule", "Turn a different automation on or off", android.R.drawable.ic_menu_recent_history, "Delay & Control Flow"));
        list.add(new ActionOption("Goto Label", "Jump to a different section of automation", android.R.drawable.ic_menu_recent_history, "Delay & Control Flow"));
        list.add(new ActionOption("Run Shell Command", "Execute Linux shell command (requires root)", android.R.drawable.ic_menu_recent_history, "Delay & Control Flow"));
        list.add(new ActionOption("Run JavaScript", "Execute JavaScript code", android.R.drawable.ic_menu_recent_history, "Delay & Control Flow"));
        list.add(new ActionOption("Run Tasker Task", "Trigger a Tasker task (if installed)", android.R.drawable.ic_menu_recent_history, "Delay & Control Flow"));
        list.add(new ActionOption("Clipboard & Text"));
        list.add(new ActionOption("Copy to Clipboard", "Save text to clipboard", android.R.drawable.ic_menu_edit, "Clipboard & Text"));
        list.add(new ActionOption("Paste from Clipboard", "Retrieve and use clipboard content", android.R.drawable.ic_menu_edit, "Clipboard & Text"));
        list.add(new ActionOption("Set Clipboard as Variable", "Store clipboard text for use in other actions", android.R.drawable.ic_menu_edit, "Clipboard & Text"));
        list.add(new ActionOption("Clear Clipboard", "Delete clipboard contents", android.R.drawable.ic_menu_edit, "Clipboard & Text"));
        list.add(new ActionOption("Append to Clipboard", "Add text to existing clipboard content", android.R.drawable.ic_menu_edit, "Clipboard & Text"));
        list.add(new ActionOption("Vibration & Haptics"));
        list.add(new ActionOption("Short Vibrate", "Vibrate for 100ms", android.R.drawable.ic_dialog_info, "Vibration & Haptics"));
        list.add(new ActionOption("Long Vibrate", "Vibrate for 500ms", android.R.drawable.ic_dialog_info, "Vibration & Haptics"));
        list.add(new ActionOption("Custom Vibration Pattern", "Define on/off pattern (e.g., 200ms on, 100ms off)", android.R.drawable.ic_dialog_info, "Vibration & Haptics"));
        list.add(new ActionOption("Double Vibrate", "Two short buzzes", android.R.drawable.ic_dialog_info, "Vibration & Haptics"));
        list.add(new ActionOption("Vibrate While Condition True", "Continuous vibration during an event", android.R.drawable.ic_dialog_info, "Vibration & Haptics"));
        list.add(new ActionOption("Visual Effects"));
        list.add(new ActionOption("Flash Screen", "Quick screen blink (visual alert)", android.R.drawable.ic_menu_view, "Visual Effects"));
        list.add(new ActionOption("Show Overlay", "Display custom UI on top of other apps", android.R.drawable.ic_menu_view, "Visual Effects"));
        list.add(new ActionOption("Change Accent Color", "Modify system theme color (Android 12+)", android.R.drawable.ic_menu_view, "Visual Effects"));
        list.add(new ActionOption("Set Live Wallpaper", "Change to animated wallpaper", android.R.drawable.ic_menu_view, "Visual Effects"));
        list.add(new ActionOption("Show Toast Message", "Brief popup at bottom of screen", android.R.drawable.ic_menu_view, "Visual Effects"));
        list.add(new ActionOption("Show Dialog with Buttons", "Interactive popup with Yes/No/Cancel", android.R.drawable.ic_menu_view, "Visual Effects"));
        list.add(new ActionOption("Variable & Math Operations"));
        list.add(new ActionOption("Set Variable", "Store a value (number, text, true/false)", android.R.drawable.ic_menu_sort_by_size, "Variable & Math Operations"));
        list.add(new ActionOption("Increment Variable", "Add 1 to a number variable", android.R.drawable.ic_menu_sort_by_size, "Variable & Math Operations"));
        list.add(new ActionOption("Decrement Variable", "Subtract 1 from a number variable", android.R.drawable.ic_menu_sort_by_size, "Variable & Math Operations"));
        list.add(new ActionOption("Concatenate Text", "Combine two strings", android.R.drawable.ic_menu_sort_by_size, "Variable & Math Operations"));
        list.add(new ActionOption("Split Text", "Divide text by delimiter", android.R.drawable.ic_menu_sort_by_size, "Variable & Math Operations"));
        list.add(new ActionOption("Math Operation", "Add, subtract, multiply, divide numbers", android.R.drawable.ic_menu_sort_by_size, "Variable & Math Operations"));
        list.add(new ActionOption("Compare Values", "Check if A = B, A > B, etc.", android.R.drawable.ic_menu_sort_by_size, "Variable & Math Operations"));
        list.add(new ActionOption("Generate Random Number", "Create a random value within range", android.R.drawable.ic_menu_sort_by_size, "Variable & Math Operations"));
        list.add(new ActionOption("Get Current Timestamp", "Store Unix time or formatted date/time", android.R.drawable.ic_menu_sort_by_size, "Variable & Math Operations"));
        list.add(new ActionOption("Get Battery Percentage", "Store battery level as variable", android.R.drawable.ic_menu_sort_by_size, "Variable & Math Operations"));

        return list;
    }
}
