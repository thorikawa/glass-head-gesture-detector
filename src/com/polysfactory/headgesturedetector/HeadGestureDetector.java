package com.polysfactory.headgesturedetector;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.polysfactory.headgesturedetector.internal.Constants;

public class HeadGestureDetector {

    private static final long STATE_TIMEOUT_NSEC = 1000 * 1000 * 1000;
    private static final float MIN_MOVE_ANGULAR_VELOCITY = 1.00F;
    private static final float STABLE_ANGULAR_VELOCITY = 0.10F;

    private final SensorManager mSensorManager;
    private final SensorEventListener mSensorEventListener;
    private OnHeadGestureListener mListener;
    private float[] orientationVelocity = new float[3];

    private static enum State {
        IDLE, SHAKE_TO_RIGHT, SHAKE_BACK_TO_LEFT, SHAKE_TO_LEFT, SHAKE_BACK_TO_RIGHT, GO_DOWN, BACK_UP, GO_UP, BACK_DOWN
    }

    private State mState = State.IDLE;
    private long mLastStateChanged = -1;

    public HeadGestureDetector(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mSensorEventListener = new HeadGestureSensorEventListener();
    }

    private static final int[] REQUIRED_SENSORS = { Sensor.TYPE_GYROSCOPE };

    private static final int[] SENSOR_RATES = { SensorManager.SENSOR_DELAY_NORMAL };

    public void start() {
        for (int i = 0; i < REQUIRED_SENSORS.length; i++) {
            Sensor sensor = mSensorManager.getDefaultSensor(REQUIRED_SENSORS[i]);
            if (sensor != null) {
                Log.d(Constants.TAG, "registered:" + sensor.getName());
                mSensorManager.registerListener(mSensorEventListener, sensor, SENSOR_RATES[i]);
            }
        }
    }

    public void stop() {
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    public void setOnHeadGestureListener(OnHeadGestureListener listener) {
        this.mListener = listener;
    }

    private static boolean isStable(float[] orientationVelocity) {
        if (Math.abs(orientationVelocity[0]) < STABLE_ANGULAR_VELOCITY
                && Math.abs(orientationVelocity[1]) < STABLE_ANGULAR_VELOCITY
                && Math.abs(orientationVelocity[2]) < STABLE_ANGULAR_VELOCITY) {
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

    private class HeadGestureSensorEventListener implements SensorEventListener {

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
                int maxVelocityIndex = maxAbsIndex(orientationVelocity);
                if (isStable(orientationVelocity)) {
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
    }

    public interface OnHeadGestureListener {
        public void onNod();

        public void onShakeToLeft();

        public void onShakeToRight();
    }
}
