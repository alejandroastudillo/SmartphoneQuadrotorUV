package com.survey360.quadcoptercontroluv.Utils.StateEstimation;

import android.content.Context;

import org.ejml.data.DMatrixRMaj;

/**
 * Created by AAstudillo on 03/11/2017.
 */

public class AltHoldKalmanFilter {
    public InitialConditions mInitialConditions = null;

    private static final double dt = 0.01; //Our sample time
    private static final double Q_val = 0.05;
    //private static final double R_val = 1;
    private static final double R_val = 10;
    public double x_ic, y_ic, z_ic, psi_ic, theta_ic, phi_ic = 0;

    double[] ic, x_hat;
    DMatrixRMaj xhat_k_1, P_k_1, A, B, Q, Ro, H, z, U;
    KalmanFilterTotal f;

    public AltHoldKalmanFilter(Context context) {
        mInitialConditions = new InitialConditions(context);
        mInitialConditions.acquireIC();
    }

    public void initAltHoldKF(float mass, float i_xx, float i_yy, float i_zz){
        x_ic = mInitialConditions.getx_ic();
        y_ic = mInitialConditions.gety_ic();
        z_ic = mInitialConditions.getz_ic();
        psi_ic = mInitialConditions.getpsi_ic();
        theta_ic = mInitialConditions.gettheta_ic();
        phi_ic = mInitialConditions.getphi_ic();

        f = new KalmanFilterOperationsTotal();

        ic = new double[]{x_ic, 0 , y_ic, 0, z_ic , 0 , psi_ic , 0 , theta_ic , 0 , phi_ic , 0 };
        xhat_k_1 = new DMatrixRMaj(12, 1, true, ic);
        P_k_1 = new DMatrixRMaj(12,12); //9x9 matrix of zeros

        A = createAfull();
        B = createBfull();
        Q = createQfull(dt*Q_val);
        Ro = createR_6(R_val); // full
        H = createH_6(); //full
        z = new DMatrixRMaj(6, 1); //9x1 vector of zeros
        x_hat = new double[12];

        f.configure(A,B,Q,H);
        f.setState(xhat_k_1, P_k_1);

    }

    //public void executeAltHoldKF(float posx, float posy, float posz, float psi, float psi_dot, float theta, float theta_dot, float phi, float phi_dot, float[] u){
    //public void executeAltHoldKF(float posx, float posy, float posz, float psi, float theta, float phi, float[] u, float MG){public void executeAltHoldKF(float posx, float posy, float posz, float psi, float theta, float phi, float[] u, float MG){
    //public void executeAltHoldKF(float posx, float posy, float posz, float psi, float psi_dot, float theta, float theta_dot, float phi, float phi_dot, float u, float tau_psi, float tau_theta, float tau_phi){
    public void executeAltHoldKF(float posx, float posy, float posz, float psi, float theta, float phi, float u, float tau_psi, float tau_theta, float tau_phi){
        //z.setData(new double[]{posx, posy, posz,psi,psi_dot,theta,theta_dot,phi,phi_dot});
        z.setData(new double[]{posx, posy, posz, psi, theta, phi});
        //U = new DMatrixRMaj(4,1, true, new double[]{(-u[0]+17.74),-u[1]/10,-u[2]/10,-u[3]/10});
        U = new DMatrixRMaj(4,1, true, new double[]{-u,-tau_psi/10,-tau_theta/10,-tau_phi/10});
        f.predict(U);
        f.update(z,Ro);
    }

    public double[] getEstimatedState(){
        x_hat = f.getState().getData();
        return x_hat;
    }

    public static DMatrixRMaj createAfull() {
        double []a = new double[]{
                1,  0.01, 0 , 0 ,  0 , 0 ,  0 , 0 , 0.0004903 , 0.000001634 , 0 , 0 ,
                0,  1,    0 , 0 ,  0 , 0 ,  0 , 0 , 0.09807 , 0.0004903 , 0 , 0 ,
                0,  0,    1, 0.01, 0 , 0 ,  0 , 0 , 0 , 0 , 0.0004903 , 0.000001634,
                0,  0,    0 , 1 ,  0 , 0 ,  0 , 0 , 0 , 0 , 0.09807 , 0.0004903 ,
                0 , 0,    0,  0 ,  1, 0.01, 0 , 0 , 0 , 0 , 0 , 0 ,
                0,  0,    0 , 0 ,  0 , 1 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0,  0,    0,  0 ,  0 , 0 , 1 , 0.01 , 0 , 0 , 0 , 0 ,
                0,  0,    0 , 0 ,  0 , 0 , 0 , 1 , 0 , 0 , 0 , 0 ,
                0,  0,    0,  0 ,  0 , 0 , 0 , 0 , 1 , 0.01 , 0 , 0 ,
                0,  0,    0 , 0 ,  0 , 0 , 0 , 0 , 0 , 1 , 0 , 0 ,
                0,  0,    0,  0 ,  0 , 0 , 0 , 0 , 0 , 0 , 1 , 0.01 ,
                0,  0,    0 , 0 ,  0 , 0 , 0 , 0 , 0 , 0 , 0 , 1 };
        return new DMatrixRMaj(12,12, true, a);
    }

    public static DMatrixRMaj createBfull() {
        double []b = new double[]{
                0 ,         0 ,   0.0000003295, 0 ,
                0 ,         0 ,    0.0001318 ,  0 ,
                0 ,         0 ,       0 ,    0.0000003027 ,
                0 ,         0 ,       0 ,     0.0001211 ,
                0.00003189, 0 ,       0 ,       0 ,
                0.006378,   0 ,       0 ,       0,
                0,          0.001488, 0 ,       0 ,
                0,          0.2976,   0 ,       0 ,
                0,          0 ,       0.004032, 0 ,
                0,          0 ,       0.8065,   0 ,
                0,          0 ,       0 ,       0.003704,
                0,          0 ,       0 ,       0.7407};
        return new DMatrixRMaj(12,4, true, b);
    }

    public static DMatrixRMaj createQfull(double var) {
        double []q = new double[]{
                var*10, 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, var , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , var*10 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , var , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , var*10 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , var , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , var , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 0 , var , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 0 , 0 , var , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , var , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , var , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , var };
        return new DMatrixRMaj(12,12, true, q);
    }

    public static DMatrixRMaj createHfull() {
        double []h = new double[]{
                1, 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 1 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 1 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 1 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 0 , 1 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 0 , 0 , 1 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 1 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 1 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 1 };
        return new DMatrixRMaj(9,12, true, h);
    }

    public static DMatrixRMaj createRfull(double var) {
        double []r = new double[]{
                var*1000, 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, var*1000 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , var*1000 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , var , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , var , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , var , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0, var , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0, 0 , var , 0 ,
                0, 0 , 0 , 0 , 0 , 0, 0 , 0 , var};
        return new DMatrixRMaj(9,9, true, r);
    }

    public static DMatrixRMaj createR_6(double var) {
        double []r = new double[]{
                var*1000, 0 , 0 , 0 , 0 , 0 ,
                0, var*1000 , 0 , 0 , 0 , 0 ,
                0, 0 , var*1000 , 0 , 0 , 0 ,
                0, 0 , 0 , var , 0 , 0 ,
                0, 0 , 0 , 0 , var , 0 ,
                0, 0 , 0 , 0 , 0 , var };
        return new DMatrixRMaj(6,6, true, r);
    }

    public static DMatrixRMaj createH_6() {
        double []h = new double[]{
                1, 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 1 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 1 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 1 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 0 , 0 , 1 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 1 , 0 };
        return new DMatrixRMaj(6,12, true, h);
    }
}
