package com.survey360.quadcoptercontroluv.Utils.Controllers;

import android.app.Activity;
import android.content.Context;
import android.os.BatteryManager;
import android.util.Log;

import com.survey360.quadcoptercontroluv.MenuActivities.MissionActivity;
import com.survey360.quadcoptercontroluv.Utils.Communication.AdkCommunicator;
import com.survey360.quadcoptercontroluv.Utils.PermissionsRequest;
import com.survey360.quadcoptercontroluv.Utils.SaveFile;
import com.survey360.quadcoptercontroluv.Utils.StateEstimation.AltHoldKalmanFilter;
import com.survey360.quadcoptercontroluv.Utils.StateEstimation.DataCollection;
import com.survey360.quadcoptercontroluv.Utils.StateEstimation.EKF;
import com.survey360.quadcoptercontroluv.Utils.StateEstimation.InitialConditions;
import com.survey360.quadcoptercontroluv.Utils.StateEstimation.PositionKalmanFilter;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolver;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import static android.content.Context.BATTERY_SERVICE;
import static org.ejml.dense.row.CommonOps_DDRM.mult;

/**
 * Created by AAstudillo on 21/09/2017.
 */

public class FlightController implements AdkCommunicator.AdbListener {

    public DataCollection mDataCollection;
    public PositionKalmanFilter posKF;
    public AdkCommunicator adkCommunicator;
    public MotorsPowers motorsPowers;
    public AltHoldKalmanFilter altHoldKF;
    public EKF EKF;

    Context ctx;
    Activity act;

    DecimalFormat df = new DecimalFormat("0.000");

    BatteryManager bm;
    private int smartphoneBatLevel;
    private int batteryPercentage = 1;
    private float batteryPercentegeDec = 1;
    private float batteryCorrection = 1;
    private int batteryLecture = 1;
    private float batterySmoother = 0.2f;

    Timer controllerScheduler;
    ControllerThread controllerThread;
    double t;
    long measured_time, last_time, last_time_kf, last_time_kf_wogps;
    float delta_time, delta_time_kf, delta_time_kf_wogps;

    public float[] controlSignals = new float[4];
    public float[] linearControlSignals = new float[4];

    public SaveFile mSaveFile;
    private ArrayList<String> dataList, listToSave;

    public final float QUAD_MASS = 1.568f; // 1.568 [kg]
    public final float GRAVITY = 9.807f; // [m/s^2]
    private final float I_XX = 0.0135f;
    private final float I_YY = 0.0124f;
    private final float I_ZZ = 0.0336f;
    public final float L = 0.244f; // [m]
    public final float TORQUE_DISTANCE = L*((float)Math.cos(Math.toRadians(45))); // [m]
    public final float K_T = 0.0210f;

    public float X_ref = 0f;
    public float Xdot_ref = 0f;
    public float Y_ref = 0f;
    public float Ydot_ref = 0f;
    public float Z_ref = 0f;
    public float Zdot_ref = 0f;
    public float Psi_ref = 0f;
    public float Psidot_ref = 0f;
    public float Theta_ref = 0f;
    public float Thetadot_ref = 0f;
    public float Phi_ref = 0f;
    public float Phidot_ref = 0f;

    public float Throttle = 0f; //[N]

    private double this_x, this_y, this_z, this_xdot, this_ydot, this_zdot;
    private double this_x_1, this_y_1, this_xdot_1, this_ydot_1;
    private double this_xKF, this_yKF, this_zKF, this_xdotKF, this_ydotKF, this_zdotKF;

    private String FlightMode = "nothing";

    public float[] Motor_Forces = new float[4];

    private float Thrust_Compensation;

    private PidRegulator xControl, yControl, zControl;

    public FlightController(Context ctx, Activity act){

        this.ctx = ctx;
        this.act = act;

        mDataCollection = new DataCollection(ctx);          // Sensor data acquisition
        posKF = new PositionKalmanFilter(ctx);              // Position Kalman filter and Initial position acquisition
        adkCommunicator = new AdkCommunicator(this, ctx);   // Communication with the Arduino Mega ADK
        motorsPowers = new MotorsPowers();                  // Class that contains the signals sent to the motors
        altHoldKF = new AltHoldKalmanFilter(ctx);
        EKF = new EKF(ctx);


        try {
            adkCommunicator.start(false);                   // Start the communication with the Arduino Mega ADK
        } catch (Exception e) {
            e.printStackTrace();
        }

        bm = (BatteryManager)ctx.getSystemService(BATTERY_SERVICE);
        smartphoneBatLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        mSaveFile = new SaveFile(ctx, act);                         // Data logging class
        dataList = new ArrayList<>();
        listToSave = new ArrayList<>();

        xControl = new PidRegulator(0.0f,  0.0f,  0.0f, 0.3f, 0.0f);
        yControl = new PidRegulator(0.0f,  0.0f,  0.0f, 0.3f, 0.0f);
        zControl = new PidRegulator(0.0f,  0.0f,  0.0f, 0.3f, 0.0f);
    }

    public void acquireData(){
        if(!adkCommunicator.accessoryStarted) {
            try {
                adkCommunicator.start(false);                   // Start the communication with the Arduino Mega ADK
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        altHoldKF.initAltHoldKF(QUAD_MASS, I_XX, I_YY, I_ZZ);
        EKF.initEKF(QUAD_MASS, I_XX, I_YY, I_ZZ);
        posKF.initPositionKF();                             // Initialize the Position Kalman Filter matrices
        mDataCollection.register();                         // Begins to acquire sensor data

        Log.w("Mission thread: ","Thread start");
        if (controllerScheduler != null) {                  // Check that the controllerThread is not running through the scheduler
            controllerScheduler.cancel();
        }

        dataList = new ArrayList<>();
        listToSave = new ArrayList<>();
        mSaveFile.createFile("dataFlightController");

        last_time = System.nanoTime();
        last_time_kf = last_time;
        controllerScheduler = new Timer();
        controllerThread = new ControllerThread();
        controllerScheduler.schedule(controllerThread, 0, 10);  // The controllerThread is executed each 10 ms
        t = 0;

        xControl.setCoefficients(0.04f, 0.1f, 0.1f);
        yControl.setCoefficients(0.04f, 0.1f, 0.1f);
        zControl.setCoefficients(0.04f, 0.1f, 0.1f);
    }

    public void stopAcquiring(){
        mSaveFile.closeFile();

        xControl.resetIntegrator();
        yControl.resetIntegrator();
    }

    float joystick_gain = 1;
    float joystick_gain2 = 1;
    float joystick_gain3 = 1;
    //float M_G_Throttle = 17.74f;
    float M_G_Throttle = QUAD_MASS*GRAVITY;
    //float M_G_Throttle = 18.44f;
    //float M_G_Throttle_init = 18.44f;
    float M_G_Throttle_init = QUAD_MASS*GRAVITY;

    private void ControllerExecution(){
        if(MissionActivity.armed){                          // The control outputs are only set, if the motors are armed

            if(FlightMode.equals("Stabilize")) {

                //Throttle = ((MissionActivity.mDataExchange.throttleJoystick)-50f)*0.05f; // [N] [-1, 1]
                Throttle = Throttle + ((MissionActivity.mDataExchange.throttleJoystick)-50f)*(0.01f/50);
                Psi_ref = Psi_ref + ((MissionActivity.mDataExchange.yawJoystick)-50f)*((0.2f*3.1416f/180)/50);
                if(Psi_ref <=-150*3.1416f/180){Psi_ref = -150*3.1416f/180;}
                if(Psi_ref >=150*3.1416f/180){Psi_ref = 150*3.1416f/180;}

                //joystick_gain3 = joystick_gain3 + ((MissionActivity.mDataExchange.yawJoystick)-50f)*((0.005f)/50);
                //if(joystick_gain3 <= 0){joystick_gain3 = 0;}

                Theta_ref = ((MissionActivity.mDataExchange.rollJoystick)-50f)*((12*3.1416f/180)/50);
                Phi_ref = ((MissionActivity.mDataExchange.pitchJoystick)-50f)*((12*3.1416f/180)/50);

                // LQR controller ---------------------
                controlSignals[0] = 0f;
                controlSignals[1] = -1.7045f*(mDataCollection.psi-Psi_ref) - 0.21785f*(mDataCollection.psi_dot-Psidot_ref);
                controlSignals[2] = -1.5111f*(mDataCollection.theta-Theta_ref) - 0.3107f*(mDataCollection.theta_dot-Thetadot_ref);
                controlSignals[3] = -1.6448f*(mDataCollection.phi-Phi_ref) - 0.3381f*(mDataCollection.phi_dot-Phidot_ref);
                // ------------------------------------

                Thrust_Compensation = (float)Math.abs((1/((Math.cos(mDataCollection.theta))*(Math.cos(mDataCollection.phi)))));

                M_G_Throttle = (M_G_Throttle_init) + Throttle;
                //controlSignals[0] = controlSignals[0] + 19.15128f + (Throttle);
                controlSignals[0] = (controlSignals[0] + M_G_Throttle)*Thrust_Compensation;
                //controlSignals[0] = controlSignals[0] + QUAD_MASS*GRAVITY + (Throttle);
                    // QUAD_MASS*GRAVITY is the necessary thrust to overcome gravity [N] 17.438 N
            }

            else if(FlightMode.equals("AltHold")){
                //Throttle = ((MissionActivity.mDataExchange.throttleJoystick)-50f)*0.0001f; // [m]
                Throttle = Throttle + ((MissionActivity.mDataExchange.throttleJoystick)-50f)*(0.001f/50);
                //Psi_ref = Psi_ref + ((MissionActivity.mDataExchange.yawJoystick)-50f)*((0.1f*3.1416f/180)/50);
                //if(Psi_ref <=-160*3.1416f/180){Psi_ref = -160*3.1416f/180;}
                //if(Psi_ref >=160*3.1416f/180){Psi_ref = 160*3.1416f/180;}
                //Theta_ref = ((MissionActivity.mDataExchange.rollJoystick)-50f)*((10*3.1416f/180)/50);
                //Phi_ref = ((MissionActivity.mDataExchange.pitchJoystick)-50f)*((15*3.1416f/180)/50);
                Z_ref = Z_ref + Throttle;
                Log.w("Z_ref ##############","############## Z_REF: "+Z_ref+" ##############");

                joystick_gain = joystick_gain + ((MissionActivity.mDataExchange.yawJoystick)-50f)*((0.005f)/50);
                if(joystick_gain <= 0){joystick_gain = 0;}

                //zControl.setCoefficients(0.5f*joystick_gain, 0.03f*joystick_gain2, 0.02f*joystick_gain3);
                zControl.setCoefficients(0, -0.001f*joystick_gain, 0);
                controlSignals[0] = zControl.getInput(((float)this_z)-Z_ref, 0.01f);

                // LQR controller ---------------------
                controlSignals[0] = -1.2442f*(((float)this_z)-Z_ref) - 0.065f*(((float)this_zdot)-Zdot_ref);
                //controlSignals[0] = -1.2442f*(((float)this_z)-Z_ref) - 0.065f*(((float)this_zdot)-Zdot_ref) + controlSignals[0];
                controlSignals[1] = -1.7045f*(mDataCollection.psi-Psi_ref) - 0.21785f*(mDataCollection.psi_dot-Psidot_ref);
                controlSignals[2] = -1.5511f*(mDataCollection.theta-Theta_ref) - 0.3507f*(mDataCollection.theta_dot-Thetadot_ref);
                controlSignals[3] = -1.6848f*(mDataCollection.phi-Phi_ref) - 0.3781f*(mDataCollection.phi_dot-Phidot_ref);
                // ------------------------------------
                Thrust_Compensation = (float)Math.abs((1/((Math.cos(mDataCollection.theta))*(Math.cos(mDataCollection.phi)))));

                M_G_Throttle = M_G_Throttle_init;
                //controlSignals[0] = controlSignals[0] + 17.438f;
                controlSignals[0] = (controlSignals[0] + M_G_Throttle)*Thrust_Compensation;
                // QUAD_MASS*GRAVITY is the necessary thrust to overcome gravity [N]
            }
            else if(FlightMode.equals("Loiter")){
                //Throttle = ((MissionActivity.mDataExchange.throttleJoystick)-50f)*0.0001f; // [m]
                Throttle = Throttle + ((MissionActivity.mDataExchange.throttleJoystick)-50f)*(0.005f/50);
                if((Psi_ref <= 5*3.1416f/180) & (Psi_ref >= -5*3.1416f/180)){Psi_ref = 0;}
                else if(Psi_ref > 5*3.1416f/180){Psi_ref = Psi_ref - (0.003f*3.1416f/180);}
                else if(Psi_ref < -5*3.1416f/180){Psi_ref = Psi_ref + (0.003f*3.1416f/180);}

                if(Psi_ref <=-160*3.1416f/180){Psi_ref = -160*3.1416f/180;}
                else if(Psi_ref >=160*3.1416f/180){Psi_ref = 160*3.1416f/180;}

                //Psi_ref = Psi_ref + ((MissionActivity.mDataExchange.yawJoystick)-50f)*((0.1f*3.1416f/180)/50);
                Theta_ref = 0;
                Phi_ref = 0;

                //X_ref = X_ref + (((MissionActivity.mDataExchange.rollJoystick)-50f)*0.0001f); // [m]
                //Y_ref = Y_ref + (((MissionActivity.mDataExchange.pitchJoystick)-50f)*0.0001f); // [m]
                //Z_ref = Z_ref + Throttle;

                joystick_gain = joystick_gain + ((MissionActivity.mDataExchange.pitchJoystick)-50f)*((0.005f)/50);
                if(joystick_gain <= 0){joystick_gain = 0;}
                joystick_gain2 = joystick_gain2 + ((MissionActivity.mDataExchange.rollJoystick)-50f)*((0.005f)/50);
                if(joystick_gain2 <= 0){joystick_gain2 = 0;}
                joystick_gain3 = joystick_gain3 + ((MissionActivity.mDataExchange.yawJoystick)-50f)*((0.005f)/50);
                if(joystick_gain3 <= 0){joystick_gain3 = 0;}

                //xControl.setCoefficients(0.04f*joystick_gain, 0.1f, 0.1f*joystick_gain2);
                //yControl.setCoefficients(0.04f*joystick_gain, 0.1f, 0.1f*joystick_gain2);

                //Theta_ref = xControl.getInput(X_ref, (float)this_x, 0.01f);
                //Phi_ref = yControl.getInput(Y_ref, (float)this_y, 0.01f);

                // LQR controller ---------------------
                controlSignals[0] = -(1.2542f*joystick_gain3)*(((float)this_z)-Z_ref) - (0.075f)*(((float)this_zdot)-Zdot_ref);
                controlSignals[1] = -(1.7045f)*(mDataCollection.psi-Psi_ref) - (0.21785f)*(mDataCollection.psi_dot-Psidot_ref);
                controlSignals[2] = -1.5111f*(mDataCollection.theta-Theta_ref) - 0.3107f*(mDataCollection.theta_dot-Thetadot_ref);
                controlSignals[3] = -1.6448f*(mDataCollection.phi-Phi_ref) - 0.3381f*(mDataCollection.phi_dot-Phidot_ref);

                controlSignals[2] = controlSignals[2] - ((0.315612f*joystick_gain)*(((float)this_xKF)-X_ref)) - ((0.01119f*joystick_gain2)*(((float)this_xdotKF)-Xdot_ref));
                controlSignals[3] = controlSignals[3] - ((0.336130f*joystick_gain)*(((float)this_yKF)-Y_ref)) - ((0.01124f*joystick_gain2)*(((float)this_ydotKF)-Ydot_ref));
                // ------------------------------------

                Thrust_Compensation = (float)Math.abs(1/((Math.cos(mDataCollection.theta))*(Math.cos(mDataCollection.phi))));

                M_G_Throttle = M_G_Throttle_init + Throttle;
                //controlSignals[0] = controlSignals[0] + 17.438f;
                controlSignals[0] = (controlSignals[0] + M_G_Throttle)*Thrust_Compensation;
                // QUAD_MASS*GRAVITY is the necessary thrust to overcome the gravity [N]
            }
            /*
            else if(FlightMode.equals("RTL")){

            }
            else if(FlightMode.equals("Auto")){

            }
            else if(FlightMode.equals("Land")){

            }
            */
            else { // If armed without any flight mode, just turn on the motors
                Throttle = ((MissionActivity.mDataExchange.throttleJoystick)-50f)*0.001f; // [N] [-1, 1]
                if (controlSignals[0] <= QUAD_MASS*GRAVITY*0.9f){
                    controlSignals[0] = controlSignals[0]+Throttle;
                    controlSignals[1] = 0;
                    controlSignals[2] = 0;
                    controlSignals[3] = 0;
                }
                if(controlSignals[0] > QUAD_MASS*GRAVITY*0.9f){
                    controlSignals[0] = QUAD_MASS*GRAVITY*0.9f;
                }
            }

            setControlOutputs(controlSignals[0],controlSignals[1],controlSignals[2],controlSignals[3]);
        }
        else{
            turnOffMotors();
        }
    }

    private void setControlOutputs(float u, float tau_psi, float tau_theta, float tau_phi){

        Motor_Forces[0] = 0.2500f*u - 11.9048f*tau_psi - 1.4490f*tau_theta - 1.4490f*tau_phi; // [N] 11.9048*tau_psi
        Motor_Forces[1] = 0.2500f*u + 11.9048f*tau_psi - 1.4490f*tau_theta + 1.4490f*tau_phi; // [N]
        Motor_Forces[2] = 0.2500f*u - 11.9048f*tau_psi + 1.4490f*tau_theta + 1.4490f*tau_phi; // [N]
        Motor_Forces[3] = 0.2500f*u + 11.9048f*tau_psi + 1.4490f*tau_theta - 1.4490f*tau_phi; // [N]

        motorsPowers.m1 = (int)(-1.983f*Math.pow(Motor_Forces[0],2) + 47.84f*Motor_Forces[0] + 3.835f); // [0, 255]
        motorsPowers.m2 = (int)((-1.983f*Math.pow(Motor_Forces[1],2) + 47.84f*Motor_Forces[1] + 3.835f)*0.975f); // [0, 255]
        motorsPowers.m3 = (int)(-1.983f*Math.pow(Motor_Forces[2],2) + 47.84f*Motor_Forces[2] + 3.835f); // [0, 255]
        motorsPowers.m4 = (int)((-1.983f*Math.pow(Motor_Forces[3],2) + 47.84f*Motor_Forces[3] + 3.835f)*0.975f); // [0, 255]

        /*motorsPowers.m1 = (int)(motorsPowers.m1 * batteryCorrection);
        motorsPowers.m2 = (int)(motorsPowers.m2 * batteryCorrection);
        motorsPowers.m3 = (int)(motorsPowers.m3 * batteryCorrection);
        motorsPowers.m4 = (int)(motorsPowers.m4 * batteryCorrection);*/


        if(motorsPowers.m1 > 255){motorsPowers.m1 = 255;} // Motors saturation
        if(motorsPowers.m1 < 0){motorsPowers.m1 = 0;}
        if(motorsPowers.m2 > 255){motorsPowers.m2 = 255;}
        if(motorsPowers.m2 < 0){motorsPowers.m2 = 0;}
        if(motorsPowers.m3 > 255){motorsPowers.m3 = 255;}
        if(motorsPowers.m3 < 0){motorsPowers.m3 = 0;}
        if(motorsPowers.m4 > 255){motorsPowers.m4 = 255;}
        if(motorsPowers.m4 < 0){motorsPowers.m4 = 0;}

        adkCommunicator.setPowers(motorsPowers);

    }

    private void turnOffMotors(){
        controlSignals[0] = 0;
        controlSignals[1] = 0;
        controlSignals[2] = 0;
        controlSignals[3] = 0;

        motorsPowers.m1 = 0;
        motorsPowers.m2 = 0;
        motorsPowers.m3 = 0;
        motorsPowers.m4 = 0;

        adkCommunicator.setPowers(motorsPowers);
    }

    @Override
    public void onBatteryVoltageArrived(float batteryVoltage){ //It's executed when Android receives the Battery data from ADK
        //this.batteryPercentage = (int)(batteryVoltage*66.6667 - 740); //12.6 V full -- 11.1 V empty
        this.batteryLecture = (int)(batteryVoltage*31.25 - 293.75); //12.6 V full -- 9.4 V empty
        if(this.batteryPercentage < 5) {
            this.batteryPercentage = this.batteryLecture;
        }else{
            this.batteryPercentage = (int)(batteryPercentage*batterySmoother + batteryLecture*(1-batterySmoother));
        }
        batteryPercentegeDec = batteryPercentage/100;

        this.batteryCorrection = (float)(0.9f*((-0.0723f*(Math.pow(batteryPercentegeDec,2))) - (0.428f*batteryPercentegeDec) + 1.4605f));
        if(batteryCorrection < 1){batteryCorrection = 1;}
        else if(batteryCorrection > 1.3){batteryCorrection = 1.3f;}

        this.smartphoneBatLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        MissionActivity.UIHandler.post(new Runnable() {
            @Override
            public void run() {
                MissionActivity.tv_quadbatt.setText(batteryPercentage + " %");
                MissionActivity.tv_smartbatt.setText(smartphoneBatLevel + " %");
            }
        });
    }

    public void turnLed(boolean on){
        if(on){adkCommunicator.commTest(1,0,0,0);}
        else{adkCommunicator.commTest(0,0,0,0);}
    }

    public void changeFlightMode(String flightMode){
        FlightMode = flightMode;
        if(flightMode.equals("")){

        }
        else if(flightMode.equals("Stabilize")){
            Psi_ref = mDataCollection.psi;
            Theta_ref = 0;
            Phi_ref = 0;
        }
        else if(flightMode.equals("AltHold")){
            Z_ref = (float)this_z + 0.3f;
            Psi_ref = mDataCollection.psi;
            Theta_ref = 0;
            Phi_ref = 0;

            joystick_gain = 1;
            joystick_gain2 = 1;

            zControl.setCoefficients(0f, -0.03f, 0);
            zControl.resetIntegrator();
        }
        else if(flightMode.equals("Loiter")){
            X_ref = (float)this_x;
            Y_ref = (float)this_y;
            Z_ref = (float)this_z + 0.5f;
            Psi_ref = mDataCollection.psi;
            Theta_ref = 0;
            Phi_ref = 0;

            joystick_gain = 1;
            joystick_gain2 = 1;

            xControl.setCoefficients(0.04f, 0.1f, 0.1f);
            yControl.setCoefficients(0.04f, 0.1f, 0.1f);
            xControl.resetIntegrator();
            yControl.resetIntegrator();
        }
        else if(flightMode.equals("RTL")){
            X_ref = (float)this_x;
            Y_ref = (float)this_y;
            Z_ref = (float)this_z;
            Psi_ref = 0;
            Theta_ref = 0;
            Phi_ref = 0;
        }
        else if(flightMode.equals("Auto")){
            X_ref = (float)this_x;
            Y_ref = (float)this_y;
            Z_ref = (float)this_z;
            Psi_ref = 0;
            Theta_ref = 0;
            Phi_ref = 0;
        }
        else if(flightMode.equals("Land")){
            X_ref = (float)this_x;
            Y_ref = (float)this_y;
            Z_ref = (float)this_z;
            Psi_ref = 0;
            Theta_ref = 0;
            Phi_ref = 0;
        }
    }


    private class ControllerThread extends TimerTask {
        @Override
        public void run() {
            measured_time = System.nanoTime();
            delta_time = ((float) (measured_time - last_time)) / 1000000.0f; // [ms].;
            last_time = measured_time;
            t = t + delta_time;     // [ms];
            delta_time_kf = (measured_time - last_time_kf) / 1000000000; // [s].;
            delta_time_kf_wogps = (measured_time - last_time_kf_wogps) / 1000000000; // [s].;

            estimateQuadrotorStates();

            ControllerExecution();

            mSaveFile.writeDatainFile(df.format(t) + "," + df.format(delta_time) +
                    "," + df.format(this_x) + "," + df.format(this_y) + "," + df.format(this_z) +
                    "," + df.format(MissionActivity.quadrotorState[3])+ "," + df.format(MissionActivity.quadrotorState[4]) + "," + df.format(MissionActivity.quadrotorState[5]) +
                    "," + df.format(controlSignals[0]) + "," + df.format(controlSignals[1]) + "," + df.format(controlSignals[2]) + "," + df.format(controlSignals[3]) +
                    "," + motorsPowers.m1 + "," + motorsPowers.m2 + "," + motorsPowers.m3 + "," + motorsPowers.m4 +
                    "," + MissionActivity.quadrotorState[6] + "," + MissionActivity.quadrotorState[7] + "," + df.format(Throttle) +
                    "," + df.format(X_ref) + "," + df.format(Y_ref) + "," + df.format(Z_ref) + "," + df.format(Psi_ref) + "," + df.format(Theta_ref) + "," + df.format(Phi_ref) +
                    "," + df.format(this_xdot) + "," + df.format(this_ydot) + "," + df.format(this_zdot) +
                    "," + df.format(mDataCollection.earthAccVals[0]) + "," + df.format(mDataCollection.earthAccVals[1]) + "," + df.format(mDataCollection.earthAccVals[2]) +
                    "," + df.format(altHoldKF.getEstimatedState()[0]) + "," + df.format(altHoldKF.getEstimatedState()[1]) +
                    "," + df.format(altHoldKF.getEstimatedState()[2]) + "," + df.format(altHoldKF.getEstimatedState()[3]) +
                    "," + df.format(altHoldKF.getEstimatedState()[4]) + "," + df.format(altHoldKF.getEstimatedState()[5]) +
                    "," + df.format(altHoldKF.getEstimatedState()[6]) + "," + df.format(altHoldKF.getEstimatedState()[7]) +
                    "," + df.format(altHoldKF.getEstimatedState()[8]) + "," + df.format(altHoldKF.getEstimatedState()[9]) +
                    "," + df.format(altHoldKF.getEstimatedState()[10]) + "," + df.format(altHoldKF.getEstimatedState()[11]) +
                    "," + df.format(EKF.getEstimatedState()[0]) + "," + df.format(EKF.getEstimatedState()[1]) +
                    "," + df.format(EKF.getEstimatedState()[2]) + "," + df.format(EKF.getEstimatedState()[3]) +
                    "," + df.format(EKF.getEstimatedState()[4]) + "," + df.format(EKF.getEstimatedState()[5]) +
                    "," + df.format(EKF.getEstimatedState()[6]) + "," + df.format(EKF.getEstimatedState()[7]) +
                    "," + df.format(EKF.getEstimatedState()[8]) + "," + df.format(EKF.getEstimatedState()[9]) +
                    "," + df.format(EKF.getEstimatedState()[10]) + "," + df.format(EKF.getEstimatedState()[11]) +
                    "," + joystick_gain + "," + joystick_gain2 + "," + joystick_gain3 + "," + df.format(Thrust_Compensation) +
                    "," + batteryCorrection + System.lineSeparator());

            editGUI();
        }

        private void estimateQuadrotorStates(){
            if(delta_time_kf >= 0.02){ //delta_time_kf >= 0.05
                //posKF.executePositionKF(0, 0, mDataCollection.baroElevation,mDataCollection.earthAccVals[0],mDataCollection.earthAccVals[1],mDataCollection.earthAccVals[2]);
                posKF.executePositionKF(mDataCollection.conv_x,mDataCollection.conv_y,mDataCollection.baroElevation,mDataCollection.earthAccVals[0],mDataCollection.earthAccVals[1],mDataCollection.earthAccVals[2]);
                this_x = posKF.getEstimatedState()[0];
                this_y = posKF.getEstimatedState()[1];
                this_z = mDataCollection.baroElevation;
                this_xdot = this_xdotKF;
                this_ydot = this_ydotKF;
                this_zdot = posKF.getEstimatedState()[5];

                last_time_kf = measured_time;
                this_x_1 = this_x;
                this_y_1 = this_y;
            }
            else{
                posKF.executePositionKF_woGPS(mDataCollection.earthAccVals[0],mDataCollection.earthAccVals[1],mDataCollection.earthAccVals[2]);
                this_x = posKF.getEstimatedState_woGPS()[0];
                this_y = posKF.getEstimatedState_woGPS()[1];
                this_z = mDataCollection.baroElevation;
                //this_xdot = (this_x - this_x_1)/delta_time_kf_wogps;
                //this_ydot = (this_y - this_y_1)/delta_time_kf_wogps;
                this_xdot = this_xdotKF;
                this_ydot = this_ydotKF;
                if(Math.abs((this_x - this_x_1)/delta_time_kf_wogps) < 0.02){
                    this_xdot = 0;
                }
                if(Math.abs((this_y - this_y_1)/delta_time_kf_wogps) < 0.02){
                    this_ydot = 0;
                }
                this_zdot = posKF.getEstimatedState_woGPS()[5];

                last_time_kf_wogps = measured_time;
                this_x_1 = this_x;
                this_y_1 = this_y;
                this_xdot_1 = this_xdot;
                this_ydot_1 = this_ydot;
            }

            //altHoldKF.executeAltHoldKF(mDataCollection.baroElevation,mDataCollection.psi,mDataCollection.psi_dot,mDataCollection.theta,mDataCollection.theta_dot,mDataCollection.phi,mDataCollection.phi_dot,controlSignals, M_G_Throttle);
            //altHoldKF.executeAltHoldKF((float)this_x, (float)this_y, mDataCollection.baroElevation,mDataCollection.psi,mDataCollection.psi_dot,mDataCollection.theta,mDataCollection.theta_dot,mDataCollection.phi,mDataCollection.phi_dot,controlSignals);
            //altHoldKF.executeAltHoldKF((float)this_x, (float)this_y, mDataCollection.baroElevation,mDataCollection.psi,mDataCollection.theta,mDataCollection.phi,controlSignals, M_G_Throttle);
            //altHoldKF.executeAltHoldKF((float)this_x, (float)this_y, mDataCollection.baroElevation,mDataCollection.psi,mDataCollection.psi_dot,mDataCollection.theta,mDataCollection.theta_dot,mDataCollection.phi,mDataCollection.phi_dot,((controlSignals[0]/Thrust_Compensation)-M_G_Throttle),controlSignals[1],controlSignals[2],controlSignals[3]);
            altHoldKF.executeAltHoldKF((float)this_x, (float)this_y, mDataCollection.baroElevation,mDataCollection.psi,mDataCollection.theta,mDataCollection.phi,((controlSignals[0]/Thrust_Compensation)-M_G_Throttle),controlSignals[1],controlSignals[2],controlSignals[3]);

            EKF.executeEKF((float)this_x, (float)this_y, mDataCollection.baroElevation,mDataCollection.psi,controlSignals[0],controlSignals[1],controlSignals[2],controlSignals[3]);

            this_xKF = altHoldKF.getEstimatedState()[0];
            this_xdotKF = altHoldKF.getEstimatedState()[1];
            this_yKF = altHoldKF.getEstimatedState()[2];
            this_ydotKF = altHoldKF.getEstimatedState()[3];
            this_zKF = altHoldKF.getEstimatedState()[4];
            this_zdotKF = altHoldKF.getEstimatedState()[5];


            setQuadrotorState();
        }

        private void setQuadrotorState(){
            MissionActivity.quadrotorState[0] = (float)this_x;          // x [m]
            MissionActivity.quadrotorState[1] = (float)this_y;          // y [m]
            MissionActivity.quadrotorState[2] = (float)this_z;          // z [m]
            MissionActivity.quadrotorState[3] = mDataCollection.theta;  // roll [rad]
            MissionActivity.quadrotorState[4] = mDataCollection.phi;    // pitch [rad]
            MissionActivity.quadrotorState[5] = mDataCollection.psi;    // yaw [rad]
            MissionActivity.quadrotorState[6] = batteryPercentage;      // quadrotor battery [%]
            MissionActivity.quadrotorState[7] = smartphoneBatLevel;     // smartphone battery [%]
        }

        private void editGUI(){
            MissionActivity.UIHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(MissionActivity.armed){
                        MissionActivity.tv_east.setText(df.format(MissionActivity.quadrotorState[0]));
                        MissionActivity.tv_north.setText(df.format(MissionActivity.quadrotorState[1]));
                        MissionActivity.tv_elevation.setText(df.format(MissionActivity.quadrotorState[2]));
                    }
                    MissionActivity.tv_roll.setText(df.format(mDataCollection.orientationValsDeg[2]) + " °");
                    MissionActivity.tv_pitch.setText(df.format(mDataCollection.orientationValsDeg[1]) + " °");
                    MissionActivity.tv_yaw.setText(df.format(mDataCollection.orientationValsDeg[0]) + " °");
                    MissionActivity.tv_dt.setText(df.format(delta_time)+" ms");
                    MissionActivity.pb_rolljoystick.setProgress(MissionActivity.mDataExchange.rollJoystick);
                    MissionActivity.pb_pitchjoystick.setProgress(MissionActivity.mDataExchange.pitchJoystick);
                    MissionActivity.pb_yawjoystick.setProgress(MissionActivity.mDataExchange.yawJoystick);
                    MissionActivity.pb_throttlejoystick.setProgress(MissionActivity.mDataExchange.throttleJoystick);
                    MissionActivity.pb_motor1.setProgress(motorsPowers.m1*100/255);
                    MissionActivity.pb_motor2.setProgress(motorsPowers.m2*100/255);
                    MissionActivity.pb_motor3.setProgress(motorsPowers.m3*100/255);
                    MissionActivity.pb_motor4.setProgress(motorsPowers.m4*100/255);
                }
            });
        }
    }


    public static class MotorsPowers
    {
        public int m1, m2, m3, m4;  // 0-1023 (10 bits values).

        public int getMean()
        {
            return (m1+m2+m3+m4) / 4;
        }
    }
}
