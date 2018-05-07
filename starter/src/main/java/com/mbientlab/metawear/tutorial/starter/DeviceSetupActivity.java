/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 *
 * IMPORTANT: Your use of this Software is limited to those specific rights
 * granted under the terms of a software license agreement between the user who
 * downloaded the software, his/her employer (which must be your employer) and
 * MbientLab Inc, (the "License").  You may not use this Software unless you
 * agree to abide by the terms of the License which can be found at
 * www.mbientlab.com/terms . The License limits your use, and you acknowledge,
 * that the  Software may not be modified, copied or distributed and can be used
 * solely and exclusively in conjunction with a MbientLab Inc, product.  Other
 * than for the foregoing purpose, you may not use, reproduce, copy, prepare
 * derivative works of, modify, distribute, perform, display or sell this
 * Software and/or its documentation for any purpose.
 *
 * YOU FURTHER ACKNOWLEDGE AND AGREE THAT THE SOFTWARE AND DOCUMENTATION ARE
 * PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF MERCHANTABILITY, TITLE,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL
 * MBIENTLAB OR ITS LICENSORS BE LIABLE OR OBLIGATED UNDER CONTRACT, NEGLIGENCE,
 * STRICT LIABILITY, CONTRIBUTION, BREACH OF WARRANTY, OR OTHER LEGAL EQUITABLE
 * THEORY ANY DIRECT OR INDIRECT DAMAGES OR EXPENSES INCLUDING BUT NOT LIMITED
 * TO ANY INCIDENTAL, SPECIAL, INDIRECT, PUNITIVE OR CONSEQUENTIAL DAMAGES, LOST
 * PROFITS OR LOST DATA, COST OF PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY,
 * SERVICES, OR ANY CLAIMS BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY
 * DEFENSE THEREOF), OR OTHER SIMILAR COSTS.
 *
 * Should you have any questions regarding your right to use this Software,
 * contact MbientLab Inc, at www.mbientlab.com.
 */

package com.mbientlab.metawear.tutorial.starter;

import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.os.Handler;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import android.widget.Toast;
import com.mbientlab.metawear.DeviceInformation;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.tutorial.starter.DeviceSetupActivityFragment.FragmentSettings;

import bolts.Continuation;
import bolts.Task;

import static android.content.DialogInterface.*;

public class DeviceSetupActivity extends AppCompatActivity implements ServiceConnection, FragmentSettings {
    public final static String EXTRA_BT_DEVICE= "com.mbientlab.metawear.starter.DeviceSetupActivity.EXTRA_BT_DEVICE";
    String rssiValue;
    ToneGenerator toneG;

    double distanceValue;
    final int TxPower=-55;
    final double propogation_constant=2.5;
    public static class ReconnectDialogFragment extends DialogFragment implements  ServiceConnection {
        private static final String KEY_BLUETOOTH_DEVICE = "com.mbientlab.metawear.starter.DeviceSetupActivity.ReconnectDialogFragment.KEY_BLUETOOTH_DEVICE";

        private ProgressDialog reconnectDialog = null;
        private BluetoothDevice btDevice = null;
        private MetaWearBoard currentMwBoard = null;

        public static ReconnectDialogFragment newInstance(BluetoothDevice btDevice) {
            Bundle args = new Bundle();
            args.putParcelable(KEY_BLUETOOTH_DEVICE, btDevice);

            ReconnectDialogFragment newFragment = new ReconnectDialogFragment();
            newFragment.setArguments(args);

            return newFragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            btDevice = getArguments().getParcelable(KEY_BLUETOOTH_DEVICE);
            getActivity().getApplicationContext().bindService(new Intent(getActivity(), BtleService.class), this, BIND_AUTO_CREATE);

            reconnectDialog = new ProgressDialog(getActivity());
            reconnectDialog.setTitle(getString(R.string.title_reconnect_attempt));
            reconnectDialog.setMessage(getString(R.string.message_wait));
            reconnectDialog.setCancelable(false);
            reconnectDialog.setCanceledOnTouchOutside(false);
            reconnectDialog.setIndeterminate(true);
            reconnectDialog.setButton(BUTTON_NEGATIVE, getString(android.R.string.cancel), (dialogInterface, i) -> {
                currentMwBoard.disconnectAsync();
                getActivity().finish();
            });

            return reconnectDialog;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            currentMwBoard= ((BtleService.LocalBinder) service).getMetaWearBoard(btDevice);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) { }
    }

    private BluetoothDevice btDevice;
    private MetaWearBoard metawear;
    private Button switchBtn;

    private final String RECONNECT_DIALOG_TAG= "reconnect_dialog_tag";

    // This is the timer code
    Timer timer;
    TimerTask timerTask;
    //we are going to use a handler to be able to run in our TimerTask

    final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_setup);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        btDevice= getIntent().getParcelableExtra(EXTRA_BT_DEVICE);
        getApplicationContext().bindService(new Intent(this, BtleService.class), this, BIND_AUTO_CREATE);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_device_setup, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_disconnect:
                metawear.disconnectAsync();
                finish();
                return true;
            case R.id.led_switch:

//                metawear.readDeviceInformationAsync()
//                        .continueWith(new Continuation<DeviceInformation, Void>() {
//                            @Override
//                            public Void then(Task<DeviceInformation> task) throws Exception {
//                                TextView rssitxt=(TextView) findViewById( R.id.rssiValueTxt);
//                                rssitxt.setText(" " +task.getResult());
//                                //Log.i("MainActivity", "Device Information: " + task.getResult());
//                                return null;
//                            }
//                        });



                startTimer();
                /*Led led;
                if ((led= metawear.getModule(Led.class)) != null) {
                    led.editPattern(Led.Color.BLUE, Led.PatternPreset.BLINK)
                            .repeatCount((byte) 10)
                            .commit();
                    led.play();
                }*/
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        metawear.disconnectAsync();
        super.onBackPressed();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        metawear = ((BtleService.LocalBinder) service).getMetaWearBoard(btDevice);
        metawear.onUnexpectedDisconnect(status -> {
            ReconnectDialogFragment dialogFragment= ReconnectDialogFragment.newInstance(btDevice);
            dialogFragment.show(getSupportFragmentManager(), RECONNECT_DIALOG_TAG);

            metawear.connectAsync().continueWithTask(task -> task.isCancelled() || !task.isFaulted() ? task : MainActivity.reconnect(metawear))
                    .continueWith((Continuation<Void, Void>) task -> {
                        if (!task.isCancelled()) {
                            runOnUiThread(() -> {
                                ((DialogFragment) getSupportFragmentManager().findFragmentByTag(RECONNECT_DIALOG_TAG)).dismiss();
                                ((DeviceSetupActivityFragment) getSupportFragmentManager().findFragmentById(R.id.device_setup_fragment)).reconnected();
                            });
                        } else {
                            finish();
                        }

                        return null;
                    });
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    @Override
    public BluetoothDevice getBtDevice() {
        return btDevice;
    }

    public void startTimer() {

        //set a new Timer

        timer = new Timer();
        //initialize the TimerTask's job
        initializeTimerTask();

        //schedule the timer, after the first 5000ms the TimerTask will run every 10000ms
        timer.schedule(timerTask, 1000, 10000); //
    }

    public void stoptimertask(View v) {

        //stop the timer, if it's not already null
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public void initializeTimerTask() {

        timerTask = new TimerTask() {
            public void run() {

                //use a handler to run a toast that shows the current timestamp
                handler.post(new Runnable() {
                    public void run() {
                        //get the current timeStamp
                        //Calendar calendar = Calendar.getInstance();
                        //SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd:MMMM:yyyy HH:mm:ss a");
                        //final String strDate = simpleDateFormat.format(calendar.getTime());
                        //show the toast
                        int duration = Toast.LENGTH_SHORT;
                        metawear.readRssiAsync()
                                .continueWith(new Continuation<Integer, Void>() {
                                    @Override
                                    public Void then(Task<Integer> task) throws Exception {
                                        TextView rssitxt=(TextView) findViewById( R.id.rssiValueTxt);
                                        rssitxt.setText(" " +task.getResult());
                                        rssiValue=task.getResult().toString();

                                        distanceValue = Math.pow(10,
                                                ((TxPower - task.getResult()) /  (10 * propogation_constant)));
                                        Log.i("MainActivity", "Distance(m): " + distanceValue);
                                        if(task.getResult()>-68) {
                                            //playbeep(1000,2500);
                                            call_phone("+4917636789792");
                                            vibrate();
                                       }
                                       else{
                                            playSound();
                                        }
                                        return null;
                                    }
                                });
                        Toast toast = Toast.makeText(getApplicationContext(), rssiValue, duration);
                        toast.show();
                        notify_homescreen( "bote","Is Rodi around?", 123);
                    }
                });
            }
        };
    }

    // play beep
    public void playbeep(int volume, int duration){
        ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, volume);
        toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP,duration);
    }
    // ring tone
    public void playSound() throws IllegalArgumentException,
            SecurityException,
            IllegalStateException,
            IOException {

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        MediaPlayer mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setDataSource(this, soundUri);
        final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) != 0) {
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            //mMediaPlayer.setLooping(true);
            mMediaPlayer.prepare();
            mMediaPlayer.start();
        }
    }
    ///   vibrate the phone
    public void vibrate()
    {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // Vibrate for 500 milliseconds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(2000,VibrationEffect.DEFAULT_AMPLITUDE));
        }else{
            //deprecated in API 26
            v.vibrate(1000);
        }
    }

    public void notify_homescreen(String textTitle,String textContent,int notificationId){
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.bote_notification_icon)
                .setContentTitle(textTitle)
                .setContentText(textContent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
         // notificationId is a unique int for each notification that you must define
        notificationManager.notify(notificationId, mBuilder.build());
    }

    public void call_phone(String phoneNumber){
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:"+phoneNumber));
        startActivity(callIntent);


    }
}
