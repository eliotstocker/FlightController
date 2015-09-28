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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.larswerkman.holocolorpicker.ColorPicker;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class BluetoothControlFragment extends Fragment {

    private static final String TAG = "ControlFragment";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

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

    private TextView altitude;
    private TextView longitude;
    private TextView latitude;
    private TextView pitchCorrection;
    private TextView rollCorrection;
    public JoystickView LeftStick;
    public JoystickView RightStick;
    public VerticalSeekBar throttle;

    private boolean showMessage = true;
    private Handler handler;

    private int ThrottleCache = 0;

    private Handler timeoutHandler;
    private boolean blocking = false;

    private float SpeedDivider = 10.0f;

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

        handler = new Handler();
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.control_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        timeoutHandler = new Handler();

        throttle = (VerticalSeekBar) view.findViewById(R.id.throttle);
        final ColorPicker ledColor = (ColorPicker) view.findViewById(R.id.ledColor);
        final Switch ledOn = (Switch) view.findViewById(R.id.ledSwitch);

        throttle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ThrottleCache = progress;
                if (!blocking) {
                    JSONObject msg = new JSONObject();
                    try {
                        msg.put("action", "throttle");
                        msg.put("value", progress);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    sendMessage(msg.toString());
                    timeoutHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            blocking = false;
                            JSONObject msg = new JSONObject();
                            try {
                                msg.put("action", "throttle");
                                msg.put("value", ThrottleCache);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            sendMessage(msg.toString());
                        }
                    }, 150);
                    blocking = true;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                JSONObject msg = new JSONObject();
                try {
                    msg.put("action", "throttle");
                    msg.put("value", seekBar.getProgress());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                sendMessage(msg.toString());
            }
        });

        ledColor.setShowOldCenterColor(false);
        ledColor.setOnColorChangedListener(new ColorPicker.OnColorChangedListener() {
            @Override
            public void onColorChanged(int i) {
                if (!blocking) {
                    JSONObject msg = new JSONObject();
                    try {
                        msg.put("action", "led");
                        msg.put("color", i);
                        msg.put("on", ledOn.isChecked());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    sendMessage(msg.toString());
                    timeoutHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            blocking = false;
                        }
                    }, 150);
                    blocking = true;
                }
            }
        });

        ledOn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                JSONObject msg = new JSONObject();
                try {
                    msg.put("action", "led");
                    msg.put("color", ledColor.getColor());
                    msg.put("on", isChecked);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                sendMessage(msg.toString());
            }
        });

        longitude = (TextView) view.findViewById(R.id.longitude);
        latitude = (TextView) view.findViewById(R.id.latitude);
        altitude = (TextView) view.findViewById(R.id.altitude);
        pitchCorrection = (TextView) view.findViewById(R.id.picth_correction);
        rollCorrection = (TextView) view.findViewById(R.id.roll_correction);
        LeftStick = (JoystickView) view.findViewById(R.id.left_stick);
        RightStick = (JoystickView) view.findViewById(R.id.right_stick);

        LeftStick.setOnJoystickMoveListener(new JoystickView.OnJoystickMoveListener() {
            @Override
            public void onValueChanged(int angle, int power, int direction) {
                JSONObject msg = new JSONObject();
                try {
                    msg.put("action", "movement");
                    msg.put("pitch", 0);
                    msg.put("roll", 0);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                if(power == 0) {
                    sendMessage(msg.toString());
                } else {
                    if (angle > -10 && angle < 10) {
                        //just pitch forwards
                        try {
                            msg.put("pitch", Math.round((double)power/SpeedDivider));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else if (angle < -170 || angle > 170) {
                        //just pitch backwards
                        try {
                            msg.put("pitch", - Math.round((double)power/SpeedDivider));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else if (angle < -80 && angle > -100) {
                        //just roll Left
                        try {
                            msg.put("roll", - Math.round((double)power/SpeedDivider));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else if (angle > 80 && angle < 100) {
                        //just roll right
                        try {
                            msg.put("roll", Math.round((double)power/SpeedDivider));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        // combination
                        int pitch = 0;
                        int roll = 0;
                        if(angle > 0 && angle < 90) {
                            //forwards and right
                            roll = Math.round(power * (float)angle/90.0f);
                            pitch = Math.round(power - roll);
                        } else if(angle > 90 && angle < 180) {
                            angle = angle - 90;
                            //backwards and right
                            pitch = Math.round(power * (float)angle/90.0f);
                            roll = Math.round(power - pitch);
                            pitch = -pitch;
                        } else if(angle < 0 && angle > -90) {
                            angle = - angle;
                            //forwards and left
                            roll = Math.round(power * (float)angle/90.0f);
                            pitch = Math.round(power - roll);
                            roll = -roll;
                        } else {
                            angle = (- angle) - 90;
                            // backwards and right
                            pitch = Math.round(power * (float)angle/90.0f);
                            roll = -Math.round(power - pitch);
                            pitch = -pitch;
                        }
                        try {
                            msg.put("pitch", Math.round((double)pitch/SpeedDivider));
                            msg.put("roll", Math.round((double)roll/SpeedDivider));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    sendMessage(msg.toString());
                }
            }
        }, 150);

        RightStick.setOnJoystickMoveListener(new JoystickView.OnJoystickMoveListener() {
            @Override
            public void onValueChanged(int angle, int power, int direction) {
                JSONObject msg = new JSONObject();
                try {
                    msg.put("action", "movement");
                    msg.put("yaw", 0);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                int yaw = 0;
                int pitch = 0;
                if(angle > 0 && angle < 90) {
                    //forwards and right
                    yaw = Math.round(power * (float)angle/90.0f);
                    pitch = Math.round(power - yaw);
                } else if(angle > 90 && angle < 180) {
                    angle = angle - 90;
                    //backwards and right
                    pitch = Math.round(power * (float)angle/90.0f);
                    yaw = Math.round(power - pitch);
                    pitch = -pitch;
                } else if(angle < 0 && angle > -90) {
                    angle = - angle;
                    //forwards and left
                    yaw = Math.round(power * (float)angle/90.0f);
                    pitch = Math.round(power - yaw);
                    yaw = -yaw;
                } else {
                    angle = (- angle) - 90;
                    // backwards and right
                    pitch = Math.round(power * (float)angle/90.0f);
                    yaw = -Math.round(power - pitch);
                    pitch = -pitch;
                }
                try {
                    msg.put("yaw", Math.round((double)yaw/SpeedDivider));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                sendMessage(msg.toString());
            }
        }, 150);
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the BluetoothChatService to perform bluetooth connections
        mFlightControllerService = new BluetoothFlightControllerService(getActivity(), mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
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
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        try {
            if (mFlightControllerService.getState() != BluetoothFlightControllerService.STATE_CONNECTED) {
                if(showMessage) {
                    Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                    showMessage = false;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showMessage = true;
                        }
                    }, 3000);
                }
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
        } catch(Exception e) {

        }
    }

    /**
     * The action listener for the EditText widget, to listen for the return key
     */
    private TextView.OnEditorActionListener mWriteListener
            = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            return true;
        }
    };

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
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
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

    public void parseMessage(JSONObject m) {
        try {
            switch(m.getString("action")) {
                case "altitude_update":
                    updateAltitude(m.getDouble("value"));
                    break;
                case "location_update":
                    updateLocation(m.getDouble("long"), m.getDouble("lat"));
                    break;
                case "throttle_update":
                    throttle.setProgress(m.getInt("value"));
                    break;
                case "correction_update":
                    updateCorrection(m.getInt("pitch"), m.getInt("roll"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void updateLocation(double Long, double Lat) {
        longitude.setText(""+Long);
        latitude.setText(""+Lat);
    }

    public void updateCorrection(int pitch, int roll) {
        pitchCorrection.setText(""+pitch);
        rollCorrection.setText(""+roll);
    }

    public void updateAltitude(double Alt) {
        altitude.setText(String.format("%.1f", Alt));
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
        inflater.inflate(R.menu.controller, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }
            case R.id.insecure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            }
            case R.id.discoverable: {
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            }
        }
        return false;
    }

}
