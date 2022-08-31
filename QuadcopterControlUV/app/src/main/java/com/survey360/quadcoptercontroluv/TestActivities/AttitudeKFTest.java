package com.survey360.quadcoptercontroluv.TestActivities;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.survey360.quadcoptercontroluv.Utils.PermissionsRequest;
import com.survey360.quadcoptercontroluv.Utils.SaveFile;
import com.survey360.quadcoptercontroluv.Utils.StateEstimation.DataCollection;
import com.survey360.quadcoptercontroluv.R;
import com.survey360.quadcoptercontroluv.MenuActivities.TestsActivity;
import com.survey360.quadcoptercontroluv.Utils.StateEstimation.InitialConditions;
import com.survey360.quadcoptercontroluv.Utils.StateEstimation.PositionKalmanFilter;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;



public class AttitudeKFTest extends AppCompatActivity {

    DataCollection mDataCollection = null;

    // ------ POSITION KF ----------------------
    InitialConditions mInitialConditions = null;
    PositionKalmanFilter posKF = null;
    double[] posKFestimation;
    double this_x_1, this_y_1, this_xdot_1, this_ydot_1;
    // -----------------------------------------

    long t_medido;
    float dt;

    Timer timer;
    Temporizer mainThread;
    double t;
    float Ts = (float) 0.01;
    DecimalFormat df = new DecimalFormat("0.0");
    DecimalFormat dfmm = new DecimalFormat("0.000");
    DecimalFormat dfint = new DecimalFormat("0");

    private Button bt_start, bt_stop;
    private TextView tv_pitch, tv_roll, tv_yaw, tv_GPS_latitude, tv_GPS_longitude, tv_GPS_altitude, tv_GPS_bearing, tv_GPS_accuracy, tv_GPS_speed, tv_GPS_time;

    GraphView graphAKF;
    LineGraphSeries<DataPoint> seriesAKF, seriesAKF2;
    private double graphAKFLastXValue = 0d;

    Spinner spinnerAKF, spinnerAKF2;
    String spinnerSelection1, spinnerSelection2;
    List<String> listSpinner1, listSpinner2;

    public SaveFile mSaveFile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attitude_kftest);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mDataCollection = new DataCollection(this);

        // ------ POSITION KF ----------------------
        mInitialConditions = new InitialConditions(AttitudeKFTest.this);
        posKF = new PositionKalmanFilter(this);
        // -----------------------------------------

        bt_start = (Button) findViewById(R.id.bt_startAKF);
        bt_stop = (Button) findViewById(R.id.bt_stopAKF);

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

        tv_pitch = (TextView)findViewById(R.id.Pitch_TV);
        tv_roll = (TextView)findViewById(R.id.Roll_TV);
        tv_yaw = (TextView)findViewById(R.id.Yaw_TV);
        tv_GPS_latitude = (TextView)findViewById(R.id.GPS_Latitude_TVp);
        tv_GPS_longitude = (TextView)findViewById(R.id.GPS_Longitude_TV);
        tv_GPS_altitude = (TextView)findViewById(R.id.Altitude_TV);
        tv_GPS_bearing = (TextView)findViewById(R.id.GPS_Bearing_TV);
        tv_GPS_accuracy = (TextView)findViewById(R.id.GPS_Accuracy_TV);
        tv_GPS_speed = (TextView)findViewById(R.id.GPS_Speed_TV);
        tv_GPS_time = (TextView)findViewById(R.id.GPS_Time_TV);

        graphAKF = (GraphView) findViewById(R.id.graphAKF);

        seriesAKF = new LineGraphSeries<>();
        //seriesAKF.setTitle("Roll angle");
        seriesAKF.setColor(Color.RED);

        seriesAKF2 = new LineGraphSeries<>();
        //seriesAKF2.setTitle("Roll angle");
        seriesAKF2.setColor(Color.BLUE);

        graphAKF.getViewport().setXAxisBoundsManual(true);
        graphAKF.getViewport().setMinX(0);
        graphAKF.getViewport().setMaxX(5);
        graphAKF.addSeries(seriesAKF);
        graphAKF.addSeries(seriesAKF2);

        InitializeSpinnersAKF();

        mSaveFile = new SaveFile(this, this);                         // Data logging class
    }

    public void InitializeSpinnersAKF() {

        spinnerAKF = (Spinner) findViewById(R.id.spinnerGraphAKF);
        listSpinner1 = new ArrayList<String>();
        listSpinner1.add("Plot 1");
        listSpinner1.add("Roll (RV)");
        listSpinner1.add("Pitch (RV)");
        listSpinner1.add("Yaw (RV)");
        listSpinner1.add("Lin. Acc X");
        listSpinner1.add("Lin. Acc Y");
        listSpinner1.add("Lin. Acc Z");
        listSpinner1.add("Gyro X");
        listSpinner1.add("Gyro Y");
        listSpinner1.add("Gyro Z");
        listSpinner1.add("PosKF X");
        listSpinner1.add("PosKF Y");
        listSpinner1.add("PosKF Z");
        listSpinner1.add("PosKF dX");
        listSpinner1.add("PosKF dY");
        listSpinner1.add("PosKF dZ");
        listSpinner1.add("PosKF ddX");
        listSpinner1.add("PosKF ddY");
        listSpinner1.add("PosKF ddZ");
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, listSpinner1);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAKF.setAdapter(dataAdapter);

        spinnerAKF.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id)
            {
                spinnerSelection1 = (String) adapterView.getItemAtPosition(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent)
            {    }
        });

        spinnerAKF2 = (Spinner) findViewById(R.id.spinnerGraphAKF2);
        listSpinner2 = new ArrayList<String>();
        listSpinner2.add("Plot 2");
        listSpinner2.add("Roll (KF)");
        listSpinner2.add("Pitch (KF)");
        listSpinner2.add("Yaw (KF)");
        listSpinner2.add("Baro Z");
        listSpinner2.add("Baro dZ");
        listSpinner2.add("PosKF X");
        listSpinner2.add("PosKF Y");
        listSpinner2.add("PosKF dX");
        listSpinner2.add("PosKF dY");
        ArrayAdapter<String> dataAdapter2 = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, listSpinner2);
        dataAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAKF2.setAdapter(dataAdapter2);

        spinnerAKF2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id)
            {
                spinnerSelection2 = (String) adapterView.getItemAtPosition(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent)
            {    }
        });

    }


    public void acquireData(){
        posKF.initPositionKF();
        mDataCollection.register();
        if (timer != null) {
            timer.cancel();
        }
        timer = new Timer();
        mainThread = new Temporizer();
        mSaveFile.createFile("dataAttAcc");

        timer.schedule(mainThread, 10, 10);

        t = 0; // inicia la simulación

    }

    public void stopAcquiring(){
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        mSaveFile.closeFile();
        mDataCollection.unregister();
    }

    public void updateTextViews(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv_yaw.setText(dfmm.format(mDataCollection.orientationValsDeg[0])+" °");
                tv_pitch.setText(dfmm.format(mDataCollection.orientationValsDeg[1])+" °");
                tv_roll.setText(dfmm.format(mDataCollection.orientationValsDeg[2])+" °");
                tv_GPS_latitude.setText(String.valueOf(mDataCollection.gps_latitude));
                tv_GPS_longitude.setText(String.valueOf(mDataCollection.gps_longitude));
                tv_GPS_altitude.setText(String.valueOf(mDataCollection.baroElevation));
                tv_GPS_accuracy.setText(String.valueOf(mDataCollection.gps_accuracy));
                tv_GPS_bearing.setText(String.valueOf(mDataCollection.gps_bearing));
                tv_GPS_speed.setText(String.valueOf(mDataCollection.gps_speed));
                tv_GPS_time.setText(String.valueOf(mDataCollection.gps_time));

                graphAKFLastXValue += 0.01d;
                //graphAKFLastXValue += dt/1000;

                if(spinnerSelection1.contentEquals(listSpinner1.get(0))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, 0), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(1))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, (float)Math.toDegrees(mDataCollection.theta)), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(2))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, (float)Math.toDegrees(mDataCollection.phi)), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(3))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, (float)Math.toDegrees(mDataCollection.psi)), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(4))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, mDataCollection.earthAccVals[0]), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(5))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, mDataCollection.earthAccVals[1]), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(6))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, mDataCollection.earthAccVals[2]), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(7))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, mDataCollection.phi_dot), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(8))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, mDataCollection.theta_dot), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(9))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, mDataCollection.psi_dot), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(10))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, posKFestimation[0]), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(11))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, posKFestimation[1]), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(12))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, posKFestimation[2]), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(13))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, posKFestimation[3]), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(14))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, posKFestimation[4]), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(15))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, posKFestimation[5]), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(16))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, posKFestimation[6]), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(17))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, posKFestimation[7]), true, 500);
                }
                else if(spinnerSelection1.contentEquals(listSpinner1.get(18))){
                    seriesAKF.appendData(new DataPoint(graphAKFLastXValue, posKFestimation[8]), true, 500);
                }

                if(spinnerSelection2.contentEquals(listSpinner2.get(0))){
                    seriesAKF2.appendData(new DataPoint(graphAKFLastXValue, 0), true, 500);
                }
                else if(spinnerSelection2.contentEquals(listSpinner2.get(1))){
                    seriesAKF2.appendData(new DataPoint(graphAKFLastXValue,(float)Math.toDegrees(mDataCollection.theta)), true, 500);
                }
                else if(spinnerSelection2.contentEquals(listSpinner2.get(2))){
                    seriesAKF2.appendData(new DataPoint(graphAKFLastXValue, (float)Math.toDegrees(mDataCollection.phi)), true, 500);
                }
                else if(spinnerSelection2.contentEquals(listSpinner2.get(3))){
                    seriesAKF2.appendData(new DataPoint(graphAKFLastXValue, (float)Math.toDegrees(mDataCollection.psi)), true, 500);
                }
                else if(spinnerSelection2.contentEquals(listSpinner2.get(4))){
                    seriesAKF2.appendData(new DataPoint(graphAKFLastXValue, mDataCollection.baroElevation), true, 500);
                }
                else if(spinnerSelection2.contentEquals(listSpinner2.get(5))){
                    seriesAKF2.appendData(new DataPoint(graphAKFLastXValue, mDataCollection.baro_velocity), true, 500);
                }
                else if(spinnerSelection2.contentEquals(listSpinner2.get(6))){
                    seriesAKF2.appendData(new DataPoint(graphAKFLastXValue, posKFestimation[0]), true, 500);
                }
                else if(spinnerSelection2.contentEquals(listSpinner2.get(7))){
                    seriesAKF2.appendData(new DataPoint(graphAKFLastXValue, posKFestimation[1]), true, 500);
                }
                else if(spinnerSelection2.contentEquals(listSpinner2.get(8))){
                    seriesAKF2.appendData(new DataPoint(graphAKFLastXValue, posKFestimation[3]), true, 500);
                }
                else if(spinnerSelection2.contentEquals(listSpinner2.get(9))){
                    seriesAKF2.appendData(new DataPoint(graphAKFLastXValue, posKFestimation[4]), true, 500);
                }
            }
        });
    }

    Long t_pasado = System.nanoTime();
    // ------ POSITION KF ----------------------
    Long t_pasado_kf = t_pasado;
    Long t_pasado_kf_wogps = t_pasado;
    // -----------------------------------------

    private class Temporizer extends TimerTask {

        @Override
        public void run() {

            t_medido = System.nanoTime();
            dt = ((float) (t_medido - t_pasado)) / 1000000.0f; // [ms].;
            t_pasado = t_medido;
            // ------ POSITION KF ----------------------
            float dt_kf = ((float) (t_medido - t_pasado_kf)) / 1000000000.0f;// [s].;
            float dt_kf_wogps = ((float) (t_medido - t_pasado_kf_wogps)) / 1000000000.0f;// [s].;

            if(dt_kf >= 0.02){
                posKF.executePositionKF(mDataCollection.conv_x,mDataCollection.conv_y,mDataCollection.baroElevation,mDataCollection.earthAccVals[0],mDataCollection.earthAccVals[1],mDataCollection.earthAccVals[2]);
                t_pasado_kf = t_medido;
                posKFestimation = posKF.getEstimatedState();

                posKFestimation[3] = this_xdot_1;
                posKFestimation[4] = this_ydot_1;

                t_pasado_kf = t_medido;
                this_x_1 = posKFestimation[0];
                this_y_1 = posKFestimation[1];
            }
            else{
                posKF.executePositionKF_woGPS(mDataCollection.earthAccVals[0],mDataCollection.earthAccVals[1],mDataCollection.earthAccVals[2]);
                posKFestimation = posKF.getEstimatedState_woGPS();

                //------------
                /*posKFestimation[3] = (posKFestimation[0] - this_x_1)/dt_kf_wogps;
                posKFestimation[4] = (posKFestimation[1] - this_y_1)/dt_kf_wogps;
                */
                //------------
                if(Math.abs((posKFestimation[0] - this_x_1)/dt_kf_wogps) < 0.02){
                    posKFestimation[3] = 0;
                }
                if(Math.abs((posKFestimation[1] - this_y_1)/dt_kf_wogps) < 0.02){
                    posKFestimation[4] = 0;
                }
                //------------

                t_pasado_kf_wogps = t_medido;
                this_x_1 = posKFestimation[0];
                this_y_1 = posKFestimation[1];
                this_xdot_1 = posKFestimation[3];
                this_ydot_1 = posKFestimation[4];
            }
            // ------------------------------------------
            updateTextViews();
            t = t + dt/1000;
            /*mSaveFile.writeDatainFile(t + "," + dt + "," +
                    mDataCollection.orientationValsDeg[2] + "," + mDataCollection.orientationValsDeg[1] + "," + mDataCollection.orientationValsDeg[0] + "," +
                    mDataCollection.earthAccVals[0] + "," + mDataCollection.earthAccVals[1] + "," + mDataCollection.earthAccVals[2] + "," +
                    mDataCollection.quaternionVals[0] + "," + mDataCollection.quaternionVals[1] + "," + mDataCollection.quaternionVals[2] + "," + mDataCollection.quaternionVals[3] + System.lineSeparator());*/
            mSaveFile.writeDatainFile(t + "," + dt + "," +
                    mDataCollection.conv_x + "," + mDataCollection.conv_y + "," + mDataCollection.gps_accuracy + "," + mDataCollection.baroElevation + "," +
                    mDataCollection.orientationValsDeg[2] + "," + mDataCollection.orientationValsDeg[1] + "," + mDataCollection.orientationValsDeg[0] +
                    System.lineSeparator());
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

    public void onBackPressed() {
        finish();
        Intent intentTest = new Intent(AttitudeKFTest.this, TestsActivity.class);
        startActivity(intentTest);
        return;
    }
}
