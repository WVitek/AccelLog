package com.wapps.accellog;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
/*
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
*/
        prepCurrFile();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private long startMillis = System.currentTimeMillis() - SystemClock.elapsedRealtimeNanos()/1000000;
    private long nMeasuresTotal = 0;
    private long prevSec = 0;
    double sumAx = 0, sumAy = 0, sumAz = 0;
    int nMeasures = 0;

    private StringBuilder sb = new StringBuilder(16500);

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER)
            return;
        double ax, ay, az;
        ax = event.values[0];
        ay = event.values[1];
        az = event.values[2];
        long millis = startMillis + SystemClock.elapsedRealtimeNanos()/1000000;
        //long millis = System.currentTimeMillis();
        long sec = millis / 1000;
        if (prevSec != sec) {
            // update view
            double k = 1.0d / nMeasures;
            String text = String.format(Locale.US,
                    "***** Accelerometer data Logger\n\ntotal measures=%d\nper second=%d\n\navg Ax=%.3f\navg Ay=%.3f\navg Az=%.3f\n\n%s",
                    nMeasuresTotal, nMeasures, sumAx * k, sumAy * k, sumAz * k, fileStatus
            );
            ((TextView) findViewById(R.id.textView)).setText(text);
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
        sb.append(String.format("%d\t%.3f\t%.3f\t%.3f\n",millis,ax,ay,az));
    }

    File currFile;

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
        currFile = new File(getExternalFilesDir(null).getPath(), fileName);
        sb.append("timestamp,ms\taX\taY\taZ\n");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveBufToFile();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveBufToFile();
    }

    String fileStatus = "...";

    void saveBufToFile() {
        if(sb.length()==0)
            return;
        long length = 0;
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

}
