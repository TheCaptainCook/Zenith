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
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutomationService extends Service {

    private static final String CHANNEL_ID = "zenith_engine_channel";
    private static final String ACTION_CHANNEL_ID = "zenith_action_channel";
    private static final int NOTIFICATION_ID = 1001;

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
    
    private List<RuleEntity> activeRules = new ArrayList<>();
    private boolean batteryTriggerHasFired = false;

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
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

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
                                            handleSystemEvent(getApplicationContext(), new Intent("zenith.SMS_SENT"));
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
                                                            handleSystemEvent(getApplicationContext(), new Intent("zenith.DEVICE_SHAKE"));
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
                        e.printStackTrace();
                    }
                }
            }

            if (hasSystemRules && filter.countActions() > 0) {
                unifiedReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        handleSystemEvent(context, intent);
                    }
                };
                registerReceiver(unifiedReceiver, filter);
            }
        });
    }

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
                } catch (Exception e) {}
                return false;
            case "Power Connected":
            case "Power Connected / Disconnected":
                try {
                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
                    if (batteryStatus != null) {
                        int status = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
                        boolean isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
                        return stateParam == null ? isCharging : (stateParam.equals("Connected") ? isCharging : !isCharging);
                    }
                } catch (Exception e) {}
                return false;
            case "Screen On / Off / Unlocked":
                try {
                    android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                    boolean isScreenOn = pm != null && pm.isInteractive();
                    return stateParam == null ? isScreenOn : (stateParam.equals("Screen On") || stateParam.equals("User Present") ? isScreenOn : !isScreenOn);
                } catch (Exception e) {}
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
                return am != null && am.isWiredHeadsetOn();
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
                    return stateParam == null ? isAirplaneModeOn : (stateParam.equals("On") ? isAirplaneModeOn : !isAirplaneModeOn);
                } catch (Exception e) {}
                return false;
            case "Wi-Fi Connected / Disconnected":
                try {
                    android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (cm != null) {
                        android.net.Network net = cm.getActiveNetwork();
                        android.net.NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
                        boolean isWifi = caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
                        return stateParam == null ? isWifi : (stateParam.equals("Connected") ? isWifi : !isWifi);
                    }
                } catch (Exception e) {}
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
                    return stateParam == null ? isOn : (stateParam.equals("On") ? isOn : !isOn);
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

    private void handleSystemEvent(Context context, Intent intent) {
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
                            if (batteryPct < 20.0f && !batteryTriggerHasFired) {
                                isCatalystMet = true;
                                catalystTrigger = fullT;
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
                            android.net.NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                            if (info != null) {
                                boolean isConn = info.isConnected();
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

    private java.util.Map<Integer, Long> lastExecutionTimes = new java.util.HashMap<>();

    private void executeActions(RuleEntity rule) {
        long now = System.currentTimeMillis();
        Long lastTime = lastExecutionTimes.get(rule.id);
        if (lastTime != null && (now - lastTime) < 2000) {
            return; // Debounce to prevent duplicate executions
        }
        lastExecutionTimes.put(rule.id, now);

        boolean overallSuccess = true;
        StringBuilder detailsBuilder = new StringBuilder("Scheduled actions: ");

        try {
            JSONArray actionsArray = new JSONArray(rule.actionsJson);
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            
            for (int i = 0; i < actionsArray.length(); i++) {
                String actionType = actionsArray.getString(i);
                String[] parts = actionType.split("\\|", 2);
                String action = parts[0];
                String param  = parts.length > 1 ? parts[1] : "";

                if (i > 0) detailsBuilder.append(", ");
                detailsBuilder.append(action);

                handler.postDelayed(() -> {
                    try {
                        if ("Show Notification".equals(action)) {
                            showActionNotification(rule.ruleName);
                        } else if ("Play Sound".equals(action)) {
                            playSound();
                        } else if ("Vibrate Phone".equals(action)) {
                            vibratePhone();
                        } else if ("Turn on Flashlight".equals(action)) {
                            controlFlashlight(true);
                        } else if ("Turn off Flashlight".equals(action)) {
                            controlFlashlight(false);
                        } else if ("Toggle WiFi".equals(action)) {
                            toggleWiFi();
                        } else if ("Set Volume: Max".equals(action)) {
                            setVolume(true);
                        } else if ("Set Volume: Silent".equals(action)) {
                            setVolume(false);
                        } else if ("Toggle Bluetooth".equals(action)) {
                            toggleBluetooth();
                        } else if ("Speak Text".equals(action)) {
                            speakText(param.isEmpty() ? rule.ruleName + " triggered" : param);
                        } else if ("Open App".equals(action)) {
                            openApp(param);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("AutomationService", "Error in delayed action", e);
                    }
                }, i * 500L);
            }
        } catch (Exception e) {
            overallSuccess = false;
            detailsBuilder.append(" | Error parsing actions: ").append(e.getMessage());
        }

        logExecution(rule.ruleName, overallSuccess, detailsBuilder.toString());
    }

    private void showActionNotification(String ruleName) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = new NotificationCompat.Builder(this, ACTION_CHANNEL_ID)
                .setContentTitle("Zenith Rule Triggered!")
                .setContentText("Rule executed: " + ruleName)
                .setSmallIcon(R.mipmap.ic_launcher)
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
            e.printStackTrace();
        }
    }

    private void vibratePhone() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(500);
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
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            if (max) {
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                        am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC), 0);
                am.setRingerMode(android.media.AudioManager.RINGER_MODE_NORMAL);
            } else {
                am.setRingerMode(android.media.AudioManager.RINGER_MODE_SILENT);
            }
        }
    }

    private void toggleBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent btIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            btIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(btIntent);
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
            startActivity(launchIntent);
        }
    }

    private void toggleWiFi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
            panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(panelIntent);
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (unifiedReceiver != null) {
            try { unregisterReceiver(unifiedReceiver); } catch (Exception ignored) {}
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
