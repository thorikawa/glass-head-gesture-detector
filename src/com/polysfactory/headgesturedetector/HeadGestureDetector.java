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

    public HeadGestureDetector(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public void start() {
        List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);

        for (Sensor sensor : sensors) {
            if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
            }

            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
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
                Log.w(Constants.TAG, "Unreliable event...");
            }
        }

        switch (event.sensor.getType()) {
        case Sensor.TYPE_MAGNETIC_FIELD:
            magneticValues = event.values.clone();
            break;
        case Sensor.TYPE_ACCELEROMETER:
            accelerometerValues = event.values.clone();
            break;
        }

        if (magneticValues != null && accelerometerValues != null) {

            SensorManager.getRotationMatrix(inR, I, accelerometerValues, magneticValues);

            SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR);
            SensorManager.getOrientation(outR, orientationValues);

            if (BuildConfig.DEBUG) {
                Log.d(Constants.TAG, Arrays.toString(orientationValues));
            }

            if (!isPutOn(orientationValues)) {
                if (BuildConfig.DEBUG) {
                    Log.d(Constants.TAG, "Looks like glass is off?");
                }
                mPreviousStableOrientation = -1;
                mPreviousNodOrientation = -1;
            }

            if (isStable(orientationValues)) {
                if (isConsideredNod(event.timestamp, mPreviousStableOrientation, mPreviousNodOrientation)) {
                    mPreviousNodOrientation = -1;
                    if (mListener != null) {
                        mListener.onNod();
                    }
                }
                mPreviousStableOrientation = event.timestamp;
            } else if (isNod(orientationValues)) {
                mPreviousNodOrientation = event.timestamp;
            }
        }
    }

    private static final float maxStableRadian = 0.10F;

    private static final float nodBorderRadian = 0.20F;

    private static final float maxPutOnPitchRadian = 0.45F;

    private static final float maxPutOnRollRadian = 0.75F;

    private static boolean isStable(float[] orientationValues) {
        if (Math.abs(orientationValues[1]) < maxStableRadian) {
            return true;
        }
        return false;
    }

    private static boolean isNod(float[] orientationValues) {
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
}
