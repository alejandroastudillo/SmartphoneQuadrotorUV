/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package missioncontrolleruv1;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import missioncontrolleruv1.Communication.Communication;
import static missioncontrolleruv1.Communication.Communication.window;
import missioncontrolleruv1.map.FancyWaypointRenderer;
import missioncontrolleruv1.map.MyWaypoint;
import missioncontrolleruv1.map.Proj4;
import missioncontrolleruv1.map.QuadPositionRenderer;
import missioncontrolleruv1.map.RoutePainter;


import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.JXMapKit;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.WaypointPainter;
import org.jxmapviewer.input.MapClickListener;
import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.painter.CompoundPainter;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.WaypointRenderer;
import org.osgeo.proj4j.ProjCoordinate;

/**
 *
 * @author Alejandro Astudillo V
 */
public class MissionGUI extends javax.swing.JFrame {

    /**
     * Creates new form MissionGUI
     * 
     */
    MissionControllerUV missionControllerUV = new MissionControllerUV();
    
    JXMapKit mapkit;
    JXMapViewer mapViewer;
    List<GeoPosition> track;
    RoutePainter routePainter;
    Set<Waypoint> waypoints;
    WaypointPainter<Waypoint> waypointPainter;
    WaypointPainter<MyWaypoint> quadPainter;
    List<Painter<JXMapViewer>> painters;
    CompoundPainter<JXMapViewer> painter;
    GeoPosition quadPos;
    
    DecimalFormat df = new DecimalFormat("#.00"); 
    Proj4 mProj4 = null;
    ProjCoordinate p_result, p_in, converted_coord, converted_ellip, ellip_quad;
    List<double[]> magnaWaypoints;
    public List<double[]> magnaWaypointsSet = new ArrayList(Arrays.asList());
    public boolean waypointsSet = false;
    Set<MyWaypoint> quadPosition;
    double quad_east, quad_north;
    
    public MissionGUI() {
        initComponents();
        
        mProj4 = new Proj4();
        p_in = new ProjCoordinate();
        p_result = new ProjCoordinate();
        
        this.setResizable(false);
        this.setLocationRelativeTo(null);
        this.setVisible(true);
        
        initializeMap();               
    }

    private void initializeMap(){
       
        final GeoPosition eiee_gp = new GeoPosition(3.3724, -76.5319); 
        mapkit = new JXMapKit();
        mapViewer = mapkit.getMainMap();
        
        int online = 1;
        
        switch (online){
            case 0:
                mapkit.setDefaultProvider(JXMapKit.DefaultProviders.OpenStreetMaps); //if online
                break;
            case 1:
                TileFactoryInfo tileFactory = new TileFactoryInfo(
                0, //int minimumZoomLevel ...0
                5, //int maximumZoomLevel ..8
                17, //int totalMapZoom ...10
                256, //int tileSize
                true, true, //int minimumZoomLevel, boolean yt2b ..  x/y orientation is normal
                getClass().getResource("/MapTiles/Cali").toString(),  // java.lang.String baseURL "file:///C:/Users/Asus/Documents/MapTiles/CaliSur",
                "x","y","z" // java.lang.String xparam, java.lang.String yparam, java.lang.String zparam .. url args for x, y &amp; z
                ){
                    @Override
                    public String getTileUrl(int x, int y, int zoom) {
                        return this.baseURL +"/"+(17-zoom)+"/"+x+"/"+y+".png";
                    }
                };
                mapkit.setTileFactory(new DefaultTileFactory(tileFactory));
                break;
        }
        
        mapkit.setCenterPosition(eiee_gp);
        mapkit.setZoom(0);
               
        jPanelMap.setLayout(new BorderLayout());
        jPanelMap.add(mapkit, BorderLayout.CENTER);

        mapViewer.addMouseListener(new MapClickListener(mapViewer) {
            @Override
            public void mapClicked(GeoPosition gp) {
                if(!waypointsSet){
                    addWaypoint(gp);
                    drawWaypointsAndRoute();
                }
            }
        });
        // Create a track from the geo-positions
        //track = Arrays.asList(cancha1_CENTER, cancha1_NW, cancha1_SW, cancha1_SE, cancha1_NE);
        track = new ArrayList(Arrays.asList());
        magnaWaypoints = new ArrayList(Arrays.asList());
            
        // Create waypoints from the geo-positions
        waypoints = new HashSet<>(Arrays.asList());
        /*waypoints = new HashSet<>(Arrays.asList(
                        new DefaultWaypoint(cancha1_CENTER),
                        new DefaultWaypoint(cancha1_NW)
                        ,new DefaultWaypoint(cancha1_SW)
        ));*/
        //drawWaypointsAndRoute();
    }
    
    public void quadPositionMark(double quad_east, double quad_north){
            ellip_quad = convertToEllipCoordinates(quad_east, quad_north);
            quadPos = new GeoPosition(ellip_quad.y, ellip_quad.x);
            if(Communication.centerMapOnQuad){
                mapkit.setCenterPosition(quadPos);
            }
            quadPosition = new HashSet<>(Arrays.asList(
                                    new MyWaypoint("", Color.YELLOW, quadPos)
                                    ));
            //quadPosition = new HashSet<>(Arrays.asList(new DefaultWaypoint(new GeoPosition(ellip_quad.y, ellip_quad.x))));
            quadPainter = new WaypointPainter<>();
            quadPainter.setWaypoints(quadPosition);
            quadPainter.setRenderer(new QuadPositionRenderer());

            painters = new ArrayList<>();
            if(routePainter != null && waypointPainter != null){
                painters.add(routePainter);
                painters.add(waypointPainter);
            }
            painters.add(quadPainter);

            painter = new CompoundPainter<>(painters);
            mapViewer.setOverlayPainter(painter);
    }
    
    private void addWaypoint(GeoPosition wy_coord){
        waypoints.add(new DefaultWaypoint(wy_coord));
        track.add(wy_coord);
        double lat = wy_coord.getLatitude();
        double lon = wy_coord.getLongitude();
        converted_coord = convertCoordinates(lon, lat);
        magnaWaypoints.add(new double[]{converted_coord.x,converted_coord.y});
        String coord = "E: "+df.format(converted_coord.x)+" m; N: "+df.format(converted_coord.y)+" m";
        int size = track.size();
        try {
            appendString(size+": "+coord+'\n');
        } catch (BadLocationException ex) {
            Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void drawWaypointsAndRoute(){
        routePainter = new RoutePainter(track);
        // Create a waypoint painter that takes all the waypoints
        waypointPainter = new WaypointPainter<>();
        waypointPainter.setWaypoints(waypoints);

        // Create a compound painter that uses both the route-painter and the waypoint-painter
        painters = new ArrayList<>();
        painters.add(routePainter);
        painters.add(waypointPainter);
        if(quadPainter!=null){
            painters.add(quadPainter);
        }
        painter = new CompoundPainter<>(painters);
        mapViewer.setOverlayPainter(painter);
    }

    public void appendString(String str) throws BadLocationException{
        StyledDocument document = (StyledDocument) jTextPaneWaypoints.getDocument();
        document.insertString(document.getLength(), str, null);
                                                       // ^ or your style attribute  
    }
    
    public ProjCoordinate convertCoordinates(double coord_x, double coord_y){
        p_in.x = coord_x;
        p_in.y = coord_y;

        p_result = mProj4.TransformCoordinates(p_in, mProj4.crsWGS84, mProj4.crsMagnaSirgasWest);

        return p_result;
    }
    
    public ProjCoordinate convertToEllipCoordinates(double coord_x, double coord_y){
        p_in.x = coord_x;
        p_in.y = coord_y;

        p_result = mProj4.TransformCoordinates(p_in, mProj4.crsMagnaSirgasWest, mProj4.crsWGS84);

        return p_result;
    }
    
    private void sendWaypointList(List<double[]> Waypoints, float elev, float yaw) throws IOException{
        missionControllerUV.sendWaypointList(Waypoints, elev, yaw);
    }
    
    private void resetWaypointList()throws IOException{
        missionControllerUV.resetWaypointList();
    }
    
    public void stopConnectionWithServer(){
        try {
            if(Communication.armed){
                missionControllerUV.armMotors(false);
                Thread.sleep(500);
            }
            MissionControllerUV.stopConnection();
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSeparator4 = new javax.swing.JSeparator();
        jTabbed1 = new javax.swing.JTabbedPane();
        jPanelMission = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        bt_startConnection = new javax.swing.JButton();
        bt_stopConnection = new javax.swing.JButton();
        jTF_ip1 = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        cb_QuadList = new javax.swing.JComboBox<>();
        jPanelModes = new javax.swing.JPanel();
        bt_stabilizeMode = new javax.swing.JButton();
        jSeparator5 = new javax.swing.JSeparator();
        jSeparator6 = new javax.swing.JSeparator();
        jLabel8 = new javax.swing.JLabel();
        bt_AltHoldMode = new javax.swing.JButton();
        bt_RTLmode = new javax.swing.JButton();
        bt_AutoMode = new javax.swing.JButton();
        bt_LandMode = new javax.swing.JButton();
        bt_LoiterMode = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextAreaConsole = new javax.swing.JTextArea();
        jLabel9 = new javax.swing.JLabel();
        tf_currentflightmode = new javax.swing.JLabel();
        bt_clearConsole = new javax.swing.JButton();
        jLabelArmed = new javax.swing.JLabel();
        tf_armed = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel();
        bt_arm = new javax.swing.JButton();
        bt_disarm = new javax.swing.JButton();
        jSeparator2 = new javax.swing.JSeparator();
        jPanel4 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTextPaneWaypoints = new javax.swing.JTextPane();
        jLabel10 = new javax.swing.JLabel();
        bt_updateWaypoints = new javax.swing.JButton();
        bt_setWaypoints = new javax.swing.JButton();
        jLabel11 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        tf_missionAltitude = new javax.swing.JTextField();
        tf_missionYaw = new javax.swing.JTextField();
        jPanelMap = new javax.swing.JPanel();
        jPanelIndicators = new javax.swing.JPanel();
        jLabel13 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        jLabel15 = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();
        jLabel17 = new javax.swing.JLabel();
        jLabel18 = new javax.swing.JLabel();
        jLabel19 = new javax.swing.JLabel();
        tv_northQuad = new javax.swing.JLabel();
        tv_eastQuad = new javax.swing.JLabel();
        tv_elevationQuad = new javax.swing.JLabel();
        tv_rollQuad = new javax.swing.JLabel();
        tv_pitchQuad = new javax.swing.JLabel();
        tv_yawQuad = new javax.swing.JLabel();
        jLabel20 = new javax.swing.JLabel();
        tv_quadBatt = new javax.swing.JLabel();
        tv_phoneBatt = new javax.swing.JLabel();
        jLabel22 = new javax.swing.JLabel();
        jSeparator3 = new javax.swing.JSeparator();
        jSeparator7 = new javax.swing.JSeparator();
        jPanelComm = new javax.swing.JPanel();
        jLabelTitleComm = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jLabelTitleController = new javax.swing.JLabel();
        jComboBox_controllers = new javax.swing.JComboBox<>();
        jProgressBarX = new javax.swing.JProgressBar();
        jProgressBarY = new javax.swing.JProgressBar();
        jProgressBarZ = new javax.swing.JProgressBar();
        jProgressBarZrot = new javax.swing.JProgressBar();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabelDPad = new javax.swing.JLabel();
        jButtonControlX = new javax.swing.JButton();
        jButtonControlY = new javax.swing.JButton();
        jButtonControlB = new javax.swing.JButton();
        jButtonControlA = new javax.swing.JButton();
        jButtonControlStart = new javax.swing.JButton();
        jButtonControlBack = new javax.swing.JButton();
        jButtonControlLJ = new javax.swing.JButton();
        jButtonControlRJ = new javax.swing.JButton();
        jButtonControlRT = new javax.swing.JButton();
        jButtonControlRB = new javax.swing.JButton();
        jButtonControlLB = new javax.swing.JButton();
        jButtonControlLT = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();
        jLabelUVlogo = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        bt_startConnection.setText("<html><center>Establish<br />Connection</center></html>");
        bt_startConnection.setFocusPainted(false);
        bt_startConnection.setFocusable(false);
        bt_startConnection.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        bt_startConnection.setMargin(new java.awt.Insets(2, 2, 2, 2));
        bt_startConnection.setOpaque(false);
        bt_startConnection.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_startConnectionActionPerformed(evt);
            }
        });

        bt_stopConnection.setText("<html><center>Stop<br />Connection</center></html>");
        bt_stopConnection.setEnabled(false);
        bt_stopConnection.setFocusPainted(false);
        bt_stopConnection.setFocusable(false);
        bt_stopConnection.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        bt_stopConnection.setMargin(new java.awt.Insets(2, 2, 2, 2));
        bt_stopConnection.setOpaque(false);
        bt_stopConnection.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_stopConnectionActionPerformed(evt);
            }
        });

        jTF_ip1.setText("192.168.0.18");
        jTF_ip1.setToolTipText("");

        jLabel1.setText("IP address");

        cb_QuadList.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Quadrotor 1" }));

        bt_stabilizeMode.setText("<html><center>Stabilize</center></html>");
        bt_stabilizeMode.setEnabled(false);
        bt_stabilizeMode.setFocusable(false);
        bt_stabilizeMode.setMargin(new java.awt.Insets(2, 2, 2, 2));
        bt_stabilizeMode.setMaximumSize(new java.awt.Dimension(69, 23));
        bt_stabilizeMode.setMinimumSize(new java.awt.Dimension(69, 23));
        bt_stabilizeMode.setPreferredSize(new java.awt.Dimension(69, 23));
        bt_stabilizeMode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_stabilizeModeActionPerformed(evt);
            }
        });

        jSeparator5.setOrientation(javax.swing.SwingConstants.VERTICAL);

        jSeparator6.setOrientation(javax.swing.SwingConstants.VERTICAL);

        jLabel8.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel8.setText("Flight Modes");
        jLabel8.setMaximumSize(new java.awt.Dimension(69, 14));
        jLabel8.setMinimumSize(new java.awt.Dimension(69, 14));
        jLabel8.setPreferredSize(new java.awt.Dimension(69, 14));

        bt_AltHoldMode.setText("<html><center>Altitude<br />Hold</center></html>");
        bt_AltHoldMode.setEnabled(false);
        bt_AltHoldMode.setFocusPainted(false);
        bt_AltHoldMode.setMargin(new java.awt.Insets(2, 2, 2, 2));
        bt_AltHoldMode.setPreferredSize(new java.awt.Dimension(69, 37));
        bt_AltHoldMode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_AltHoldModeActionPerformed(evt);
            }
        });

        bt_RTLmode.setText("<html><center>RTL</center></html>");
        bt_RTLmode.setEnabled(false);
        bt_RTLmode.setFocusable(false);
        bt_RTLmode.setMargin(new java.awt.Insets(2, 2, 2, 2));
        bt_RTLmode.setMaximumSize(new java.awt.Dimension(69, 23));
        bt_RTLmode.setMinimumSize(new java.awt.Dimension(69, 23));
        bt_RTLmode.setPreferredSize(new java.awt.Dimension(69, 23));
        bt_RTLmode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_RTLmodeActionPerformed(evt);
            }
        });

        bt_AutoMode.setText("<html><center>Auto</center></html>");
        bt_AutoMode.setEnabled(false);
        bt_AutoMode.setFocusable(false);
        bt_AutoMode.setMargin(new java.awt.Insets(2, 2, 2, 2));
        bt_AutoMode.setMinimumSize(new java.awt.Dimension(69, 23));
        bt_AutoMode.setPreferredSize(new java.awt.Dimension(69, 23));
        bt_AutoMode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_AutoModeActionPerformed(evt);
            }
        });

        bt_LandMode.setText("<html><center>Land</center></html>");
        bt_LandMode.setEnabled(false);
        bt_LandMode.setFocusable(false);
        bt_LandMode.setMargin(new java.awt.Insets(2, 2, 2, 2));
        bt_LandMode.setMaximumSize(new java.awt.Dimension(69, 23));
        bt_LandMode.setMinimumSize(new java.awt.Dimension(69, 23));
        bt_LandMode.setPreferredSize(new java.awt.Dimension(69, 23));
        bt_LandMode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_LandModeActionPerformed(evt);
            }
        });

        bt_LoiterMode.setText("<html><center>Loiter</center></html>");
        bt_LoiterMode.setEnabled(false);
        bt_LoiterMode.setFocusPainted(false);
        bt_LoiterMode.setMargin(new java.awt.Insets(2, 2, 2, 2));
        bt_LoiterMode.setPreferredSize(new java.awt.Dimension(69, 37));
        bt_LoiterMode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_LoiterModeActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelModesLayout = new javax.swing.GroupLayout(jPanelModes);
        jPanelModes.setLayout(jPanelModesLayout);
        jPanelModesLayout.setHorizontalGroup(
            jPanelModesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelModesLayout.createSequentialGroup()
                .addComponent(jSeparator6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelModesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelModesLayout.createSequentialGroup()
                        .addGroup(jPanelModesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(bt_stabilizeMode, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(bt_RTLmode, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanelModesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(jPanelModesLayout.createSequentialGroup()
                                .addComponent(bt_AutoMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(bt_LandMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanelModesLayout.createSequentialGroup()
                                .addComponent(bt_AltHoldMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(bt_LoiterMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addComponent(jLabel8, javax.swing.GroupLayout.PREFERRED_SIZE, 216, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jSeparator5))
        );
        jPanelModesLayout.setVerticalGroup(
            jPanelModesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSeparator5)
            .addComponent(jSeparator6)
            .addGroup(jPanelModesLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(jLabel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelModesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(bt_stabilizeMode, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bt_LoiterMode, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bt_AltHoldMode, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelModesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(bt_AutoMode, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanelModesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(bt_RTLmode, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(bt_LandMode, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTextAreaConsole.setEditable(false);
        jTextAreaConsole.setColumns(20);
        jTextAreaConsole.setLineWrap(true);
        jTextAreaConsole.setRows(5);
        jTextAreaConsole.setFocusable(false);
        jTextAreaConsole.setRequestFocusEnabled(false);
        jScrollPane1.setViewportView(jTextAreaConsole);
        jTextAreaConsole.getAccessibleContext().setAccessibleParent(jTextAreaConsole);

        jLabel9.setText("Current Flight Mode: ");

        tf_currentflightmode.setText("-");

        bt_clearConsole.setText("Clear");
        bt_clearConsole.setFocusable(false);
        bt_clearConsole.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_clearConsoleActionPerformed(evt);
            }
        });

        jLabelArmed.setText("Motors Armed/Disarmed:");

        tf_armed.setText("-");

        bt_arm.setText("<html><center>Arm<br />Motors</center></html>");
        bt_arm.setEnabled(false);
        bt_arm.setFocusable(false);
        bt_arm.setMargin(new java.awt.Insets(2, 2, 2, 2));
        bt_arm.setMaximumSize(new java.awt.Dimension(65, 37));
        bt_arm.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_armActionPerformed(evt);
            }
        });

        bt_disarm.setText("<html><center>Disarm<br />Motors</center></html>");
        bt_disarm.setEnabled(false);
        bt_disarm.setFocusable(false);
        bt_disarm.setMargin(new java.awt.Insets(2, 2, 2, 2));
        bt_disarm.setMaximumSize(new java.awt.Dimension(65, 37));
        bt_disarm.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_disarmActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addComponent(bt_arm, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(bt_disarm, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel5Layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bt_arm, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bt_disarm, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addGap(18, 18, 18)
                        .addComponent(jTF_ip1, javax.swing.GroupLayout.PREFERRED_SIZE, 134, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(cb_QuadList, javax.swing.GroupLayout.PREFERRED_SIZE, 205, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(bt_startConnection, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bt_stopConnection, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelModes, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(bt_clearConsole))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabelArmed)
                            .addComponent(jLabel9))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(tf_currentflightmode, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(tf_armed, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 266, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanelModes, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(bt_startConnection, javax.swing.GroupLayout.PREFERRED_SIZE, 43, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(cb_QuadList, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(bt_stopConnection)
                            .addComponent(jTF_ip1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel1))
                        .addGap(16, 16, 16))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(jLabel9)
                                    .addComponent(tf_currentflightmode))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(jLabelArmed)
                                    .addComponent(tf_armed))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(bt_clearConsole, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(jPanel5, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addComponent(jScrollPane1))
                        .addContainerGap())))
        );

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, 117, Short.MAX_VALUE)
        );

        jPanel4.setMaximumSize(new java.awt.Dimension(266, 404));
        jPanel4.setMinimumSize(new java.awt.Dimension(266, 404));

        jScrollPane2.setViewportView(jTextPaneWaypoints);

        jLabel10.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel10.setText("Waypoints");
        jLabel10.setMaximumSize(new java.awt.Dimension(69, 14));
        jLabel10.setMinimumSize(new java.awt.Dimension(69, 14));
        jLabel10.setPreferredSize(new java.awt.Dimension(69, 14));

        bt_updateWaypoints.setText("Update");
        bt_updateWaypoints.setEnabled(false);
        bt_updateWaypoints.setFocusable(false);
        bt_updateWaypoints.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_updateWaypointsActionPerformed(evt);
            }
        });

        bt_setWaypoints.setText("Set");
        bt_setWaypoints.setFocusable(false);
        bt_setWaypoints.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_setWaypointsActionPerformed(evt);
            }
        });

        jLabel11.setText("Altitude");

        jLabel12.setText("Azimuth");

        tf_missionAltitude.setText("15");

        tf_missionYaw.setText("0");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addContainerGap(27, Short.MAX_VALUE)
                .addComponent(jLabel10, javax.swing.GroupLayout.PREFERRED_SIZE, 216, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(23, 23, 23))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane2))
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(46, 46, 46)
                .addComponent(bt_updateWaypoints)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(bt_setWaypoints, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(41, 41, 41))
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(84, 84, 84)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel12)
                    .addComponent(jLabel11))
                .addGap(18, 18, 18)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(tf_missionYaw)
                    .addComponent(tf_missionAltitude, javax.swing.GroupLayout.DEFAULT_SIZE, 51, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(jLabel10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 266, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel11)
                    .addComponent(tf_missionAltitude, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel12)
                    .addComponent(tf_missionYaw, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 11, Short.MAX_VALUE)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bt_setWaypoints)
                    .addComponent(bt_updateWaypoints))
                .addContainerGap())
        );

        jPanelMap.setMinimumSize(new java.awt.Dimension(658, 404));

        jPanelIndicators.setMaximumSize(new java.awt.Dimension(201, 404));
        jPanelIndicators.setMinimumSize(new java.awt.Dimension(201, 404));
        jPanelIndicators.setRequestFocusEnabled(false);

        jLabel13.setText("Quadrotor State");

        jLabel14.setText("North:");

        jLabel15.setText("East:");

        jLabel16.setText("Elevation:");

        jLabel17.setText("Roll:");

        jLabel18.setText("Pitch:");

        jLabel19.setText("Yaw:");

        tv_northQuad.setText("-");

        tv_eastQuad.setText("-");

        tv_elevationQuad.setText("-");

        tv_rollQuad.setText("-");

        tv_pitchQuad.setText("-");

        tv_yawQuad.setText("-");

        jLabel20.setText("Quad. Batt:");

        tv_quadBatt.setText("-");

        tv_phoneBatt.setText("-");

        jLabel22.setText("Phone. Batt:");

        javax.swing.GroupLayout jPanelIndicatorsLayout = new javax.swing.GroupLayout(jPanelIndicators);
        jPanelIndicators.setLayout(jPanelIndicatorsLayout);
        jPanelIndicatorsLayout.setHorizontalGroup(
            jPanelIndicatorsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelIndicatorsLayout.createSequentialGroup()
                .addGroup(jPanelIndicatorsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanelIndicatorsLayout.createSequentialGroup()
                        .addGap(69, 69, 69)
                        .addComponent(jLabel13))
                    .addGroup(jPanelIndicatorsLayout.createSequentialGroup()
                        .addGap(26, 26, 26)
                        .addGroup(jPanelIndicatorsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel14)
                            .addComponent(jLabel15)
                            .addComponent(jLabel16)
                            .addComponent(jLabel17)
                            .addComponent(jLabel18)
                            .addComponent(jLabel19)
                            .addComponent(jLabel20)
                            .addComponent(jLabel22))
                        .addGap(22, 22, 22)
                        .addGroup(jPanelIndicatorsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(tv_yawQuad, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(tv_pitchQuad, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(tv_rollQuad, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(tv_elevationQuad, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(tv_eastQuad, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(jPanelIndicatorsLayout.createSequentialGroup()
                                .addComponent(tv_northQuad, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addComponent(tv_quadBatt, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(tv_phoneBatt, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap(23, Short.MAX_VALUE))
        );
        jPanelIndicatorsLayout.setVerticalGroup(
            jPanelIndicatorsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelIndicatorsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel13)
                .addGap(18, 18, 18)
                .addGroup(jPanelIndicatorsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel14)
                    .addComponent(tv_northQuad))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelIndicatorsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel15)
                    .addComponent(tv_eastQuad))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelIndicatorsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel16)
                    .addComponent(tv_elevationQuad))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelIndicatorsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel17)
                    .addComponent(tv_rollQuad))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelIndicatorsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel18)
                    .addComponent(tv_pitchQuad))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelIndicatorsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel19)
                    .addComponent(tv_yawQuad))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelIndicatorsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel20)
                    .addComponent(tv_quadBatt))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelIndicatorsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tv_phoneBatt)
                    .addComponent(jLabel22))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jSeparator3.setOrientation(javax.swing.SwingConstants.VERTICAL);

        jSeparator7.setOrientation(javax.swing.SwingConstants.VERTICAL);

        javax.swing.GroupLayout jPanelMissionLayout = new javax.swing.GroupLayout(jPanelMission);
        jPanelMission.setLayout(jPanelMissionLayout);
        jPanelMissionLayout.setHorizontalGroup(
            jPanelMissionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelMissionLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSeparator2)
                .addContainerGap())
            .addGroup(jPanelMissionLayout.createSequentialGroup()
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jSeparator7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelMap, javax.swing.GroupLayout.PREFERRED_SIZE, 628, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelIndicators, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanelMissionLayout.setVerticalGroup(
            jPanelMissionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelMissionLayout.createSequentialGroup()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelMissionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelMap, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelIndicators, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jSeparator3)
                    .addComponent(jSeparator7)))
        );

        jTabbed1.addTab("Mission Control", jPanelMission);

        jLabelTitleComm.setFont(new java.awt.Font("Times New Roman", 0, 14)); // NOI18N
        jLabelTitleComm.setText("Communication Settings");

        jLabelTitleController.setFont(new java.awt.Font("Times New Roman", 0, 14)); // NOI18N
        jLabelTitleController.setText("Controller Settings");

        jLabel2.setFont(new java.awt.Font("Times New Roman", 0, 12)); // NOI18N
        jLabel2.setText("X Axis");
        jLabel2.setToolTipText("");

        jLabel3.setFont(new java.awt.Font("Times New Roman", 0, 12)); // NOI18N
        jLabel3.setText("Y Axis");
        jLabel3.setToolTipText("");

        jLabel4.setFont(new java.awt.Font("Times New Roman", 0, 12)); // NOI18N
        jLabel4.setText("Z Axis");
        jLabel4.setToolTipText("");

        jLabel5.setFont(new java.awt.Font("Times New Roman", 0, 12)); // NOI18N
        jLabel5.setText("Z Rotation");
        jLabel5.setToolTipText("");

        jLabel6.setFont(new java.awt.Font("Times New Roman", 0, 12)); // NOI18N
        jLabel6.setText("D-Pad");
        jLabel6.setToolTipText("");

        jLabelDPad.setFont(new java.awt.Font("Times New Roman", 0, 12)); // NOI18N
        jLabelDPad.setText("D-Pad");
        jLabelDPad.setToolTipText("");

        jButtonControlX.setBackground(new java.awt.Color(255, 255, 255));
        jButtonControlX.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        jButtonControlX.setText("X");
        jButtonControlX.setFocusable(false);
        jButtonControlX.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jButtonControlX.setMaximumSize(new java.awt.Dimension(21, 21));
        jButtonControlX.setMinimumSize(new java.awt.Dimension(21, 21));
        jButtonControlX.setPreferredSize(new java.awt.Dimension(21, 21));

        jButtonControlY.setBackground(new java.awt.Color(255, 255, 255));
        jButtonControlY.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        jButtonControlY.setText("Y");
        jButtonControlY.setFocusable(false);
        jButtonControlY.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jButtonControlY.setMaximumSize(new java.awt.Dimension(21, 21));
        jButtonControlY.setMinimumSize(new java.awt.Dimension(21, 21));
        jButtonControlY.setPreferredSize(new java.awt.Dimension(21, 21));

        jButtonControlB.setBackground(new java.awt.Color(255, 255, 255));
        jButtonControlB.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        jButtonControlB.setText("B");
        jButtonControlB.setFocusable(false);
        jButtonControlB.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jButtonControlB.setMaximumSize(new java.awt.Dimension(21, 21));
        jButtonControlB.setMinimumSize(new java.awt.Dimension(21, 21));
        jButtonControlB.setPreferredSize(new java.awt.Dimension(21, 21));

        jButtonControlA.setBackground(new java.awt.Color(255, 255, 255));
        jButtonControlA.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        jButtonControlA.setText("A");
        jButtonControlA.setFocusable(false);
        jButtonControlA.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jButtonControlA.setMaximumSize(new java.awt.Dimension(21, 21));
        jButtonControlA.setMinimumSize(new java.awt.Dimension(21, 21));
        jButtonControlA.setPreferredSize(new java.awt.Dimension(21, 21));

        jButtonControlStart.setBackground(new java.awt.Color(255, 255, 255));
        jButtonControlStart.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        jButtonControlStart.setText("Start");
        jButtonControlStart.setFocusable(false);
        jButtonControlStart.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jButtonControlStart.setMaximumSize(new java.awt.Dimension(21, 21));
        jButtonControlStart.setMinimumSize(new java.awt.Dimension(21, 21));
        jButtonControlStart.setPreferredSize(new java.awt.Dimension(21, 21));

        jButtonControlBack.setBackground(new java.awt.Color(255, 255, 255));
        jButtonControlBack.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        jButtonControlBack.setText("Back");
        jButtonControlBack.setFocusable(false);
        jButtonControlBack.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jButtonControlBack.setMaximumSize(new java.awt.Dimension(21, 21));
        jButtonControlBack.setMinimumSize(new java.awt.Dimension(21, 21));
        jButtonControlBack.setPreferredSize(new java.awt.Dimension(21, 21));

        jButtonControlLJ.setBackground(new java.awt.Color(255, 255, 255));
        jButtonControlLJ.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        jButtonControlLJ.setText("LJ");
        jButtonControlLJ.setFocusable(false);
        jButtonControlLJ.setMargin(new java.awt.Insets(2, 2, 2, 2));
        jButtonControlLJ.setMaximumSize(new java.awt.Dimension(21, 21));
        jButtonControlLJ.setMinimumSize(new java.awt.Dimension(21, 21));
        jButtonControlLJ.setPreferredSize(new java.awt.Dimension(21, 21));

        jButtonControlRJ.setBackground(new java.awt.Color(255, 255, 255));
        jButtonControlRJ.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        jButtonControlRJ.setText("RJ");
        jButtonControlRJ.setFocusable(false);
        jButtonControlRJ.setMargin(new java.awt.Insets(2, 2, 2, 2));

        jButtonControlRT.setBackground(new java.awt.Color(255, 255, 255));
        jButtonControlRT.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        jButtonControlRT.setText("RT");
        jButtonControlRT.setFocusable(false);
        jButtonControlRT.setMargin(new java.awt.Insets(2, 2, 2, 2));

        jButtonControlRB.setBackground(new java.awt.Color(255, 255, 255));
        jButtonControlRB.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        jButtonControlRB.setText("RB");
        jButtonControlRB.setFocusable(false);
        jButtonControlRB.setMargin(new java.awt.Insets(2, 2, 2, 2));

        jButtonControlLB.setBackground(new java.awt.Color(255, 255, 255));
        jButtonControlLB.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        jButtonControlLB.setText("LB");
        jButtonControlLB.setFocusable(false);
        jButtonControlLB.setMargin(new java.awt.Insets(2, 2, 2, 2));

        jButtonControlLT.setBackground(new java.awt.Color(255, 255, 255));
        jButtonControlLT.setFont(new java.awt.Font("Tahoma", 1, 10)); // NOI18N
        jButtonControlLT.setText("LT");
        jButtonControlLT.setFocusable(false);
        jButtonControlLT.setMargin(new java.awt.Insets(2, 2, 2, 2));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabelTitleController)
                        .addGap(103, 103, 103))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addComponent(jButtonControlRJ)
                        .addGap(101, 101, 101))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addComponent(jButtonControlX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(91, 91, 91))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jButtonControlA, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jButtonControlY, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(64, 64, 64))))
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(39, 39, 39)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jComboBox_controllers, javax.swing.GroupLayout.PREFERRED_SIZE, 233, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                            .addComponent(jLabel4)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jProgressBarZ, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                            .addComponent(jLabel3)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jProgressBarY, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(jPanel1Layout.createSequentialGroup()
                            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                        .addComponent(jButtonControlLJ, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel6))
                                    .addGap(56, 56, 56))
                                .addGroup(jPanel1Layout.createSequentialGroup()
                                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel5)
                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                            .addGap(10, 10, 10)
                                            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                .addComponent(jButtonControlLT, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addComponent(jButtonControlLB, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE))))
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(jLabelDPad)
                                .addComponent(jProgressBarZrot, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(jButtonControlRT, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addComponent(jButtonControlB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jButtonControlRB, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                            .addComponent(jLabel2)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jProgressBarX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(jPanel1Layout.createSequentialGroup()
                            .addComponent(jButtonControlBack, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(jButtonControlStart, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGap(85, 85, 85))))
                .addContainerGap(39, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addComponent(jLabelTitleController)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jComboBox_controllers, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(74, 74, 74)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel5)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                            .addComponent(jProgressBarX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(jLabel2))
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(jProgressBarY, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addComponent(jLabel3))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jProgressBarZ, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jLabel4))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jProgressBarZrot, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(jLabelDPad))
                .addGap(12, 12, 12)
                .addComponent(jButtonControlY, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtonControlX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButtonControlB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButtonControlLJ, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtonControlA, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButtonControlBack, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButtonControlStart, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButtonControlRJ)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtonControlRB)
                    .addComponent(jButtonControlLB))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtonControlRT)
                    .addComponent(jButtonControlLT))
                .addGap(32, 32, 32))
        );

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

        javax.swing.GroupLayout jPanelCommLayout = new javax.swing.GroupLayout(jPanelComm);
        jPanelComm.setLayout(jPanelCommLayout);
        jPanelCommLayout.setHorizontalGroup(
            jPanelCommLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelCommLayout.createSequentialGroup()
                .addGap(230, 230, 230)
                .addComponent(jLabelTitleComm)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 461, Short.MAX_VALUE)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanelCommLayout.setVerticalGroup(
            jPanelCommLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jSeparator1)
            .addGroup(jPanelCommLayout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addComponent(jLabelTitleComm)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTabbed1.addTab("Communication", jPanelComm);

        jLabelUVlogo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/uvlogo.jpg"))); // NOI18N

        jLabel7.setIcon(new javax.swing.ImageIcon(getClass().getResource("/gicilogo.jpg"))); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbed1)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabelUVlogo)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel7)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jTabbed1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabelUVlogo)
                    .addComponent(jLabel7))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void bt_setWaypointsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_setWaypointsActionPerformed
        // TODO add your handling code here:
        if(!waypointsSet){
            magnaWaypointsSet = magnaWaypoints;
            jTextAreaConsole.append("Waypoints set\n");
            waypointsSet = true;
            bt_updateWaypoints.setEnabled(!waypointsSet);
            jTextPaneWaypoints.setFocusable(!waypointsSet);
            bt_setWaypoints.setText("Edit");
            tf_missionAltitude.setEnabled(!waypointsSet);
            tf_missionYaw.setEnabled(!waypointsSet);
            float missionAltitude = Float.valueOf(tf_missionAltitude.getText());
            float missionYaw = Float.valueOf(tf_missionYaw.getText());

            try {
                sendWaypointList(magnaWaypointsSet, missionAltitude, missionYaw);
            } catch (IOException ex) {
                Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else {
            waypointsSet = false;
            magnaWaypointsSet.clear();
            magnaWaypoints.clear();
            bt_updateWaypoints.setEnabled(!waypointsSet);
            jTextPaneWaypoints.setFocusable(!waypointsSet);
            tf_missionAltitude.setEnabled(!waypointsSet);
            tf_missionYaw.setEnabled(!waypointsSet);
            bt_setWaypoints.setText("Set");
            bt_setWaypoints.setEnabled(waypointsSet);

            try {
                resetWaypointList();
            } catch (IOException ex) {
                Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_bt_setWaypointsActionPerformed

    private void bt_updateWaypointsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_updateWaypointsActionPerformed
        try {
            // TODO add your handling code here:
            waypoints.clear();
            magnaWaypointsSet.clear();
            magnaWaypoints.clear();
            track.clear();

            String waypointsDoc = jTextPaneWaypoints.getDocument().getText(0, jTextPaneWaypoints.getDocument().getLength());
            jTextPaneWaypoints.setText("");
            String[] wps = waypointsDoc.split("\n");
            for(int i=0; i<=wps.length-1; i++){
                String[] parts = wps[i].split(" ");
                double east_coord = Double.parseDouble(parts[2]);
                double north_coord = Double.parseDouble(parts[5]);
                System.out.println("East: "+east_coord+"; North: "+north_coord);
                converted_ellip = convertToEllipCoordinates(east_coord, north_coord);
                GeoPosition gp = new GeoPosition(converted_ellip.y,converted_ellip.x);
                System.out.println("x: "+converted_ellip.x+", y: "+converted_ellip.y);
                addWaypoint(gp);
                jTextAreaConsole.append("Waypoints added\n");
                drawWaypointsAndRoute();
                jTextAreaConsole.append("Waypoints size: "+magnaWaypoints.size()+"\n");
            }
            jTextAreaConsole.append("Waypoints updated with "+magnaWaypoints.size()+" waypoints \n");
            bt_setWaypoints.setEnabled(true);
        } catch (BadLocationException ex) {
            Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_bt_updateWaypointsActionPerformed

    private void bt_disarmActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_disarmActionPerformed
        try {
            // TODO add your handling code here:
            missionControllerUV.armMotors(false);
            Thread.sleep(100);
        } catch (IOException ex) {
            Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_bt_disarmActionPerformed

    private void bt_armActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_armActionPerformed
        try {
            // TODO add your handling code here:
            missionControllerUV.armMotors(true);
        } catch (IOException ex) {
            Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_bt_armActionPerformed

    private void bt_clearConsoleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_clearConsoleActionPerformed
        // TODO add your handling code here:
        jTextAreaConsole.setText(null);
    }//GEN-LAST:event_bt_clearConsoleActionPerformed

    private void bt_LoiterModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_LoiterModeActionPerformed
        // TODO add your handling code here:
        int selectedOption = JOptionPane.showConfirmDialog(null,
            "Do you want to select Loiter mode?",
            "Choose",
            JOptionPane.YES_NO_OPTION);
        if (selectedOption == JOptionPane.YES_OPTION) {
            try {
                // TODO add your handling code here:
                MissionControllerUV.requestModeChange("Loiter");
            } catch (IOException ex) {
                Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_bt_LoiterModeActionPerformed

    private void bt_LandModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_LandModeActionPerformed
        // TODO add your handling code here:
        int selectedOption = JOptionPane.showConfirmDialog(null,
            "Do you want to select Land mode?",
            "Choose",
            JOptionPane.YES_NO_OPTION);
        if (selectedOption == JOptionPane.YES_OPTION) {
            try {
                // TODO add your handling code here:
                MissionControllerUV.requestModeChange("Land");
            } catch (IOException ex) {
                Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_bt_LandModeActionPerformed

    private void bt_AutoModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_AutoModeActionPerformed
        // TODO add your handling code here:
        int selectedOption = JOptionPane.showConfirmDialog(null,
            "Do you want to select Auto mode?",
            "Choose",
            JOptionPane.YES_NO_OPTION);
        if (selectedOption == JOptionPane.YES_OPTION) {
            try {
                // TODO add your handling code here:
                MissionControllerUV.requestModeChange("Auto");
            } catch (IOException ex) {
                Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_bt_AutoModeActionPerformed

    private void bt_RTLmodeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_RTLmodeActionPerformed
        // TODO add your handling code here:
        int selectedOption = JOptionPane.showConfirmDialog(null,
            "Do you want to select Return To Launch mode?",
            "Choose",
            JOptionPane.YES_NO_OPTION);
        if (selectedOption == JOptionPane.YES_OPTION) {
            try {
                // TODO add your handling code here:
                MissionControllerUV.requestModeChange("RTL");
            } catch (IOException ex) {
                Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_bt_RTLmodeActionPerformed

    private void bt_AltHoldModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_AltHoldModeActionPerformed
        // TODO add your handling code here:
        int selectedOption = JOptionPane.showConfirmDialog(null,
            "Do you want to select Altitude Hold mode?",
            "Choose",
            JOptionPane.YES_NO_OPTION);
        if (selectedOption == JOptionPane.YES_OPTION) {
            try {
                // TODO add your handling code here:
                MissionControllerUV.requestModeChange("AltHold");
            } catch (IOException ex) {
                Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_bt_AltHoldModeActionPerformed

    private void bt_stabilizeModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_stabilizeModeActionPerformed
        int selectedOption = JOptionPane.showConfirmDialog(null,
            "Do you want to select Stabilize mode?",
            "Choose",
            JOptionPane.YES_NO_OPTION);
        if (selectedOption == JOptionPane.YES_OPTION) {
            try {
                // TODO add your handling code here:
                MissionControllerUV.requestModeChange("Stabilize");
            } catch (IOException ex) {
                Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_bt_stabilizeModeActionPerformed

    private void bt_stopConnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_stopConnectionActionPerformed
        int selectedOption = JOptionPane.showConfirmDialog(null,
            "Do you want to stop the connection with the Quadrotor?",
            "Choose",
            JOptionPane.YES_NO_OPTION);
        if (selectedOption == JOptionPane.YES_OPTION) {
            stopConnectionWithServer();
        }

    }//GEN-LAST:event_bt_stopConnectionActionPerformed

    private void bt_startConnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_startConnectionActionPerformed
        // TODO add your handling code here:
        MissionControllerUV.startConnection();
    }//GEN-LAST:event_bt_startConnectionActionPerformed
    
    public void armMotorsFromRC(){
        try {
            // TODO add your handling code here:
            missionControllerUV.armMotors(true);
        } catch (IOException ex) {
            Logger.getLogger(MissionGUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void establishConnectionFromRC(){
        MissionControllerUV.startConnection();
    }
    
    public void addControllerName(String controllerName){
        jComboBox_controllers.addItem(controllerName);
    }
    
    public int getSelectedControllerName(){
        return jComboBox_controllers.getSelectedIndex();
    }
    
    public void showControllerDisconnected(){
        jComboBox_controllers.removeAllItems();
        jComboBox_controllers.addItem("Controller disconnected!");
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JButton bt_AltHoldMode;
    public javax.swing.JButton bt_AutoMode;
    public javax.swing.JButton bt_LandMode;
    public javax.swing.JButton bt_LoiterMode;
    public javax.swing.JButton bt_RTLmode;
    public javax.swing.JButton bt_arm;
    private javax.swing.JButton bt_clearConsole;
    public javax.swing.JButton bt_disarm;
    public javax.swing.JButton bt_setWaypoints;
    public javax.swing.JButton bt_stabilizeMode;
    public javax.swing.JButton bt_startConnection;
    public javax.swing.JButton bt_stopConnection;
    public javax.swing.JButton bt_updateWaypoints;
    private javax.swing.JComboBox<String> cb_QuadList;
    public javax.swing.JButton jButtonControlA;
    public javax.swing.JButton jButtonControlB;
    public javax.swing.JButton jButtonControlBack;
    public javax.swing.JButton jButtonControlLB;
    public javax.swing.JButton jButtonControlLJ;
    public javax.swing.JButton jButtonControlLT;
    public javax.swing.JButton jButtonControlRB;
    public javax.swing.JButton jButtonControlRJ;
    public javax.swing.JButton jButtonControlRT;
    public javax.swing.JButton jButtonControlStart;
    public javax.swing.JButton jButtonControlX;
    public javax.swing.JButton jButtonControlY;
    private javax.swing.JComboBox<String> jComboBox_controllers;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JLabel jLabelArmed;
    public javax.swing.JLabel jLabelDPad;
    private javax.swing.JLabel jLabelTitleComm;
    private javax.swing.JLabel jLabelTitleController;
    private javax.swing.JLabel jLabelUVlogo;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanelComm;
    private javax.swing.JPanel jPanelIndicators;
    private javax.swing.JPanel jPanelMap;
    private javax.swing.JPanel jPanelMission;
    private javax.swing.JPanel jPanelModes;
    public javax.swing.JProgressBar jProgressBarX;
    public javax.swing.JProgressBar jProgressBarY;
    public javax.swing.JProgressBar jProgressBarZ;
    public javax.swing.JProgressBar jProgressBarZrot;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JSeparator jSeparator6;
    private javax.swing.JSeparator jSeparator7;
    public javax.swing.JTextField jTF_ip1;
    private javax.swing.JTabbedPane jTabbed1;
    public javax.swing.JTextArea jTextAreaConsole;
    public javax.swing.JTextPane jTextPaneWaypoints;
    public javax.swing.JLabel tf_armed;
    public javax.swing.JLabel tf_currentflightmode;
    public javax.swing.JTextField tf_missionAltitude;
    public javax.swing.JTextField tf_missionYaw;
    public javax.swing.JLabel tv_eastQuad;
    public javax.swing.JLabel tv_elevationQuad;
    public javax.swing.JLabel tv_northQuad;
    public javax.swing.JLabel tv_phoneBatt;
    public javax.swing.JLabel tv_pitchQuad;
    public javax.swing.JLabel tv_quadBatt;
    public javax.swing.JLabel tv_rollQuad;
    public javax.swing.JLabel tv_yawQuad;
    // End of variables declaration//GEN-END:variables

}

