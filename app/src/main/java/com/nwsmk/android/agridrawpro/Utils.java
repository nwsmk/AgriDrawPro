package com.nwsmk.android.agridrawpro;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.maps.android.SphericalUtil;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;

/**
 * Created by nwsmk on 6/27/2017.
 */

public class Utils {
    /** Sum of a matrix */
    public static int sum2D(int[][] m) {
        int sum = 0;

        int num_rows = m.length;
        int num_cols = m[0].length;

        for (int i = 0; i < num_rows; i++) {
            for (int j = 0; j < num_cols; j++) {
                sum = sum + m[i][j];
            }
        }
        return sum;
    }


}
