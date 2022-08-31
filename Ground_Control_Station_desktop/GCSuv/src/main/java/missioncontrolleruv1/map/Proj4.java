/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package missioncontrolleruv1.map;

import org.osgeo.proj4j.CRSFactory;
import org.osgeo.proj4j.CoordinateReferenceSystem;
import org.osgeo.proj4j.CoordinateTransform;
import org.osgeo.proj4j.CoordinateTransformFactory;
import org.osgeo.proj4j.ProjCoordinate;
/**
 *
 * @author AAstudillo
 */

public final class Proj4 {

    public CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
    public CRSFactory csFactory = new CRSFactory();

    public CoordinateReferenceSystem crsWGS84, crsECEF, crsMagnaSirgasWest, crsMagnaSirgasCali;

    public Proj4() {
        Configure();
    }


    public void Configure(){

        crsWGS84 = csFactory.createFromName("EPSG:4326");
        //crsECEF = csFactory.createFromParameters("EPSG:4978", "+proj=geocent +datum=WGS84 +units=m +no_defs");
        crsMagnaSirgasWest = csFactory.createFromName("EPSG:3115");
        crsMagnaSirgasCali = csFactory.createFromParameters("SR-ORG:7664", "+proj=tmerc +lat_0=3.441883333 +lon_0=-76.5205625 +k=1 +x_0=1061900.18 +y_0=872364.63 +a=6379137 +b=6357748.961329674 +units=m +no_defs");


    }

    public ProjCoordinate TransformCoordinates(ProjCoordinate p_in, CoordinateReferenceSystem crs_in, CoordinateReferenceSystem crs_out){

        ProjCoordinate p_out = new ProjCoordinate();
        CoordinateTransform transformation = ctFactory.createTransform(crs_in, crs_out);

        transformation.transform(p_in, p_out);

        return p_out;
    }

    public double ConvertToDecimalDegrees(double degrees, double minutes, double seconds){
        double result = 0;
        if (degrees < 0) {result = degrees + (-1*((minutes/60) + (seconds/3600)));}
        if (degrees >= 0) {result = degrees + (minutes/60) + (seconds/3600);}
        return result;
    }

    public double[] ConvertToDMS(double decimaldegrees){
        double result[] = new double[3];
        result[0] = (long) decimaldegrees;                              //DEGREES
        result[1] = (long) ((decimaldegrees - result[0])*60);           //MINUTES
        result[2] = Math.abs((decimaldegrees - result[0] - (result[1]/60))*3600); //SECONDS

        result[1] = Math.abs(result[1]);
        return result;
    }


}
