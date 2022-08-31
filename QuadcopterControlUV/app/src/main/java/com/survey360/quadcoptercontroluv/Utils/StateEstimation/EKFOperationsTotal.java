/*
 * Copyright (c) 2009-2017, Peter Abeles. All Rights Reserved.
 *
 * This file is part of Efficient Java Matrix Library (EJML).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.survey360.quadcoptercontroluv.Utils.StateEstimation;

/**
 * Created by Alejandro Astudillo on 17/04/2018.
 */

import android.util.Log;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolver;
import org.ejml.simple.SimpleMatrix;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static org.ejml.dense.row.CommonOps_DDRM.add;
import static org.ejml.dense.row.CommonOps_DDRM.addEquals;
import static org.ejml.dense.row.CommonOps_DDRM.mult;
import static org.ejml.dense.row.CommonOps_DDRM.multTransA;
import static org.ejml.dense.row.CommonOps_DDRM.multTransB;
import static org.ejml.dense.row.CommonOps_DDRM.subtract;
import static org.ejml.dense.row.CommonOps_DDRM.subtractEquals;

public class EKFOperationsTotal implements EKFTotal {

    // kinematics description
    private DMatrixRMaj F,Q,H;

    // system state estimate
    private DMatrixRMaj x,P;

    // these are predeclared for efficiency reasons
    private DMatrixRMaj a,b,o;
    private DMatrixRMaj y,S,S_inv,c,d;
    private DMatrixRMaj K;

    private LinearSolver<DMatrixRMaj> solver;

    // predictions
    private double x_k_, xdot_k_, y_k_, ydot_k_, z_k_, zdot_k_, psi_k_, psidot_k_, theta_k_, thetadot_k_, phi_k_, phidot_k_;
    private double[] A_aux, x_est;

    @Override
    public void configure(DMatrixRMaj F, DMatrixRMaj Q, DMatrixRMaj H) {
        this.F = F;
        this.Q = Q;
        this.H = H;

        int dimenX = F.numCols;
        int dimenZ = H.numRows;

        a = new DMatrixRMaj(dimenX,1);
        o = new DMatrixRMaj(dimenX,1);
        b = new DMatrixRMaj(dimenX,dimenX);
        y = new DMatrixRMaj(dimenZ,1);
        S = new DMatrixRMaj(dimenZ,dimenZ);
        S_inv = new DMatrixRMaj(dimenZ,dimenZ);
        c = new DMatrixRMaj(dimenZ,dimenX);
        d = new DMatrixRMaj(dimenX,dimenZ);
        K = new DMatrixRMaj(dimenX,dimenZ);

        x = new DMatrixRMaj(dimenX,1);
        P = new DMatrixRMaj(dimenX,dimenX);

        // covariance matrices are symmetric positive semi-definite
        solver = LinearSolverFactory_DDRM.symmPosDef(dimenX);
    }

    @Override
    public void setState(DMatrixRMaj x, DMatrixRMaj P) {
        this.x.set(x);
        this.P.set(P);
    }

    @Override
    public void predict(double u1, double u2, double u3, double u4, double dt, double m, double Izz, double Iyy, double Ixx) {

        // New Jacobian calculation
        x_est = x.getData();
        A_aux = new double[]{
                1, dt, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 1, 0, 0, 0, 0, ((dt*u1/m)*(cos(x_est[6])*sin(x_est[10])-sin(x_est[6])*sin(x_est[8])*cos(x_est[10]))), 0, ((dt*u1/m)*(cos(x_est[6])*cos(x_est[8])*cos(x_est[10]))), 0, ((dt*u1/m)*(sin(x_est[6])*cos(x_est[10])+cos(x_est[6])*sin(x_est[8])*(-sin(x_est[10])))), 0,
                0, 0, 1, dt, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 1, 0, 0, ((dt*u1/m)*(-cos(x_est[6])*sin(x_est[8])*cos(x_est[10])-sin(x_est[6])*sin(x_est[10]))), 0, ((dt*u1/m)*(-sin(x_est[6])*cos(x_est[8])*cos(x_est[10]))), 0, ((dt*u1/m)*(-sin(x_est[6])*sin(x_est[8])*(-sin(x_est[10]))+cos(x_est[6])*cos(x_est[10]))), 0,
                0, 0, 0, 0, 1, dt, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 1, 0, 0, ((dt*u1/m)*(-sin(x_est[8])*cos(x_est[10]))), 0, ((dt*u1/m)*(-sin(x_est[10])*cos(x_est[8]))), 0,
                0, 0, 0, 0, 0, 0, 1, dt, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, ((dt*(x_est[11])*((Ixx-Iyy)/Izz))), 0, ((dt*(x_est[9])*((Ixx-Iyy)/Izz))),
                0, 0, 0, 0, 0, 0, 0, 0, 1, dt, 0, 0,
                0, 0, 0, 0, 0, 0, 0, ((dt*(x_est[11])*((Izz-Ixx)/Iyy))), 0, 1, 0, ((dt*(x_est[7])*((Izz-Ixx)/Iyy))),
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, dt,
                0, 0, 0, 0, 0, 0, 0, ((dt*(x_est[9])*((Iyy-Izz)/Ixx))), 0, ((dt*(x_est[7])*((Iyy-Izz)/Ixx))), 0, 1};
        F.setData(A_aux);

        // Prediction from the non-linear model
        x_k_  = x_est[0] + dt*x_est[1];
        xdot_k_  = x_est[1]  + dt*((u1/m)*(sin(x_est[6])*sin(x_est[10]) + cos(x_est[6])*sin(x_est[8])*cos(x_est[10])));
        y_k_  = x_est[2]  + dt*x_est[3];
        ydot_k_  = x_est[3]  + dt*((u1/m)*(-sin(x_est[6])*sin(x_est[8])*cos(x_est[10]) + cos(x_est[6])*sin(x_est[10])));
        z_k_  = x_est[4]  + dt*x_est[5];
        zdot_k_  = x_est[5]  + dt*(((u1/m)*cos(x_est[8])*cos(x_est[10])) - 9.807);
        psi_k_  = x_est[6]  + dt*x_est[7];
        psidot_k_  = x_est[7]  + dt*((((Ixx-Iyy)/Izz)*x_est[11]*x_est[9]) + (u2/Izz));
        theta_k_  = x_est[8]  + dt*x_est[9];
        thetadot_k_ = x_est[9] + dt*((((Izz-Ixx)/Iyy)*x_est[11]*x_est[7]) + (u3/Iyy));
        phi_k_ = x_est[10] + dt*x_est[11];
        phidot_k_ = x_est[11] + dt*((((Iyy-Izz)/Ixx)*x_est[9]*x_est[7]) + (u4/Ixx));

        x.setData(new double[]{x_k_, xdot_k_, y_k_, ydot_k_, z_k_, zdot_k_, psi_k_, psidot_k_, theta_k_, thetadot_k_, phi_k_, phidot_k_});

        // P calculation, P = F P F' + Q
        mult(F,P,b);
        multTransB(b,F, P);
        addEquals(P,Q);

    }

    @Override
    public void update(DMatrixRMaj z, DMatrixRMaj R) {
        // y = z - H x
        mult(H,x,y);
        subtract(z, y, y);

        // S = H P H' + R
        mult(H,P,c);
        multTransB(c,H,S);
        addEquals(S,R);
        //System.out.println("////////////////////////////////////////////////////////////////////////////");
        //System.out.println("S -> " +S.getNumRows()+ " x " + S.getNumCols() + ", Det(S) = "+org.ejml.dense.row.CommonOps_DDRM.det(S));

        // K = PH'S^(-1)
        //if( !solver.setA(S) ) throw new RuntimeException("Invert failed");
        //if (org.ejml.dense.row.CommonOps_DDRM.det(S) == 0) {
        //    Log.e("WARNING", "Determinant of S = 0"); }
        if( !solver.setA(S) ){ Log.e("WARNING ---_----", "S - Not set");}
        solver.invert(S_inv);
        multTransA(H,S_inv,d);
        mult(P,d,K);

        // x = x + Ky
        mult(K,y,a);
        addEquals(x,a);

        // P = (I-kH)P = P - (KH)P = P-K(HP)
        mult(H,P,c);
        mult(K,c,b);
        subtractEquals(P, b);
        Log.e("-------------------","Executed EKF");
    }

    @Override
    public DMatrixRMaj getState() {
        return x;
    }

    @Override
    public DMatrixRMaj getCovariance() {
        return P;
    }
}