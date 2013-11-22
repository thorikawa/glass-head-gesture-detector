package com.polysfactory.headgesturedetector;

import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class HeadGestureDetector implements SensorEventListener {
    private static final int MATRIX_SIZE = 16;
    private float[] inR = new float[MATRIX_SIZE];
    private float[] outR = new float[MATRIX_SIZE];
    private float[] I = new float[MATRIX_SIZE];

    private float[] orientationValues = new float[3];
    private float[] magneticValues = new float[3];
    private float[] accelerometerValues = new float[3];

    private SensorManager mSensorManager;

    private OnHeadGestureListener mListener;
    private OrientationEvent mPreviousOrientationEvent;

    static enum State {
        IDLE, SHAKE_TO_RIGHT, SHAKE_BACK_TO_LEFT, SHAKE_TO_LEFT, SHAKE_BACK_TO_RIGHT, GO_DOWN, BACK_UP, GO_UP, BACK_DOWN
    }

    private State mState = State.IDLE;
    private long mLastStateChanged = -1;
    private static final long STATE_TIMEOUT_NSEC = 1000 * 1000 * 1000;
    private static final int SENSOR_RATE = SensorManager.SENSOR_DELAY_GAME;

    private static class OrientationEvent {
        private float[] orientationValues;
        private long timestamp;

        private OrientationEvent(float[] orientationValues, long timestamp) {
            this.orientationValues = orientationValues;
            this.timestamp = timestamp;
        }
    }

    public HeadGestureDetector(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public void start() {
        List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);

        for (Sensor sensor : sensors) {
            if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                mSensorManager.registerListener(this, sensor, SENSOR_RATE);
            }

            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                mSensorManager.registerListener(this, sensor, SENSOR_RATE);
            }
        }
    }

    public void stop() {
        mSensorManager.unregisterListener(this);
    }

    public void setOnHeadGestureListener(OnHeadGestureListener listener) {
        this.mListener = listener;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            if (BuildConfig.DEBUG) {
                // Log.w(Constants.TAG, "Unreliable event...");
            }
        }

        int sensorType = event.sensor.getType();
        if (sensorType == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticValues = event.values.clone();
            return;
        }

        // If sensorType is not TYPE_MAGNETIC_FIELD, then it is surely
        // TYPE_ACCELEROMETER.
        accelerometerValues = event.values.clone();

        if (magneticValues == null || accelerometerValues == null) {
            return;
        }

        // state timeout check
        if (event.timestamp - mLastStateChanged > STATE_TIMEOUT_NSEC && mState != State.IDLE) {
            Log.d(Constants.TAG, "state timeouted");
            mLastStateChanged = event.timestamp;
            mState = State.IDLE;
        }

        SensorManager.getRotationMatrix(inR, I, accelerometerValues, magneticValues);
        SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR);
        SensorManager.getOrientation(outR, orientationValues);

        if (BuildConfig.DEBUG) {
            // Log.d(Constants.TAG, Arrays.toString(orientationValues));
        }

        float[] orientationVelocity = null;
        OrientationEvent currentOrientationEvent = new OrientationEvent(orientationValues.clone(), event.timestamp);
        if (mPreviousOrientationEvent == null) {
            mPreviousOrientationEvent = currentOrientationEvent;
            return;
        }

        // calc orientation velocity
        orientationVelocity = getOrientationVelocity(mPreviousOrientationEvent, currentOrientationEvent);
        if (BuildConfig.DEBUG) {
            // Log.d(Constants.TAG, "V:" + Arrays.toString(orientationVelocity));
        }

        // check if glass is put on
        if (!isPutOn(orientationValues, orientationVelocity)) {
            if (BuildConfig.DEBUG) {
                Log.d(Constants.TAG, "Looks like glass is off?");
            }
        }

        int maxVelocityIndex = maxAbsIndex(orientationVelocity);
        if (isStable(orientationValues, orientationVelocity)) {
            // Log.d(Constants.TAG, "isStable");
        } else if (maxVelocityIndex == 1) {
            if (orientationVelocity[1] > minShakeSpeed) {
                if (mState == State.IDLE) {
                    Log.d(Constants.TAG, "isNod");
                    mState = State.GO_DOWN;
                    mLastStateChanged = event.timestamp;
                }
            }
        } else if (maxVelocityIndex == 0) {
            if (orientationVelocity[0] > minShakeSpeed) {
                if (mState == State.IDLE) {
                    Log.d(Constants.TAG, Arrays.toString(orientationValues));
                    Log.d(Constants.TAG, "V:" + Arrays.toString(orientationVelocity));
                    Log.d(Constants.TAG, "ShakingToRight");
                    mState = State.SHAKE_TO_RIGHT;
                    mLastStateChanged = event.timestamp;
                }
            } else if (orientationVelocity[0] < -minShakeSpeed) {
                if (mState == State.IDLE) {
                    Log.d(Constants.TAG, Arrays.toString(orientationValues));
                    Log.d(Constants.TAG, "V:" + Arrays.toString(orientationVelocity));
                    Log.d(Constants.TAG, "ShakingToLeft");
                    mState = State.SHAKE_TO_LEFT;
                    mLastStateChanged = event.timestamp;
                }
            }
        }

        mPreviousOrientationEvent = currentOrientationEvent;
    }

    private static final float minShakeSpeed = 0.01F;

    private static final float maxStableRadian = 0.10F;

    private static final float maxPutOnPitchRadian = 0.45F;

    private static final float maxPutOnRollRadian = 0.75F;

    private static final float STABLE_ORIENTATION_VELOCITY = 0.0005F;

    private static boolean isStable(float[] orientationValues, float[] orientationVelocity) {
        if (Math.abs(orientationValues[1]) < maxStableRadian
                && Math.abs(orientationVelocity[0]) < STABLE_ORIENTATION_VELOCITY
                && Math.abs(orientationVelocity[1]) < STABLE_ORIENTATION_VELOCITY
                && Math.abs(orientationVelocity[2]) < STABLE_ORIENTATION_VELOCITY) {
            return true;
        }
        return false;
    }

    private static boolean isPutOn(float[] orientationValues, float[] orientationVelocity) {
        if (orientationValues[1] < maxPutOnPitchRadian && Math.abs(orientationValues[2]) < maxPutOnRollRadian) {
            return true;
        }
        return false;
    }

    private static float[] getOrientationVelocity(OrientationEvent e1, OrientationEvent e2) {
        if (e1.timestamp > e2.timestamp) {
            return getOrientationVelocity(e2, e1);
        }

        long diff = e2.timestamp - e1.timestamp;
        diff /= 1000000; // msec
        if (diff == 0) {
            if (BuildConfig.DEBUG) {
                Log.d(Constants.TAG, "Time is too close: " + e1.timestamp + ", " + e2.timestamp);
            }
            return null;
        }

        float[] velocity = new float[3];
        for (int i = 0; i < 3; i++) {
            // TODO normalize orientation value (what if e1=3.1 and e2=-3.1?)
            velocity[i] = (e2.orientationValues[i] - e1.orientationValues[i]) / diff;
        }
        return velocity;
    }

    private static int maxAbsIndex(float[] array) {
        int n = array.length;
        float maxValue = Float.MIN_VALUE;
        int maxIndex = -1;
        for (int i = 0; i < n; i++) {
            float val = Math.abs(array[i]);
            if (val > maxValue) {
                maxValue = val;
                maxIndex = i;
            }
        }
        return maxIndex;
    }
}
