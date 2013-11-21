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
    private long mPreviousStableOrientation;
    private long mPreviousNodOrientation;
    private OrientationEvent mPreviousOrientationEvent;

    private class OrientationEvent {
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
                // mSensorManager.registerListener(this, sensor,
                // SensorManager.SENSOR_DELAY_UI);
                mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            }

            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                // mSensorManager.registerListener(this, sensor,
                // SensorManager.SENSOR_DELAY_UI);
                mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
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

        SensorManager.getRotationMatrix(inR, I, accelerometerValues, magneticValues);

        SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR);
        SensorManager.getOrientation(outR, orientationValues);

        if (BuildConfig.DEBUG) {
            Log.d(Constants.TAG, Arrays.toString(orientationValues));
        }

        float[] velocity = null;
        OrientationEvent currentOrientationEvent = new OrientationEvent(orientationValues.clone(), event.timestamp);
        if (mPreviousOrientationEvent != null) {
            velocity = getVelocity(mPreviousOrientationEvent, currentOrientationEvent);
        }

        // if (velocity == null) {
        if (BuildConfig.DEBUG) {
            Log.d(Constants.TAG, "V:" + Arrays.toString(velocity));
        }
        // }

        // check if glass is put on
        if (!isPutOn(orientationValues)) {
            if (BuildConfig.DEBUG) {
                Log.d(Constants.TAG, "Looks like glass is off?");
            }
            mPreviousStableOrientation = -1;
            mPreviousNodOrientation = -1;
        }

        if (isStable(orientationValues)) {
            Log.d(Constants.TAG, "isStable");
            if (isConsideredNod(event.timestamp, mPreviousStableOrientation, mPreviousNodOrientation)) {
                mPreviousNodOrientation = -1;
                if (mListener != null) {
                    mListener.onNod();
                }
            }
            mPreviousStableOrientation = event.timestamp;
        } else if (isNod(orientationValues)) {
            Log.d(Constants.TAG, "isNod");
            mPreviousNodOrientation = event.timestamp;
        } else if (isShakingToRight(velocity)) {
            Log.d(Constants.TAG, "ShakingToRight");
        } else if (isShakingToLeft(velocity)) {
            Log.d(Constants.TAG, "ShakingToLeft");
        }

        mPreviousOrientationEvent = currentOrientationEvent;
    }

    private static final float minNodSpeed = 0.001F;

    private static final float minShakeSpeed = 0.001F;

    private static final float maxStableRadian = 0.10F;

    private static final float nodBorderRadian = 0.20F;

    private static final float maxPutOnPitchRadian = 0.45F;

    private static final float maxPutOnRollRadian = 0.75F;

    private static boolean isStable(float[] orientationValues) {
        // TODO add more criterias
        if (Math.abs(orientationValues[1]) < maxStableRadian) {
            return true;
        }
        return false;
    }

    private static boolean isShakingToLeft(float[] velocity) {
        if (velocity[2] > minShakeSpeed) {
            return true;
        }
        return false;
    }

    private static boolean isShakingToRight(float[] velocity) {
        if (velocity[2] < -minShakeSpeed) {
            return true;
        }
        return false;
    }

    private static boolean isNod(float[] orientationValues) {
        // TODO use velocity
        if (orientationValues[1] > nodBorderRadian) {
            return true;
        }
        return false;
    }

    private static boolean isPutOn(float[] orientationValues) {
        if (orientationValues[1] < maxPutOnPitchRadian && Math.abs(orientationValues[2]) < maxPutOnRollRadian) {
            return true;
        }
        return false;
    }

    private boolean isConsideredNod(long currentOrientation, long previousStable, long previousNod) {
        if (currentOrientation < 0 || previousStable < 0 || previousNod < 0) {
            return false;
        }
        if (previousNod <= previousStable || currentOrientation <= previousStable || currentOrientation <= previousNod) {
            return false;
        }
        if (currentOrientation - previousStable > 500000000) {
            if (BuildConfig.DEBUG) {
                Log.d(Constants.TAG, "timeout:" + currentOrientation + "," + previousStable + ", "
                        + (currentOrientation - previousStable) + " nanosecs ellapsed.");
            }
            return false;
        }
        return true;
    }

    private static float[] getVelocity(OrientationEvent e1, OrientationEvent e2) {
        if (e1.timestamp > e2.timestamp) {
            return getVelocity(e2, e1);
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
}
