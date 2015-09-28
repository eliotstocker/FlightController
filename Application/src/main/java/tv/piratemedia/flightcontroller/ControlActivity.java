/*
* Copyright 2013 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/


package tv.piratemedia.flightcontroller;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;

/**
 * A simple launcher activity containing a summary sample description, sample log and a custom
 * {@link android.support.v4.app.Fragment} which can display a view.
 * <p>
 * For devices with displays with a width of 720dp or greater, the sample log is always visible,
 * on other devices it's visibility is controlled by an item on the Action Bar.
 */
public class ControlActivity extends FragmentActivity {

    public static final String TAG = "MainActivity";

    // Whether the Log Fragment is currently shown
    private boolean mLogShown;
    private BluetoothControlFragment fragment;
    private float[] lStickCache = new float[2];
    private float[] rStickCache = new float[2];
    private float[] triggerCache = new float[2];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            fragment = new BluetoothControlFragment();
            transaction.replace(R.id.sample_content_fragment, fragment);
            transaction.commit();
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) ==
                InputDevice.SOURCE_JOYSTICK &&
                event.getAction() == MotionEvent.ACTION_MOVE) {

            final int historySize = event.getHistorySize();

            // Process the movements starting from the
            // earliest historical position in the batch
            for (int i = 0; i < historySize; i++) {
                // Process the event at historical position i
                processJoystickInput(event, i);
            }

            // Process the current movement sample in the batch (position -1)
            processJoystickInput(event, -1);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private void processJoystickInput(MotionEvent event,
                                      int historyPos) {

        InputDevice mInputDevice = event.getDevice();

        float x = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_X, historyPos);

        float y = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_Y, historyPos);

        float z = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_Z, historyPos);

        float rz = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_RZ, historyPos);

        float l = getTriggerValue(event, mInputDevice,
                MotionEvent.AXIS_LTRIGGER, historyPos);

        float r = getTriggerValue(event, mInputDevice,
                MotionEvent.AXIS_RTRIGGER, historyPos);

        if(lStickCache[0] != x || lStickCache[1] != y) {
            fragment.LeftStick.setPosition(x, y);
            lStickCache[0] = x;
            lStickCache[1] = y;
        }

        if(rStickCache[0] != z || rStickCache[1] != rz) {
            fragment.RightStick.setPosition(z, rz);
            rStickCache[0] = z;
            rStickCache[1] = rz;
        }

        if(0f != l || 0f != r) {
            int range = fragment.throttle.getMax();
            float throttle = (float) fragment.throttle.getProgress() / range;
            if(l > 0) {
                if(l > triggerCache[0]) {
                    throttle += (l - triggerCache[0]) / 2f;
                }
            } else {
                if(r > triggerCache[1]) {
                    throttle -= (r - triggerCache[1]) / 2f;
                }
            }

            fragment.throttle.setProgress((int) (throttle * (float) range));
            fragment.throttle.invalidate();
        }
        triggerCache[0] = l;
        triggerCache[1] = r;
    }

    private static float getCenteredAxis(MotionEvent event,
                                         InputDevice device, int axis, int historyPos) {
        final InputDevice.MotionRange range =
                device.getMotionRange(axis, event.getSource());

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        if (range != null) {
            final float flat = range.getFlat();
            final float value =
                    historyPos < 0 ? event.getAxisValue(axis):
                            event.getHistoricalAxisValue(axis, historyPos);

            // Ignore axis values that are within the 'flat' region of the
            // joystick axis center.
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }

    private static float getTriggerValue(MotionEvent event,
                                         InputDevice device, int axis, int historyPos) {
        final InputDevice.MotionRange range =
                device.getMotionRange(axis, event.getSource());
        if (range != null) {
            final float value =
                    historyPos < 0 ? event.getAxisValue(axis):
                            event.getHistoricalAxisValue(axis, historyPos);
            return value / range.getRange();
        }
        return 0;
    }
}
