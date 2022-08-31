package com.survey360.quadcoptercontroluv.Utils.StateEstimation;

/**
 * Created by Alejandro Astudillo on 17/09/2017.
 */
import org.ejml.data.DMatrixRMaj;
import org.ejml.equation.Equation;
import org.ejml.equation.Sequence;

/**
 * Example of how the equation interface can greatly simplify code
 *
 * @author Peter Abeles
 */
public class KalmanFilterEquation implements KalmanFilter{

    // system state estimate
    private DMatrixRMaj x,P;

    private Equation eq;

    // Storage for precompiled code for predict and update
    Sequence predictX,predictP;
    Sequence updateY,updateK,updateX,updateP;

    @Override
    public void configure(DMatrixRMaj F, DMatrixRMaj Q, DMatrixRMaj H) {
        int dimenX = F.numCols;

        x = new DMatrixRMaj(dimenX,1);
        P = new DMatrixRMaj(dimenX,dimenX);

        eq = new Equation();

        // Provide aliases between the symbolic variables and matrices we normally interact with
        // The names do not have to be the same.
        eq.alias(x,"x",P,"P",Q,"Q",F,"F",H,"H");

        // Dummy matrix place holder to avoid compiler errors.  Will be replaced later on
        eq.alias(new DMatrixRMaj(1,1),"z");
        eq.alias(new DMatrixRMaj(1,1),"R");

        // Pre-compile so that it doesn't have to compile it each time it's invoked.  More cumbersome
        // but for small matrices the overhead is significant
        predictX = eq.compile("x = F*x");
        predictP = eq.compile("P = F*P*F' + Q");

        updateY = eq.compile("y = z - H*x");
        updateK = eq.compile("K = P*H'*inv( H*P*H' + R )");
        updateX = eq.compile("x = x + K*y");
        updateP = eq.compile("P = P-K*(H*P)");
    }

    @Override
    public void setState(DMatrixRMaj x, DMatrixRMaj P) {
        this.x.set(x);
        this.P.set(P);
    }

    @Override
    public void predict() {
        predictX.perform();
        predictP.perform();
    }

    @Override
    public void update(DMatrixRMaj z, DMatrixRMaj R) {

        // Alias will overwrite the reference to the previous matrices with the same name
        eq.alias(z,"z"); eq.alias(R,"R");

        updateY.perform();
        updateK.perform();
        updateX.perform();
        updateP.perform();
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


