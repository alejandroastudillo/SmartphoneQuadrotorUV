package com.survey360.quadcoptercontroluv.MenuActivities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.survey360.quadcoptercontroluv.R;
import com.survey360.quadcoptercontroluv.Utils.Communication.DataExchange;
import com.survey360.quadcoptercontroluv.Utils.Controllers.FlightController;
import com.survey360.quadcoptercontroluv.Utils.PermissionsRequest;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MissionActivity extends AppCompatActivity{

    MissionActivity mMission = this;

    public static ToggleButton tb_led;
    public static TextView tv_arm, tv_flightmode, tv_controller, tv_quadbatt, tv_smartbatt, tv_waypoints;
    public static TextView tv_east, tv_north, tv_elevation, tv_roll, tv_pitch, tv_yaw, tv_dt;
    public static ProgressBar pb_motor1, pb_motor2, pb_motor3, pb_motor4, pb_rolljoystick, pb_pitchjoystick, pb_yawjoystick, pb_throttlejoystick;
    public static Handler UIHandler = null;
    public static boolean ic_ready = false;
    private boolean backPressed = false;

    public static DataExchange mDataExchange = null;
    public static FlightController mFlightController = null;

    FlightController.MotorsPowers motorsPowers;

    public static boolean armed = false;
    public static MediaPlayer mp;
    private static Context ctx;
    private static Activity act;

    public static String flightMode = "AltHold";
    public static List<float[]> waypointsList1 = new ArrayList<>();
    public static float[] quadrotorState = new float[8]; // north ,east, elevation, roll, pitch, yaw, quad_bat, smart_bat



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mission);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mp = new MediaPlayer();
        ctx = this;
        act = this;

        PermissionsRequest.verifyStoragePermissions(this); // Permission for data saving

        UIHandler = new Handler(Looper.getMainLooper());

        tv_arm = (TextView) findViewById(R.id.tv_arm);
        tv_flightmode = (TextView) findViewById(R.id.tv_flightmode);
        tv_controller = (TextView) findViewById(R.id.tv_controller);
        tv_quadbatt = (TextView) findViewById(R.id.tv_quadbatt);
        tv_smartbatt = (TextView) findViewById(R.id.tv_smartbatt);
        tv_waypoints = (TextView) findViewById(R.id.tv_waypoints);
        tv_east = (TextView) findViewById(R.id.tv_east);
        tv_north = (TextView) findViewById(R.id.tv_north);
        tv_elevation = (TextView) findViewById(R.id.tv_elevation);
        tv_roll = (TextView) findViewById(R.id.tv_roll);
        tv_pitch = (TextView) findViewById(R.id.tv_pitch);
        tv_yaw = (TextView) findViewById(R.id.tv_yaw);
        tv_dt = (TextView) findViewById(R.id.tv_dt);
        pb_motor1 = (ProgressBar) findViewById(R.id.pb_motor1);
        pb_motor2 = (ProgressBar) findViewById(R.id.pb_motor2);
        pb_motor3 = (ProgressBar) findViewById(R.id.pb_motor3);
        pb_motor4 = (ProgressBar) findViewById(R.id.pb_motor4);
        pb_rolljoystick = (ProgressBar) findViewById(R.id.pb_rolljoystick);
        pb_pitchjoystick = (ProgressBar) findViewById(R.id.pb_pitchjoystick);
        pb_yawjoystick = (ProgressBar) findViewById(R.id.pb_yawjoystick);
        pb_throttlejoystick= (ProgressBar) findViewById(R.id.pb_throttlejoystick);

        tb_led = (ToggleButton) findViewById(R.id.tb_led);
        tb_led.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled
                    turnLed(true);
                } else {
                    // The toggle is disabled
                    turnLed(false);
                }
            }
        });

        mFlightController = new FlightController(this, this);

        // Start the sensor acquisition
        try {
            mDataExchange = new DataExchange(this);
            Thread.sleep(500);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mDataExchange.startTCPserver();
        Toast.makeText(MissionActivity.this, "TCP Server Started", Toast.LENGTH_SHORT).show();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        tv_flightmode.setText("Prepared for Take-off");


        playSound("waitingmission");

    }

    private static void playSound(String sound){

        if(mp.isPlaying()) {
            mp.stop();
        }
        try {
            mp.reset();
            AssetFileDescriptor afd;
            afd = ctx.getAssets().openFd(sound+".mp3");
            mp.setDataSource(afd.getFileDescriptor(),afd.getStartOffset(),afd.getLength());
            mp.prepare();
            mp.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void turnLed(boolean on){
        mFlightController.turnLed(on);
    }

    public static void armMotors(){
        mFlightController.acquireData();
        armed =  true;
        playSound("armed");
        if(UIHandler!=null) {
            UIHandler.post(new Runnable() {
                @Override
                public void run() {
                    tv_arm.setText("Armed");
                    tv_arm.setTextColor(Color.GREEN);
                }
            });
        }

    }

    public static void disarmMotors(){
        armed = false;
        mFlightController.stopAcquiring();
        //playSound("disarmed");
        changeFlightMode("");
        if(UIHandler!=null) {
            UIHandler.post(new Runnable() {
                @Override
                public void run() {
                    tv_arm.setText("Disarmed");
                    tv_arm.setTextColor(Color.RED);
                }
            });
        }
        //mFlightController = new FlightController(ctx, act);

    }

    public static void waypointsUpdated(){
        if(UIHandler!=null) {
            UIHandler.post(new Runnable() {
                @Override
                public void run() {
                    tv_waypoints.setText(String.valueOf(waypointsList1.size()));
                }
            });
        }
    }


    public static void changeFlightMode(String mode){
        flightMode = mode;
        if(UIHandler!=null) {
            UIHandler.post(new Runnable() {
                @Override
                public void run() {
                    tv_flightmode.setText(flightMode);
                }
            });
        }
        if(flightMode.equals("")){
            playSound("disarmed");
            mFlightController.changeFlightMode("");
        }
        if(flightMode.equals("Stabilize")){
            playSound("stabilize");
            mFlightController.changeFlightMode("Stabilize");
        }
        else if(flightMode.equals("AltHold")){
            playSound("althold");
            mFlightController.changeFlightMode("AltHold");
        }
        else if(flightMode.equals("Loiter")){
            playSound("loiter");
            mFlightController.changeFlightMode("Loiter");
        }
        else if(flightMode.equals("RTL")){
            playSound("rth");
            mFlightController.changeFlightMode("RTL");
        }
        else if(flightMode.equals("Auto")){
            playSound("auto");
            mFlightController.changeFlightMode("Auto");
        }
        else if(flightMode.equals("Land")){
            playSound("land");
            mFlightController.changeFlightMode("Land");
        }
    }

    protected void onDestroy(){
        if(!backPressed) {
            mDataExchange.stopTCPserver();
            mFlightController.adkCommunicator.stop();
            mFlightController.stopAcquiring();
            //mFlightController.mDataCollection.unregister();
            backPressed = false;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if(!armed) {
            backPressed = true;
            mDataExchange.stopTCPserver();
            mFlightController.adkCommunicator.stop();
            //mFlightController.mDataCollection.unregister();
            finish();
            Intent intentMainMenu = new Intent(MissionActivity.this, MainActivity.class);
            startActivity(intentMainMenu);

            return;
        }
    }
}
