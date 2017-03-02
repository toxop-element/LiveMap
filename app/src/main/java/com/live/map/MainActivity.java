package com.live.map;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.DirectionsApi;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.android.SphericalUtil;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.TravelMode;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private final Handler mHandler = new Handler();
    private List<LatLng> latLngs = new ArrayList<>();
    private GoogleMap map;
    private Marker startMarker;
    private Marker endMarker;
    private GoogleApiClient googleApiClient;
    private PolylineOptions polylineOptions;
    private GeoApiContext geoApiContext = new GeoApiContext().setApiKey("AIzaSyAYOv8_JBLe2bYtRtOG2aXNhHs0C2BmXfs");
    private boolean directionsFetched = false;
    private Animator animator = new Animator();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.onCreate(null);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        this.map = googleMap;
        this.map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(com.google.android.gms.maps.model.LatLng latLng) {
                startMarker = map.addMarker(new MarkerOptions().position(latLng).draggable(false));
            }
        });
        this.map.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                endMarker = map.addMarker(new MarkerOptions().position(latLng).draggable(false));
            }
        });
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new DirectionsFetcher(startMarker.getPosition(), endMarker.getPosition()).execute();
            }
        });
    }

    private void updateNavigationStopStart() {
//        MenuItem startAnimation = this.menu.findItem(R.id.action_bar_start_animation);
//        startAnimation.setVisible(!animator.isAnimating() && directionsFetched);
//        MenuItem stopAnimation = this.menu.findItem(R.id.action_bar_stop_animation);
//        stopAnimation.setVisible(animator.isAnimating());
    }

    /**
     * Adds a list of markers to the map.
     */
    public void addPolylineToMap(List<com.google.android.gms.maps.model.LatLng> latLngs) {
        PolylineOptions options = new PolylineOptions();
        for (com.google.android.gms.maps.model.LatLng latLng : latLngs) {
            options.add(latLng);
        }
        map.addPolyline(options);
    }

    private class DirectionsFetcher extends AsyncTask<URL, Integer, Void> {
        private LatLng origin;
        private LatLng destination;

        public DirectionsFetcher(LatLng origin, LatLng destination) {
            this.origin = origin;
            this.destination = destination;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        protected Void doInBackground(URL... urls) {
            try {
                DirectionsApiRequest request = DirectionsApi.newRequest(geoApiContext);
                request.origin(new com.google.maps.model.LatLng(origin.latitude, origin.longitude));
                request.destination(new com.google.maps.model.LatLng(destination.latitude, destination.longitude));
                request.mode(TravelMode.DRIVING);
                request.alternatives(true);
                request.avoid(DirectionsApi.RouteRestriction.TOLLS);
                request.optimizeWaypoints(true);
                DirectionsResult result = request.await();
                if (result != null) {
                    latLngs.clear();
                    for (com.google.maps.model.LatLng latLng : result.routes[0].overviewPolyline.decodePath()) {
                        LatLng latLng1 = new LatLng(latLng.lat, latLng.lng);
                        latLngs.add(latLng1);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return null;

        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(Void result) {
            directionsFetched = true;
            System.out.println("Adding polyline");
            addPolylineToMap(latLngs);
            System.out.println("Fix Zoom");
//            GoogleMapUtis.fixZoomForLatLngs(map, latLngs);
            System.out.println("Start anim");
            animator.startAnimation(true, latLngs);
//            updateNavigationStopStart();
//           this.setProgressBarIndeterminateVisibility(Boolean.FALSE);
        }
    }

    public class Animator implements Runnable {

        private static final int ANIMATE_SPEEED = 1500;
        private static final int ANIMATE_SPEEED_TURN = 1500;
        private static final int BEARING_OFFSET = 20;

        private final Interpolator interpolator = new LinearInterpolator();
        int currentIndex = 0;
        float tilt = 90;
        float zoom = 15.5f;
        boolean upward = true;
        long start = SystemClock.uptimeMillis();
        LatLng endLatLng = null;
        LatLng beginLatLng = null;
        boolean showPolyline = false;
        private boolean animating = false;
        private List<LatLng> latLngs = new ArrayList<LatLng>();
        private Marker trackingMarker;
        private Polyline polyLine;
        private PolylineOptions rectOptions = new PolylineOptions();

        public void reset() {
//            resetMarkers();
            start = SystemClock.uptimeMillis();
            currentIndex = 0;
            endLatLng = getEndLatLng();
            beginLatLng = getBeginLatLng();

        }

        public void stopAnimation() {
            animating = false;
            mHandler.removeCallbacks(animator);

        }

        public void initialize(boolean showPolyLine) {
            reset();
            this.showPolyline = showPolyLine;

//            highLightMarker(0);

            if (showPolyLine) {
                polyLine = initializePolyLine();
            }

            // We first need to put the camera in the correct position for the first run (we need 2 markers for this).....
            LatLng markerPos = latLngs.get(0);
            LatLng secondPos = latLngs.get(1);

            setInitialCameraPosition(markerPos, secondPos);

        }

        private void setInitialCameraPosition(LatLng markerPos,
                                              LatLng secondPos) {

//            float bearing = GoogleMapUtis.bearingBetweenLatLngs(markerPos, secondPos);
//
            trackingMarker = map.addMarker(new MarkerOptions().position(markerPos)
                    .title("title")
                    .snippet("snippet"));
//
//            float mapZoom = map.getCameraPosition().zoom >= 16 ? map.getCameraPosition().zoom : 16;
//
//            CameraPosition cameraPosition =
//                    new CameraPosition.Builder()
//                            .target(markerPos)
//                            .bearing(bearing + BEARING_OFFSET)
//                            .tilt(90)
//                            .zoom(mapZoom)
//                            .build();
//
//            map.animateCamera(
//                    CameraUpdateFactory.newCameraPosition(cameraPosition),
//                    ANIMATE_SPEEED_TURN,
//                    new GoogleMap.CancelableCallback() {
//
//                        @Override
//                        public void onFinish() {
//                            System.out.println("finished camera");
//                            animator.reset();
//                            Handler handler = new Handler();
//                            handler.post(animator);
//                        }
//
//                        @Override
//                        public void onCancel() {
//                            System.out.println("cancelling camera");
//                        }
//                    }
//            );
            Handler handler = new Handler();
            handler.post(animator);
        }

        private Polyline initializePolyLine() {
            //polyLinePoints = new ArrayList<LatLng>();
            rectOptions.add(new com.google.android.gms.maps.model.LatLng(latLngs.get(0).latitude, latLngs.get(0).longitude));
            return map.addPolyline(rectOptions);
        }

        /**
         * Add the marker to the polyline.
         */
        private void updatePolyLine(LatLng latLng) {
            List<LatLng> points = polyLine.getPoints();
            points.add(latLng);
            polyLine.setPoints(points);
        }

        public void startAnimation(boolean showPolyLine, List<LatLng> latLngs) {
            if (trackingMarker != null) {
                trackingMarker.remove();
            }
            this.animating = true;
            this.latLngs = latLngs;
            if (latLngs.size() > 2) {
                initialize(showPolyLine);
            }

        }

        public boolean isAnimating() {
            return this.animating;
        }


        @Override
        public void run() {

            long elapsed = SystemClock.uptimeMillis() - start;
            double t = interpolator.getInterpolation((float) elapsed / ANIMATE_SPEEED);
            LatLng intermediatePosition = SphericalUtil.interpolate(beginLatLng, endLatLng, t);

            Double mapZoomDouble = 18.5 - (Math.abs((0.5 - t)) * 5);
            float mapZoom = mapZoomDouble.floatValue();

            System.out.println("mapZoom = " + mapZoom);

            trackingMarker.setPosition(intermediatePosition);

            if (showPolyline) {
                updatePolyLine(intermediatePosition);
            }

            if (t < 1) {
                mHandler.postDelayed(this, 16);
            } else {

                System.out.println("Move to next marker.... current = " + currentIndex + " and size = " + latLngs.size());
                // imagine 5 elements -  0|1|2|3|4 currentindex must be smaller than 4
                if (currentIndex < latLngs.size() - 2) {

                    currentIndex++;

                    endLatLng = getEndLatLng();
                    beginLatLng = getBeginLatLng();


                    start = SystemClock.uptimeMillis();

                    Double heading = SphericalUtil.computeHeading(beginLatLng, endLatLng);

//                    highLightMarker(currentIndex);

                    CameraPosition cameraPosition =
                            new CameraPosition.Builder()
                                    .target(endLatLng)
                                    .bearing(heading.floatValue() /*+ BEARING_OFFSET*/) // .bearing(bearingL  + BEARING_OFFSET)
                                    .tilt(tilt)
                                    .zoom(map.getCameraPosition().zoom)
                                    .build();
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), ANIMATE_SPEEED_TURN, null);
                    //start = SystemClock.uptimeMillis();
                    mHandler.postDelayed(this, 16);

                } else {
                    currentIndex++;
//                    highLightMarker(currentIndex);
                    stopAnimation();
                }

            }
        }

        private LatLng getEndLatLng() {
            return latLngs.get(currentIndex + 1);
        }

        private LatLng getBeginLatLng() {
            return latLngs.get(currentIndex);
        }

    }
}
