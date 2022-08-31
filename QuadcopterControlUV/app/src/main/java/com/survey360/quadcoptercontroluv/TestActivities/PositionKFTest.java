package com.survey360.quadcoptercontroluv.TestActivities;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.survey360.quadcoptercontroluv.Utils.StateEstimation.DataCollection;
import com.survey360.quadcoptercontroluv.R;
import com.survey360.quadcoptercontroluv.MenuActivities.TestsActivity;
import com.survey360.quadcoptercontroluv.Utils.StateEstimation.InitialConditions;
import com.survey360.quadcoptercontroluv.Utils.StateEstimation.PositionKalmanFilter;

import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

public class PositionKFTest extends AppCompatActivity {

    DataCollection mDataCollection = null;
    InitialConditions mInitialConditions = null;
    PositionKalmanFilter posKF = null;

    Timer timer, timer2;
    TemporizerPKF mainThread;
    double t;
    float Ts = (float) 0.01;
    DecimalFormat df = new DecimalFormat("0.0");
    DecimalFormat dfmm = new DecimalFormat("0.000");
    DecimalFormat dfint = new DecimalFormat("0");

    private Button bt_start, bt_stop;
    private TextView tv_pitch, tv_roll, tv_yaw, tv_GPS_latitude, tv_GPS_longitude, tv_GPS_altitude, tv_GPS_bearing, tv_GPS_accuracy, tv_GPS_speed, tv_GPS_time;
    private TextView tv_x, tv_y, tv_z, tv_roll2, tv_pitch2, tv_yaw2, tv_baroalt;

    private double last_x, last_y, last_z;
    private double this_x, this_y, this_z;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_position_kftest);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mDataCollection = new DataCollection(this);
        mInitialConditions = new InitialConditions(PositionKFTest.this);
        posKF = new PositionKalmanFilter(this);
        bt_start = (Button) findViewById(R.id.bt_Arm);
        bt_stop = (Button) findViewById(R.id.bt_stopPKF);

        bt_start.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick (View view){acquireData();
            }

        });
        bt_stop.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick (View view){
                stopAcquiring();
            }

        });

        tv_pitch = (TextView)findViewById(R.id.Pitch_TVp);
        tv_roll = (TextView)findViewById(R.id.Roll_TVp);
        tv_yaw = (TextView)findViewById(R.id.Yaw_TVp);
        tv_GPS_latitude = (TextView)findViewById(R.id.GPS_Latitude_TVp);
        tv_GPS_longitude = (TextView)findViewById(R.id.GPS_Longitude_TVp);
        tv_GPS_altitude = (TextView)findViewById(R.id.Altitude_TVp);
        tv_GPS_bearing = (TextView)findViewById(R.id.GPS_Bearing_TVp);
        tv_GPS_accuracy = (TextView)findViewById(R.id.GPS_Accuracy_TVp);
        tv_GPS_speed = (TextView)findViewById(R.id.GPS_Speed_TVp);
        tv_GPS_time = (TextView)findViewById(R.id.GPS_Time_TVp);
        tv_baroalt = (TextView)findViewById(R.id.Baro_Alt_TVp);

        tv_pitch2 = (TextView)findViewById(R.id.Pitch_TV2);
        tv_roll2 = (TextView)findViewById(R.id.Roll_TV2);
        tv_yaw2 = (TextView)findViewById(R.id.Yaw_TV2);
        tv_x = (TextView)findViewById(R.id.X_TV);
        tv_y = (TextView)findViewById(R.id.Y_TV);
        tv_z = (TextView)findViewById(R.id.Z_TV);

        //posKF.initPositionKF();

    }

    public void acquireData(){
        posKF.initPositionKF();
        if (timer != null) {
            timer.cancel();
        }
        timer = new Timer();
        mainThread = new TemporizerPKF();
        timer.schedule(mainThread, 10, 10);

        t = 0; // inicia la simulación

        mDataCollection.register();
    }

    public void stopAcquiring(){
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        mDataCollection.unregister();
    }

    public void updateTextViews(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv_yaw.setText(dfmm.format(mDataCollection.orientationValsDeg[0])+" °");
                tv_pitch.setText(dfmm.format(mDataCollection.orientationValsDeg[1])+" °");
                tv_roll.setText(dfmm.format(mDataCollection.orientationValsDeg[2])+" °");
                tv_GPS_latitude.setText(String.valueOf(mDataCollection.gps_latitude)+" °");
                tv_GPS_longitude.setText(String.valueOf(mDataCollection.gps_longitude)+" °");
                tv_GPS_altitude.setText(String.valueOf(mDataCollection.gps_altitude)+" m");
                tv_GPS_accuracy.setText(df.format(mDataCollection.gps_accuracy)+" m");
                tv_GPS_bearing.setText(dfmm.format(mDataCollection.gps_bearing));
                tv_GPS_speed.setText(dfmm.format(mDataCollection.gps_speed));
                tv_GPS_time.setText(dfint.format(mDataCollection.gps_time/1000000000)+" s");
                tv_baroalt.setText(dfmm.format(mDataCollection.baroElevation));

                tv_yaw2.setText(String.valueOf(mDataCollection.orientationValsRad[0]));
                tv_pitch2.setText(String.valueOf(mDataCollection.orientationValsRad[1]));
                tv_roll2.setText(String.valueOf(mDataCollection.orientationValsRad[2]));
                tv_x.setText(dfmm.format(this_x)+" m");
                tv_y.setText(dfmm.format(this_y)+" m");
                tv_z.setText(String.valueOf(this_z)+" m");
            }
        });
    }

    Long t_pasado = System.nanoTime();
    Long t_pasado_kf = t_pasado;
    private class TemporizerPKF extends TimerTask {

        @Override
        public void run() {

            long t_medido = System.nanoTime();
            float dt = ((float) (t_medido - t_pasado)) / 1000000000.0f; // [s].;
            t_pasado = t_medido;
            double dt_kf = (t_medido - t_pasado_kf) / 1000000000; // [s].;

            if(dt_kf >= 0.99){
                posKF.executePositionKF(mDataCollection.conv_x,mDataCollection.conv_y,mDataCollection.baroElevation,mDataCollection.earthAccVals[0],mDataCollection.earthAccVals[1],mDataCollection.earthAccVals[2]);
                this_x = posKF.getEstimatedState()[0];
                this_y = posKF.getEstimatedState()[1];
                this_z = posKF.getEstimatedState()[2];

                t_pasado_kf = t_medido;
            }
            else{
                posKF.executePositionKF_woGPS(mDataCollection.earthAccVals[0],mDataCollection.earthAccVals[1],mDataCollection.earthAccVals[2]);
                this_x = posKF.getEstimatedState_woGPS()[0];
                this_y = posKF.getEstimatedState_woGPS()[1];
                this_z = posKF.getEstimatedState_woGPS()[2];
            }

            Log.w("Hilo 10 ms mainControl", "Tiempo de hilo = " + dt * 1000);
            updateTextViews();
            t = t + Ts;
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        mDataCollection.register();
    }

    @Override
    protected void onPause(){
        super.onPause();
        mDataCollection.unregister();
    }

    protected void onDestroy(){
        super.onDestroy();
        mDataCollection.closeApp();
    }

    @Override
    public void onBackPressed() {
        finish();
        Intent intentTest = new Intent(PositionKFTest.this, TestsActivity.class);
        startActivity(intentTest);
        return;
    }
}
