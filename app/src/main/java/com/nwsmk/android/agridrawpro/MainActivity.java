package com.nwsmk.android.agridrawpro;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.maps.android.SphericalUtil;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.w3c.dom.Text;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.nwsmk.android.agridrawpro.Utils.sum2D;
import static java.lang.Math.abs;
import static java.lang.Math.floor;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private final Context mContext = this;
    private static final int MY_PERMISSIONS_REQ = 0x01;
    private boolean totalPermissions = false;
    private final static int MODE_MANUAL = 0;
    private final static int MODE_AUTO = 1;

    private int opMode = MODE_MANUAL;

    private GoogleMap mMap;

    // UI variables
    private boolean seedSelectFlag = true;

    // Main UI component
    private TextView statusTxt;
    private Button autoButton;
    private Button manualButton;
    private ProgressDialog mProgressDialog;

    // Process variables
    private double userArea;
    private Point seedPoint;
    private int r_threshold = 0;    // region grow
    private int d_threshold = 0;    // contour approximation


    /** Initialize OpenCV library */
    private BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {

        @Override
        public void onManagerConnected(int status) {

            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.i("OpenCV", "OpenCV loaded successfully");
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check required permissions
        if (Build.VERSION.SDK_INT < 23) {
            // no need to check permissions
        } else {
            if (checkPermissions()) {
                totalPermissions = true;
                Log.d("Main Permissions", "You're good to go!");
            } else {

            }
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Get main UI component
        // progress dialog
        mProgressDialog = new ProgressDialog(this);

        // status
        statusTxt = (TextView) findViewById(R.id.txt_status);

        // auto button
        autoButton = (Button) findViewById(R.id.btn_auto);
        autoButton.setEnabled(false);
        autoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // show auto dialog
                // get auto layout view
                LayoutInflater li = LayoutInflater.from(mContext);
                View autoView = li.inflate(R.layout.diag_auto, null);
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
                // set auto view to dialog
                alertDialogBuilder.setView(autoView);

                // inputs
                final EditText userReqArea = (EditText) autoView.findViewById(R.id.edit_area);
                userReqArea.setGravity(Gravity.CENTER);

                // set dialog message
                alertDialogBuilder
                        .setCancelable(true)
                        .setPositiveButton("Start Drawing", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                opMode = MODE_AUTO;
                                mMap.clear();

                                userArea = Double.parseDouble(userReqArea.getText().toString());

                                // reset thresholds
                                r_threshold = 0;
                                d_threshold = 0;

                                // start edge detection process
                                mProgressDialog.setMessage("Drawing...");
                                mProgressDialog.setCancelable(false);
                                mProgressDialog.show();

                                // capture current map image
                                GoogleMap.SnapshotReadyCallback callback = new GoogleMap.SnapshotReadyCallback() {
                                    Bitmap bmp;

                                    @Override
                                    public void onSnapshotReady(Bitmap snapshot) {
                                        bmp = snapshot;

                                        try {
                                            FileOutputStream out = new FileOutputStream("/mnt/sdcard/Pictures/out.png");
                                            bmp.compress(Bitmap.CompressFormat.PNG, 90, out);

                                            new EdgeDetector().execute();

                                        } catch (IOException e) {
                                            e.printStackTrace();
                                            Log.e("Map Image", "Cannot export map image!");
                                        }
                                    }
                                };
                                mMap.snapshot(callback);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                // create dialog
                AlertDialog alertDialog = alertDialogBuilder.create();
                // show dialog
                alertDialog.show();
            }
        });

        // manual button
        manualButton = (Button) findViewById(R.id.btn_manual);
        manualButton.setEnabled(false);
        manualButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // show manual dialog
                // get manual layout view
                LayoutInflater li = LayoutInflater.from(mContext);
                View manualView = li.inflate(R.layout.diag_manual, null);
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
                // set auto view to dialog
                alertDialogBuilder.setView(manualView);

                // input
                final TextView rSeekBarTxt = (TextView) manualView.findViewById(R.id.txt_r);
                final SeekBar rSeekBar = (SeekBar) manualView.findViewById(R.id.seekbar_r);
                rSeekBarTxt.setText("Region grow threshold level: " + Integer.toString(r_threshold));
                rSeekBar.setProgress(r_threshold);
                rSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        r_threshold = progress;
                        rSeekBarTxt.setText("Region grow threshold level: " + Integer.toString(r_threshold));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {

                    }
                });

                final TextView dSeekBarTxt = (TextView) manualView.findViewById(R.id.txt_d);
                final SeekBar dSeekBar = (SeekBar) manualView.findViewById(R.id.seekbar_d);
                dSeekBarTxt.setText("Contour approximation threshold level: " + Integer.toString(d_threshold));
                dSeekBar.setProgress(d_threshold);
                dSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        d_threshold = progress;
                        dSeekBarTxt.setText("Contour approximation threshold level: " + Integer.toString(d_threshold));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {

                    }
                });

                // set dialog message
                alertDialogBuilder
                        .setCancelable(true)
                        .setPositiveButton("Start Drawing", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                opMode = MODE_MANUAL;
                                mMap.clear();

                                // start edge detection process
                                mProgressDialog.setMessage("Drawing...");
                                mProgressDialog.setCancelable(false);
                                mProgressDialog.show();

                                // capture current map image
                                GoogleMap.SnapshotReadyCallback callback = new GoogleMap.SnapshotReadyCallback() {
                                    Bitmap bmp;

                                    @Override
                                    public void onSnapshotReady(Bitmap snapshot) {
                                        bmp = snapshot;

                                        try {
                                            FileOutputStream out = new FileOutputStream("/mnt/sdcard/Pictures/out.png");
                                            bmp.compress(Bitmap.CompressFormat.PNG, 90, out);

                                            new EdgeDetector().execute();

                                        } catch (IOException e) {
                                            e.printStackTrace();
                                            Log.e("Map Image", "Cannot export map image!");
                                        }
                                    }
                                };
                                mMap.snapshot(callback);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                // create dialog
                AlertDialog alertDialog = alertDialogBuilder.create();
                // show dialog
                alertDialog.show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, baseLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    /**
     * Check if user permissions have been granted or not
     * @return permission status
     */
    private boolean checkPermissions() {
        int permissionLocation = ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION);
        int permissionStorage = ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        List<String> listPermissionsNeeded = new ArrayList<>();
        if (permissionLocation != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (permissionStorage != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), MY_PERMISSIONS_REQ);
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        int totalGrants = 0;

        switch (requestCode) {
            case MY_PERMISSIONS_REQ:

                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // granted
                    totalGrants++;
                    Log.d("Permissions", "Location permission granted!");
                } else {
                    // permissions not granted
                    Log.d("Permissions", "Location permission denied!");
                }

                if (grantResults.length > 0 && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    // granted
                    totalGrants++;
                    Log.d("Permissions", "Storage permission granted!");
                } else {
                    // permissions not granted
                    Log.d("Permissions", "Storage permission denied!");
                }
                break;
        }
    }

    /**
     * Enable map current location
     */
    private void enableCurrentLocation() {
        try {
            mMap.setMyLocationEnabled(true);
        } catch (SecurityException se) {
            Log.e("Security", "Cannot get current location!");
            se.printStackTrace();
        }
    }

    /**
     * Disable map current location
     */
    private void disableCurrentLocation() {
        try {
            mMap.setMyLocationEnabled(false);
        } catch (SecurityException se) {
            Log.e("Security", "Cannot get current location!");
            se.printStackTrace();
        }
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        // status
        statusTxt.setText("Map ready... \nLong press to select seed.");

        mMap = googleMap;

        // Set satellite view
        mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);

        // Move marker to Bangkok
        LatLng bkk = new LatLng(13.7563, 100.5018);
        LatLng kk  = new LatLng(16.3236, 103.2998);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(kk , 14.0f));

        // enable current location
        if (totalPermissions) {
            enableCurrentLocation();
        }

        // Disable marker focus
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            boolean doNotMoveCameraToCenterMarker = true;

            @Override
            public boolean onMarkerClick(Marker marker) {
                // Todo
                return doNotMoveCameraToCenterMarker;
            }
        });

        // Set map long press listener
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                // Enable/disable seed point selection
                if (seedSelectFlag) {
                    // set seed -> lock map -> disable select mode
                    statusTxt.setText("Seed selected. Screen locked. \nLong press to re-select seed.");

                    seedPoint = getPixelFromLatLng(latLng);
                    mMap.addMarker(new MarkerOptions()
                            .position(latLng)
                            .title("Seed Point")).showInfoWindow();
                    mMap.getUiSettings().setAllGesturesEnabled(false);
                    seedSelectFlag = false;

                    manualButton.setEnabled(true);
                    autoButton.setEnabled(true);

                    // disable current location
                    if (totalPermissions) {
                        disableCurrentLocation();
                    }

                } else {
                    // clear seed -> unlock map -> enable select mode
                    statusTxt.setText("Map clear... \nLong press to select seed.");
                    seedPoint = new Point(0, 0);
                    mMap.clear();
                    mMap.getUiSettings().setAllGesturesEnabled(true);
                    seedSelectFlag = true;

                    manualButton.setEnabled(false);
                    autoButton.setEnabled(false);

                    // enable current location
                    if (totalPermissions) {
                        enableCurrentLocation();
                    }
                }
            }
        });
    }

    /**
     * Get pixel point from lat/lon coordinates
     * @param latLng    lat/lon input
     * @return          pixel point in (column, row) format
     */
    private Point getPixelFromLatLng(LatLng latLng) {
        android.graphics.Point pixelPoint = mMap.getProjection().toScreenLocation(latLng);
        Point openCVPoint = new Point(pixelPoint.x, pixelPoint.y);

        return openCVPoint;
    }

    /**
     * Get lat/lon coordinates from pixel point
     * @param point     pixel point in (column, row) format
     * @return          lat/lon coordinate
     */
    private LatLng getLatLngFromPixel(Point point) {

        android.graphics.Point pixelPoint = new android.graphics.Point((int)point.x, (int)point.y);
        LatLng latLng = mMap.getProjection().fromScreenLocation(pixelPoint);

        return latLng;
    }

    /**
     * Draw calculated contour on map
     * @param contour       contour
     */
    private void drawContourOnMap(MatOfPoint contour) {

        Point[] points = contour.toArray();
        LatLng tmpLatLng;

        PolygonOptions polygonOptions = new PolygonOptions();

        if (points.length > 0) {
            for (int i = 0; i < points.length; i++) {
                tmpLatLng = getLatLngFromPixel(points[i]);
                mMap.addMarker(new MarkerOptions().position(tmpLatLng));
                polygonOptions.add(tmpLatLng);
            }
            polygonOptions.fillColor(Color.argb(40, 128, 156, 247));
            mMap.addPolygon(polygonOptions);

            // add area info
            tmpLatLng = getLatLngFromPixel(points[0]);
            double calAreaSqm = SphericalUtil.computeArea(polygonOptions.getPoints());

            // find rai
            int rai           = (int) floor(calAreaSqm / 1600);
            int residual_rai  = (int) (calAreaSqm - (rai*1600));
            int ngan          = (int) floor(residual_rai / 400);
            int residual_ngan = (int) (residual_rai - (ngan*400));
            int wa            = (int) floor(residual_ngan/4);

            String sTitle = "Total area: " + calAreaSqm + " sq.m.";
            String sCalAreaSqm = "Area: Rai = " + rai + ", Ngan = " + ngan + ", Tarang-wa = " + wa;
            mMap.addMarker(new MarkerOptions().position(tmpLatLng).title(sTitle).snippet(sCalAreaSqm)).showInfoWindow();

        } else {
            Toast.makeText(getApplicationContext(), "Cannot detect edges", Toast.LENGTH_SHORT).show();
        }
    }

    /** AsyncTask */
    private class EdgeDetector extends AsyncTask<Void, String, Void> {

        // Input image properties
        private int num_rows = 0;
        private int num_cols = 0;

        // Processing box properties
        int box_size = 4;
        int box_rows = 0;
        int box_cols = 0;

        // Feature matrix
        double[][] feat_mean;

        // Seed point
        int seed_row = 0;
        int seed_col = 0;

        public EdgeDetector() {
            /** Initialize */
            // Read image
            String filename = "/mnt/sdcard/Pictures/out.png";
            try {
                InputStream in = new FileInputStream(filename);
                // process image
                Bitmap originalBmp = BitmapFactory.decodeStream(in);
                num_rows = originalBmp.getHeight();
                num_cols = originalBmp.getWidth();

                Mat originalMat = new Mat(num_rows,
                        num_cols,
                        CvType.CV_8UC4,
                        new Scalar(0));
                Utils.bitmapToMat(originalBmp, originalMat);

                // convert to grayscale
                Mat grayMat = new Mat();
                Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGB2GRAY);

                /** Box */
                box_rows = num_rows / box_size;
                box_cols = num_cols / box_size;

                // calculate features
                feat_mean = new double[box_rows][box_cols];
                for (int i = 0; i < box_rows; i++) {
                    for (int j = 0; j < box_cols; j++) {
                        int start_row = i * box_size;
                        int start_col = j * box_size;
                        double avg_mean = 0;
                        int num_px      = 0;
                        for (int k = start_row; k < start_row + box_size; k++) {
                            for (int m = start_col; m < start_col + box_size; m++) {
                                double[] px_rgb = grayMat.get(k, m);
                                double tmp_mean = px_rgb[0];
                                avg_mean = ((num_px * avg_mean) + (1 * tmp_mean)) / (1 + num_px);
                                num_px++;
                            }
                        }

                        feat_mean[i][j] = avg_mean;
                    }
                }

                // seed point info
                seed_row = (int) seedPoint.y / box_size;
                seed_col = (int) seedPoint.x / box_size;

            } catch (IOException ioe) {
                ioe.printStackTrace();
                Log.e("Edge Detector", "Error loading image file");
            }
        }

        @Override
        protected void onPreExecute() {super.onPreExecute();}

        @Override
        protected Void doInBackground(Void... arg0) {
            return null;
        }

        @Override
        protected void onProgressUpdate(String... progress) {

        }

        @Override
        protected void onPostExecute(Void result) {
            if (opMode == MODE_MANUAL) {
                MatOfPoint manualContour = manualDraw();
                mProgressDialog.dismiss();
                drawContourOnMap(manualContour);
            } else {
                MatOfPoint autoContour = automaticDraw();
                mProgressDialog.dismiss();
                drawContourOnMap(autoContour);
            }
        }

        private MatOfPoint automaticDraw() {
            // determine perfect r and d threshold
            autoRThreshold();
            autoDThreshold();

            List<MatOfPoint> rContour = regionGrow(r_threshold);
            MatOfPoint aContour = approxContour(rContour, d_threshold);

            return aContour;
        }

        private MatOfPoint manualDraw() {
            List<MatOfPoint> rContour = regionGrow(r_threshold);
            MatOfPoint aContour = approxContour(rContour, d_threshold);

            return aContour;
        }

        private void autoRThreshold() {
            int rThreshold = 0;
            int dThreshold = 0;
            List<MatOfPoint> rContour = regionGrow(rThreshold);
            MatOfPoint aContour = approxContour(rContour, dThreshold);
            double calArea = getContourArea(aContour);

            while (calArea <= userArea) {
                rThreshold = rThreshold + 1;
                rContour = regionGrow(rThreshold);
                aContour = approxContour(rContour, dThreshold);
                calArea = getContourArea(aContour);
            }

            r_threshold = rThreshold - 1;
        }

        private void autoDThreshold() {
            int rThreshold = r_threshold;
            int dThreshold = 0;

            List<MatOfPoint> rContour = regionGrow(rThreshold);
            MatOfPoint aContour = approxContour(rContour, dThreshold);
            double calArea = getContourArea(aContour);
            int    calN    = getContourPoint(aContour);

            double[] area_array = new double[3];
            area_array[0] = calArea;
            area_array[1] = 0.0;
            area_array[2] = 0.0;

            int[] n_array = new int[3];
            n_array[0] = calN;
            n_array[1] = 0;
            n_array[2] = 0;

            double curr_slope = 1;
            double prev_slope = 1;

            int round = 0;

            while (curr_slope/prev_slope <= 500) {

                dThreshold = dThreshold + 5;
                aContour = approxContour(rContour, dThreshold);

                double tmpArea = getContourArea(aContour);
                int    tmpN    = getContourPoint(aContour);

                // update array
                area_array[1] = tmpArea;
                n_array[1] = tmpN;

                if (round == 0) {
                    area_array[2] = area_array[1];
                    n_array[2] = n_array[1];

                    curr_slope = abs(area_array[1] - area_array[0])/(n_array[0] - n_array[1]);
                    prev_slope = curr_slope;

                    round = 1;

                } else {
                    curr_slope = abs(area_array[1] - area_array[2])/(n_array[2] - n_array[1]);
                    prev_slope = abs(area_array[2] - area_array[0])/(n_array[0] - n_array[2]);

                    area_array[2] = area_array[1];
                    n_array[2] = n_array[1];
                }
            }

            d_threshold = dThreshold - 5;
        }

        private List<MatOfPoint> regionGrow(int growThreshold) {

            List<MatOfPoint> validContours;

            /** Initialization */
            // edge
            int edge_index = 1;
            int[][] edge_list = new int[box_rows * box_cols][2];
            edge_list[0][0] = seed_col;
            edge_list[0][1] = seed_row;

            // neighbor
            int neigh_index = 0;
            int[][] neigh_list = new int[box_rows * box_cols][2];
            int[][] neigh_mask = new int[4][2];
            neigh_mask[0][0] =  0;
            neigh_mask[0][1] = -1;
            neigh_mask[1][0] =  0;
            neigh_mask[1][1] =  1;
            neigh_mask[2][0] =  1;
            neigh_mask[2][1] =  0;
            neigh_mask[3][0] = -1;
            neigh_mask[3][1] =  0;

            // region
            double mean_region = feat_mean[seed_row][seed_col];
            int num_members = 1;

            // flags
            int[][] box_flag = new int[box_rows][box_cols];

            // output
            int[][] box_out = new int[box_rows][box_cols];

            while ((sum2D(box_out) < (2 * box_rows * box_cols)) && (edge_index > 0)) {

                // process all edge members
                int total_edge = edge_index;
                for (int k = 0; k < total_edge; k++) {

                    edge_index = edge_index - 1;
                    int[][] edge = new int[1][2];
                    edge[0][0] = edge_list[edge_index][0];
                    edge[0][1] = edge_list[edge_index][1];

                    // find all neighbors of current edge
                    for (int j = 0; j < neigh_mask.length; j++) {

                        int x = min(max((edge[0][0] + neigh_mask[j][0]), 0), (box_cols - 1));
                        int y = min(max((edge[0][1] + neigh_mask[j][1]), 0), (box_rows - 1));

                        int[][] new_neighbor = new int[1][2];
                        new_neighbor[0][0] = x;
                        new_neighbor[0][1] = y;

                        // only add unprocessed neighbors to neighbor list
                        if (box_flag[y][x] == 0) {
                            box_flag[y][x] = 1;

                            // increase number of neighbor by one
                            neigh_list[neigh_index][0] = x;
                            neigh_list[neigh_index][1] = y;
                            neigh_index = neigh_index + 1;
                        }

                    }

                    // clear processed edge list
                    edge_list[edge_index][0] = 0;
                    edge_list[edge_index][1] = 0;
                }

                // process neighbors to get new edge
                int total_neigh = neigh_index;
                for (int j = 0; j < total_neigh; j++) {

                    // check if neighbor is in the region
                    // if YES, add this neighbor as a new edge
                    neigh_index = neigh_index - 1;

                    int x = neigh_list[neigh_index][0];
                    int y = neigh_list[neigh_index][1];

                    double mean_diff = abs(feat_mean[y][x] - mean_region);

                    if (mean_diff < growThreshold) {

                        // update region mean
                        mean_region = ((1 * feat_mean[y][x]) + (num_members * mean_region)) / (1 + num_members);
                        num_members = num_members + 1;

                        // update output
                        box_out[y][x] = 2;

                        // update edge index
                        edge_list[edge_index][0] = x;
                        edge_list[edge_index][1] = y;
                        edge_index = edge_index + 1;
                    }

                    // clear processed neighbor
                    neigh_list[neigh_index][0] = 0;
                    neigh_list[neigh_index][1] = 0;
                }

            }

            // see output image
            Mat out_mat = new Mat(num_rows, num_cols, CvType.CV_8U, new Scalar(0));
            for (int i = 0; i < box_rows; i++) {
                for (int j = 0; j < box_cols; j++) {
                    if (box_out[i][j] == 2) {
                        int start_row = i * box_size;
                        int start_col = j * box_size;

                        for (int k = start_row; k < start_row + box_size; k++) {
                            for (int m = start_col; m < start_col + box_size; m++) {
                                out_mat.put(k, m, 255);
                            }
                        }
                    }
                }
            }

            Mat final_mat = new Mat();
            // threshold
            Imgproc.threshold(out_mat, final_mat, 50, 255, Imgproc.THRESH_BINARY);
            validContours  = getContours(final_mat, 0.1f);

            return validContours;
        }

        private MatOfPoint approxContour(List<MatOfPoint> contour, int approxThreshold) {
            MatOfPoint encloseContour;

            List<MatOfPoint2f> approxContours = getApproxPolyContours(contour, approxThreshold);
            encloseContour = getEncloseContour(approxContours);

            return encloseContour;
        }

        protected double getContourArea(MatOfPoint contour) {
            double contourArea = 0.0;

            // calculate area
            Point[] points = contour.toArray();
            LatLng tmpLatLng;

            PolygonOptions polygonOptions = new PolygonOptions();

            if (points.length > 0) {
                for (int i = 0; i < points.length; i++) {
                    tmpLatLng = getLatLngFromPixel(points[i]);
                    polygonOptions.add(tmpLatLng);
                }
                // add area info
                contourArea = SphericalUtil.computeArea(polygonOptions.getPoints());
            }

            return contourArea;
        }

        protected int getContourPoint(MatOfPoint contour) {
            int contourPoint = 0;

            // calculate area
            Point[] points = contour.toArray();
            contourPoint = points.length;

            return contourPoint;
        }

        // perform contour detection
        private List<MatOfPoint> getContours(Mat inMat, float areaWeight) {

            List<MatOfPoint> contours = new ArrayList<>();
            List<MatOfPoint> contour;

            Mat hierarchy = new Mat();

            Imgproc.findContours(inMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            Mat drawing = new Mat(inMat.rows(), inMat.cols(), CvType.CV_8UC3, new Scalar(0));

            Random r = new Random();
            for (int i=0; i<contours.size(); i++) {
                Imgproc.drawContours(drawing, contours, i, new Scalar(r.nextInt(255), r.nextInt(255), r.nextInt(255)), -1);
            }

            // find max area
            double maxArea = getContourMaxArea(contours, hierarchy);

            // find valid contours
            contour = getValidContours(contours, hierarchy, maxArea, areaWeight);

            return contour;
        }

        // get maximum contour area
        private double getContourMaxArea(List<MatOfPoint> contours, Mat hierarchy) {

            double maxArea = 0;
            for (int i = 0; i < contours.size(); i++) {

                double[] contourInfo = hierarchy.get(0, i);
                int tmpChild = (int) contourInfo[2];

                if(tmpChild < 0) {
                    double tmpArea = Imgproc.contourArea(contours.get(i));
                    if (tmpArea > maxArea) {
                        maxArea = tmpArea;
                    }
                }
            }

            return maxArea;
        }

        // get valid contours
        private List<MatOfPoint> getValidContours(List<MatOfPoint> contours, Mat hierarchy, double maxArea, float weight) {
            List<MatOfPoint> validContours = new ArrayList<>();

            for (int i = 0; i < contours.size(); i++) {
                double[] contourInfo = hierarchy.get(0, i);
                int tmpChild = (int) contourInfo[2];
                if(tmpChild < 0) {
                    double tmp_area = Imgproc.contourArea(contours.get(i));
                    if (tmp_area >= weight*maxArea) {
                        validContours.add(contours.get(i));
                    }
                }
            }

            return validContours;
        }

        private List<MatOfPoint2f> getApproxPolyContours(List<MatOfPoint> contours, int p_threshold) {
            // contours in 2D (float)
            List<MatOfPoint2f> contours2f     = new ArrayList<>();

            // approximated contours in 2D (float)
            List<MatOfPoint2f> polyMOP2f      = new ArrayList<>();

            // init list
            for (int i = 0; i < contours.size(); i++) {
                contours2f.add(new MatOfPoint2f());
                polyMOP2f.add(new MatOfPoint2f());
            }

            // convert contours into MatOfPoint2f
            for (int i = 0; i < contours.size(); i++) {
                contours.get(i).convertTo(contours2f.get(i), CvType.CV_32FC2);
                // contour in float
                Imgproc.approxPolyDP(contours2f.get(i), polyMOP2f.get(i), p_threshold, true);
            }

            return polyMOP2f;
        }

        // search for a contours which encloses the search point
        private MatOfPoint getEncloseContour(List<MatOfPoint2f> polyMOP2f) {
            // approximated enclose contours (int)
            MatOfPoint polyMOP = new MatOfPoint();

            // convert contours into MatOfPoint2f
            for (int i = 0; i < polyMOP2f.size(); i++) {
                double ppt = Imgproc.pointPolygonTest(polyMOP2f.get(i), seedPoint, false);
                if (ppt >= 0) {
                    polyMOP2f.get(i).convertTo(polyMOP, CvType.CV_32S);
                }
            }

            return polyMOP;
        }
    }
}
