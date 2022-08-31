package com.survey360.quadcoptercontroluv.Utils.StateEstimation;

import android.content.Context;

import org.ejml.data.DMatrixRMaj;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

/**
 * Created by AAstudillo on 11/05/2018.
 */

public class EKF {
    public InitialConditions mInitialConditions = null;

    private static final double dt = 0.01; //Our sample time
    private static final double Q_val = 0.05;
    private static final double R_val = 1;
    public double x_ic, y_ic, z_ic, psi_ic, theta_ic, phi_ic = 0;
    public double Mass, Izz, Iyy, Ixx;

    double[] ic, x_hat;
    DMatrixRMaj xhat_k_1, P_k_1, A, B, Q, Ro, H, z, U;
    EKFTotal f;

    public EKF(Context context) {
        mInitialConditions = new InitialConditions(context);
        mInitialConditions.acquireIC();
    }

    public void initEKF(float mass, float i_xx, float i_yy, float i_zz){
        Mass = mass;
        Izz = i_zz;
        Iyy = i_yy;
        Ixx = i_xx;

        x_ic = mInitialConditions.getx_ic();
        y_ic = mInitialConditions.gety_ic();
        z_ic = mInitialConditions.getz_ic();
        psi_ic = mInitialConditions.getpsi_ic();
        theta_ic = mInitialConditions.gettheta_ic();
        phi_ic = mInitialConditions.getphi_ic();

        f = new EKFOperationsTotal();

        ic = new double[]{x_ic, 0 , y_ic, 0, z_ic , 0 , psi_ic , 0 , theta_ic , 0 , phi_ic , 0 };
        xhat_k_1 = new DMatrixRMaj(12, 1, true, ic);
        P_k_1 = new DMatrixRMaj(12,12); //9x9 matrix of zeros

        A = new DMatrixRMaj(12, 12);
        Q = createQ(dt*Q_val);
        Ro = createR(R_val); // full
        H = createH(); //full
        z = new DMatrixRMaj(4, 1); //4x1 vector of zeros
        x_hat = new double[12];

        f.configure(A,Q,H);
        f.setState(xhat_k_1, P_k_1);

    }

    public void executeEKF(float posx, float posy, float posz, float psi, float u, float tau_psi, float tau_theta, float tau_phi){
        z.setData(new double[]{posx, posy, posz, psi});

        f.predict(u,tau_psi,tau_theta,tau_phi,dt,Mass,Izz,Iyy,Ixx);
        f.update(z,Ro);
    }

    public double[] getEstimatedState(){
        x_hat = f.getState().getData();
        return x_hat;
    }

    public static DMatrixRMaj createQ(double var) {
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

    public static DMatrixRMaj createR(double var) {
        double []r = new double[]{
                var*100, 0 , 0 , 0 ,
                0, var*100 , 0 , 0 ,
                0, 0 , var*100 , 0 ,
                0, 0 , 0 , var};
        return new DMatrixRMaj(4,4, true, r);
    }

    public static DMatrixRMaj createH() {
        double []h = new double[]{
                1, dt , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 1 , dt , 0 , 0 , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 1 , dt , 0 , 0 , 0 , 0 , 0 , 0 ,
                0, 0 , 0 , 0 , 0 , 0 , 1 , dt , 0 , 0 , 0 , 0 };
        return new DMatrixRMaj(4,12, true, h);
    }
}
