package com.wapps.accellog;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Calendar;
import java.util.Locale;

import static com.wapps.accellog.MainActivity.MESSENGER_INTENT_KEY;
import static com.wapps.accellog.MainActivity.MSG_PER_SEC_STR;

public class AccelLogService extends Service implements SensorEventListener {
    public AccelLogService() {
    }

    @Override
    public void onCreate() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AccelLogServiceNeedAlwaysRunning");
        wl.acquire();
        //w1.
        prepCurrFile();
        //Toast.makeText(this,"AccelLogService created",Toast.LENGTH_SHORT);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mActivityMessenger = intent.getParcelableExtra(MESSENGER_INTENT_KEY);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        saveBufToFile();
        sensorManager.unregisterListener(this);
        wl.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private PowerManager.WakeLock wl;
    private Messenger mActivityMessenger;
    private SensorManager sensorManager;
    private long startMillis = System.currentTimeMillis() - SystemClock.elapsedRealtimeNanos()/1000000;
    private long nMeasuresTotal = 0;
    private long prevSec = 0;
    double sumAx = 0, sumAy = 0, sumAz = 0;
    int nMeasures = 0;
    private StringBuilder sb = new StringBuilder(16500);
    File currFile;
    String fileStatus = "...";

    void prepCurrFile(){
        Calendar cal = Calendar.getInstance();
        String fileName = String.format(Locale.US, "%04d%02d%02dT%02d%02d%02d.acclog.tsv",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH)+1,
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND)
        );
        //noinspection ConstantConditions
        currFile = new File(getExternalFilesDir(null).getPath(), fileName);
        sb.append("timestamp,ms\taX\taY\taZ\n");
    }

    void saveBufToFile() {
        if(sb.length()==0)
            return;
        long length;
        try {
            FileOutputStream outputStream = new FileOutputStream(currFile.getPath(), true);
            OutputStreamWriter osw = new OutputStreamWriter(outputStream);
            osw.append(sb);
            sb.setLength(0);
            osw.flush();
            length = outputStream.getChannel().size();
            osw.close();
            outputStream.close();
            fileStatus = String.format(Locale.US, "file size=%d\nfile path=%s", length, currFile.getPath());
            if(length>=4096*1024)
                prepCurrFile();
        } catch (IOException e) {
            fileStatus = e.getMessage();
        }
    }

    private void sendMessage(int messageID, @Nullable Object params) {
        if (mActivityMessenger == null) {
//            Log.d(TAG, "Service is bound, not started. There's no callback to send a message to.");
            return;
        }
        Message m = Message.obtain();
        m.what = messageID;
        m.obj = params;
        try {
            mActivityMessenger.send(m);
        } catch (RemoteException e) {
            //Log.e(TAG, "Error passing service object back to activity.");
        }
    }
    //region SensorEventListener implementation
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER)
            return;
        double ax, ay, az;
        ax = event.values[0];
        ay = event.values[1];
        az = event.values[2];
        long millis = startMillis + SystemClock.elapsedRealtimeNanos()/1000000;
        long sec = millis / 1000;
        if (prevSec != sec) {
            // update view
            double k = 1.0d / nMeasures;
            String text = String.format(Locale.US,
                    //"***** Accelerometer data Logger\n\ntotal measures=%d\nper second=%d\n\navg Ax=%.3f\navg Ay=%.3f\navg Az=%.3f\n\n%s",
                    "total measures=%d\nper second=%d\n\navg Ax=%.3f\navg Ay=%.3f\navg Az=%.3f\n\n%s",
                    nMeasuresTotal, nMeasures, sumAx * k, sumAy * k, sumAz * k, fileStatus
            );
            sendMessage(MSG_PER_SEC_STR, text);
            prevSec = sec;
            nMeasuresTotal += nMeasures;
            nMeasures = 0;
            sumAx = sumAy = sumAz = 0;
            //getApplication().getExternalFilesDir(null);
            if(sb.length()>16384)
                saveBufToFile();
        }
        sumAx += ax;
        sumAy += ay;
        sumAz += az;
        nMeasures++;
        sb.append(String.format(Locale.US,"%d\t%.3f\t%.3f\t%.3f\n",millis,ax,ay,az));

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    //endregion
}
