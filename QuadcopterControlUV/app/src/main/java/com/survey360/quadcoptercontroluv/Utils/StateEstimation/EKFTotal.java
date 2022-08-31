package com.survey360.quadcoptercontroluv.Utils.StateEstimation;

/**
 * Created by Alejandro Astudillo on 17/09/2017.
 */

import org.ejml.data.DMatrixRMaj;

/**
 * <p>
 * This is an interface for a discrete time Kalman filter with no control input:<br>
 * <br>
 * x<sub>k</sub> = F<sub>k</sub> x<sub>k-1</sub> + w<sub>k</sub><br>
 * z<sub>k</sub> = H<sub>k</sub> x<sub>k</sub> + v<sub>k</sub> <br>
 * <br>
 * w<sub>k</sub> ~ N(0,Q<sub>k</sub>)<br>
 * v<sub>k</sub> ~ N(0,R<sub>k</sub>)<br>
 * </p>
 *
 * @author Peter Abeles
 */
public interface EKFTotal {

    /**
     * Specify the kinematics model of the Kalman filter.  This must be called
     * first before any other functions.
     *
     * @param F State transition matrix.
     * @param Q plant noise.
     * @param H measurement projection matrix.
     */
    void configure(DMatrixRMaj F, DMatrixRMaj Q,
                   DMatrixRMaj H);

    /**
     * The prior state estimate and covariance.
     *
     * @param x The estimated system state.
     * @param P The covariance of the estimated system state.
     */
    void setState(DMatrixRMaj x, DMatrixRMaj P);

    /**
     * Predicts the state of the system forward one time step.
     */
    void predict(double u1, double u2, double u3, double u4, double dt, double m, double Izz, double Iyy, double Ixx);

    /**
     * Updates the state provided the observation from a sensor.
     *
     * @param z Measurement.
     * @param R Measurement covariance.
     */
    void update(DMatrixRMaj z, DMatrixRMaj R);

    /**
     * Returns the current estimated state of the system.
     *
     * @return The state.
     */
    DMatrixRMaj getState();

    /**
     * Returns the estimated state's covariance matrix.
     *
     * @return The covariance.
     */
    DMatrixRMaj getCovariance();
}
