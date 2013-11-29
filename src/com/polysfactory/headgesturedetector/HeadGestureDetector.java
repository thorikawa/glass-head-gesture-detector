package com.polysfactory.headgesturedetector;

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
    private float[] orientationVelocity = new float[3];

    private SensorManager mSensorManager;

    private OnHeadGestureListener mListener;

    static enum State {
        IDLE, SHAKE_TO_RIGHT, SHAKE_BACK_TO_LEFT, SHAKE_TO_LEFT, SHAKE_BACK_TO_RIGHT, GO_DOWN, BACK_UP, GO_UP, BACK_DOWN
    }

    private State mState = State.IDLE;
    private long mLastStateChanged = -1;
    private static final long STATE_TIMEOUT_NSEC = 1000 * 1000 * 1000;

    public HeadGestureDetector(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    private static final int[] REQUIRED_SENSORS = { Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE };

    private static final int[] SENSOR_RATES = { SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_NORMAL,
            SensorManager.SENSOR_DELAY_NORMAL };

    public void start() {
        for (int i = 0; i < REQUIRED_SENSORS.length; i++) {
            int sensor_type = REQUIRED_SENSORS[i];
            Sensor sensor = null;
            List<Sensor> sensors = mSensorManager.getSensorList(sensor_type);
            if (sensors.size() > 1) {
                // Google Glass has two gyroscopes: "MPL Gyroscope" and "Corrected Gyroscope Sensor". Try the later one.
                sensor = sensors.get(1);
            } else {
                sensor = sensors.get(0);
            }
            Log.d(Constants.TAG, "registered:" + sensor.getName());
            mSensorManager.registerListener(this, sensor, SENSOR_RATES[i]);
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
            // Log.w(Constants.TAG, "Unreliable event...");
        }

        int sensorType = event.sensor.getType();

        if (sensorType == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticValues = event.values.clone();
            return;
        }

        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            accelerometerValues = event.values.clone();
            SensorManager.getRotationMatrix(inR, I, accelerometerValues, magneticValues);
            SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR);
            SensorManager.getOrientation(outR, orientationValues);
            return;
        }

        if (sensorType == Sensor.TYPE_GYROSCOPE) {
            if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                // Log.w(Constants.TAG, "Unreliable gyroscope event...");
                // return;
            }

            orientationVelocity = event.values.clone();

            // state timeout check
            if (event.timestamp - mLastStateChanged > STATE_TIMEOUT_NSEC && mState != State.IDLE) {
                Log.d(Constants.TAG, "state timeouted");
                mLastStateChanged = event.timestamp;
                mState = State.IDLE;
            }

            // Log.d(Constants.TAG, Arrays.toString(orientationValues));
            // Log.d(Constants.TAG, "V:" + Arrays.toString(orientationVelocity));

            // check if glass is put on
            if (!isPutOn(orientationValues, orientationVelocity)) {
                Log.d(Constants.TAG, "Looks like glass is off?");
            }

            int maxVelocityIndex = maxAbsIndex(orientationVelocity);
            if (!isStable(orientationValues, orientationVelocity)) {
                // Log.d(Constants.TAG, "V:" + Arrays.toString(orientationVelocity));
            }
            if (isStable(orientationValues, orientationVelocity)) {
                // Log.d(Constants.TAG, "isStable");
            } else if (maxVelocityIndex == 0) {
                if (orientationVelocity[0] < -MIN_MOVE_ANGULAR_VELOCITY) {
                    if (mState == State.IDLE) {
                        // Log.d(Constants.TAG, "isNod");
                        mState = State.GO_DOWN;
                        mLastStateChanged = event.timestamp;
                        if (mListener != null) {
                            mListener.onNod();
                        }
                    }
                }
            } else if (maxVelocityIndex == 1) {
                if (orientationVelocity[1] < -MIN_MOVE_ANGULAR_VELOCITY) {
                    if (mState == State.IDLE) {
                        // Log.d(Constants.TAG, Arrays.toString(orientationValues));
                        // Log.d(Constants.TAG, "V:" + Arrays.toString(orientationVelocity));
                        mState = State.SHAKE_TO_RIGHT;
                        mLastStateChanged = event.timestamp;
                        if (mListener != null) {
                            mListener.onShakeToRight();
                        }
                    }
                } else if (orientationVelocity[1] > MIN_MOVE_ANGULAR_VELOCITY) {
                    if (mState == State.IDLE) {
                        // Log.d(Constants.TAG, Arrays.toString(orientationValues));
                        // Log.d(Constants.TAG, "V:" + Arrays.toString(orientationVelocity));
                        mState = State.SHAKE_TO_LEFT;
                        mLastStateChanged = event.timestamp;
                        if (mListener != null) {
                            mListener.onShakeToLeft();
                        }
                    }
                }
            }
        }
    }

    private static final float MIN_MOVE_ANGULAR_VELOCITY = 1.00F;

    private static final float MAX_STABLE_RADIAN = 0.10F;

    private static final float MAX_PUT_ON_PITCH_RADIAN = 0.45F;

    private static final float MAX_PUT_ON_ROLL_RADIAN = 0.75F;

    private static final float STABLE_ANGULAR_VELOCITY = 0.10F;

    private static boolean isStable(float[] orientationValues, float[] orientationVelocity) {
        if (Math.abs(orientationValues[1]) < MAX_STABLE_RADIAN
                && Math.abs(orientationVelocity[0]) < STABLE_ANGULAR_VELOCITY
                && Math.abs(orientationVelocity[1]) < STABLE_ANGULAR_VELOCITY
                && Math.abs(orientationVelocity[2]) < STABLE_ANGULAR_VELOCITY) {
            return true;
        }
        return false;
    }

    private static boolean isPutOn(float[] orientationValues, float[] orientationVelocity) {
        if (orientationValues[1] < MAX_PUT_ON_PITCH_RADIAN && Math.abs(orientationValues[2]) < MAX_PUT_ON_ROLL_RADIAN) {
            return true;
        }
        return false;
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
