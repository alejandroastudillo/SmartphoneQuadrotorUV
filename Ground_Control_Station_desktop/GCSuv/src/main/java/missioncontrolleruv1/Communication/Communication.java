/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package missioncontrolleruv1.Communication;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTextArea;
import missioncontrolleruv1.MissionControllerUV;
import missioncontrolleruv1.MissionGUI;
import missioncontrolleruv1.ReadController;

/**
 *
 * @author Alejandro Astudillo
 */
public class Communication {
    
    public static MissionGUI window = MissionControllerUV.window; 
    public static ReadController readController = MissionControllerUV.readController;
    Timer timer;
    TemporizerComm mainThread;
    double t;
    float Ts = (float) 0.01;
    public static double quad_east, quad_north, quad_elevation, quad_roll, quad_pitch, quad_yaw, quad_battery, quad_smartphoneBat;
    Thread updateQuadMark;
    public static boolean centerMapOnQuad = false;
    
    Socket connectionSocket = null;
    BufferedReader inFromClient = null;
    DataOutputStream outToClient = null;
    JTextArea jTextAreaConsole = null;
    Socket clientSocket = null;
    String receivedSentence, responseSentence, ip_1;
    String[] receivedData;
    BufferedReader inFromServer, inFromUser;
    DataOutputStream outToServer;
    long startTime, estimatedTime;
    Thread decodeFrame;
    int j = 0;
    
    public boolean connected = false;
    public boolean freeBuffer = true;
    public static boolean armed = false;
    public static boolean RCthreadRunning = false;
    
    DecimalFormat df = new DecimalFormat("0.000");
    
    public Communication(){
        jTextAreaConsole = window.jTextAreaConsole;        
    }
    
    public void startConnection(String id){
        ip_1 = window.jTF_ip1.getText();
        try {
            clientSocket = new Socket(ip_1, 6789); //192.168.0.18
            //clientSocket.setSoTimeout(100);
            sendToServer(id+",0");
            receiveFromServer();
            Thread.sleep(2000);
            if(connected){
                window.jTextAreaConsole.append("Connection Set with IP: "+ip_1+"\n");
                                        
                window.bt_startConnection.setEnabled(false);
                window.bt_stopConnection.setEnabled(true);
                window.bt_LoiterMode.setEnabled(true);
                window.bt_RTLmode.setEnabled(true);
                window.bt_AltHoldMode.setEnabled(true);
                window.bt_LandMode.setEnabled(true);
                window.bt_stabilizeMode.setEnabled(true);
                window.bt_AutoMode.setEnabled(true);
                window.jTextPaneWaypoints.setEnabled(true);
                window.bt_arm.setEnabled(true);
                window.bt_setWaypoints.setEnabled(true);
                //startSendingRC();
                
            }
            //requestQuadrotorState("1");
        } catch (IOException ex) {
            Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Connection with "+ip_1+ " was not set");
        } catch (Exception ex) {
            Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    public void sendToServer(String sentence) throws IOException{
        outToServer = new DataOutputStream(clientSocket.getOutputStream());
        startTime = System.nanoTime();
        outToServer.writeBytes(sentence + '\n');
    }
    
    public void receiveFromServer(){
        try {
            inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            receivedSentence = inFromServer.readLine();
            estimatedTime = (System.nanoTime() - startTime)/1000000;
            decodeReceived(receivedSentence);
        } catch (IOException ex) {
            Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void closeCommunication(String id) throws IOException{
        sendToServer(id+",!c");
        receiveFromServer();
    }
        
    public void decodeReceived(final String received){
        decodeFrame = new Thread() {
            @Override
            public void run() {
                receivedData = received.split(",");
                System.out.println("Received: " + received);
                System.out.println("The number of frames is: " + receivedData.length);
                    if(receivedData[1].equals("state")){
                        decodeState(receivedData);
                    }
                    else if(receivedData[1].equals("0")){
                        System.out.println("Connection Set with Quadrotor "+receivedData[0]);
                        connected = true;                       
                    }
                    else if(receivedData[1].equals("!c")){
                        window.jTextAreaConsole.append("Connection Ended with Quadrotor "+receivedData[0]+"\n");
                        connected = false;   
                        timer.cancel();
                        RCthreadRunning = false;
                        try {
                            clientSocket.close();
                        } catch (IOException ex) {
                            Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        window.bt_startConnection.setEnabled(true);
                        window.bt_stopConnection.setEnabled(false);
                        window.bt_arm.setEnabled(false);
                        window.bt_LoiterMode.setEnabled(false);
                        window.bt_RTLmode.setEnabled(false);
                        window.bt_AltHoldMode.setEnabled(false);
                        window.bt_LandMode.setEnabled(false);
                        window.bt_stabilizeMode.setEnabled(false);
                        window.bt_AutoMode.setEnabled(false);
                        window.bt_setWaypoints.setEnabled(false);
                        window.tv_eastQuad.setText("-");
                        window.tv_northQuad.setText("-");
                        window.tv_elevationQuad.setText("-");
                        window.tv_rollQuad.setText("-");
                        window.tv_pitchQuad.setText("-");
                        window.tv_yawQuad.setText("-");
                        window.tv_quadBatt.setText("-");
                        window.tv_phoneBatt.setText("-");
                    }
                    else if(receivedData[1].equals("wys")){
                        System.out.println("Waypoint Set for Quadrotor "+receivedData[0]);
                    }
                    else if(receivedData[1].equals("mode")){
                        window.tf_currentflightmode.setText(receivedData[2]);
                        jTextAreaConsole.append("Quadrotor "+receivedData[0]+" mode changed to: "+receivedData[2]+'\n');
                    }
                    else if(receivedData[1].equals("arm")){
                        if(receivedData[2].equals("Disarmed")){
                            window.bt_arm.setEnabled(true);
                            window.bt_disarm.setEnabled(false);
                            window.bt_setWaypoints.setEnabled(true);
                            window.tf_currentflightmode.setText("-");
                        }
                        else if(receivedData[2].equals("Armed")){
                            window.bt_disarm.setEnabled(true);
                            window.bt_arm.setEnabled(false);
                            window.bt_setWaypoints.setEnabled(false);
                            window.bt_updateWaypoints.setEnabled(false);
                            try {
                                startSendingRC();
                            } catch (IOException ex) {
                                Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        window.tf_armed.setText(receivedData[2]);
                        jTextAreaConsole.append("Quadrotor "+receivedData[0]+" motors "+receivedData[2]+'\n');
                    }
                System.out.println("timeellapsed: "+estimatedTime+" ms");               
            }
        };
        decodeFrame.start();
    }
    
    private void decodeState(String[] receivedFrame){
        quad_east = Double.parseDouble(receivedFrame[2]);
        quad_north = Double.parseDouble(receivedFrame[3]);
        quad_elevation = Double.parseDouble(receivedFrame[4]);
        quad_roll = Double.parseDouble(receivedFrame[5]);
        quad_pitch = Double.parseDouble(receivedFrame[6]);
        quad_yaw = Double.parseDouble(receivedFrame[7]);
        quad_battery = Double.parseDouble(receivedFrame[8]);
        quad_smartphoneBat = Double.parseDouble(receivedFrame[9]);
        window.tv_northQuad.setText(String.valueOf(quad_north)+" m");
        window.tv_eastQuad.setText(String.valueOf(quad_east)+" m");
        window.tv_elevationQuad.setText(String.valueOf(quad_elevation)+" m");
        window.tv_rollQuad.setText(String.valueOf(quad_roll)+" °");
        window.tv_pitchQuad.setText(String.valueOf(quad_pitch)+" °");
        window.tv_yawQuad.setText(String.valueOf(quad_yaw)+" °");
        window.tv_quadBatt.setText(String.valueOf((int)quad_battery)+" %");
        window.tv_phoneBatt.setText(String.valueOf((int)quad_smartphoneBat)+" %");
        
        window.quadPositionMark(quad_east,quad_north);

    }
    
    public void requestModeChange(String id, String mode) throws IOException{
        freeBuffer = false;
        clientSocket.setSoTimeout(2000);
        sendToServer(id+",mode,"+mode);
        receiveFromServer();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
            Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
        }
        clientSocket.setSoTimeout(100);
        freeBuffer = true;
    }
    
    public void armQuadrotor(String id, boolean arm) throws IOException{
        freeBuffer = false;
        clientSocket.setSoTimeout(2000);
        sendToServer(id+",arm,"+arm);
        receiveFromServer();
        armed = arm;
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
            Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
        }
        clientSocket.setSoTimeout(100);
        freeBuffer = true;
    }
    
    public void sendWaypoint(String id, int waypointnumber, float north, float east, float elevation, float yaw) throws IOException{
        sendToServer(id+",wy,"+waypointnumber+","+df.format(north)+","+df.format(east)+","+elevation+","+yaw);
        receiveFromServer();
    }
    
    public void sendWaypointList(List<double[]> Waypoints, float elev, float yaw) throws IOException{
        freeBuffer = false;
        clientSocket.setSoTimeout(1000);
        float north_coord, east_coord;
        for(int i=0; i<=(Waypoints.size()-1); i++){
            north_coord = (float) Waypoints.get(i)[1];
            east_coord = (float) Waypoints.get(i)[0];
            sendWaypoint("1", i+1, north_coord, east_coord, elev, yaw);
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
            Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
        }
        clientSocket.setSoTimeout(100);
        freeBuffer = true;
    }
    
    public void resetWaypointList() throws IOException{
        freeBuffer = false;
        clientSocket.setSoTimeout(1000);
        sendToServer("1,rwp");
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
            Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
        }
        clientSocket.setSoTimeout(100);
        freeBuffer = true;
    }
    
    public void requestQuadrotorState(String id) throws IOException{
        freeBuffer = false;
        sendToServer(id+",state");
        receiveFromServer();
        freeBuffer = true;
    }
    
    public void sendRCwaitForState(String id) throws IOException{
        
        if(readController.getBACKbutton() && readController.getSTARTbutton() && readController.getLTbutton() && readController.getRTbutton()){          
            // Pressing BACK+START+LT+RT, arm/disarm the motors
            if(armed){ armQuadrotor("1",false);}
            else{ armQuadrotor("1",true);}
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else if(readController.getBACKbutton() && readController.getBbutton()){    
            // If armed, pressing BACK + B, set RTL mode
            if(armed){ requestModeChange("1","RTL");}
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else if(readController.getBACKbutton() && readController.getYbutton()){    
            // If armed, pressing BACK + Y, set Stabilize mode
            if(armed){ requestModeChange("1","Stabilize");}
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else if(readController.getBACKbutton() && readController.getXbutton()){    
            // If armed, pressing BACK + X, set Loiter mode
            if(armed){ requestModeChange("1","Loiter");}
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else if(readController.getBACKbutton() && readController.getAbutton()){    
            // If armed, pressing BACK + A, set AltHold mode
            if(armed){ requestModeChange("1","AltHold");}
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else if(readController.getSTARTbutton() && readController.getBbutton()){    
            // If connected, pressing START + B, disconnect from server
            if(RCthreadRunning){
                if(armed){ armQuadrotor("1",false);}
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
                }
                closeCommunication("1");
            }
        }
        else{ // This way, the GameController is prioritized over any other action in the GUI
            sendToServer(id+",rcstate,"+readController.getRollJoystick()+","+readController.getPitchJoystick()+","+readController.getYawJoystick()+","+
                    readController.getThrottleJoystick()+","+readController.getDPadPosition()+","+readController.getXbutton()+","+
                    readController.getYbutton()+","+readController.getAbutton()+","+readController.getBbutton()+","+
                    readController.getSTARTbutton()+","+readController.getBACKbutton()+","+readController.getLTbutton()+","+
                    readController.getRTbutton()+","+readController.getLBbutton()+","+readController.getRBbutton()+","+
                    readController.getLJbutton()+","+readController.getRJbutton());
            receiveFromServer();
        }
    }  
    
    public void startSendingRC() throws IOException{
        if (timer != null) {
            timer.cancel();
        }
        timer = new Timer();
        mainThread = new TemporizerComm();
        timer.schedule(mainThread, 10, 100);
        RCthreadRunning = true;
        t = 0; // inicia la simulación
    }
    
    Long t_pasado = System.nanoTime();
    
    private class TemporizerComm extends TimerTask {
        public void run()  {
            try {
                if(freeBuffer){
                    sendRCwaitForState("1");
                }
            } catch (IOException ex) {
                Logger.getLogger(Communication.class.getName()).log(Level.SEVERE, null, ex);
            }
            t = t + Ts;
            }
    }
     
    

    
}
