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
 * Created by Alejandro Astudillo on 17/09/2017.
 */

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolver;


import static org.ejml.dense.row.CommonOps_DDRM.*;

public class KalmanFilterOperationsTotal implements KalmanFilterTotal {

    // kinematics description
    private DMatrixRMaj F,G,Q,H;

    // system state estimate
    private DMatrixRMaj x,P;

    // these are predeclared for efficiency reasons
    private DMatrixRMaj a,b,o;
    private DMatrixRMaj y,S,S_inv,c,d;
    private DMatrixRMaj K;

    private LinearSolver<DMatrixRMaj> solver;

    @Override
    public void configure(DMatrixRMaj F, DMatrixRMaj G, DMatrixRMaj Q, DMatrixRMaj H) {
        this.F = F;
        this.Q = Q;
        this.H = H;
        this.G = G;

        int dimenX = F.numCols;
        int dimenZ = H.numRows;
        int dimenU = G.numCols;

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
        //u = new DMatrixRMaj(dimenU,1);
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
    public void predict(DMatrixRMaj u) {

        // x = F x + G u
        mult(F,x,a);  // a = F x
        mult(G,u,o);  // o = G u
        add(a,o,x); // x = a + o

        // P = F P F' + Q
        mult(F,P,b);
        multTransB(b,F, P);
        addEquals(P,Q);
        //System.out.println("P: "+P);
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

        // K = PH'S^(-1)
        if( !solver.setA(S) ) throw new RuntimeException("Invert failed");
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