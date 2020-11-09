package com.example.locationtest;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SearchView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LocationListener {

    private GoogleMap mMap;
    private static final String BASE_URL = "http://52.149.135.175:80";
    private final static String TAG = "MapActivity";
    private final static double nearbyDistance = 4; //km
    public List<Incident> incidents = new LinkedList<>();
    private LatLng myLocation;
    private Marker blueMarker;
    private RetrofitInterface retrofitInterface;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        // Initialize the search bar
        SearchView searchView;
        searchView = findViewById(R.id.sv_location);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return getQueryResult(searchView);
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });


        // Initialize home button
        Button returnHomeButton = findViewById(R.id.returnHomeButton);
        returnHomeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent homeIntent = new Intent(MapsActivity.this, MainActivity.class);
                startActivity(homeIntent);
            }
        });

        // Initialize Safety Score Button
        Button safetyScoreButton = findViewById(R.id.getSafetyScoreButton);
        safetyScoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                computeSafetyScore();
            }
        });

        // Initialize Post New Incident Button
        Button postNewIncidentButton = findViewById(R.id.addIncidentButton);
        postNewIncidentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent postIncidentIntent = new Intent(MapsActivity.this, NewIncidentPostActivity.class);
                postIncidentIntent.putExtra("latitude", blueMarker.getPosition().latitude);
                postIncidentIntent.putExtra("longitude", blueMarker.getPosition().longitude);
                startActivity(postIncidentIntent);

            }
        });

        // Initialize retrofit interface for HTTP get
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        retrofitInterface = retrofit.create(RetrofitInterface.class);


        // If arriving from submitting an incident, show that incident
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            Double latitude = extras.getDouble("latitude");
            Double longitude = extras.getDouble("longitude");
            int severity = extras.getInt("severity");
            String title = extras.getString("title");
            incidents.add(new Incident(title, latitude, longitude, severity));
        }


        // Initialize Location Manager; request location update every second
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
    }

    // Go to the result of search bar query
    private boolean getQueryResult(SearchView searchView) {
        String result = searchView.getQuery().toString();
        List<Address> addressList = null;

        if (result != null && !result.equals("")){
            Geocoder geocoder = new Geocoder(MapsActivity.this);
            try {
                addressList = geocoder.getFromLocationName(result, 1);
                Address address = addressList.get(0);
                LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                blueMarker.setPosition(latLng);
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 10));
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return false;
    }


    // Calculate the safety score at the blue marker based on number of nearby incidents
    private void computeSafetyScore() {
        int score = 0;
        String isSafe = "";
        int nearbyIncidents = 0;

        // Count the number of incidents
        for (Incident i : incidents) {
            double distance = i.distanceFrom(blueMarker.getPosition()); //Distance from blue marker
            if (distance < nearbyDistance) {
                nearbyIncidents+=1;
            }
        }

        // Score from 1 to 5
        // Many incidents -> Low score
        // No incidents -> High score
        if (nearbyIncidents >= 10) {
            score = 1;
            isSafe = "(Unsafe)";
        } else if (nearbyIncidents >= 7) {
            score = 2;
        } else if (nearbyIncidents >= 4) {
            score = 3;
        } else if (nearbyIncidents >= 1) {
            score = 4;
            isSafe = "(Safe)";
        } else {
            score = 5;
            isSafe = "(Very Safe)";
        }

        // Show the safety score to the user
        String toPrint = "The safety score at this location is: " + score + "/5 " + isSafe;
        Toast.makeText(MapsActivity.this, toPrint, Toast.LENGTH_LONG).show();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        // Enable all functionality for map navigation
        mMap.setMyLocationEnabled(true);
        UiSettings mUiSettings = mMap.getUiSettings();
        mUiSettings.setAllGesturesEnabled(true);
        mUiSettings.setZoomControlsEnabled(true);
        mUiSettings.setMyLocationButtonEnabled(true);
        mUiSettings.setZoomGesturesEnabled(true);
        mUiSettings.setTiltGesturesEnabled(true);
        mUiSettings.setRotateGesturesEnabled(true);
        mMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
            @Override
            public boolean onMyLocationButtonClick() {
                blueMarker.setPosition(myLocation);
                return false;
            }
        });

        // Wait 5 seconds before displaying current location
        Toast.makeText(MapsActivity.this, "Give us a moment...", Toast.LENGTH_LONG).show();
        (new Handler()).postDelayed(this::displayCurrentLocation, 5000);

        // Call the sever and get the list of incidents
        Call<List<Incident>> call = retrofitInterface.getIncidents();
        call.enqueue(new Callback<List<Incident>>() {
            @Override
            public void onResponse(Call<List<Incident>> call, Response<List<Incident>> response) {
                if (!response.isSuccessful()) {
                    Toast.makeText(MapsActivity.this, "Code: " + response.code(), Toast.LENGTH_LONG).show();
                    return;
                }

                // Add incidents from HTTP response
                incidents.addAll(response.body());

                // Add markers at incidents from server
                for (Incident i: incidents) {
                    mMap.addMarker(new MarkerOptions().position(i.getLocation()).title(i.getTitle() + " (" + i.getSeverity() + ")"));
                }
                Toast.makeText(MapsActivity.this, "Response was successful", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(Call<List<Incident>> call, Throwable t) {

                Toast.makeText(MapsActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });


    }

    // Method to display current location
    private void displayCurrentLocation() {
        blueMarker = mMap.addMarker(new MarkerOptions().position(myLocation).title("You are here").draggable(true).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
                blueMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
            }

            @Override
            public void onMarkerDrag(Marker marker) {
                blueMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                blueMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
            }
        });
        mMap.moveCamera(CameraUpdateFactory.newLatLng(myLocation));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 12.0f));
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        Log.d(TAG, "Lat: " + location.getLatitude() + " Long: " + location.getLongitude());
        myLocation = new LatLng(location.getLatitude(), location.getLongitude());

    }


}