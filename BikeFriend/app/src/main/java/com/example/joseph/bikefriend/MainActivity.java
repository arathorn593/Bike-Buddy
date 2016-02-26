package com.example.joseph.bikefriend;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private final int NUM_STATES = 2;
    private final int DIST = 0;
    private final int MAX_SPEED = 1;
    private int state = 0;

    private String stateNames[] = {"Dist", "Max"};
    private String stateUnits[] = {"mi", "mph"};
    private AudioRecord recorder = null;
    private Thread audioThread = null;
    private TextView topText = null;
    private TextView bottomText = null;
    private TextView bottomLabel = null;
    private TextView bottomUnits = null;
    private boolean isRecording = false;

    private double rpm = 0;
    private long total = 0;
    private int pointsRead = 0;

    private double speed = 0;
    private double dist = 0;
    private double maxSpeed = 0;
    private double tireCirc = 86.3; //in; tire circumference

    private int SAMPLE_RATE = 8000;
    private short AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private short AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    int bufferSize = 0;

    private final int DATA_BUF_LEN = 2;
    private int[] dataBuf;

    //private static int[] mSampleRates = new int[] { 8000, 11025, 16000, 22050, 44100 };
    public AudioRecord findAudioRecord() {
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_FORMAT) +
                (SAMPLE_RATE * 4);

        if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
            Log.d("MainActivity", "buffer size good");
            recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE, AUDIO_CHANNEL,
                    AUDIO_FORMAT, bufferSize);

            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                return recorder;
            }
        }

        return null;
    }

    private void startRecording () {
        if(recorder == null) {
            Button startButton = (Button) findViewById(R.id.leftButton);
            startButton.setText("STOP");

            recorder = findAudioRecord();
            recorder.startRecording();
            isRecording = true;

            audioThread = new Thread(new Runnable() {
                public void run() {
                    analyzeAudio();
                }
            }, "AudioRecorder Thread");
            audioThread.start();
        }
    }

    private void stopRecording () {
        if (null != recorder) {
            Button stopButton = (Button) findViewById(R.id.leftButton);
            stopButton.setText("START");

            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            audioThread = null;
            speed = 0;
            updateData();
        }
    }

    private void analyzeAudio () {
        short buf[] = new short[bufferSize];

        int sampleSkip = 1000; //samples to skip after spike found (basic debounce)
        int threshold = 80;  //difference b/w samples must be this big for a spike
        int trailLen = 5;      //how many samples back to look when calculating difference
        int readTotal = 0;
        int secsPerHour = 3600;
        int inchesPerMile = 12 * 5280;
        boolean lastTickInLastSample = false;
        int lastSampleTick = 0;
        int oldPointsRead = 0;
        while (isRecording) {
            readTotal = 0;
            pointsRead = recorder.read(buf, 0, SAMPLE_RATE * 3 / 2);
            int maxDif = 0;
            int tickDif = 0;
            int lastTick = 0;
            //Log.d("recorder", "start for loop");
            for (int i = trailLen; i < pointsRead; i++) {
                int difference = (buf[i]) - (buf[i - trailLen]);
                if (difference >= threshold) {
                    readTotal++;

                    if(lastTick != 0) {
                        tickDif = i - lastTick;
                    } else if (lastTickInLastSample) {
                        tickDif = i + (pointsRead - lastSampleTick);
                    }
                    if(tickDif < 0){
                        Log.d("Recorder", String.format("negative tickDif = %d, lastTick = %d, i = %d", tickDif, lastTick, i));
                    }
                    lastTick = i;
                    i += sampleSkip;
                }

                if(difference > maxDif) {
                    maxDif = difference;
                }

            }
            if (lastTick != 0) {
                lastTickInLastSample = true;
                lastSampleTick = lastTick;
                oldPointsRead = pointsRead;
            } else {
                lastTickInLastSample = false;
                lastSampleTick = 0;
                oldPointsRead = 0;
            }
            total += readTotal;
            float memTicks = updateDataBuf(readTotal);

            dist = (total * tireCirc) / (inchesPerMile); //convert rotations to miles
            if(tickDif == 0) {
                speed = 0.2 * speed;
            } else {
                speed = (tireCirc * SAMPLE_RATE * secsPerHour) / (tickDif * inchesPerMile);
            }
            //speed = 0.75 * (memTicks * tireCirc * secsPerHour) / (inchesPerMile * DATA_BUF_LEN) + 0.25 * speed;
            if (speed > maxSpeed) {
                maxSpeed = speed;
            }
            //speed = total;
            //dist = maxDif;
            //speed =  0.75 * (readTotal * tireCirc * SAMPLE_RATE * 3600) / (pointsRead * 12 * 5280) + 0.25 * speed;
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateData();
                }
            });
        }

    }

    private float updateDataBuf(int readTotal) {
        int total = 0;
        for (int i = 1; i < DATA_BUF_LEN; i++) {
            total += dataBuf[i];
            dataBuf[i] = dataBuf[i - 1];
        }

        dataBuf[0] = readTotal;
        total += readTotal;

        return (float) total;
    }

    private void updateData() {
        if (state == DIST) {
            bottomText.setText(String.format("%.2f", dist));
        } else if (state == MAX_SPEED) {
            bottomText.setText(String.format("%.1f", maxSpeed));
        }
;
        topText.setText(String.format("%.1f", speed));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        topText = (TextView) findViewById(R.id.topText);
        bottomText = (TextView) findViewById(R.id.bottomText);
        bottomLabel = (TextView) findViewById(R.id.bottomLabel);
        bottomUnits = (TextView) findViewById(R.id.bottomUnits);

        dataBuf = new int[DATA_BUF_LEN];
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording();
    }

    public void leftButtonClick (View view) {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    public void rightButtonClick (View view) {
        state++;
        if (state == NUM_STATES) {
            state = 0;
        }
        bottomLabel.setText(stateNames[state]);
        bottomUnits.setText(stateUnits[state]);
        updateData();
        //bottomText.setText(stateNames[state]);
    }
}
