/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.piratemedia.flightcontroller;

import android.app.ActionBar;
import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class BluetoothComputerFragment extends Fragment implements Runnable {

    private static final String TAG = "ComputerFragment";
    private static final String ACTION_USB_PERMISSION = "tv.piratemedia.quadrocopterflightcomputer.action.USB_PERMISSION";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    public static final int LED_STATE = 1;
    public static final int LED_HUE = 2;

    // Layout Views
    private ListView mTransactionView;

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mTransactionArrayAdapter;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothFlightControllerService mFlightControllerService = null;

    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;

    ParcelFileDescriptor mFileDescriptor;
    FileInputStream mInputStream;
    FileOutputStream mOutputStream;
    UsbAccessory mAccessory;

    private VerticalProgressBar throttle;
    private ImageView LedColor;

    private Sensor sensor;
    private float currentPreasure = 0.0f;
    private float currentAltitude = 0.0f;
    private double currentLat = 0;
    private double currentLong = 0;

    private int currentTrottle = 0;
    private int currentPitchOffset = 0;
    private int currentRollOffset = 0;
    private int currentYawOffset = 0;

    private boolean Moving = false;

    private int PitchCorrection = 0;
    private int RollCorrection = 0;
    private int YawCorrection = 0;
    private int currentPitch = 0;
    private int currentRoll = 0;
    private int currentYaw = 0;

    private boolean isBreaking = false;

    private ProgressBar rollLeft;
    private ProgressBar rollRight;
    private VerticalProgressBar pitchForward;
    private VerticalProgressBar pitchBackward;
    private ProgressBar yawLeft;
    private ProgressBar yawRight;

    private int MaxCorrectionValue = 5;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mFlightControllerService == null) {
            setupChat();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFlightControllerService != null) {
            mFlightControllerService.stop();
        }
        closeAccessory();
        getActivity().unregisterReceiver(mUsbReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mFlightControllerService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mFlightControllerService.getState() == BluetoothFlightControllerService.STATE_NONE) {
                // Start the Bluetooth chat services
                mFlightControllerService.start();
            }
        }

        Intent intent = getActivity().getIntent();
        if (mInputStream != null && mOutputStream != null) {
            return;
        }

        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            if (mUsbManager.hasPermission(accessory)) {
                openAccessory(accessory);
            } else {
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        mUsbManager.requestPermission(accessory,
                                mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "mAccessory is null");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.computer_ui_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mTransactionView = (ListView) view.findViewById(R.id.list);

        throttle = (VerticalProgressBar) view.findViewById(R.id.throttle);
        LedColor = (ImageView) view.findViewById(R.id.LedColor);

        rollLeft = (ProgressBar) view.findViewById(R.id.rollLeft);
        rollRight = (ProgressBar) view.findViewById(R.id.rollRight);
        pitchForward = (VerticalProgressBar) view.findViewById(R.id.pitchForward);
        pitchBackward = (VerticalProgressBar) view.findViewById(R.id.pitchBackward);
        yawLeft = (ProgressBar) view.findViewById(R.id.yawLeft);
        yawRight = (ProgressBar) view.findViewById(R.id.yawRight);

        mUsbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(
                ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        getActivity().registerReceiver(mUsbReceiver, filter);

        if (getActivity().getLastNonConfigurationInstance() != null) {
            mAccessory = (UsbAccessory) getActivity().getLastNonConfigurationInstance();
            openAccessory(mAccessory);
        }

        setupAtimiter();
        setupGPS();
        //setupGyro();
        setupAccel();
    }

    public void updateThrottle(int val) {
        throttle.setProgress(val);
        currentTrottle = val;
        val += 40;

        if(currentTrottle == 0) {
            sendSpeedVectors(40, 40, 40, 40);
            return;
        }

        /*int motor1Power = val + (currentPitchOffset + PitchCorrection) + (currentYawOffset + YawCorrection);
        int motor2Power = val + (currentRollOffset + RollCorrection) - (currentYawOffset + YawCorrection);
        int motor3Power = val - (currentPitchOffset + PitchCorrection) + (currentYawOffset + YawCorrection);
        int motor4Power = val - (currentRollOffset + RollCorrection) - (currentYawOffset + YawCorrection);*/

        int motor1Power = val + (currentRollOffset + RollCorrection) - (currentPitchOffset + PitchCorrection) + (currentYawOffset + YawCorrection);
        int motor2Power = val + (currentRollOffset + RollCorrection) + (currentPitchOffset + PitchCorrection) - (currentYawOffset + YawCorrection);
        int motor3Power = val - (currentRollOffset + RollCorrection) + (currentPitchOffset + PitchCorrection) + (currentYawOffset + YawCorrection);
        int motor4Power = val - (currentRollOffset + RollCorrection) - (currentPitchOffset + PitchCorrection) - (currentYawOffset + YawCorrection);

        Log.d("power", "1: " + motor1Power + ", 2: " + motor2Power + ", 3: " + motor3Power + ", 4: " + motor4Power);
        sendSpeedVectors(motor1Power, motor2Power, motor3Power, motor4Power);
    }

    public void updateOrientation(int PitchOffset, int RollOffset, int YawOffset) {
        if(PitchOffset > -999) {
            currentPitchOffset = PitchOffset;
        }
        if(RollOffset > -999) {
            currentRollOffset = RollOffset;
        }
        if(YawOffset > -999) {
            currentYawOffset = YawOffset;
        }

        if(PitchOffset < 0) {
            pitchForward.setProgress(0);
            pitchBackward.setProgress(-currentPitchOffset);
        } else {
            pitchForward.setProgress(currentPitchOffset);
            pitchBackward.setProgress(0);
        }

        if(RollOffset < 0) {
            rollRight.setProgress(0);
            rollLeft.setProgress(-currentRollOffset);
        } else {
            rollRight.setProgress(currentRollOffset);
            rollLeft.setProgress(0);
        }

        if(YawOffset < 0) {
            yawRight.setProgress(0);
            yawLeft.setProgress(-currentYawOffset);
        } else {
            yawRight.setProgress(currentYawOffset);
            yawLeft.setProgress(0);
        }

        if(currentYawOffset == 0 && currentRollOffset == 0 && currentPitchOffset == 0) {
            Moving = false;
        } else {
            Moving = true;
        }

        updateThrottle(currentTrottle);
    }

    public void updateOrientation(int PitchOffset, int RollOffset) {
        updateOrientation(PitchOffset, RollOffset, -999);
    }

    public void updateOrientation(int YawOffset) {
        updateOrientation(-999, -999, YawOffset);
    }

    public void updateLEDStatus(boolean on, int color) {
        sendCommand((byte) 5, (byte) (on ? 0 : 1), 1);
        if(on) {
            LedColor.setBackgroundColor(color);
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            int hue = Math.round(hsv[0] * 0.708f);
            sendCommand((byte) 5, (byte) 2, hue);
        } else {
            LedColor.setBackgroundColor(Color.BLACK);
        }
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mTransactionArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.message);

        mTransactionView.setAdapter(mTransactionArrayAdapter);

        // Initialize the BluetoothChatService to perform bluetooth connections
        mFlightControllerService = new BluetoothFlightControllerService(getActivity(), mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    public void setupAtimiter() {
        SensorManager mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_PRESSURE);
        SensorEventListener sensorListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if(currentPreasure > event.values[0] - 0.5 || currentPreasure < event.values[0] + 0.5) {
                    currentPreasure = event.values[0];
                    currentAltitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, currentPreasure);

                    JSONObject msg = new JSONObject();
                    try {
                        msg.put("action", "altitude_update");
                        msg.put("value", currentAltitude);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    sendTextMessage(msg.toString());
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };
        if(sensors.size() > 0) {
            sensor = sensors.get(0);
            mSensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {

        }
    }

    public void setupGPS() {
        LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);

        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                currentLat = location.getLatitude();
                currentLong = location.getLongitude();
                JSONObject msg = new JSONObject();
                try {
                    msg.put("action", "location_update");
                    msg.put("long", currentLong);
                    msg.put("lat", currentLat);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                sendTextMessage(msg.toString());
                Log.d("location", currentLat + " x " + currentLong);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    private float currentXOffset = 0f;
    private float currentYOffset = 0f;

    private float maxOffset = 20f;
    private float multiValue = 0.3f;

    public void setupAccel() {
        SensorManager mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        Sensor mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        mSensorManager.registerListener(new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (!Moving && currentTrottle > 20) {
                    float[] filtered = new float[3];
                    lowPass(event.values, filtered);

                    float x = getAverageX(filtered[0]);
                    float y = getAverageY(filtered[1]);

                    currentXOffset += x * multiValue;
                    currentYOffset += y * multiValue;

                    if (currentXOffset > maxOffset) {
                        currentXOffset = maxOffset;
                    } else if (currentXOffset < -maxOffset) {
                        currentXOffset = -maxOffset;
                    }

                    if (currentYOffset > maxOffset) {
                        currentYOffset = maxOffset;
                    } else if (currentYOffset < -maxOffset) {
                        currentYOffset = -maxOffset;
                    }

                    if (PitchCorrection != (int) currentYOffset && RollCorrection != (int) currentXOffset) {
                        PitchCorrection = (int) currentYOffset;
                        RollCorrection = -(int) currentXOffset;

                        JSONObject msg = new JSONObject();
                        try {
                            msg.put("action", "correction_update");
                            msg.put("pitch", PitchCorrection);
                            msg.put("roll", RollCorrection);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        sendTextMessage(msg.toString());
                        updateThrottle(currentTrottle);
                    }
                } else if(!Moving && currentTrottle <= 20) {
                    PitchCorrection = 0;
                    RollCorrection = 0;

                    JSONObject msg = new JSONObject();
                    try {
                        msg.put("action", "correction_update");
                        msg.put("pitch", PitchCorrection);
                        msg.put("roll", RollCorrection);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    sendTextMessage(msg.toString());
                    updateThrottle(currentTrottle);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        }, mSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    List<Float> xCache = new ArrayList<>();
    private float getAverageX(float x) {
        if(xCache.size() > 10) {
            xCache.remove(0);
        }
        xCache.add(x);

        float xTotal = 0f;
        for(int i = 0; i < xCache.size(); i++) {
            xTotal += xCache.get(i);
        }
        return xTotal/(float)xCache.size();
    }

    List<Float> yCache = new ArrayList<>();
    private float getAverageY(float y) {
        if(yCache.size() > 10) {
            yCache.remove(0);
        }
        yCache.add(y);

        float yTotal = 0f;
        for(int i = 0; i < yCache.size(); i++) {
            yTotal += yCache.get(i);
        }
        return yTotal/(float)yCache.size();
    }

    static final float ALPHA = 0.15f;

    protected float[] lowPass( float[] input, float[] output ) {
        if ( output == null ) return input;

        for ( int i=0; i<input.length; i++ ) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }
        return output;
    }

    public void setupGyro() {
        final Handler TimeoutHandler = new Handler();
        SensorManager mSensorManager;
        Sensor mSensor;

        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        SensorEventListener sensorListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                int X = (int) (event.values[0] / 2.0f);
                int Y = (int) (event.values[1] / 2.0f);
                int Z = (int) (event.values[2] / 2.0f);
                Y = -Y;

                //Log.d("gyro", "X: " + X + " Y: " + Y + " Z: " + Z);
                if (currentPitch != Y || currentRoll != X || currentYaw != Z) {
                    if (!Moving && currentTrottle > 10) {
                        if ((PitchCorrection < MaxCorrectionValue || Y < 0) && (PitchCorrection > -MaxCorrectionValue || Y > 0)) {
                            PitchCorrection += Y;
                            if (PitchCorrection > MaxCorrectionValue) {
                                PitchCorrection = MaxCorrectionValue;
                            } else if (PitchCorrection < -MaxCorrectionValue) {
                                PitchCorrection = -MaxCorrectionValue;
                            }
                        }
                        if ((RollCorrection < MaxCorrectionValue || X < 0) && (RollCorrection > -MaxCorrectionValue || X > 0)) {
                            RollCorrection += X;
                            if (RollCorrection > MaxCorrectionValue) {
                                RollCorrection = MaxCorrectionValue;
                            } else if (RollCorrection < -MaxCorrectionValue) {
                                RollCorrection = -MaxCorrectionValue;
                            }
                        }
                        updateThrottle(currentTrottle);
                    } else if (currentTrottle <= 10) {
                        PitchCorrection = 0;
                        RollCorrection = 0;
                    }
                    currentPitch = Y;
                    currentRoll = X;
                    currentYaw = Z;

                    JSONObject msg = new JSONObject();
                    try {
                        msg.put("action", "gyro_update");
                        msg.put("x", currentRoll);
                        msg.put("y", currentPitch);
                        msg.put("z", currentYaw);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    sendTextMessage(msg.toString());
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

        if(mSensor != null) {
            mSensorManager.registerListener(sensorListener, mSensor, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            Toast.makeText(getActivity().getApplicationContext(), "No Gravity Sensor on device", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Makes this device discoverable.
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendTextMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mFlightControllerService.getState() != BluetoothFlightControllerService.STATE_CONNECTED) {
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mFlightControllerService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
        }
    }

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothFlightControllerService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            mTransactionArrayAdapter.clear();
                            JSONObject obj = new JSONObject();
                            try {
                                obj.put("action", "throttle_update");
                                obj.put("value", currentTrottle);
                                sendTextMessage(obj.toString());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            break;
                        case BluetoothFlightControllerService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothFlightControllerService.STATE_LISTEN:
                        case BluetoothFlightControllerService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    //mTransactionArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mTransactionArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    try {
                        parseMessage(new JSONObject(readMessage));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    public void parseMessage(JSONObject m) throws JSONException {
        switch(m.getString("action")) {
            case "throttle":
                updateThrottle(m.getInt("value"));
                break;
            case "led":
                updateLEDStatus(m.getBoolean("on"), m.getInt("color"));
                break;
            case "movement":
                updateOrientation(m.has("pitch") ? m.getInt("pitch") : -999, m.has("roll") ? m.getInt("roll") : -999, m.has("yaw") ? m.getInt("yaw") : -999);
            case "stream":
                //msg.obj = message.getBoolean("stream");
                break;
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }

    /**
     * Establish connection with other divice
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mFlightControllerService.connect(device, secure);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.computer, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.discoverable: {
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            }
        }
        return false;
    }

    public void sendCommand(byte command, byte target, int value) {
        byte[] buffer = new byte[3];
        if (value > 255)
            value = 255;

        buffer[0] = command;
        buffer[1] = target;
        buffer[2] = (byte) value;
        if (mOutputStream != null && buffer[1] != -1) {
            try {
                mOutputStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "write failed", e);
            }
        }
    }

    public void sendSpeedVectors(int M1, int M2, int M3, int M4) {
        byte[] buffer = new byte[5];
        buffer[0] = 4;
        buffer[1] = (byte) M1;
        buffer[2] = (byte) M2;
        buffer[3] = (byte) M3;
        buffer[4] = (byte) M4;

        if (mOutputStream != null && buffer[1] != -1) {
            try {
                mOutputStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "write failed", e);
            }
        }
    }

    private void openAccessory(UsbAccessory accessory) {
        mFileDescriptor = mUsbManager.openAccessory(accessory);
        if (mFileDescriptor != null) {
            mAccessory = accessory;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);
            Thread thread = new Thread(null, this, "Quadrocopter");
            thread.start();
            Log.d(TAG, "accessory opened");
            sendCommand((byte) 0, (byte) 0, 1);
            //enableControls(true);
        } else {
            Log.d(TAG, "accessory open fail");
        }
    }

    private void closeAccessory() {
        //enableControls(false);

        try {
            if (mFileDescriptor != null) {
                mFileDescriptor.close();
            }
        } catch (IOException e) {
        } finally {
            mFileDescriptor = null;
            mAccessory = null;
        }
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openAccessory(accessory);
                    } else {
                        Log.d(TAG, "permission denied for accessory "
                                + accessory);
                    }
                    mPermissionRequestPending = false;
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                if (accessory != null && accessory.equals(mAccessory)) {
                    closeAccessory();
                    getActivity().finish();
                }
            }
        }
    };

    @Override
    public void run() {
        int ret = 0;
        byte[] buffer = new byte[16384];
        int i;

        while (ret >= 0) {
            try {
                ret = mInputStream.read(buffer);
            } catch (IOException e) {
                break;
            }

            i = 0;
            while (i < ret) {
                int len = ret - i;

                switch (buffer[i]) {
                    case 0x1:
                        if (len >= 2) {
                            Message m = Message.obtain(mHandler, LED_STATE);
                            m.obj = buffer[i + 1] == 0x1;
                            //mHandler.sendMessage(m);
                        }
                        i += 2;
                        break;
                    case 0x2:
                        if (len >= 2) {
                            Message m = Message.obtain(mHandler, LED_HUE);
                            m.obj = (float) buffer[i + 1] / 0.708f;
                            //mHandler.sendMessage(m);
                        }
                        i += 2;
                }
            }

        }
    }
}
