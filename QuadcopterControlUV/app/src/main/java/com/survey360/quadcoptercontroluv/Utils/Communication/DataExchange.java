package com.survey360.quadcoptercontroluv.Utils.Communication;

/**
 * Created by AAstudillo on 18/09/2017.
 */

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.survey360.quadcoptercontroluv.MenuActivities.MissionActivity;

import java.io.*;
import java.net.*;
import java.text.DecimalFormat;

public class DataExchange {

    int timeout = 0;
    DecimalFormat df = new DecimalFormat("0.000");

    Thread tcpServer, decodeFrame;

    String clientSentence;
    String responseSentence;
    ServerSocket welcomeSocket1 = new ServerSocket(6789);
    BufferedReader inFromClient;
    DataOutputStream outToClient;
    Socket connectionSocket1 = null;
    Context ctx;

    long lastreceivedtime = 0, newreceivedtime = 0, ellapsedreceivedtime = 0;
    boolean isCommWorking = false;
    boolean commFailed = false;

    public int rollJoystick, pitchJoystick, yawJoystick, throttleJoystick, dPad;
    public boolean xButton, yButton, aButton, bButton, startButton, backButton, ltButton, rtButton, lbButton, rbButton, ljButton, rjButton;

    String[] receivedData;
    public float[] quadrotorState = new float[8]; // north ,east, elevation, roll, pitch, yaw, quad_bat, smart_bat

    public DataExchange(Context context) throws IOException {
        ctx = context;
    }

    public void startTCPserver() {
        lastreceivedtime = System.nanoTime();
        commFailed = false;
        tcpServer = new Thread() {
            @Override
            public void run() {
                try {
                    connectionSocket1 = welcomeSocket1.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                while (true) {
                    TCPserver();
                }
            }
        };
        tcpServer.start();
        //Toast.makeText(ctx, "TCP Server Started", Toast.LENGTH_SHORT).show();
    }

    public void stopTCPserver() {
        tcpServer.interrupt();
        //Toast.makeText(ctx, "TCP Server Stopped", Toast.LENGTH_SHORT).show();
    }

    private void TCPserver(){
        try{
            //connectionSocket = welcomeSocket.accept();
            inFromClient = new BufferedReader(new InputStreamReader(connectionSocket1.getInputStream()));
            outToClient = new DataOutputStream(connectionSocket1.getOutputStream());
            clientSentence = null;
            //timeout = 0;
            commFailed = false;
            while((clientSentence = inFromClient.readLine()) == null){}
            /*timeout++;
            ellapsedreceivedtime = (System.nanoTime() - lastreceivedtime)/1000000; // ms
            Log.e("error","read: "+clientSentence);
            Log.e("error", "Timeout: " + timeout);
            Log.e("error", "Time since last communication: " + ellapsedreceivedtime/1000 + " s");
            if(timeout>=100){ Log.e("error","timeout to high"); }
            if(ellapsedreceivedtime>=5000){ communicationFailed(); }
            lastreceivedtime = System.nanoTime();*/
            decodeReceived(clientSentence);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void decodeReceived(final String received){
        decodeFrame = new Thread() {
            @Override
            public void run() {
                receivedData = received.split(",");
                //System.out.println("Received: " + received);
                responseSentence = received + '\n';

                try {
                    if(receivedData[1].equals("0") && receivedData.length>=1){ // Started Connection
                        verifyConnectionStarted(receivedData[0]);
                    }
                    else if(receivedData[1].equals("!c")){ // End connection query
                        verifyConnectionFinished(receivedData[0]);
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        stopTCPserver();
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        startTCPserver();
                    }
                    else if(receivedData[1].equals("wy")){ // Waypoint received
                        decodeWaypoints(receivedData);
                    }
                    else if(receivedData[1].equals("rwp")){ // Reset waypoints requested
                        resetWaypoints("1");
                    }
                    else if(receivedData[1].equals("state")){ // Quadrotor state query
                        sendState(receivedData[0]);
                    }
                    else if(receivedData[1].equals("arm")){ // Quadrotor state query
                        if(receivedData[2].equals("true")){
                            //Arm motors
                            ArmMotors();
                            outToClient.writeBytes(receivedData[0]+",arm,Armed"+'\n');
                        }
                        else if(receivedData[2].equals("false")){
                            //Disarm motors
                            DisarmMotors();
                            outToClient.writeBytes(receivedData[0]+",arm,Disarmed"+'\n');
                        }
                    }
                    else if(receivedData[1].equals("mode")){ // Requested flight mode change
                        changeFlightMode(receivedData[2]);
                    }
                    else if(receivedData[1].equals("rc")){ // RC controller commands received
                        decodeRCframe(receivedData);
                    }
                    else if(receivedData[1].equals("rcstate")){ // RC controller commands received, quadrotor state query
                        decodeRCframe(receivedData);
                        sendState(receivedData[0]);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        };
        decodeFrame.start();
    }

    private void decodeWaypoints(String[] receivedFrame) throws IOException {
        outToClient.writeBytes(receivedData[0]+",wys"+'\n');
        int wp_id =  Integer.parseInt(receivedFrame[2]) - 1;
        float wp_north = Float.valueOf(receivedFrame[3]);
        float wp_east = Float.valueOf(receivedFrame[4]);
        float wp_elevation = Float.valueOf(receivedFrame[5]);
        float wp_yaw = Float.valueOf(receivedFrame[6]);
        System.out.println("Waypoint number: "+wp_id);
        System.out.println("North: "+df.format(wp_north));
        System.out.println("East: "+df.format(wp_east));
        System.out.println("Elevation: "+wp_elevation);
        System.out.println("Yaw: "+wp_yaw);
        if(MissionActivity.waypointsList1.size()<=wp_id) {
            MissionActivity.waypointsList1.add(new float[]{wp_north, wp_east, wp_elevation, wp_yaw});
        }
        else{
            MissionActivity.waypointsList1.set(wp_id,new float[]{wp_north, wp_east, wp_elevation, wp_yaw});
        }
        MissionActivity.waypointsUpdated();
    }

    private void resetWaypoints(String id){
        if(id.equals("1")) {
            MissionActivity.waypointsList1.clear();
        }
    }

    private void changeFlightMode(String mode) throws IOException {
        // http://ardupilot.org/copter/docs/flight-modes.html
        MissionActivity.changeFlightMode(mode);
        outToClient.writeBytes(receivedData[0]+",mode,"+receivedData[2]+'\n');
    }

    private void verifyConnectionStarted(String id) throws IOException {
        outToClient.writeBytes(id+",0"+'\n');
    }

    private void verifyConnectionFinished(String id) throws IOException {
        outToClient.writeBytes(id+",!c"+'\n');

    }

    private void sendState(String id) throws IOException {
        quadrotorState = MissionActivity.quadrotorState;
        //outToClient.writeBytes(id+",state,843211.10,1062939.204,1930.204,0,1,2,85,74"+'\n');
        outToClient.writeBytes(id+",state,"+quadrotorState[0]+","+quadrotorState[1]+","+quadrotorState[2]+","+df.format(quadrotorState[3]*180/3.14159265)+","+df.format(quadrotorState[4]*180/3.14159265)+","+df.format(quadrotorState[5]*180/3.14159265)+","+quadrotorState[6]+","+quadrotorState[7]+'\n');
    }

    private void decodeRCframe(String[] receivedFrame){
        rollJoystick = Integer.parseInt(receivedFrame[2]);
        pitchJoystick  = Integer.parseInt(receivedFrame[3]);
        yawJoystick = Integer.parseInt(receivedFrame[4]);
        throttleJoystick = Integer.parseInt(receivedFrame[5]);
        dPad = Integer.parseInt(receivedFrame[6]);
        xButton = Boolean.parseBoolean(receivedFrame[7]);
        yButton = Boolean.parseBoolean(receivedFrame[8]);
        aButton = Boolean.parseBoolean(receivedFrame[9]);
        bButton = Boolean.parseBoolean(receivedFrame[10]);
        startButton = Boolean.parseBoolean(receivedFrame[11]);
        backButton = Boolean.parseBoolean(receivedFrame[12]);
        ltButton = Boolean.parseBoolean(receivedFrame[13]);
        rtButton = Boolean.parseBoolean(receivedFrame[14]);
        lbButton = Boolean.parseBoolean(receivedFrame[15]);
        rbButton = Boolean.parseBoolean(receivedFrame[16]);
        ljButton = Boolean.parseBoolean(receivedFrame[17]);
        rjButton = Boolean.parseBoolean(receivedFrame[18]);
        Log.w("RC: ",rollJoystick+", "+pitchJoystick+", "+yawJoystick+", "+throttleJoystick+", "+dPad);
    }

    private void ArmMotors(){
        if(MissionActivity.UIHandler!=null) {
            MissionActivity.armMotors();
        }
    }

    private void DisarmMotors(){
        if(MissionActivity.UIHandler!=null) {
            MissionActivity.disarmMotors();
        }
    }

    private void communicationFailed(){
        if(!commFailed) {
            commFailed = true;
            Log.e("error", "Communication failed. Time since last communication: " + ellapsedreceivedtime/1000 + " s");
        }
    }


}