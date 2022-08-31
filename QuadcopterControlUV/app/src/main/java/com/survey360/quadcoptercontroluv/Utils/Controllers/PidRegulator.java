package com.survey360.quadcoptercontroluv.Utils.Controllers;

/**
 * Created by AAstudillo on 23/11/2017.
 */

public class PidRegulator
{
    public PidRegulator(float kp, float ki, float kd, float smoothingStrength,
                        float aPriori)
    {
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
        this.smoothingStrength = smoothingStrength;
        this.aPriori = aPriori;

        previousError = 0.0f;

        integrator = 0.0f;
        errorMean = 0.0f;
    }

    public float getInput(float error, float dt)
    {

        // Now, the PID computation can be done.
        float input = aPriori;

        // Proportional part.
        input += error * kp;

        // Integral part.
        integrator += error * ki * dt;
        input += integrator;

        // Derivative part, with filtering.
        errorMean = errorMean*smoothingStrength + error*(1-smoothingStrength);
        derivative = (errorMean - previousError) / dt;
        previousError = errorMean;
        input += derivative * kd;

        return input;
    }

    public void setCoefficients(float kp, float ki, float kd)
    {
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
    }

    public void setAPriori(float aPriori)
    {
        this.aPriori = aPriori;
    }

    public void resetIntegrator()
    {
        integrator = 0.0f;
    }

    private float kp, ki, kd, integrator, derivative, smoothingStrength, errorMean,
            previousError, aPriori;
}
