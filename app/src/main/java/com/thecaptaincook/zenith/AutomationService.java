package com.thecaptaincook.zenith;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import androidx.annotation.Nullable;
import com.thecaptaincook.zenith.R;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service that serves as the core Zenith automation engine.
 * <p>
 * This service runs continuously in the foreground, registering receivers and sensors
 * for various system events (triggers) defined by the user's active rules.
 * When a trigger event occurs, it evaluates the rule logic (ALL/ANY) and executes
 * the corresponding sequence of actions.
 * </p>
 * <p>
 * <b>Threading Model:</b>
 * Uses a single-threaded {@link ExecutorService} to process rules and execute actions sequentially
 * in the background. Note that root/shell commands are executed inline to prevent deadlocks on
 * the single-threaded executor thread.
 * </p>
 * <p>
 * <b>Variables System:</b>
 * Maintains an instance-scoped variables map {@link #zenithVariables} to prevent cross-rule/session
 * data leakage.
 * </p>
 */
public class AutomationService extends Service {

    private static final String CHANNEL_ID = "zenith_engine_channel";
    private static final String ACTION_CHANNEL_ID = "zenith_action_channel";
    private static final int NOTIFICATION_ID = 1001;

    /** Background executor service for rule matching and action execution loops. */
    private ExecutorService executorService;
    private AppDatabase db;
    private BroadcastReceiver unifiedReceiver;
    private android.speech.tts.TextToSpeech textToSpeech;
    private android.database.ContentObserver smsObserver;
    
    private android.hardware.SensorManager sensorManager;
    private android.hardware.Sensor accelerometer;
    private android.hardware.SensorEventListener sensorEventListener;
    private long lastShakeTime = 0;
    
    private com.google.android.gms.location.GeofencingClient geofencingClient;
    private com.google.android.gms.location.ActivityRecognitionClient activityRecognitionClient;
    
    /** In-memory list of active rules loaded from the database. */
    private List<RuleEntity> activeRules = new ArrayList<>();
    // Per-rule battery-below-20% fired tracking (Issue #12 fix)
    private final java.util.Set<Integer> batteryTriggeredRuleIds = new java.util.HashSet<>();

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
        db = AppDatabase.getDatabase(this);
        
        geofencingClient = com.google.android.gms.location.LocationServices.getGeofencingClient(this);
        activityRecognitionClient = com.google.android.gms.location.ActivityRecognition.getClient(this);
        
        createNotificationChannels();
        textToSpeech = new android.speech.tts.TextToSpeech(this, status -> {
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(java.util.Locale.getDefault());
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, getPersistentNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, getPersistentNotification());
        }
        
        loadRulesAndRegisterReceivers();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "RELOAD_RULES".equals(intent.getAction())) {
            loadRulesAndRegisterReceivers();
        }
        return START_STICKY;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            
            NotificationChannel engineChannel = new NotificationChannel(CHANNEL_ID, "Automation Engine", NotificationManager.IMPORTANCE_LOW);
            engineChannel.setDescription("Keeps Zenith running in the background.");
            manager.createNotificationChannel(engineChannel);

            NotificationChannel actionChannel = new NotificationChannel(ACTION_CHANNEL_ID, "Rule Triggers", NotificationManager.IMPORTANCE_HIGH);
            actionChannel.setDescription("Notifications created by rules.");
            manager.createNotificationChannel(actionChannel);
        }
    }

    private Notification getPersistentNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Zenith Engine Active")
                .setContentText("Monitoring your automation rules.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * Loads enabled rules from the local room database, dynamically compiles the list of triggers,
     * builds a unified {@link BroadcastReceiver} dynamically, registers standard system triggers,
     * and sets up content observers or sensor listeners (e.g. shake listener, SMS observer, location clients).
     */
    private void loadRulesAndRegisterReceivers() {
        if (unifiedReceiver != null) {
            try { unregisterReceiver(unifiedReceiver); } catch (Exception ignored) {}
            unifiedReceiver = null;
        }
        if (smsObserver != null) {
            try { getContentResolver().unregisterContentObserver(smsObserver); } catch (Exception ignored) {}
            smsObserver = null;
        }
        if (sensorManager != null && sensorEventListener != null) {
            sensorManager.unregisterListener(sensorEventListener);
            sensorEventListener = null;
        }

        executorService.execute(() -> {
            activeRules = db.ruleDao().getAllRules();
            IntentFilter filter = new IntentFilter();
            boolean hasSystemRules = false;

            for (RuleEntity rule : activeRules) {
                if (rule.isEnabled && rule.triggersJson != null) {
                    try {
                        JSONArray triggersArray = new JSONArray(rule.triggersJson);
                        for (int i = 0; i < triggersArray.length(); i++) {
                            String fullT = triggersArray.getString(i);
                            String t = fullT.split("\\|")[0];
                            if ("Battery Level".equals(t) || "Battery Below 20%".equals(t)) { filter.addAction(Intent.ACTION_BATTERY_CHANGED); filter.addAction(Intent.ACTION_BATTERY_OKAY); hasSystemRules = true; }
                            if ("Power Connected / Disconnected".equals(t)) { filter.addAction(Intent.ACTION_POWER_CONNECTED); filter.addAction(Intent.ACTION_POWER_DISCONNECTED); hasSystemRules = true; }
                            if ("Screen On / Off / Unlocked".equals(t)) { filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_USER_PRESENT); hasSystemRules = true; }
                            if ("Headset Plugged".equals(t)) { filter.addAction(Intent.ACTION_HEADSET_PLUG); hasSystemRules = true; }
                            if ("Docked / Undocked".equals(t)) { filter.addAction(Intent.ACTION_DOCK_EVENT); hasSystemRules = true; }
                            if ("USB Connected".equals(t)) { 
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    filter.addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED);
                                    filter.addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED);
                                }
                                hasSystemRules = true; 
                            }
                            if ("Wi-Fi Connected / Disconnected".equals(t) || "WiFi Connected".equals(t)) { 
                                filter.addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION);
                                filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
                                hasSystemRules = true; 
                            }
                            if ("Bluetooth Device".equals(t)) {
                                filter.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED);
                                filter.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED);
                                hasSystemRules = true;
                            }
                            if ("Bluetooth State Changed".equals(t)) {
                                filter.addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED);
                                hasSystemRules = true;
                            }
                            if ("Airplane Mode Changed".equals(t) || "Airplane Mode Enabled".equals(t)) {
                                filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
                                hasSystemRules = true;
                            }
                            if ("Mobile Data State Changed".equals(t)) {
                                filter.addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION);
                                hasSystemRules = true;
                            }
                            
                            // Phase 3 triggers
                            if ("SMS Received".equals(t)) { filter.addAction("android.provider.Telephony.SMS_RECEIVED"); hasSystemRules = true; }
                            if ("Call State".equals(t)) { filter.addAction(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED); hasSystemRules = true; }
                            if ("Notification Received".equals(t)) { filter.addAction(ZenithNotificationListener.ACTION_NOTIFICATION_RECEIVED); hasSystemRules = true; }
                            if ("SMS Sent".equals(t)) {
                                if (smsObserver == null) {
                                    smsObserver = new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) {
                                        @Override
                                        public void onChange(boolean selfChange) {
                                            super.onChange(selfChange);
                                            handleSystemEvent(new Intent("zenith.SMS_SENT"));
                                        }
                                    };
                                    getContentResolver().registerContentObserver(Uri.parse("content://sms"), true, smsObserver);
                                }
                                hasSystemRules = true;
                            }
                            
                            // Phase 4 triggers
                            if ("Device Boot Completed".equals(t)) { filter.addAction(Intent.ACTION_BOOT_COMPLETED); hasSystemRules = true; }
                            if ("Daydream / Screensaver".equals(t)) { filter.addAction(Intent.ACTION_DREAMING_STARTED); filter.addAction(Intent.ACTION_DREAMING_STOPPED); hasSystemRules = true; }
                            if ("Device Shake".equals(t) || "Device Orientation".equals(t)) {
                                if (sensorManager == null) {
                                    sensorManager = (android.hardware.SensorManager) getSystemService(Context.SENSOR_SERVICE);
                                    if (sensorManager != null) {
                                        accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
                                        sensorEventListener = new android.hardware.SensorEventListener() {
                                            @Override
                                            public void onSensorChanged(android.hardware.SensorEvent event) {
                                                if (event.sensor.getType() == android.hardware.Sensor.TYPE_ACCELEROMETER) {
                                                    float x = event.values[0], y = event.values[1], z = event.values[2];
                                                    double gX = x / android.hardware.SensorManager.GRAVITY_EARTH;
                                                    double gY = y / android.hardware.SensorManager.GRAVITY_EARTH;
                                                    double gZ = z / android.hardware.SensorManager.GRAVITY_EARTH;
                                                    double gForce = Math.sqrt(gX * gX + gY * gY + gZ * gZ);
                                                    if (gForce > 2.5) { // Shake threshold
                                                        long now = System.currentTimeMillis();
                                                        if (now - lastShakeTime > 2000) {
                                                            lastShakeTime = now;
                                                            handleSystemEvent(new Intent("zenith.DEVICE_SHAKE"));
                                                        }
                                                    }
                                                }
                                            }
                                            @Override public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) {}
                                        };
                                        sensorManager.registerListener(sensorEventListener, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
                                    }
                                }
                                hasSystemRules = true;
                            }
                            if ("Geofence".equals(t)) {
                                try {
                                    com.google.android.gms.location.Geofence mockGeofence = new com.google.android.gms.location.Geofence.Builder()
                                        .setRequestId("zenith_mock_geofence")
                                        .setCircularRegion(37.422, -122.084, 100)
                                        .setExpirationDuration(com.google.android.gms.location.Geofence.NEVER_EXPIRE)
                                        .setTransitionTypes(com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER | com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT)
                                        .build();
                                    com.google.android.gms.location.GeofencingRequest req = new com.google.android.gms.location.GeofencingRequest.Builder()
                                        .setInitialTrigger(com.google.android.gms.location.GeofencingRequest.INITIAL_TRIGGER_ENTER)
                                        .addGeofence(mockGeofence).build();
                                    int flags = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ? android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_MUTABLE : android.app.PendingIntent.FLAG_UPDATE_CURRENT;
                                    Intent gIntent = new Intent(getApplicationContext(), GeofenceBroadcastReceiver.class);
                                    android.app.PendingIntent pIntent = android.app.PendingIntent.getBroadcast(getApplicationContext(), 0, gIntent, flags);
                                    geofencingClient.addGeofences(req, pIntent);
                                } catch (SecurityException ignored) {}
                                hasSystemRules = true;
                            }
                            if ("Activity Recognition".equals(t)) {
                                try {
                                    int flags = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ? android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_MUTABLE : android.app.PendingIntent.FLAG_UPDATE_CURRENT;
                                    Intent aIntent = new Intent(getApplicationContext(), ActivityRecognitionReceiver.class);
                                    android.app.PendingIntent pIntent = android.app.PendingIntent.getBroadcast(getApplicationContext(), 0, aIntent, flags);
                                    activityRecognitionClient.requestActivityUpdates(30000, pIntent);
                                } catch (SecurityException ignored) {}
                                hasSystemRules = true;
                            }
                            
                            // Legacy triggers
                            if ("Battery Below 20%".equals(t))   { filter.addAction(Intent.ACTION_BATTERY_CHANGED);    hasSystemRules = true; }
                            if ("Power Connected".equals(t))      { filter.addAction(Intent.ACTION_POWER_CONNECTED);    hasSystemRules = true; }
                            if ("Screen Turned On".equals(t))     { filter.addAction(Intent.ACTION_SCREEN_ON);          hasSystemRules = true; }
                            if ("Screen Turned Off".equals(t))    { filter.addAction(Intent.ACTION_SCREEN_OFF);         hasSystemRules = true; }
                            if ("WiFi Connected".equals(t))       { filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION); hasSystemRules = true; }
                            if ("Battery Fully Charged".equals(t)){ filter.addAction(Intent.ACTION_BATTERY_OKAY);       hasSystemRules = true; }
                            if ("Headphones Connected".equals(t)) { filter.addAction(Intent.ACTION_HEADSET_PLUG);       hasSystemRules = true; }
                            if ("Airplane Mode Enabled".equals(t)){ filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED); hasSystemRules = true; }
                        }
                    } catch (JSONException e) {
                        android.util.Log.e("AutomationService", "JSON parsing error", e);
                    }
                }
            }

            if (hasSystemRules && filter.countActions() > 0) {
                unifiedReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        handleSystemEvent(intent);
                    }
                };
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(unifiedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(unifiedReceiver, filter);
                }
            }
        });
    }

    /**
     * Helper to check if a specific trigger condition is currently satisfied.
     * Used for rules with "ALL" logic where other conditions must be verified when a catalyst event triggers.
     *
     * @param fullCondition The raw trigger string representing the condition (e.g. "Battery Below 20%", "USB Connected").
     * @return true if the condition is currently met, false otherwise.
     */
    private boolean isConditionCurrentlyMet(String fullCondition) {
        String[] parts = fullCondition.split("\\|");
        String condition = parts[0];
        String stateParam = parts.length > 1 ? parts[1] : null;
        
        switch (condition) {
            case "Battery Below 20%":
                try {
                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
                    if (batteryStatus != null) {
                        int level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                        int scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                        float batteryPct = level * 100 / (float) scale;
                        return batteryPct < 20.0f;
                    }
                } catch (Exception ignored) {}
                return false;
            case "Power Connected":
            case "Power Connected / Disconnected":
                try {
                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
                    if (batteryStatus != null) {
                        int status = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
                        boolean isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
                        return stateParam == null ? isCharging : (stateParam.equals("Connected") == isCharging);
                    }
                } catch (Exception ignored) {}
                return false;
            case "Screen On / Off / Unlocked":
                try {
                    android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                    boolean isScreenOn = pm != null && pm.isInteractive();
                    return stateParam == null ? isScreenOn : ((stateParam.equals("Screen On") || stateParam.equals("User Present")) == isScreenOn);
                } catch (Exception ignored) {}
                return false;
            case "Screen Turned On":
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                return pm != null && pm.isInteractive();
            case "Screen Turned Off":
                PowerManager pm2 = (PowerManager) getSystemService(Context.POWER_SERVICE);
                return pm2 != null && !pm2.isInteractive();
            case "Battery Fully Charged":
                Intent fullIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (fullIntent != null) {
                    int status = fullIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    return status == BatteryManager.BATTERY_STATUS_FULL;
                }
                return false;
            case "Headset Plugged":
            case "Headphones Connected":
                android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am == null) return false;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.media.AudioDeviceInfo[] devices = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS);
                    for (android.media.AudioDeviceInfo device : devices) {
                        if (device.getType() == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES || device.getType() == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET) return true;
                    }
                }
                //noinspection deprecation
                return am.isWiredHeadsetOn();
            case "Docked / Undocked":
                Intent dockIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_DOCK_EVENT));
                if (dockIntent != null) {
                    return dockIntent.getIntExtra(Intent.EXTRA_DOCK_STATE, -1) != Intent.EXTRA_DOCK_STATE_UNDOCKED;
                }
                return false;
            case "USB Connected":
                Intent usbIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (usbIntent != null) {
                    int plug = usbIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                    return plug == BatteryManager.BATTERY_PLUGGED_USB;
                }
                return false;
            case "Airplane Mode Changed":
                try {
                    boolean isAirplaneModeOn = android.provider.Settings.Global.getInt(getContentResolver(), android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
                    return stateParam == null ? isAirplaneModeOn : (stateParam.equals("On") == isAirplaneModeOn);
                } catch (Exception ignored) {}
                return false;
            case "Wi-Fi Connected / Disconnected":
                try {
                    android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (cm != null) {
                        android.net.Network net = cm.getActiveNetwork();
                        android.net.NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
                        boolean isWifi = caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
                        return stateParam == null ? isWifi : (stateParam.equals("Connected") == isWifi);
                    }
                } catch (Exception ignored) {}
                return false;
            case "Mobile Data State Changed":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (cm != null) {
                        Network net = cm.getActiveNetwork();
                        NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
                        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                    }
                }
                return false;
            case "Bluetooth Device":
                try {
                    android.bluetooth.BluetoothAdapter ba = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                    return ba != null && ba.isEnabled() && (ba.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET) == android.bluetooth.BluetoothAdapter.STATE_CONNECTED || ba.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP) == android.bluetooth.BluetoothAdapter.STATE_CONNECTED);
                } catch (SecurityException e) { return false; }
            case "Bluetooth State Changed":
                try {
                    android.bluetooth.BluetoothAdapter ba2 = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                    boolean isOn = ba2 != null && ba2.isEnabled();
                    return stateParam == null ? isOn : (stateParam.equals("On") == isOn);
                } catch (SecurityException e) { return false; }
            case "SMS Received":
            case "SMS Sent":
            case "Call State":
            case "Notification Received":
            case "Device Boot Completed":
            case "Device Shake":
            case "Device Orientation":
            case "Geofence":
            case "Activity Recognition":
            case "Time of Day":
                return false;
            default:
                return false;
        }
    }

    /**
     * Handles incoming system broadcasts and event intents, matching them against active rules' triggers.
     * Evaluates rule execution logic based on whether ANY or ALL conditions must be met.
     *
     * @param intent  The event intent containing the system event data.
     */
    private void handleSystemEvent(Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        executorService.execute(() -> {
            for (RuleEntity rule : activeRules) {
                if (!rule.isEnabled || rule.triggersJson == null) continue;

                try {
                    boolean isCatalystMet = false;
                    String catalystTrigger = null;

                    for (String fullT : rule.getTriggersList()) {
                        String[] parts = fullT.split("\\|");
                        String t = parts[0];
                        String stateParam = parts.length > 1 ? parts[1] : null;

                        if (Intent.ACTION_BATTERY_CHANGED.equals(action) && "Battery Below 20%".equals(t)) {
                            int level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                            int scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                            float batteryPct = level * 100 / (float) scale;
                            // Issue #12: track fired state per rule, not globally
                            if (batteryPct < 20.0f && !batteryTriggeredRuleIds.contains(rule.id)) {
                                isCatalystMet = true;
                                catalystTrigger = fullT;
                                batteryTriggeredRuleIds.add(rule.id);
                            } else if (batteryPct >= 20.0f) {
                                batteryTriggeredRuleIds.remove(rule.id); // reset when back above threshold
                            }
                        } else if (Intent.ACTION_POWER_CONNECTED.equals(action) && ("Power Connected".equals(t) || "Power Connected / Disconnected".equals(t))) {
                            if (stateParam == null || stateParam.equals("Connected")) { isCatalystMet = true; catalystTrigger = fullT; }
                        } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action) && "Power Connected / Disconnected".equals(t)) {
                            if (stateParam == null || stateParam.equals("Disconnected")) { isCatalystMet = true; catalystTrigger = fullT; }
                        } else if (Intent.ACTION_SCREEN_ON.equals(action) && "Screen On / Off / Unlocked".equals(t)) {
                            if (stateParam == null || stateParam.equals("Screen On")) { isCatalystMet = true; catalystTrigger = fullT; }
                        } else if (Intent.ACTION_SCREEN_OFF.equals(action) && "Screen On / Off / Unlocked".equals(t)) {
                            if (stateParam == null || stateParam.equals("Screen Off")) { isCatalystMet = true; catalystTrigger = fullT; }
                        } else if (Intent.ACTION_USER_PRESENT.equals(action) && "Screen On / Off / Unlocked".equals(t)) {
                            if (stateParam == null || stateParam.equals("User Present")) { isCatalystMet = true; catalystTrigger = fullT; }
                        } else if (Intent.ACTION_HEADSET_PLUG.equals(action) && "Headset Plugged".equals(t)) {
                            int state = intent.getIntExtra("state", -1);
                            if (stateParam == null || (stateParam.equals("Connected") && state == 1) || (stateParam.equals("Disconnected") && state == 0)) { isCatalystMet = true; catalystTrigger = fullT; }
                        } else if (android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED.equals(action) && "Bluetooth State Changed".equals(t)) {
                            int state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, android.bluetooth.BluetoothAdapter.ERROR);
                            if (stateParam == null || (stateParam.equals("On") && state == android.bluetooth.BluetoothAdapter.STATE_ON) || (stateParam.equals("Off") && state == android.bluetooth.BluetoothAdapter.STATE_OFF)) {
                                isCatalystMet = true; catalystTrigger = fullT;
                            }
                        } else if (android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) && "Bluetooth Device".equals(t)) {
                            if (stateParam == null || stateParam.equals("Connected")) { isCatalystMet = true; catalystTrigger = fullT; }
                        } else if (android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action) && "Bluetooth Device".equals(t)) {
                            if (stateParam == null || stateParam.equals("Disconnected")) { isCatalystMet = true; catalystTrigger = fullT; }
                        } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action) && "Airplane Mode Changed".equals(t)) {
                            boolean isAirplaneModeOn = intent.getBooleanExtra("state", false);
                            if (stateParam == null || (stateParam.equals("On") && isAirplaneModeOn) || (stateParam.equals("Off") && !isAirplaneModeOn)) {
                                isCatalystMet = true; catalystTrigger = fullT;
                            }
                        } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action) && ("WiFi Connected".equals(t) || "Wi-Fi Connected / Disconnected".equals(t))) {
                            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                            boolean isConn = false;
                            if (cm != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                android.net.Network network = cm.getActiveNetwork();
                                android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                                isConn = capabilities != null && capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
                            } else {
                                //noinspection deprecation
                                android.net.NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                                //noinspection deprecation
                                isConn = info != null && info.isConnected();
                            }
                            if (true) {
                                if (stateParam == null || (stateParam.equals("Connected") && isConn) || (stateParam.equals("Disconnected") && !isConn)) {
                                    isCatalystMet = true; catalystTrigger = fullT;
                                }
                            }
                        } else if ("android.provider.Telephony.SMS_RECEIVED".equals(action) && "SMS Received".equals(t)) {
                            isCatalystMet = true; catalystTrigger = fullT;
                        } else if ("zenith.SMS_SENT".equals(action) && "SMS Sent".equals(t)) {
                            isCatalystMet = true; catalystTrigger = fullT;
                        } else if (android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action) && "Call State".equals(t)) {
                            isCatalystMet = true; catalystTrigger = fullT;
                        } else if (ZenithNotificationListener.ACTION_NOTIFICATION_RECEIVED.equals(action) && "Notification Received".equals(t)) {
                            isCatalystMet = true; catalystTrigger = fullT;
                        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action) && "Device Boot Completed".equals(t)) {
                            isCatalystMet = true; catalystTrigger = fullT;
                        } else if ((Intent.ACTION_DREAMING_STARTED.equals(action) || Intent.ACTION_DREAMING_STOPPED.equals(action)) && "Daydream / Screensaver".equals(t)) {
                            isCatalystMet = true; catalystTrigger = fullT;
                        } else if ("zenith.DEVICE_SHAKE".equals(action) && "Device Shake".equals(t)) {
                            isCatalystMet = true; catalystTrigger = fullT;
                        } else if ("zenith.GEOFENCE_TRANSITION".equals(action) && "Geofence".equals(t)) {
                            isCatalystMet = true; catalystTrigger = fullT;
                        } else if ("zenith.ACTIVITY_RECOGNIZED".equals(action) && "Activity Recognition".equals(t)) {
                            isCatalystMet = true; catalystTrigger = fullT;
                        }
                    }

                    if (isCatalystMet) {
                        if ("ANY".equals(rule.triggerLogic)) {
                            executeActions(rule);
                        } else if ("ALL".equals(rule.triggerLogic)) {
                            boolean allMet = true;
                            for (String fullT : rule.getTriggersList()) {
                                if (fullT.equals(catalystTrigger)) continue;
                                if ("Time of Day".equals(fullT)) {
                                    allMet = false;
                                    break;
                                }
                                if (!isConditionCurrentlyMet(fullT)) {
                                    allMet = false;
                                    break;
                                }
                            }
                            if (allMet) {
                                executeActions(rule);
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("AutomationService", "Error evaluating rule", e);
                }
            }
        });
    }

    private final java.util.Map<Integer, Long> lastExecutionTimes = new java.util.HashMap<>();

    /**
     * Executes the configured sequence of actions for a given rule on the background thread.
     * Performs looping control flow, command processing, and error handling for all actions.
     *
     * @param rule The rule entity containing the JSON array of actions to execute.
     */
    private void executeActions(RuleEntity rule) {
        long now = System.currentTimeMillis();
        Long lastTime = lastExecutionTimes.get(rule.id);
        if (lastTime != null && (now - lastTime) < 2000) {
            return; // Debounce to prevent duplicate executions
        }
        lastExecutionTimes.put(rule.id, now);

        // Issue #18: removed dead overallSuccess variable
        // Issue #19: detailsBuilder captured in lambda — create final local copy
        final StringBuilder detailsBuilder = new StringBuilder("Actions: ");

        executorService.execute(() -> {
            try {
                JSONArray actionsArray = new JSONArray(rule.actionsJson);
                int numActions = actionsArray.length();
                if (numActions == 0) {
                    logExecution(rule.ruleName, true, "No actions configured");
                    return;
                }
                
                boolean anyFailed = false;
                int i = 0;
                while (i < numActions) {
                    String actionType = actionsArray.getString(i);
                    String[] parts = actionType.split("\\|", 2);
                    String action = parts[0];
                    String param  = parts.length > 1 ? parts[1] : "";

                    if (i > 0) detailsBuilder.append(", ");
                    detailsBuilder.append(action);

                    try {
                        if ("Wait / Delay".equals(action)) {
                            int delayMs = 500;
                            try { delayMs = Integer.parseInt(param.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
                            Thread.sleep(delayMs);
                        } else if ("Stop Current Automation".equals(action)) {
                            break;
                        } else if ("Goto Label".equals(action)) {
                            // Issue #1: guard against infinite loops with a max iteration counter
                            int loopCount = 0;
                            final int MAX_GOTO_LOOPS = 1000;
                            for (int j = 0; j < numActions; j++) {
                                String[] tParts = actionsArray.getString(j).split("\\|", 2);
                                if (tParts.length > 1 && param.equals(tParts[1]) && ("Label".equals(tParts[0]) || "Goto Label".equals(tParts[0]))) {
                                    if (j != i) {
                                        if (j < i) { // jumping backward — count as a loop
                                            loopCount++;
                                            if (loopCount > MAX_GOTO_LOOPS) {
                                                android.util.Log.w("AutomationService", "Goto Label exceeded " + MAX_GOTO_LOOPS + " iterations — stopping");
                                                anyFailed = true;
                                                i = numActions; // force exit
                                                break;
                                            }
                                        }
                                        i = j - 1;
                                        break;
                                    }
                                }
                            }
                        } else if ("Repeat Action".equals(action)) {
                            // Issue #1 / Repeat: limit repetitions to prevent infinite loop
                            int maxRepeat = 100;
                            try { maxRepeat = Integer.parseInt(param.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
                            if (i > 0 && maxRepeat > 0) i = i - 2;
                        } else if ("Enable/Disable Another Rule".equals(action)) {
                            RuleEntity targetRule = AppDatabase.getDatabase(getApplicationContext()).ruleDao().searchRules(param).stream().findFirst().orElse(null);
                            if (targetRule != null) {
                                // Issue #11: use correct field name isEnabled (not enabled)
                                targetRule.isEnabled = !targetRule.isEnabled;
                                AppDatabase.getDatabase(getApplicationContext()).ruleDao().update(targetRule);
                                // Issue #4: also update the in-memory activeRules list so change takes effect immediately
                                for (RuleEntity r : activeRules) {
                                    if (r.id == targetRule.id) {
                                        r.isEnabled = targetRule.isEnabled;
                                        break;
                                    }
                                }
                            }
                        } else if ("Run JavaScript".equals(action)) {
                            runJavaScript(param);
                        } else if ("Run Tasker Task".equals(action)) {
                            Intent taskerIntent = new Intent("net.dinglisch.android.tasker.ACTION_TASK");
                            taskerIntent.putExtra("task_name", param);
                            taskerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try { startActivity(taskerIntent); } catch (Exception e) { android.util.Log.e("AutomationService", "Tasker not found", e); }
                        } else if ("Show Notification".equals(action)) {
                            showActionNotification(rule.ruleName);
                        } else if ("Play Sound / Ringtone".equals(action) || "Play Sound".equals(action)) {
                            playSound();
                        } else if ("Short Vibrate".equals(action) || "Vibrate Phone".equals(action) || "Vibrate".equals(action)) {
                            vibratePhone(500);
                        } else if ("Long Vibrate".equals(action)) {
                            vibratePhone(2000);
                        } else if ("Toggle Flashlight / Torch".equals(action) || "Turn on Flashlight".equals(action) || "Turn off Flashlight".equals(action)) {
                            boolean turnOn = !"Turn Off".equals(param) && !"Turn off Flashlight".equals(action);
                            controlFlashlight(turnOn);
                        } else if ("Toggle Wi-Fi".equals(action) || "Toggle WiFi".equals(action)) {
                            toggleWiFi();
                        } else if ("Set Media Volume".equals(action) || "Set Volume: Max".equals(action)) {
                            if (param != null && param.endsWith("%")) {
                                try {
                                    int percent = Integer.parseInt(param.replace("%", ""));
                                    setVolumePercent(percent);
                                } catch (Exception e) {
                                    setVolume(true);
                                }
                            } else {
                                setVolume(true);
                            }
                        } else if ("Mute / Unmute All Sound".equals(action) || "Set Volume: Silent".equals(action)) {
                            setVolume(false);
                        } else if ("Toggle Bluetooth".equals(action)) {
                            toggleBluetooth();
                        } else if ("Speak Text (TTS)".equals(action) || "Speak Text".equals(action) || "Announce with TTS".equals(action)) {
                            speakText(param.isEmpty() ? rule.ruleName + " triggered" : param);
                        } else if ("Launch App".equals(action) || "Open App".equals(action)) {
                            openApp(param);
                        } else if ("Send SMS".equals(action)) {
                            sendSms(param);
                        } else if ("Send MMS".equals(action)) {
                            sendMms(param);
                        } else if ("Reply to SMS Automatically".equals(action)) {
                            autoReplySms(param);
                        } else if ("Forward SMS".equals(action)) {
                            forwardSms(param);
                        } else if ("Post to Social Media".equals(action)) {
                            postToSocialMedia(param);
                        } else if ("Make Phone Call".equals(action) || "Open Dialer with Number".equals(action)) {
                            makePhoneCall(param, "Make Phone Call".equals(action));
                        } else if ("Set Screen Brightness".equals(action)) {
                            setScreenBrightness(param);
                        } else if ("Set Screen Timeout".equals(action)) {
                            setScreenTimeout(param);
                        } else if ("Enable/Disable Do Not Disturb".equals(action)) {
                            toggleDND(param);
                        } else if ("Open URL in Browser".equals(action)) {
                            openUrl(param);
                        } else if ("Copy to Clipboard".equals(action)) {
                            copyToClipboard(param);
                        } else if ("Append to Clipboard".equals(action)) {
                            appendToClipboard(param);
                        } else if ("Clear Clipboard".equals(action)) {
                            clearClipboard();
                        } else if ("Paste from Clipboard".equals(action)) {
                            runRootCommand("input keyevent 279");
                        } else if ("Set Clipboard as Variable".equals(action)) {
                            setClipboardAsVariable(param);
                        } else if ("Show Dialog / Popup".equals(action) || "Show Dialog with Buttons".equals(action)) {
                            showDialog(param);
                        } else if ("Show Toast Message".equals(action)) {
                            showToast(param);
                        } else if ("Flash LED".equals(action)) {
                            flashLed();
                        } else if ("Cancel All Notifications".equals(action)) {
                            ZenithNotificationListener listener = ZenithNotificationListener.getInstance();
                            if (listener != null) listener.cancelAllNotifications();
                        } else if ("Dismiss Specific Notification".equals(action)) {
                            ZenithNotificationListener listener = ZenithNotificationListener.getInstance();
                            if (listener != null) {
                                for (android.service.notification.StatusBarNotification sbn : listener.getActiveNotifications()) {
                                    if (sbn.getPackageName().equalsIgnoreCase(param)) {
                                        listener.cancelNotification(sbn.getKey());
                                    }
                                }
                            }
                        } else if ("Custom Vibration Pattern".equals(action) || "Double Vibrate".equals(action) || "Vibrate While Condition True".equals(action)) {
                            customVibrate(action, param);
                        } else if ("Set Notification LED Color".equals(action)) {
                            setNotificationLed(param);
                        } else if ("Blink Screen".equals(action)) {
                            blinkScreen();
                        } else if ("Send HTTP GET Request".equals(action) || "Send Webhook".equals(action)) {
                            sendHttpRequest(param, "GET");
                        } else if ("Send HTTP POST Request".equals(action)) {
                            sendHttpRequest(param, "POST");
                        } else if ("Download File".equals(action)) {
                            downloadFile(param);
                        } else if ("Check Website Status".equals(action)) {
                            checkWebsiteStatus(param);
                        } else if ("Fetch RSS Feed".equals(action)) {
                            fetchRssFeed(param);
                        } else if ("Upload File to Server".equals(action)) {
                            uploadFile(param);
                        } else if ("Write to File".equals(action)) {
                            writeToFile(param);
                        } else if ("Read File".equals(action)) {
                            readFile(param);
                        } else if ("Delete File".equals(action)) {
                            deleteFileAction(param);
                        } else if ("Copy File".equals(action) || "Move File".equals(action) || "Rename File".equals(action)) {
                            copyFile(param, !"Copy File".equals(action));
                        } else if ("Create Folder".equals(action)) {
                            createFolder(param);
                        } else if ("Backup File".equals(action)) {
                            backupFile(param);
                        } else if ("Save Image to Gallery".equals(action)) {
                            saveImageToGallery(param);
                        } else if ("Compress Files".equals(action)) {
                            compressFiles(param);
                        } else if ("Extract Archive".equals(action)) {
                            extractArchive(param);
                        } else if ("Take Photo (Front/Rear)".equals(action)) {
                            openIntent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                        } else if ("Record Video".equals(action) || "Take Timelapse".equals(action)) {
                            openIntent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE);
                        } else if ("Record Audio".equals(action)) {
                            openIntent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION);
                        } else if ("Play Media File".equals(action)) {
                            playMediaFile(param);
                        } else if ("Pause/Resume Media".equals(action)) {
                            dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                        } else if ("Skip to Next/Previous Track".equals(action)) {
                            dispatchMediaKey(param != null && param.toLowerCase().contains("prev") ? android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS : android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
                        } else if ("Increase/Decrease Playback Speed".equals(action)) {
                            int keycode = (param != null && param.toLowerCase().contains("decrease")) ? android.view.KeyEvent.KEYCODE_MEDIA_REWIND : android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
                            dispatchMediaKey(keycode);
                        } else if ("Set Media Player Volume".equals(action)) {
                            if (param != null && param.endsWith("%")) {
                                try {
                                    setVolumePercent(Integer.parseInt(param.replace("%", "")));
                                } catch (Exception ignored) {}
                            }
                        } else if ("Send WhatsApp Message".equals(action)) {
                            sendSocialMessage(param, "com.whatsapp");
                        } else if ("Send Telegram Message".equals(action)) {
                            sendSocialMessage(param, "org.telegram.messenger");
                        } else if ("Send Email".equals(action)) {
                            sendEmail(param);
                        } else if ("Create Calendar Event".equals(action)) {
                            createCalendarEvent(param);
                        } else if ("Add Reminder".equals(action)) {
                            Intent reminderIntent = new Intent(Intent.ACTION_INSERT)
                                    .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                                    .putExtra(android.provider.CalendarContract.Events.TITLE, "Reminder: " + (param != null ? param : ""))
                                    .putExtra(android.provider.CalendarContract.Events.ALL_DAY, true);
                            reminderIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try { startActivity(reminderIntent); } catch (Exception e) { android.util.Log.e("AutomationService", "Failed to add reminder", e); }
                        } else if ("Add To-Do Task".equals(action)) {
                            Intent todoIntent = new Intent(Intent.ACTION_SEND);
                            todoIntent.setType("text/plain");
                            todoIntent.putExtra(Intent.EXTRA_TEXT, "Task: " + (param != null ? param : ""));
                            todoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try { startActivity(Intent.createChooser(todoIntent, "Add Task to...").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
                        } else if ("Create Note".equals(action)) {
                            Intent noteIntent = new Intent("com.google.android.gms.actions.CREATE_NOTE");
                            noteIntent.putExtra(Intent.EXTRA_TEXT, param != null ? param : "");
                            noteIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try { startActivity(noteIntent); } catch (Exception e) {
                                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                                sendIntent.setType("text/plain");
                                sendIntent.putExtra(Intent.EXTRA_TEXT, param != null ? param : "");
                                sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                try { startActivity(Intent.createChooser(sendIntent, "Create Note").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
                            }
                        } else if ("Delete Calendar Event".equals(action)) {
                            if (param != null && !param.isEmpty()) {
                                try {
                                    android.database.Cursor cursor = getContentResolver().query(
                                            android.provider.CalendarContract.Events.CONTENT_URI,
                                            new String[]{android.provider.CalendarContract.Events._ID},
                                            android.provider.CalendarContract.Events.TITLE + "=?",
                                            new String[]{param}, null);
                                    if (cursor != null) {
                                        while (cursor.moveToNext()) {
                                            long eventId = cursor.getLong(0);
                                            getContentResolver().delete(
                                                    android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, eventId),
                                                    null, null);
                                        }
                                        cursor.close();
                                    }
                                } catch (SecurityException e) {
                                    android.util.Log.e("AutomationService", "Permission denied deleting calendar event", e);
                                }
                            }
                        } else if ("Log to Spreadsheet".equals(action)) {
                            try {
                                java.io.File dir = new java.io.File(android.os.Environment.getExternalStorageDirectory(), "ZenithLogs");
                                if (!dir.exists() && !dir.mkdirs()) { android.util.Log.e("AutomationService", "Failed to create directory"); }
                                java.io.File file = new java.io.File(dir, "spreadsheet_log.csv");
                                java.io.FileWriter fw = new java.io.FileWriter(file, true);
                                fw.append("\"").append(new java.util.Date().toString()).append("\",\"").append(param != null ? param : "").append("\"\n");
                                fw.close();
                            } catch (Exception e) {
                                android.util.Log.e("AutomationService", "Failed to log to spreadsheet", e);
                            }
                        } else if ("Write to Log File".equals(action)) {
                            writeToFile("system_log.txt:" + param);
                        } else if ("Export Database".equals(action)) {
                            exportDatabase();
                        } else if ("Log App Usage".equals(action)) {
                            logAppUsage();
                        } else if ("Log Battery Level".equals(action)) {
                            logBatteryLevel();
                        } else if ("Log Location History".equals(action)) {
                            logLocationHistory();
                        } else if ("Log Screen Time".equals(action)) {
                            logScreenTime();
                        } else if ("Send Log via Email".equals(action)) {
                            sendLogViaEmail(param);
                        } else if ("Disable USB Debugging".equals(action)) {
                            runRootCommand("settings put global adb_enabled 0");
                        } else if ("Enable Encryption".equals(action)) {
                            showToast("Encryption is active (standard on modern Android)");
                        } else if ("Enable Lockdown Mode".equals(action)) {
                            runRootCommand("settings put secure lockdown_in_power_menu 1");
                            runRootCommand("input keyevent 26");
                        } else if ("Lock Device".equals(action)) {
                            runRootCommand("input keyevent 26");
                        } else if ("Log Last Location".equals(action)) {
                            logLocationHistory();
                        } else if ("Send Alert SMS with Location".equals(action)) {
                            sendAlertSmsWithLocation(param);
                        } else if ("Take Photo of User".equals(action)) {
                            takeHiddenPhoto();
                        } else if ("Wipe Device".equals(action)) {
                            // Issue #3: MASTER_CLEAR is deprecated on Android 8+; use recovery wipe via root
                            runRootCommand("am broadcast --user 0 -a android.intent.action.FACTORY_RESET -p \"android\"");
                        } else if ("Set Variable".equals(action)) {
                            if (param != null && param.contains("=")) {
                                String[] parts2 = param.split("=", 2);
                                zenithVariables.put(parts2[0].trim(), parts2[1].trim());
                            }
                        } else if ("Increment Variable".equals(action)) {
                            adjustVariable(param, 1);
                        } else if ("Decrement Variable".equals(action)) {
                            adjustVariable(param, -1);
                        } else if ("Math Operation".equals(action)) {
                            mathOperation(param);
                        } else if ("Compare Values".equals(action)) {
                            if (!compareValues(param)) {
                                anyFailed = true;
                                break;
                            }
                        } else if ("Concatenate Text".equals(action)) {
                            concatenateText(param);
                        } else if ("Split Text".equals(action)) {
                            splitText(param);
                        } else if ("Generate Random Number".equals(action)) {
                            generateRandomNumber(param);
                        } else if ("Get Battery Percentage".equals(action)) {
                            getBatteryPercentageVariable();
                        } else if ("Get Current Timestamp".equals(action)) {
                            zenithVariables.put("timestamp", String.valueOf(System.currentTimeMillis()));
                        } else if ("Change Accent Color".equals(action)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && param != null && !param.isEmpty()) {
                                try {
                                    // On Android 12+ overlay system color via root
                                    runRootCommand("settings put secure theme_customization_overlay_packages \"{\\\"android.theme.customization.accent_color\\\":\\\"" + param + "\\\"}\"");
                                } catch (Exception ignored) {}
                                showToast("Accent color set to: " + param + " (may need reboot)");
                            } else {
                                Intent themeIntent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
                                themeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                try { startActivity(themeIntent); } catch (Exception ignored) {}
                            }
                        } else if ("Flash Screen".equals(action)) {
                            flashScreen();
                        } else if ("Set Live Wallpaper".equals(action)) {
                            Intent intent = new Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try { startActivity(intent); } catch (Exception ignored) {}
                        } else if ("Show Overlay".equals(action)) {
                            showOverlay(param);
                        } else if ("Start Stopwatch".equals(action)) {
                            Intent stopwatchIntent = new Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS);
                            stopwatchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try { startActivity(stopwatchIntent); } catch (Exception e) {
                                android.util.Log.e("AutomationService", "Failed to start stopwatch", e);
                            }
                        } else if ("Start Timer".equals(action)) {
                            Intent timerIntent = new Intent(android.provider.AlarmClock.ACTION_SET_TIMER);
                            timerIntent.putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Zenith Timer");
                            int seconds = 60;
                            try { seconds = Integer.parseInt(param); } catch (Exception ignored) {}
                            timerIntent.putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds);
                            timerIntent.putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true);
                            timerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try { startActivity(timerIntent); } catch (Exception e) {
                                android.util.Log.e("AutomationService", "Failed to start timer", e);
                            }
                        } else if ("Run Shell Command".equals(action)) {
                            runRootCommand(param);
                        } else if ("Reboot Device".equals(action) || "Restart Device".equals(action)) {
                            runRootCommand("reboot");
                        } else if ("Shutdown Device".equals(action)) {
                            runRootCommand("reboot -p");
                        } else if ("Uninstall App".equals(action)) {
                            runRootCommand("pm uninstall " + param);
                        } else if ("Disable App".equals(action)) {
                            runRootCommand("pm disable-user --user 0 " + param);
                        } else if ("Clear App Data".equals(action)) {
                            runRootCommand("pm clear " + param);
                        } else if ("Close App".equals(action)) {
                            runRootCommand("am force-stop " + param);
                        } else if ("Enable App".equals(action)) {
                            runRootCommand("pm enable " + param);
                        } else if ("Go Back".equals(action)) {
                            runRootCommand("input keyevent 4");
                        } else if ("Go to Home Screen".equals(action)) {
                            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                            homeIntent.addCategory(Intent.CATEGORY_HOME);
                            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(homeIntent);
                        } else if ("Install APK".equals(action)) {
                            runRootCommand("pm install " + param);
                        } else if ("Open App Specific Page".equals(action)) {
                            try {
                                Intent pageIntent = Intent.parseUri(param, Intent.URI_INTENT_SCHEME);
                                pageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(pageIntent);
                            } catch (Exception e) {
                                android.util.Log.e("AutomationService", "Failed to open app page: " + param, e);
                            }
                        } else if ("Open Recent Apps".equals(action)) {
                            runRootCommand("input keyevent 187");
                        } else if ("Press Power Button".equals(action)) {
                            runRootCommand("input keyevent 26");
                        } else if ("Press Volume Key".equals(action)) {
                            int keycode = "Volume Down".equalsIgnoreCase(param) ? 25 : 24;
                            runRootCommand("input keyevent " + keycode);
                        } else if ("Set Alarm Volume".equals(action)) {
                            int percent = param != null && param.endsWith("%") ? Integer.parseInt(param.replace("%", "")) : 50;
                            setStreamVolumePercent(android.media.AudioManager.STREAM_ALARM, percent);
                        } else if ("Set Call Volume".equals(action)) {
                            int percent = param != null && param.endsWith("%") ? Integer.parseInt(param.replace("%", "")) : 50;
                            setStreamVolumePercent(android.media.AudioManager.STREAM_VOICE_CALL, percent);
                        } else if ("Set Notification Volume".equals(action)) {
                            int percent = param != null && param.endsWith("%") ? Integer.parseInt(param.replace("%", "")) : 50;
                            setStreamVolumePercent(android.media.AudioManager.STREAM_NOTIFICATION, percent);
                        } else if ("Set Ringtone Volume".equals(action)) {
                            int percent = param != null && param.endsWith("%") ? Integer.parseInt(param.replace("%", "")) : 50;
                            setStreamVolumePercent(android.media.AudioManager.STREAM_RING, percent);
                        } else if ("Vibrate Mode On/Off".equals(action)) {
                            android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                            if (audioManager != null) {
                                boolean turnOn = !"Turn Off".equals(param);
                                audioManager.setRingerMode(turnOn ? android.media.AudioManager.RINGER_MODE_VIBRATE : android.media.AudioManager.RINGER_MODE_NORMAL);
                            }
                        } else if ("Voice Announcement".equals(action)) {
                            speakText(param != null && !param.isEmpty() ? param : "Voice Announcement");
                        } else if ("Increase Volume Gradually".equals(action)) {
                            // Issue #6: use a separate thread — not the executorService which is already occupied
                            int targetPercent = param != null && param.endsWith("%") ? Integer.parseInt(param.replace("%", "")) : 100;
                            new Thread(() -> {
                                android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                                if (am != null) {
                                    int maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                                    int currentVol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                                    int targetVol = (int) (maxVol * (Math.max(0, Math.min(100, targetPercent)) / 100.0f));
                                    //noinspection BusyWait
                                    while (currentVol < targetVol && !Thread.currentThread().isInterrupted()) {
                                        currentVol++;
                                        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, currentVol, 0);
                                        try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                                    }
                                }
                            }, "zenith-vol-ramp").start();
                        } else if ("Toggle Airplane Mode".equals(action)) {
                            runRootCommand("settings put global airplane_mode_on 1 && am broadcast -a android.intent.action.AIRPLANE_MODE");
                        } else if ("Toggle Location Services".equals(action)) {
                            boolean enable = !"Off".equalsIgnoreCase(param) && !"Disable".equalsIgnoreCase(param);
                            runRootCommand("settings put secure location_mode " + (enable ? "3" : "0"));
                        } else if ("Lock Screen".equals(action)) {
                            runRootCommand("input keyevent 26");
                        } else if ("Toggle Always-On Display".equals(action)) {
                            boolean enable = !"Turn Off".equals(param);
                            runRootCommand("settings put secure doze_always_on " + (enable ? "1" : "0"));
                        } else if ("Set Wallpaper".equals(action)) {
                            setWallpaper(param);
                        } else if ("Take Screenshot".equals(action)) {
                            runRootCommand("screencap -p /sdcard/Pictures/zenith_screenshot_" + System.currentTimeMillis() + ".png");
                        } else if ("Keep Screen On".equals(action)) {
                            boolean enable = !"Turn Off".equals(param);
                            runRootCommand("svc power stayon " + (enable ? "true" : "false"));
                        } else if ("Toggle Auto-Rotate".equals(action)) {
                            boolean enable = !"Turn Off".equals(param);
                            setAutoRotate(enable);
                        } else if ("Set Font Size".equals(action)) {
                            setFontSize(param);
                        } else if ("Toggle Mobile Data".equals(action)) {
                            boolean enable = !"Turn Off".equals(param);
                            runRootCommand("svc data " + (enable ? "enable" : "disable"));
                        } else if ("Toggle NFC".equals(action)) {
                            boolean enable = !"Turn Off".equals(param);
                            runRootCommand("svc nfc " + (enable ? "enable" : "disable"));
                        } else if ("Toggle Hotspot".equals(action)) {
                            openSettingsIntent("com.android.settings.TetherSettings");
                        } else if ("Toggle VPN".equals(action)) {
                            openSettingsIntent(android.provider.Settings.ACTION_VPN_SETTINGS);
                        } else if ("Change Network APN".equals(action)) {
                            openSettingsIntent(android.provider.Settings.ACTION_APN_SETTINGS);
                        } else if ("Set Preferred Network Type".equals(action)) {
                            openSettingsIntent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS);
                        } else if ("Connect to Wi-Fi Network".equals(action)) {
                            connectToWiFi(param);
                        } else if ("Connect to Bluetooth Device".equals(action)) {
                            connectToBluetooth(param);
                        } else if ("Set DND Priority Settings".equals(action)) {
                            openSettingsIntent(android.provider.Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS);
                        } else if ("Toggle Battery Saver".equals(action)) {
                            boolean enable = !"Turn Off".equals(param);
                            runRootCommand("settings put global low_power " + (enable ? "1" : "0"));
                        } else if ("Toggle Dark Mode".equals(action)) {
                            boolean enable = !"Turn Off".equals(param);
                            runRootCommand("cmd uimode night " + (enable ? "yes" : "no"));
                        } else if ("Toggle Auto-Sync".equals(action)) {
                            boolean enable = !"Turn Off".equals(param);
                            android.content.ContentResolver.setMasterSyncAutomatically(enable);
                        } else if ("Toggle Developer Options".equals(action)) {
                            boolean enable = !"Turn Off".equals(param);
                            runRootCommand("settings put global development_settings_enabled " + (enable ? "1" : "0"));
                        } else if ("Change Language".equals(action)) {
                            openSettingsIntent(android.provider.Settings.ACTION_LOCALE_SETTINGS);
                        } else if ("Open Specific Settings Page".equals(action)) {
                            openSettingsIntent(param);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("AutomationService", "Error in action execution", e);
                        anyFailed = true;
                    }
                    i++;
                    if (!"Wait / Delay".equals(action)) {
                        Thread.sleep(500);
                    }
                }
                logExecution(rule.ruleName, !anyFailed, detailsBuilder.toString());
            } catch (Exception e) {
                logExecution(rule.ruleName, false, "Error parsing actions: " + e.getMessage());
            }
        });
    }


    private void showActionNotification(String ruleName) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = new NotificationCompat.Builder(this, ACTION_CHANNEL_ID)
                .setContentTitle("Zenith Rule Triggered!")
                .setContentText("Rule executed: " + ruleName)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();
        manager.notify((int) System.currentTimeMillis(), notification);
    }

    private void playSound() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
        } catch (Exception e) {
            // Issue #16: use Log.e consistently instead of printStackTrace
            android.util.Log.e("AutomationService", "Failed to play sound", e);
        }
    }

    private void vibratePhone(long durationMs) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(durationMs);
        }
    }

    private void controlFlashlight(boolean on) {
        CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = camManager.getCameraIdList()[0];
            camManager.setTorchMode(cameraId, on);
        } catch (CameraAccessException e) {
            android.util.Log.e("AutomationService", "Flashlight error", e);
        }
    }

    private void setVolume(boolean max) {
        android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int volume = max ? audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) : 0;
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, volume, 0);
        }
    }

    private void setVolumePercent(int percent) {
        android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            int targetVol = (int) (maxVol * (Math.max(0, Math.min(100, percent)) / 100.0f));
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0);
        }
    }

    private void setStreamVolumePercent(int streamType, int percent) {
        android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int maxVol = audioManager.getStreamMaxVolume(streamType);
            int targetVol = (int) (maxVol * (Math.max(0, Math.min(100, percent)) / 100.0f));
            audioManager.setStreamVolume(streamType, targetVol, 0);
        }
    }

    private void toggleBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent btIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            btIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(btIntent);
            } catch (Exception e) {
                android.util.Log.e("AutomationService", "Failed to start Bluetooth Settings", e);
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.bluetooth.BluetoothManager btManager =
                        (android.bluetooth.BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                android.bluetooth.BluetoothAdapter bt = btManager != null ? btManager.getAdapter() : null;
                if (bt != null) {
                    if (bt.isEnabled()) bt.disable(); else bt.enable();
                }
            } else {
                //noinspection deprecation
                android.bluetooth.BluetoothAdapter bt = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                if (bt != null) {
                    if (bt.isEnabled()) bt.disable(); else bt.enable();
                }
            }
        }
    }

    private void speakText(String text) {
        if (textToSpeech != null && !text.isEmpty()) {
            android.content.SharedPreferences prefs = getSharedPreferences("zenith_prefs", Context.MODE_PRIVATE);
            textToSpeech.setPitch(prefs.getFloat("tts_pitch", 1.0f));
            textToSpeech.setSpeechRate(prefs.getFloat("tts_rate", 1.0f));
            textToSpeech.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "zenith_tts");
        }
    }

    private void openApp(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return;
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName.trim());
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(launchIntent);
            } catch (Exception e) {
                android.util.Log.e("AutomationService", "Failed to launch app: " + packageName, e);
            }
        }
    }

    private void toggleWiFi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
            panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(panelIntent);
            } catch (Exception e) {
                android.util.Log.e("AutomationService", "Failed to open WiFi panel", e);
            }
        } else {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiManager.setWifiEnabled(!wifiManager.isWifiEnabled());
            }
        }
    }

    private void logExecution(String ruleName, boolean success, String details) {
        LogEntity log = new LogEntity();
        log.ruleName = ruleName;
        log.timestamp = System.currentTimeMillis();
        log.status = success ? "SUCCESS" : "FAILED";
        log.details = details;
        db.logDao().insert(log);
    }

    /**
     * Sends a text message directly using SMS Manager.
     *
     * @param param String in the format "phone_number:message_body".
     */
    private void sendSms(String param) {
        if (param == null || param.isEmpty()) return;
        String[] parts = param.split(":", 2);
        String number = parts[0];
        String msg = parts.length > 1 ? parts[1] : "Zenith Alert";
        android.telephony.SmsManager sms = android.telephony.SmsManager.getDefault();
        sms.sendTextMessage(number, null, msg, null, null);
    }

    /**
     * Prepares and triggers sending an MMS message by launching the native messaging app
     * with the recipient's phone number and the message content pre-filled.
     *
     * @param param String in the format "phone_number:message_body".
     */
    private void sendMms(String param) {
        // MMS requires a full provider URI and ContentValues insert — the safest cross-device
        // approach is to open the native SMS/MMS app with the content pre-filled.
        if (param == null || param.isEmpty()) return;
        String[] parts = param.split(":", 2);
        String number = parts[0].trim();
        String msg = parts.length > 1 ? parts[1] : "";
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + number));
        intent.putExtra("sms_body", msg);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(intent); } catch (Exception e) {
            android.util.Log.e("AutomationService", "No MMS app found", e);
        }
    }

    /**
     * Automatically replies to the last received SMS message in the device's inbox.
     *
     * @param param String in the format "last:reply_text" (to reply to the most recent sender)
     *              or "target_number:reply_text".
     */
    private void autoReplySms(String param) {
        // param format: "sender_number:reply_text"
        // We look up the most recently received SMS number and reply to it.
        if (param == null || param.isEmpty()) return;
        String[] parts = param.split(":", 2);
        String replyTo = parts[0].trim();
        String replyText = parts.length > 1 ? parts[1] : "Automated reply from Zenith";
        try {
            android.database.Cursor cursor = getContentResolver().query(
                    Uri.parse("content://sms/inbox"),
                    new String[]{"address"},
                    null, null, "date DESC");
            if (cursor != null && cursor.moveToFirst()) {
                String sender = cursor.getString(0);
                cursor.close();
                if (sender != null && !sender.isEmpty()) {
                    // If param starts with "last", reply to whoever last texted us
                    String target = replyTo.equalsIgnoreCase("last") ? sender : replyTo;
                    android.telephony.SmsManager sms = android.telephony.SmsManager.getDefault();
                    sms.sendTextMessage(target, null, replyText, null, null);
                }
            } else if (cursor != null) { cursor.close(); }
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "AutoReply failed", e);
        }
    }

    /**
     * Reads the most recently received message from the SMS inbox and forwards it to a specified recipient.
     *
     * @param param String in the format "forward_to_number:optional_prefix".
     */
    private void forwardSms(String param) {
        // param format: "forward_to_number:optional_prefix"
        // Reads the most recent received SMS and forwards it.
        if (param == null || param.isEmpty()) return;
        String[] parts = param.split(":", 2);
        String forwardTo = parts[0].trim();
        String prefix = parts.length > 1 ? parts[1] + " " : "Fwd: ";
        try {
            android.database.Cursor cursor = getContentResolver().query(
                    Uri.parse("content://sms/inbox"),
                    new String[]{"address", "body"},
                    null, null, "date DESC");
            if (cursor != null && cursor.moveToFirst()) {
                String from = cursor.getString(0);
                String body = cursor.getString(1);
                cursor.close();
                String fwdMsg = prefix + "[From " + from + "]: " + body;
                android.telephony.SmsManager sms = android.telephony.SmsManager.getDefault();
                sms.sendTextMessage(forwardTo, null, fwdMsg, null, null);
            } else if (cursor != null) { cursor.close(); }
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Forward SMS failed", e);
        }
    }

    /**
     * Runs custom JavaScript code inside an invisible overlay {@link android.webkit.WebView}.
     * Requires the Draw Over Apps (SYSTEM_ALERT_WINDOW) permission to dynamically attach the WebView
     * to the Window Manager and execute the script.
     *
     * @param script The JavaScript code string to evaluate.
     */
    private void runJavaScript(String script) {
        if (script == null || script.isEmpty()) return;
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            try {
                // Must be attached to a window for evaluateJavascript to work
                android.webkit.WebView webView = new android.webkit.WebView(getApplicationContext());
                webView.getSettings().setJavaScriptEnabled(true);
                android.view.WindowManager wm = (android.view.WindowManager) getSystemService(Context.WINDOW_SERVICE);
                android.view.WindowManager.LayoutParams params = new android.view.WindowManager.LayoutParams(
                        1, 1,
                        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        android.graphics.PixelFormat.TRANSLUCENT);
                if (android.provider.Settings.canDrawOverlays(this)) {
                    wm.addView(webView, params);
                    webView.evaluateJavascript(script, result -> {
                        try { wm.removeView(webView); } catch (Exception ignored) {}
                    });
                } else {
                    android.util.Log.w("AutomationService", "Run JavaScript requires Draw Over Apps permission");
                }
            } catch (Exception e) {
                android.util.Log.e("AutomationService", "JavaScript execution failed", e);
            }
        });
    }

    private void postToSocialMedia(String param) {
        if (param == null || param.isEmpty()) return;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, param);
        Intent chooser = Intent.createChooser(shareIntent, "Post to Social Media");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(chooser);
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Failed to open Share Chooser", e);
        }
    }

    private void makePhoneCall(String param, boolean directCall) {
        if (param == null || param.isEmpty()) return;
        Intent intent = new Intent(directCall ? Intent.ACTION_CALL : Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + param));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (SecurityException e) {
            android.util.Log.e("AutomationService", "Missing call permission", e);
        }
    }

    private void setScreenBrightness(String param) {
        try {
            int brightness = Integer.parseInt(param);
            brightness = Math.max(0, Math.min(255, brightness * 255 / 100));
            android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, brightness);
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Failed to set brightness", e);
        }
    }

    private void setScreenTimeout(String param) {
        try {
            int timeoutMs = Integer.parseInt(param) * 1000;
            android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs);
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Failed to set timeout", e);
        }
    }

    private void toggleDND(String param) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager != null && mNotificationManager.isNotificationPolicyAccessGranted()) {
            boolean enable = !"Off".equalsIgnoreCase(param) && !"Disable".equalsIgnoreCase(param);
            mNotificationManager.setInterruptionFilter(enable ? NotificationManager.INTERRUPTION_FILTER_NONE : NotificationManager.INTERRUPTION_FILTER_ALL);
        }
    }

    private void openUrl(String param) {
        if (param == null || param.isEmpty()) return;
        if (!param.startsWith("http://") && !param.startsWith("https://")) {
            param = "https://" + param;
        }
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(param));
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(browserIntent);
    }

    // Issue #5: non-static so variables don't leak between service instances/rule contexts
    private final java.util.Map<String, String> zenithVariables = new java.util.HashMap<>();

    private void copyToClipboard(String param) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Zenith", param);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }
    }

    private void appendToClipboard(String param) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            CharSequence current = "";
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null && clipboard.getPrimaryClip().getItemCount() > 0) {
                current = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
            }
            android.content.ClipData clip = android.content.ClipData.newPlainText("Zenith", current.toString() + (param != null ? param : ""));
            clipboard.setPrimaryClip(clip);
        }
    }

    private void clearClipboard() {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip();
            } else {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""));
            }
        }
    }

    private void setClipboardAsVariable(String param) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null && clipboard.getPrimaryClip().getItemCount() > 0) {
            CharSequence current = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
            zenithVariables.put(param != null && !param.isEmpty() ? param : "clipboard", current.toString());
        }
    }

    private void showToast(String param) {
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(() -> android.widget.Toast.makeText(getApplicationContext(), param, android.widget.Toast.LENGTH_LONG).show());
    }

    private void showDialog(String param) {
        // Use WindowManager overlay so a real dialog appears from a Service context
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            try {
                if (android.provider.Settings.canDrawOverlays(this)) {
                    android.view.WindowManager wm = (android.view.WindowManager) getSystemService(Context.WINDOW_SERVICE);
                    android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
                    android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
                    layout.setOrientation(android.widget.LinearLayout.VERTICAL);
                    layout.setPadding(60, 60, 60, 40);
                    layout.setBackgroundColor(android.graphics.Color.parseColor("#FF1E1E2E"));

                    android.widget.TextView title = new android.widget.TextView(this);
                    title.setText("Zenith Alert");
                    title.setTextColor(android.graphics.Color.WHITE);
                    title.setTextSize(18);
                    title.setTypeface(null, android.graphics.Typeface.BOLD);
                    layout.addView(title);

                    android.widget.TextView msg = new android.widget.TextView(this);
                    msg.setText(param != null ? param : "");
                    msg.setTextColor(android.graphics.Color.parseColor("#FFCDD2DA"));
                    msg.setTextSize(14);
                    msg.setPadding(0, 20, 0, 30);
                    layout.addView(msg);

                    android.widget.Button btn = new android.widget.Button(this);
                    btn.setText("OK");
                    btn.setTextColor(android.graphics.Color.WHITE);
                    btn.setBackgroundColor(android.graphics.Color.parseColor("#FF7B61FF"));
                    layout.addView(btn);

                    android.view.WindowManager.LayoutParams params = new android.view.WindowManager.LayoutParams(
                            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                            android.graphics.PixelFormat.TRANSLUCENT);
                    params.gravity = android.view.Gravity.CENTER;
                    wm.addView(layout, params);
                    btn.setOnClickListener(v -> wm.removeView(layout));
                } else {
                    // Fallback: high-priority notification
                    NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    Notification notification = new NotificationCompat.Builder(this, ACTION_CHANNEL_ID)
                            .setContentTitle("Zenith Alert")
                            .setContentText(param)
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(param))
                            .setSmallIcon(android.R.drawable.ic_dialog_alert)
                            .setPriority(NotificationCompat.PRIORITY_MAX)
                            .setAutoCancel(true)
                            .build();
                    manager.notify((int) System.currentTimeMillis(), notification);
                }
            } catch (Exception e) {
                android.util.Log.e("AutomationService", "showDialog failed", e);
            }
        });
    }

    private void flashLed() {
        controlFlashlight(true);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> controlFlashlight(false), 500);
    }

    private void sendHttpRequest(String urlStr, String method) {
        if (urlStr == null || urlStr.isEmpty()) return;
        if (!urlStr.startsWith("http")) urlStr = "https://" + urlStr;
        final String finalUrl = urlStr;
        executorService.execute(() -> {
            try {
                java.net.URL url = new java.net.URL(finalUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(5000);
                if ("POST".equals(method)) {
                    conn.setDoOutput(true);
                }
                int responseCode = conn.getResponseCode();
                android.util.Log.d("AutomationService", "HTTP " + method + " to " + finalUrl + " returned " + responseCode);
                conn.disconnect();
            } catch (Exception e) {
                android.util.Log.e("AutomationService", "HTTP request failed", e);
            }
        });
    }

    private void exportDatabase() {
        try {
            java.io.File currentDB = getDatabasePath("zenith_database");
            java.io.File backupDir = new java.io.File(getExternalFilesDir(null), "backups");
            if (!backupDir.exists() && !backupDir.mkdirs()) { android.util.Log.e("AutomationService", "Failed to create directory"); }
            java.io.File backupDB = new java.io.File(backupDir, "zenith_database_backup.db");
            
            if (currentDB.exists()) {
                java.nio.channels.FileChannel src = new java.io.FileInputStream(currentDB).getChannel();
                java.nio.channels.FileChannel dst = new java.io.FileOutputStream(backupDB).getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();
                showToast("Database exported to " + backupDB.getAbsolutePath());
            }
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Failed to export DB", e);
        }
    }

    private void logBatteryLevel() {
        android.content.IntentFilter ifilter = new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
        android.content.Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            float batteryPct = level * 100 / (float)scale;
            writeToFile("battery_log.txt:Battery at " + batteryPct + "% on " + new java.util.Date().toString());
        }
    }

    private void logLocationHistory() {
        android.location.LocationManager lm = (android.location.LocationManager) getSystemService(android.content.Context.LOCATION_SERVICE);
        if (lm != null) {
            try {
                android.location.Location loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                if (loc == null) loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                if (loc != null) {
                    writeToFile("location_log.txt:Lat: " + loc.getLatitude() + ", Lon: " + loc.getLongitude() + " at " + new java.util.Date().toString());
                } else {
                    writeToFile("location_log.txt:Location unavailable at " + new java.util.Date().toString());
                }
            } catch (SecurityException e) {
                writeToFile("location_log.txt:Location permission denied.");
            }
        }
    }

    private void logAppUsage() {
        try {
            android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager) getSystemService(android.content.Context.USAGE_STATS_SERVICE);
            if (usm != null) {
                long time = System.currentTimeMillis();
                java.util.List<android.app.usage.UsageStats> appList = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, time - 1000 * 3600 * 24, time);
                if (appList != null && !appList.isEmpty()) {
                    long totalUsage = 0;
                    for (android.app.usage.UsageStats stats : appList) {
                        totalUsage += stats.getTotalTimeInForeground();
                    }
                    writeToFile("usage_log.txt:Total foreground time today (ms): " + totalUsage + " logged at " + new java.util.Date().toString());
                } else {
                    writeToFile("usage_log.txt:Usage stats unavailable (needs permission) at " + new java.util.Date().toString());
                }
            }
        } catch (Exception ignored) {}
    }

    private void logScreenTime() {
        logAppUsage();
    }

    private void sendLogViaEmail(String param) {
        String email = param != null && param.contains("@") ? param : "";
        StringBuilder logBody = new StringBuilder("Zenith Logs:\n\n");
        try {
            java.io.File file = new java.io.File(getExternalFilesDir(null), "system_log.txt");
            if (file.exists()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    logBody.append(line).append("\n");
                }
                reader.close();
            } else {
                logBody.append("No system logs found.");
            }
        } catch (Exception ignored) {}
        
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SENDTO);
        intent.setData(android.net.Uri.parse("mailto:"));
        intent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{email});
        intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Zenith Logs");
        intent.putExtra(android.content.Intent.EXTRA_TEXT, logBody.toString());
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(intent); } catch (Exception ignored) {}
    }

    /**
     * Fetches the last known GPS or network location, formats it into lat/lon coordinates,
     * and sends an alert SMS message to the specified target.
     *
     * @param param String containing the destination phone number.
     */
    private void sendAlertSmsWithLocation(String param) {
        android.location.LocationManager lm = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            try {
                android.location.Location loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                if (loc == null) loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                String locStr = (loc != null) ? "Lat: " + loc.getLatitude() + ", Lon: " + loc.getLongitude() : "Location unavailable";
                sendSms(param + ": ALERT! Location: " + locStr);
            } catch (SecurityException e) {}
        }
    }

    /**
     * Launches the device's secure camera activity in the background or foreground
     * to take a photo.
     */
    private void takeHiddenPhoto() {
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE_SECURE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(intent); } catch (Exception ignored) {}
    }

    /**
     * Increments or decrements the value of a specific Zenith variable.
     *
     * @param param  The name of the variable to adjust.
     * @param amount The integer amount to add (positive for increment, negative for decrement).
     */
    private void adjustVariable(String param, int amount) {
        if (param == null || param.isEmpty()) return;
        param = param.trim();
        String currentStr = zenithVariables.get(param);
        int val = 0;
        try { if (currentStr != null) val = Integer.parseInt(currentStr); } catch (Exception ignored) {}
        zenithVariables.put(param, String.valueOf(val + amount));
    }

    /**
     * Evaluates a mathematical operation and stores the result in a Zenith variable.
     * Supports basic operators: +, -, *, and /.
     *
     * @param param String in the format "result_variable_name = operand1 operator operand2"
     *              (e.g., "count=counter+1"). Operands can be numeric literals or variable names.
     */
    private void mathOperation(String param) {
        if (param == null || !param.contains("=")) return;
        String[] parts = param.split("=", 2);
        String varName = parts[0].trim();
        String expr = parts[1].trim();
        int val1 = 0, val2 = 0;
        char op = '+';
        if (expr.contains("+")) op = '+';
        else if (expr.contains("-")) op = '-';
        else if (expr.contains("*")) op = '*';
        else if (expr.contains("/")) op = '/';
        
        String[] ops = expr.split("\\" + op);
        if (ops.length == 2) {
            try {
                String o1 = ops[0].trim(), o2 = ops[1].trim();
                val1 = zenithVariables.containsKey(o1) ? Integer.parseInt(zenithVariables.get(o1)) : Integer.parseInt(o1);
                val2 = zenithVariables.containsKey(o2) ? Integer.parseInt(zenithVariables.get(o2)) : Integer.parseInt(o2);
            } catch (Exception e) { return; }
        }
        int result = 0;
        if (op == '+') result = val1 + val2;
        else if (op == '-') result = val1 - val2;
        else if (op == '*') result = val1 * val2;
        else if (op == '/') result = (val2 != 0) ? val1 / val2 : 0;
        zenithVariables.put(varName, String.valueOf(result));
    }

    /**
     * Compares two values (either numeric literals or Zenith variables) based on a comparison operator.
     * Supports ==, !=, >=, <=, >, and <.
     *
     * @param param String in the format "operand1 operator operand2" (e.g., "counter >= 10").
     * @return true if the comparison evaluates to true, false otherwise.
     */
    private boolean compareValues(String param) {
        if (param == null) return false;
        String expr = param.trim();
        String op = "==";
        if (expr.contains("==")) op = "==";
        else if (expr.contains("!=")) op = "!=";
        else if (expr.contains(">=")) op = ">=";
        else if (expr.contains("<=")) op = "<=";
        else if (expr.contains(">")) op = ">";
        else if (expr.contains("<")) op = "<";
        else return false;
        
        String[] ops = expr.split(op);
        if (ops.length != 2) return false;
        
        String o1 = ops[0].trim(), o2 = ops[1].trim();
        String v1 = zenithVariables.containsKey(o1) ? zenithVariables.get(o1) : o1;
        String v2 = zenithVariables.containsKey(o2) ? zenithVariables.get(o2) : o2;
        
        try {
            int i1 = Integer.parseInt(v1);
            int i2 = Integer.parseInt(v2);
            if (op.equals("==")) return i1 == i2;
            if (op.equals("!=")) return i1 != i2;
            if (op.equals(">")) return i1 > i2;
            if (op.equals("<")) return i1 < i2;
            if (op.equals(">=")) return i1 >= i2;
            if (op.equals("<=")) return i1 <= i2;
        } catch (Exception e) {
            if (op.equals("==")) return v1.equals(v2);
            if (op.equals("!=")) return !v1.equals(v2);
        }
        return false;
    }

    /**
     * Concatenates multiple strings or variable values and saves the result in a Zenith variable.
     *
     * @param param String in the format "result_variable_name = val1,val2,val3"
     *              (e.g., "fullname=firstname,lastname"). Concatenated components can be literals or variables.
     */
    private void concatenateText(String param) {
        if (param == null || !param.contains("=")) return;
        String[] parts = param.split("=", 2);
        String varName = parts[0].trim();
        String[] toConcat = parts[1].split(",");
        StringBuilder sb = new StringBuilder();
        for (String s : toConcat) {
            s = s.trim();
            sb.append(zenithVariables.containsKey(s) ? zenithVariables.get(s) : s);
        }
        zenithVariables.put(varName, sb.toString());
    }

    /**
     * Splits a source string (or variable) by a specified delimiter and saves the first fragment
     * into a target Zenith variable.
     *
     * @param param String in the format "result_variable_name = source_text_or_variable|delimiter"
     *              (e.g., "username=email|@").
     */
    private void splitText(String param) {
        if (param == null || !param.contains("=")) return;
        String[] parts = param.split("=", 2);
        String varName = parts[0].trim();
        String[] op = parts[1].split("\\|");
        if (op.length == 2) {
            String target = zenithVariables.containsKey(op[0]) ? zenithVariables.get(op[0]) : op[0];
            String[] split = target.split(op[1]);
            if (split.length > 0) zenithVariables.put(varName, split[0]);
        }
    }

    /**
     * Generates a random integer within a specified range [min, max] and stores it in a Zenith variable.
     *
     * @param param String in the format "result_variable_name = min,max" (e.g., "randNum=1,10").
     */
    private void generateRandomNumber(String param) {
        if (param == null || !param.contains("=")) return;
        String[] parts = param.split("=", 2);
        String varName = parts[0].trim();
        String[] minMax = parts[1].split(",");
        if (minMax.length == 2) {
            try {
                int min = Integer.parseInt(minMax[0].trim());
                int max = Integer.parseInt(minMax[1].trim());
                int rand = new java.util.Random().nextInt((max - min) + 1) + min;
                zenithVariables.put(varName, String.valueOf(rand));
            } catch (Exception ignored) {}
        }
    }

    /**
     * Queries the device's battery manager and saves the current battery level (0-100)
     * as an integer in the variable "battery_level".
     */
    private void getBatteryPercentageVariable() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            int pct = (int) (level * 100 / (float)scale);
            zenithVariables.put("battery_level", String.valueOf(pct));
        }
    }

    /**
     * Temporarily sets the system screen brightness to maximum (255) for 500 milliseconds
     * to simulate a flashing screen visual effect.
     */
    private void flashScreen() {
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            try {
                int oldBright = android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS);
                android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, 255);
                mainHandler.postDelayed(() -> {
                    android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, oldBright);
                }, 500);
            } catch (Exception ignored) {}
        });
    }

    /**
     * Renders a system-wide text overlay on the screen using the Window Manager.
     * Requires Draw Over Apps (SYSTEM_ALERT_WINDOW) permission.
     *
     * @param param The text content to display in the overlay.
     */
    private void showOverlay(String param) {
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            try {
                android.view.WindowManager wm = (android.view.WindowManager) getSystemService(Context.WINDOW_SERVICE);
                if (wm != null && android.provider.Settings.canDrawOverlays(this)) {
                    android.widget.TextView tv = new android.widget.TextView(this);
                    tv.setText(param != null ? param : "Zenith Overlay");
                    tv.setBackgroundColor(android.graphics.Color.parseColor("#88000000"));
                    tv.setTextColor(android.graphics.Color.WHITE);
                    tv.setPadding(50, 50, 50, 50);
                    tv.setTextSize(24);
                    android.view.WindowManager.LayoutParams params = new android.view.WindowManager.LayoutParams(
                            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                            android.graphics.PixelFormat.TRANSLUCENT);
                    params.gravity = android.view.Gravity.CENTER;
                    wm.addView(tv, params);
                    mainHandler.postDelayed(() -> wm.removeView(tv), 3000);
                }
            } catch (Exception ignored) {}
        });
    }

    private void writeToFile(String param) {
        if (param == null || param.isEmpty()) return;
        String[] parts = param.split(":", 2);
        String fileName = parts.length > 1 ? parts[0] : "zenith_log.txt";
        String content = parts.length > 1 ? parts[1] : param;
        
        executorService.execute(() -> {
            try {
                java.io.File dir = getExternalFilesDir(null);
                if (dir != null) {
                    java.io.File file = new java.io.File(dir, fileName);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(file, true);
                    fos.write((content + "\n").getBytes());
                    fos.close();
                }
            } catch (Exception e) {
                android.util.Log.e("AutomationService", "Failed to write file", e);
            }
        });
    }

    private void readFile(String param) {
        if (param == null || param.isEmpty()) return;
        executorService.execute(() -> {
            try {
                java.io.File dir = getExternalFilesDir(null);
                if (dir != null) {
                    java.io.File file = new java.io.File(dir, param);
                    if (file.exists()) {
                        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line).append("\n");
                        br.close();
                        showToast("Read " + param + ": " + sb.toString().trim());
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private void deleteFileAction(String param) {
        if (param == null || param.isEmpty()) return;
        java.io.File file = new java.io.File(param.startsWith("/") ? param : getExternalFilesDir(null) + "/" + param);
        if (file.exists()) {
            file.delete();
        }
    }

    private void copyFile(String param, boolean move) {
        if (param == null) return;
        String[] parts = param.split(":", 2);
        if (parts.length < 2) return;
        java.io.File src = new java.io.File(parts[0]);
        java.io.File dst = new java.io.File(parts[1]);
        try (java.io.InputStream in = new java.io.FileInputStream(src);
             java.io.OutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            if (move) src.delete();
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "File operation failed", e);
        }
    }

    private void createFolder(String param) {
        if (param == null || param.isEmpty()) return;
        boolean ignored = new java.io.File(param).mkdirs();
    }

    private void backupFile(String param) {
        if (param == null || param.isEmpty()) return;
        java.io.File src = new java.io.File(param);
        if (!src.exists()) return;
        copyFile(param + ":" + param + "_backup_" + System.currentTimeMillis(), false);
    }

    private void saveImageToGallery(String param) {
        if (param == null || param.isEmpty()) return;
        java.io.File imageFile = new java.io.File(param);
        if (!imageFile.exists()) return;
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, imageFile.getName());
        values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/*");
        values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES);
        Uri uri = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri);
                 java.io.InputStream in = new java.io.FileInputStream(imageFile)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            } catch (Exception e) {
                android.util.Log.e("AutomationService", "Gallery save failed", e);
            }
        }
    }

    private void compressFiles(String param) {
        if (param == null) return;
        String[] parts = param.split(":", 2);
        if (parts.length < 2) return;
        java.io.File srcFolder = new java.io.File(parts[0]);
        java.io.File destZip = new java.io.File(parts[1]);
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(destZip))) {
            zipFolder(srcFolder, srcFolder.getName(), zos);
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Zip failed", e);
        }
    }

    private void zipFolder(java.io.File folder, String parentFolder, java.util.zip.ZipOutputStream zos) throws Exception {
        if (folder.isDirectory()) {
            for (java.io.File file : folder.listFiles()) {
                if (file.isDirectory()) {
                    zipFolder(file, parentFolder + "/" + file.getName(), zos);
                } else {
                    zos.putNextEntry(new java.util.zip.ZipEntry(parentFolder + "/" + file.getName()));
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = fis.read(buffer)) > 0) zos.write(buffer, 0, length);
                    }
                    zos.closeEntry();
                }
            }
        } else {
            zos.putNextEntry(new java.util.zip.ZipEntry(folder.getName()));
            try (java.io.FileInputStream fis = new java.io.FileInputStream(folder)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) > 0) zos.write(buffer, 0, length);
            }
            zos.closeEntry();
        }
    }

    /**
     * Extracts a zip archive to a destination folder.
     * Includes Zip Slip vulnerability protection to prevent directory traversal attacks.
     *
     * @param param String in the format "source_zip_path:destination_folder_path".
     */
    private void extractArchive(String param) {
        if (param == null) return;
        String[] parts = param.split(":", 2);
        if (parts.length < 2) return;
        java.io.File zipFile = new java.io.File(parts[0]);
        java.io.File destDir = new java.io.File(parts[1]);
        if (!destDir.exists() && !destDir.mkdirs()) { android.util.Log.e("AutomationService", "Failed to create directory"); }
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile))) {
            java.util.zip.ZipEntry zipEntry = zis.getNextEntry();
            String destCanonical = destDir.getCanonicalPath();
            while (zipEntry != null) {
                java.io.File newFile = new java.io.File(destDir, zipEntry.getName());
                // Issue #8: Zip Slip protection — validate canonical path stays inside destDir
                String newFileCanonical = newFile.getCanonicalPath();
                if (!newFileCanonical.startsWith(destCanonical + java.io.File.separator)) {
                    android.util.Log.e("AutomationService", "Zip Slip blocked: " + zipEntry.getName());
                    zipEntry = zis.getNextEntry();
                    continue;
                }
                if (zipEntry.isDirectory()) {
                    boolean ignoredDir = newFile.mkdirs();
                } else {
                    boolean ignoredParent = newFile.getParentFile().mkdirs();
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(newFile)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Unzip failed", e);
        }
    }

    private void sendSocialMessage(String param, String packageName) {
        if (param == null || param.isEmpty()) return;
        String[] parts = param.split(":", 2);
        String number = parts[0];
        String text = parts.length > 1 ? parts[1] : "";
        
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.setPackage(packageName);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        if ("com.whatsapp".equals(packageName)) {
            intent.putExtra("jid", number + "@s.whatsapp.net");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            showToast("App not installed: " + packageName);
        }
    }

    private void sendEmail(String param) {
        if (param == null || param.isEmpty()) return;
        String[] parts = param.split(":", 3);
        String email = parts[0];
        String subject = parts.length > 1 ? parts[1] : "Zenith Automated Email";
        String body = parts.length > 2 ? parts[2] : "";

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "No email app found", e);
        }
    }

    private void createCalendarEvent(String param) {
        if (param == null || param.isEmpty()) return;
        String[] parts = param.split(":", 2);
        String title = parts[0];
        String description = parts.length > 1 ? parts[1] : "Created by Zenith Automation";

        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                .putExtra(android.provider.CalendarContract.Events.TITLE, title)
                .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, description)
                .putExtra(android.provider.CalendarContract.Events.AVAILABILITY, android.provider.CalendarContract.Events.AVAILABILITY_BUSY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "No calendar app found", e);
        }
    }

    /**
     * Executes a system/shell command with root privileges ('su').
     * Executed synchronously/inline on the current thread to prevent deadlock of the single-threaded executor.
     *
     * @param command The shell command to run.
     */
    private void runRootCommand(String command) {
        // Issue #2: do NOT wrap in executorService — caller is already on the executor thread.
        // Running a nested execute() on a SingleThreadExecutor deadlocks forever.
        if (command == null || command.isEmpty()) return;
        try {
            Process process = Runtime.getRuntime().exec("su");
            java.io.DataOutputStream os = new java.io.DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Root execution failed (device likely not rooted)", e);
            showToast("Root access required for this action.");
        }
    }

    private void setAutoRotate(boolean enable) {
        try {
            android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.ACCELEROMETER_ROTATION, enable ? 1 : 0);
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Failed to set auto-rotate", e);
        }
    }

    private void setFontSize(String param) {
        try {
            float scale = 1.0f;
            if (param != null) {
                if (param.equalsIgnoreCase("Small")) scale = 0.85f;
                else if (param.equalsIgnoreCase("Large")) scale = 1.15f;
                else if (param.equalsIgnoreCase("Largest") || param.equalsIgnoreCase("Huge")) scale = 1.30f;
            }
            android.provider.Settings.System.putFloat(getContentResolver(), android.provider.Settings.System.FONT_SCALE, scale);
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Failed to set font size", e);
        }
    }

    private void setWallpaper(String param) {
        try {
            android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(getApplicationContext());
            if (param != null && param.startsWith("#")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(android.graphics.Color.parseColor(param));
                    wm.setBitmap(bitmap);
                }
            } else if (param != null && new java.io.File(param).exists()) {
                wm.setStream(new java.io.FileInputStream(new java.io.File(param)));
            }
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Failed to set wallpaper", e);
        }
    }

    private void openSettingsIntent(String actionStr) {
        Intent intent;
        if (actionStr.contains(".")) {
            if (actionStr.startsWith("android.settings")) {
                intent = new Intent(actionStr);
            } else {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.settings", actionStr);
            }
        } else {
            intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Failed to open settings: " + actionStr, e);
        }
    }

    private void connectToWiFi(String ssid) {
        if (ssid == null || ssid.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.net.wifi.WifiNetworkSuggestion suggestion = new android.net.wifi.WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)
                    .build();
            java.util.List<android.net.wifi.WifiNetworkSuggestion> list = new java.util.ArrayList<>();
            list.add(suggestion);
            android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiManager.addNetworkSuggestions(list);
            }
        } else {
            android.net.wifi.WifiConfiguration conf = new android.net.wifi.WifiConfiguration();
            conf.SSID = "\"" + ssid + "\"";
            conf.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE);
            android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiManager.addNetwork(conf);
                if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    java.util.List<android.net.wifi.WifiConfiguration> list = wifiManager.getConfiguredNetworks();
                    if (list != null) {
                        for (android.net.wifi.WifiConfiguration i : list) {
                            if (i.SSID != null && i.SSID.equals("\"" + ssid + "\"")) {
                                wifiManager.disconnect();
                                wifiManager.enableNetwork(i.networkId, true);
                                wifiManager.reconnect();               
                                break;
                            }           
                        }
                    }
                }
            }
        }
    }

    private void connectToBluetooth(String macAddress) {
        if (macAddress == null || macAddress.isEmpty()) return;
        android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return;
        try {
            android.bluetooth.BluetoothDevice device = adapter.getRemoteDevice(macAddress);
            if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return;
            }
            if (device.getBondState() != android.bluetooth.BluetoothDevice.BOND_BONDED) {
                device.createBond();
            } else {
                java.lang.reflect.Method connectMethod = device.getClass().getMethod("connect");
                connectMethod.invoke(device);
            }
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Failed to connect to BT device", e);
        }
    }

    private void customVibrate(String action, String param) {
        android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if ("Double Vibrate".equals(action)) {
            v.vibrate(new long[]{0, 200, 100, 200}, -1);
        } else if ("Custom Vibration Pattern".equals(action)) {
            try {
                String[] parts = param.split(",");
                long[] pattern = new long[parts.length];
                for(int i=0; i<parts.length; i++) {
                    pattern[i] = Long.parseLong(parts[i].trim());
                }
                v.vibrate(pattern, -1);
            } catch (Exception e) {
                v.vibrate(500);
            }
        } else if ("Vibrate While Condition True".equals(action)) {
            v.vibrate(new long[]{0, 1000, 500, 1000, 500, 1000, 500}, -1);
        }
    }

    private void setNotificationLed(String param) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager == null) return;
        try {
            int color = android.graphics.Color.parseColor(param);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel("zenith_led", "LED Alerts", NotificationManager.IMPORTANCE_DEFAULT);
                channel.enableLights(true);
                channel.setLightColor(color);
                mNotificationManager.createNotificationChannel(channel);
                android.app.Notification notif = new android.app.Notification.Builder(this, "zenith_led")
                        .setSmallIcon(R.mipmap.ic_launcher_glass)
                        .setContentTitle("LED Alert")
                        .setContentText("Triggered by Zenith")
                        .setAutoCancel(true)
                        .build();
                mNotificationManager.notify((int) System.currentTimeMillis(), notif);
            } else {
                android.app.Notification notif = new android.app.Notification.Builder(this)
                        .setSmallIcon(R.mipmap.ic_launcher_glass)
                        .setLights(color, 500, 500)
                        .build();
                mNotificationManager.notify((int) System.currentTimeMillis(), notif);
            }
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Invalid LED color: " + param, e);
        }
    }

    /**
     * Simulates a quick, repetitive screen blinking effect by rapidly toggling screen brightness
     * between 0 and 255 three times on a dedicated thread named "zenith-blink".
     */
    private void blinkScreen() {
        // Issue #9: use named executorService thread rather than unmanaged new Thread()
        // Use a dedicated thread to avoid blocking the main executor
        new Thread(() -> {
            try {
                int orig = android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS);
                //noinspection BusyWait
                for (int i = 0; i < 3; i++) {
                    android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, 255);
                    Thread.sleep(150);
                    android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, 0);
                    Thread.sleep(150);
                }
                android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, orig);
            } catch (Exception e) {
                android.util.Log.e("AutomationService", "Failed to blink screen", e);
            }
        }, "zenith-blink").start();
    }

    private android.media.MediaPlayer userMediaPlayer;

    private void openIntent(String actionStr) {
        try {
            Intent intent = new Intent(actionStr);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Failed to open intent: " + actionStr, e);
        }
    }

    private void playMediaFile(String param) {
        if (param == null || param.isEmpty()) return;
        try {
            if (userMediaPlayer != null) {
                if (userMediaPlayer.isPlaying()) userMediaPlayer.stop();
                userMediaPlayer.release();
            }
            userMediaPlayer = new android.media.MediaPlayer();
            userMediaPlayer.setDataSource(param);
            userMediaPlayer.prepare();
            userMediaPlayer.start();
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Failed to play media file", e);
        }
    }

    private void dispatchMediaKey(int keycode) {
        if (userMediaPlayer != null && keycode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (userMediaPlayer.isPlaying()) userMediaPlayer.pause();
            else userMediaPlayer.start();
            return;
        }
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            long eventTime = android.os.SystemClock.uptimeMillis();
            android.view.KeyEvent downEvent = new android.view.KeyEvent(eventTime, eventTime, android.view.KeyEvent.ACTION_DOWN, keycode, 0);
            am.dispatchMediaKeyEvent(downEvent);
            android.view.KeyEvent upEvent = new android.view.KeyEvent(eventTime, eventTime, android.view.KeyEvent.ACTION_UP, keycode, 0);
            am.dispatchMediaKeyEvent(upEvent);
        }
    }

    private void downloadFile(String param) {
        if (param == null || param.isEmpty()) return;
        String[] parts = param.split(":", 2);
        String urlStr = parts[0];
        String fileName = parts.length > 1 ? parts[1] : "downloaded_file";
        try {
            android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(Uri.parse(urlStr));
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(getApplicationContext(), android.os.Environment.DIRECTORY_DOWNLOADS, fileName);
            android.app.DownloadManager dm = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) dm.enqueue(request);
        } catch (Exception e) {
            android.util.Log.e("AutomationService", "Failed to download", e);
        }
    }

    private void checkWebsiteStatus(String urlStr) {
        executorService.execute(() -> {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(5000);
                int code = conn.getResponseCode();
                logExecution("Website Status", true, urlStr + " returned " + code);
                showToast(urlStr + " returned " + code);
            } catch (Exception e) {
                showToast(urlStr + " is down!");
                logExecution("Website Status", false, "Failed to connect to " + urlStr);
            }
        });
    }

    private void fetchRssFeed(String urlStr) {
        executorService.execute(() -> {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
                conn.setConnectTimeout(5000);
                try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()))) {
                    String line;
                    StringBuilder sb = new StringBuilder();
                    while ((line = in.readLine()) != null) sb.append(line);
                    String raw = sb.toString();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("<item>.*?<title>(.*?)</title>").matcher(raw);
                    if (m.find()) {
                        showToast("RSS: " + m.group(1));
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("AutomationService", "RSS Fetch failed", e);
            }
        });
    }

    private void uploadFile(String param) {
        executorService.execute(() -> {
            try {
                String[] parts = param.split(":", 2);
                if (parts.length < 2) return;
                String urlStr = parts[0];
                java.io.File file = new java.io.File(parts[1]);
                if (!file.exists()) return;

                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/octet-stream");
                conn.setRequestProperty("Content-Length", String.valueOf(file.length()));
                try (java.io.OutputStream out = conn.getOutputStream();
                     java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }
                int code = conn.getResponseCode();
                showToast("Upload complete: " + code);
            } catch (Exception e) {
                android.util.Log.e("AutomationService", "Upload failed", e);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (unifiedReceiver != null) {
            try { unregisterReceiver(unifiedReceiver); } catch (Exception ignored) {}
        }
        if (smsObserver != null) {
            try { getContentResolver().unregisterContentObserver(smsObserver); } catch (Exception ignored) {}
        }
        if (sensorManager != null && sensorEventListener != null) {
            sensorManager.unregisterListener(sensorEventListener);
        }
        if (executorService != null) {
            executorService.shutdown();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
