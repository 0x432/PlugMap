package com.example.plugmapuk;

import androidx.fragment.app.FragmentActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;
import com.google.maps.android.PolyUtil;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private TextView currentAddressTextView;
    private EditText addressInput;
    private Button searchButton, startTripButton;
    private Polyline currentPolyline;
    private String destination_address;
    private LatLng destinationLatLng;
    private Marker destinationMarker;
    private CheckBox evChargerCheckbox;
    private JSONObject closestCharger;
    private static final double MAX_DETOUR_DISTANCE = 10.0;
    private LatLng optimalChargerLatLng;
    private static final String CHARGER_API_KEY = "Enter-API-Key";
    private static final String GOOGLE_MAPS_API_KEY = "Enter-API-Key";

    public class ChargerState {
        public LatLng currentLatLng;
        public double currentBatteryLevel;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        initializeComponents();
        configureSettingsIconListener();
        configureMapFragment();
        configureStartTripButton();
    }

    private void initializeComponents() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        currentAddressTextView = findViewById(R.id.currentAddress);
        addressInput = findViewById(R.id.addressInput);
        startTripButton = findViewById(R.id.startTrip);
        evChargerCheckbox = findViewById(R.id.evChargerCheckbox);
    }

    private void configureSettingsIconListener() {
        ImageView settingsIcon = findViewById(R.id.settingsIcon);
        settingsIcon.setOnClickListener(v -> {
            Intent intent = new Intent(MapsActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    private void configureMapFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private void configureStartTripButton() {
        startTripButton.setOnClickListener(v -> handleStartTrip());
    }

    private void handleStartTrip() {
        resetTripData();
        String address = addressInput.getText().toString();
        if (address.isEmpty()) {
            showToast("Please enter a destination address");
            return;
        }

        if (!resolveDestinationAddress(address)) return;
        if (evChargerCheckbox.isChecked()) {
            handleEVChargerSelection();
        } else {
            drawRoute();
        }
    }

    private void resetTripData() {
        destinationLatLng = null;
        destination_address = null;
        optimalChargerLatLng = null;
        clearMap();
    }

    private boolean resolveDestinationAddress(String address) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(address, 1);
            if (!addresses.isEmpty()) {
                Address location = addresses.get(0);
                destinationLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                destination_address = location.getAddressLine(0);
                return true;
            } else {
                showToast("Address not found");
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void handleEVChargerSelection() {
        if (!checkAndRequestLocationPermissions()) return;
        fetchCurrentLocationAndHandleCharger();
    }

    private boolean checkAndRequestLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return false;
        }
        return true;
    }

    private void fetchCurrentLocationAndHandleCharger() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) {
                showToast("Current location not available");
                return;
            }
            handleChargerBasedOnBatteryLevel(location);
        });
    }

    private void handleChargerBasedOnBatteryLevel(Location location) {
        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        double currentBatteryLevel = Double.parseDouble(getCurrentBatteryLevel());

        if (currentBatteryLevel < 15) {
            fetchEVChargersAndFindNearestCharger(currentLatLng);
        } else {
            LatLng midpoint = findMidpoint(currentLatLng, destinationLatLng);
            fetchEVChargersAndFindOptimalStops(midpoint);
        }
    }

    private void fetchEVChargersAndFindNearestCharger(LatLng currentLatLng) {
        int defaultRadius = 100;
        String url = buildChargerApiUrl(currentLatLng, defaultRadius);
        makeChargerApiRequest(url, currentLatLng, defaultRadius);
    }

    private String buildChargerApiUrl(LatLng latLng, int radius) {
        return "https://api.openchargemap.io/v3/poi/?output=json&latitude="
                + latLng.latitude
                + "&longitude="
                + latLng.longitude
                + "&distance="
                + radius
                + "&key="
                + CHARGER_API_KEY;
    }

    private void makeChargerApiRequest(String url, LatLng currentLatLng, int radius) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                showToast("Error fetching EV charger data");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    showToast("Unexpected error fetching EV charger data");
                } else {
                    handleChargerApiResponse(response, currentLatLng);
                }
            }
        });
    }

    private void handleChargerApiResponse(Response response, LatLng currentLatLng) throws IOException {
        String responseBody = response.body().string();
        try {
            JSONArray chargersArray = new JSONArray(responseBody);
            findAndSetNearestCharger(chargersArray, currentLatLng);
        } catch (JSONException e) {
            showToast("Error parsing EV charger data");
        }
    }

    private void findAndSetNearestCharger(JSONArray chargersArray, LatLng currentLatLng) {
        List<JSONObject> chargersList = new ArrayList<>();
        for (int i = 0; i < chargersArray.length(); i++) {
            try {
                chargersList.add(chargersArray.getJSONObject(i));
            } catch (JSONException e) {

            }
        }
        JSONObject nearestCharger = findNearestCharger(chargersList, currentLatLng);
        if (nearestCharger != null) {
            setOptimalChargerLatLng(nearestCharger);
        }
    }

    private void setOptimalChargerLatLng(JSONObject nearestCharger) {
        try {
            JSONObject addressInfo = nearestCharger.getJSONObject("AddressInfo");
            optimalChargerLatLng = new LatLng(addressInfo.getDouble("Latitude"), addressInfo.getDouble("Longitude"));
            runOnUiThread(this::drawRoute);
        } catch (JSONException e) {
            showToast("Error setting optimal charger location");
        }
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(MapsActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        updateLocationUI();
        getLastLocation();
    }

    private Set<String> getSavedSocketTypes() {
        SharedPreferences sharedPreferences = getSharedPreferences("UserSettings", MODE_PRIVATE);
        return sharedPreferences.getStringSet("SocketTypes", new HashSet<>());
    }

    private void findAndDisplayOptimalCharger(JSONArray chargersArray) {
        if (destinationLatLng == null) {
            showToast("Please enter a destination address");
            return;
        }

        if (!hasLocationPermissionGranted()) {
            Log.e("MapsActivity", "Location permission not granted");
            return;
        }

        getLastLocation(location -> processLocationForOptimalCharger(location, chargersArray));
    }

    private boolean hasLocationPermissionGranted() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void getLastLocation(Consumer<Location> locationConsumer) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                locationConsumer.accept(location);
            } else {
                showToast("Current location not available");
            }
        });
    }

    private void processLocationForOptimalCharger(Location location, JSONArray chargersArray) {
        try {
            List<JSONObject> chargersList = filterAndSortChargers(chargersArray, getSavedSocketTypes());
            List<JSONObject> optimalChargingStops = findOptimalChargingStops(chargersList, location, destinationLatLng);
            displayOptimalChargingStops(optimalChargingStops);
            if (!optimalChargingStops.isEmpty()) {
                updateRouteWithFirstOptimalCharger(optimalChargingStops);
            }
        } catch (JSONException e) {
            Log.e("MapsActivity", "Error processing EV charger data", e);
        }
    }

    private List<JSONObject> filterAndSortChargers(JSONArray chargersArray, Set<String> savedSocketTypes) throws JSONException {
        List<JSONObject> chargersList = new ArrayList<>();
        for (int i = 0; i < chargersArray.length(); i++) {
            JSONObject charger = chargersArray.getJSONObject(i);
            if (isChargerSuitable(charger, savedSocketTypes)) {
                chargersList.add(charger);
            }
        }
        Collections.sort(chargersList, (charger1, charger2) -> compareChargersByDistanceToDestination(charger1, charger2, destinationLatLng));
        return chargersList;
    }

    private boolean isChargerSuitable(JSONObject charger, Set<String> savedSocketTypes) throws JSONException {
        return isChargerPublic(charger) && isChargerOperational(charger) && isChargerCompatible(charger, savedSocketTypes);
    }

    private void updateRouteWithFirstOptimalCharger(List<JSONObject> optimalChargingStops) throws JSONException {
        JSONObject optimalCharger = optimalChargingStops.get(0);
        JSONObject addressInfo = optimalCharger.getJSONObject("AddressInfo");
        optimalChargerLatLng = new LatLng(addressInfo.getDouble("Latitude"), addressInfo.getDouble("Longitude"));
        drawRoute();
    }

    private boolean isChargerPublic(JSONObject charger) throws JSONException {
        if (!charger.isNull("UsageType")) {
            JSONObject usageType = charger.getJSONObject("UsageType");

            boolean isMembershipRequired = usageType.optBoolean("IsMembershipRequired", true);
            boolean isAccessKeyRequired = usageType.optBoolean("IsAccessKeyRequired", true);

            return !isMembershipRequired && !isAccessKeyRequired;
        }
        return false;
    }

    private boolean isChargerOperational(JSONObject charger) throws JSONException {
        if (!charger.isNull("StatusType")) {
            JSONObject statusType = charger.getJSONObject("StatusType");
            return statusType.optBoolean("IsOperational", false);
        }
        return false;
    }

    private boolean isChargerCompatible(JSONObject charger, Set<String> savedSocketTypes) throws JSONException {
        if (!charger.isNull("Connections")) {
            JSONArray connections = charger.getJSONArray("Connections");
            for (int j = 0; j < connections.length(); j++) {
                JSONObject connection = connections.getJSONObject(j);
                int connectionTypeID = connection.getJSONObject("ConnectionType").getInt("ID");
                if (savedSocketTypes.contains(String.valueOf(connectionTypeID))) {
                    return true;
                }
            }
        }
        return false;
    }

    private double calculateDistance(LatLng point1, LatLng point2) {
        float[] results = new float[1];
        Location.distanceBetween(point1.latitude, point1.longitude,
                point2.latitude, point2.longitude,
                results);
        return results[0];
    }

    private int compareChargersByDistanceToDestination(JSONObject charger1, JSONObject charger2, LatLng destination) {
        try {
            JSONObject addressInfo1 = charger1.getJSONObject("AddressInfo");
            LatLng charger1LatLng = new LatLng(addressInfo1.getDouble("Latitude"), addressInfo1.getDouble("Longitude"));
            double distance1 = calculateDistance(charger1LatLng, destination);

            JSONObject addressInfo2 = charger2.getJSONObject("AddressInfo");
            LatLng charger2LatLng = new LatLng(addressInfo2.getDouble("Latitude"), addressInfo2.getDouble("Longitude"));
            double distance2 = calculateDistance(charger2LatLng, destination);

            return Double.compare(distance1, distance2);
        } catch (JSONException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private List<JSONObject> findOptimalChargingStops(List<JSONObject> chargers, Location currentLocation, LatLng destination) throws JSONException {
        List<JSONObject> optimalStops = new ArrayList<>();
        double currentBatteryLevel = Double.parseDouble(getCurrentBatteryLevel());
        LatLng currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

        if (currentBatteryLevel < 15) {
            addNearestChargerToStops(chargers, currentLatLng, optimalStops);
        } else {
            addSuitableChargersToStops(chargers, currentLatLng, destination, currentBatteryLevel, optimalStops);
            addAdditionalChargerIfNeeded(chargers, currentLatLng, destination, optimalStops);
        }

        return optimalStops;
    }

    private void addNearestChargerToStops(List<JSONObject> chargers, LatLng currentLatLng, List<JSONObject> optimalStops) throws JSONException {
        JSONObject nearestCharger = findNearestCharger(chargers, currentLatLng);
        if (nearestCharger != null) {
            optimalStops.add(nearestCharger);
        }
    }

    private void addSuitableChargersToStops(List<JSONObject> chargers, LatLng currentLatLng, LatLng destination, double currentBatteryLevel, List<JSONObject> optimalStops) throws JSONException {
        for (JSONObject charger : chargers) {
            if (isSuitableCharger(charger, currentLatLng, destination, currentBatteryLevel)) {
                optimalStops.add(charger);
                updateCurrentLocationAndBatteryLevel(charger, (ChargerState) optimalStops);
            }
        }
    }

    private void addAdditionalChargerIfNeeded(List<JSONObject> chargers, LatLng currentLatLng, LatLng destination, List<JSONObject> optimalStops) throws JSONException {
        double distanceToDestination = calculateDistance(currentLatLng, destination);
        double requiredBatteryLevel = calculateRequiredBatteryLevel(distanceToDestination);
        double currentBatteryLevel = Double.parseDouble(getCurrentBatteryLevel());

        if (requiredBatteryLevel > currentBatteryLevel) {
            JSONObject additionalCharger = findFurthestChargerOnRoute(chargers, currentLatLng, destination);
            if (additionalCharger != null) {
                optimalStops.add(additionalCharger);
            }
        }
    }

    private void updateCurrentLocationAndBatteryLevel(JSONObject charger, ChargerState state) throws JSONException {
        JSONObject addressInfo = charger.getJSONObject("AddressInfo");
        state.currentLatLng = new LatLng(addressInfo.getDouble("Latitude"), addressInfo.getDouble("Longitude"));
        state.currentBatteryLevel = 100; // Reset battery level after charging
    }

    private boolean isSuitableCharger(JSONObject charger, LatLng currentLocation, LatLng destination, double currentBatteryLevel) {
        try {
            LatLng chargerLatLng = getLatLngFromCharger(charger);

            if (isDetourTooLong(currentLocation, chargerLatLng, destination)) {
                return false;
            }

            if (!isWithinCurrentRange(currentLocation, chargerLatLng, currentBatteryLevel)) {
                return false;
            }

            return isEnoughBatteryToReachCharger(currentLocation, chargerLatLng, currentBatteryLevel);
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    private LatLng getLatLngFromCharger(JSONObject charger) throws JSONException {
        JSONObject addressInfo = charger.getJSONObject("AddressInfo");
        return new LatLng(addressInfo.getDouble("Latitude"), addressInfo.getDouble("Longitude"));
    }

    private boolean isDetourTooLong(LatLng currentLocation, LatLng chargerLatLng, LatLng destination) {
        double distanceToCharger = calculateDistance(currentLocation, chargerLatLng);
        double distanceFromChargerToDestination = calculateDistance(chargerLatLng, destination);
        double totalDetourDistance = distanceToCharger + distanceFromChargerToDestination - calculateDistance(currentLocation, destination);

        return totalDetourDistance > MAX_DETOUR_DISTANCE;
    }

    private boolean isWithinCurrentRange(LatLng currentLocation, LatLng chargerLatLng, double currentBatteryLevel) {
        double milesPerKwh = Double.parseDouble(getMilesPerKwh());
        double currentRange = currentBatteryLevel * milesPerKwh;
        double distanceToCharger = calculateDistance(currentLocation, chargerLatLng);

        return distanceToCharger <= currentRange;
    }

    private boolean isEnoughBatteryToReachCharger(LatLng currentLocation, LatLng chargerLatLng, double currentBatteryLevel) {
        double distanceToCharger = calculateDistance(currentLocation, chargerLatLng);
        double requiredBatteryLevelToReachCharger = calculateRequiredBatteryLevel(distanceToCharger);

        return currentBatteryLevel >= requiredBatteryLevelToReachCharger;
    }

    private JSONObject findFurthestChargerOnRoute(List<JSONObject> chargers, LatLng currentLocation, LatLng destination) throws JSONException {
        JSONObject furthestCharger = null;
        double furthestDistance = 0;

        for (JSONObject charger : chargers) {
            double distanceToCharger = getDistanceToCharger(currentLocation, charger);
            if (distanceToCharger < 0) continue;  // Skip if distance could not be calculated.

            double totalDistanceViaCharger = calculateTotalDistanceViaCharger(charger, currentLocation, destination);
            double directDistanceToDestination = calculateDistance(currentLocation, destination);

            if (isViableChargerRoute(totalDistanceViaCharger, directDistanceToDestination, distanceToCharger, furthestDistance)) {
                furthestCharger = charger;
                furthestDistance = distanceToCharger;
            }
        }

        return furthestCharger;
    }

    private double getDistanceToCharger(LatLng currentLocation, JSONObject charger) {
        try {
            JSONObject addressInfo = charger.getJSONObject("AddressInfo");
            LatLng chargerLatLng = new LatLng(addressInfo.getDouble("Latitude"), addressInfo.getDouble("Longitude"));
            return calculateDistance(currentLocation, chargerLatLng);
        } catch (JSONException e) {
            e.printStackTrace();
            return -1; // Return negative distance to indicate an error.
        }
    }

    private double calculateTotalDistanceViaCharger(JSONObject charger, LatLng currentLocation, LatLng destination) throws JSONException {
        LatLng chargerLatLng = getLatLngFromCharger(charger);
        double distanceToCharger = calculateDistance(currentLocation, chargerLatLng);
        double distanceFromChargerToDestination = calculateDistance(chargerLatLng, destination);
        return distanceToCharger + distanceFromChargerToDestination;
    }

    private boolean isViableChargerRoute(double totalDistanceViaCharger, double directDistanceToDestination, double distanceToCharger, double furthestDistance) {
        return totalDistanceViaCharger <= directDistanceToDestination * 1.2 && distanceToCharger > furthestDistance;
    }

    private JSONObject findNearestCharger(List<JSONObject> chargers, LatLng currentLocation) {
        JSONObject nearestCharger = null;
        double nearestDistance = Double.MAX_VALUE;

        for (JSONObject charger : chargers) {
            LatLng chargerLatLng = getChargerLatLng(charger);
            if (chargerLatLng != null) {
                double distance = calculateDistance(currentLocation, chargerLatLng);
                if (distance < nearestDistance) {
                    nearestCharger = charger;
                    nearestDistance = distance;
                }
            }
        }

        return nearestCharger;
    }

    private LatLng getChargerLatLng(JSONObject charger) {
        try {
            JSONObject addressInfo = charger.getJSONObject("AddressInfo");
            return new LatLng(addressInfo.getDouble("Latitude"), addressInfo.getDouble("Longitude"));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private LatLng findMidpoint(LatLng start, LatLng end) {
        double lat = (start.latitude + end.latitude) / 2;
        double lng = (start.longitude + end.longitude) / 2;
        return new LatLng(lat, lng);
    }

    private void displayOptimalChargingStops(List<JSONObject> chargers) {
        for (JSONObject charger : chargers) {
            try {
                JSONObject addressInfo = charger.getJSONObject("AddressInfo");
                LatLng chargerLatLng = new LatLng(addressInfo.getDouble("Latitude"), addressInfo.getDouble("Longitude"));
                mMap.addMarker(new MarkerOptions().position(chargerLatLng).title(addressInfo.getString("Title")));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private double calculateRequiredBatteryLevel(double distance) {
        double milesPerKwh = Double.parseDouble(getMilesPerKwh());
        if (milesPerKwh == 0) return 100;
        double requiredKwh = distance / milesPerKwh;
        return (requiredKwh * 100);
    }

    private String getMilesPerKwh() {
        SharedPreferences sharedPreferences = getSharedPreferences("UserSettings", MODE_PRIVATE);
        return sharedPreferences.getString("MilesPerKwh", "0");
    }

    private void getLastLocation() {
        if (!hasLocationPermissions()) {
            requestLocationPermissions();
            return;
        }
        retrieveAndProcessLocation();
    }

    private boolean hasLocationPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    }

    private void retrieveAndProcessLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        Task<Location> locationResult = fusedLocationClient.getLastLocation();
        locationResult.addOnCompleteListener(this, this::handleLocationResult);
    }

    private void handleLocationResult(Task<Location> task) {
        if (task.isSuccessful() && task.getResult() != null) {
            Location location = task.getResult();
            updateUIWithLocation(location);
        }
    }

    private void updateUIWithLocation(Location location) {
        LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 17));

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (!addresses.isEmpty()) {
                String address = addresses.get(0).getAddressLine(0);
                currentAddressTextView.setText(address);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void clearMap() {
        if (mMap != null) {
            mMap.clear();
        }
        destinationMarker = null;
        currentPolyline = null;
    }

    private void drawRoute() {
        clearMap();
        if (destinationLatLng == null) {
            showToast("Please set a destination first");
            return;
        }

        try {
            String routeUrl = buildRouteUrl();
            if (routeUrl != null) {
                new FetchRouteTask().execute(routeUrl);
            }
        } catch (UnsupportedEncodingException e) {
            showToast("Error encoding the URL");
        }
    }

    private String buildRouteUrl() throws UnsupportedEncodingException {
        String encodedOrigin = URLEncoder.encode(currentAddressTextView.getText().toString(), "UTF-8");
        String encodedDestination = URLEncoder.encode(destination_address, "UTF-8");

        StringBuilder urlBuilder = new StringBuilder("https://maps.googleapis.com/maps/api/directions/json?");
        urlBuilder.append("origin=").append(encodedOrigin)
                .append("&destination=").append(encodedDestination)
                .append("&sensor=false&units=metric&mode=driving");

        if (evChargerCheckbox.isChecked() && optimalChargerLatLng != null) {
            addChargerWaypoint(urlBuilder);
        }

        urlBuilder.append("&key=").append(GOOGLE_MAPS_API_KEY);
        return urlBuilder.toString();
    }

    private void addChargerWaypoint(StringBuilder urlBuilder) {
        String chargerLocation = optimalChargerLatLng.latitude + "," + optimalChargerLatLng.longitude;
        urlBuilder.append("&waypoints=").append(chargerLocation);
        mMap.addMarker(new MarkerOptions()
                .position(optimalChargerLatLng)
                .title("Charging Location"));
    }

    private class FetchRouteTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(urls[0]).build();
            try {
                Response response = client.newCall(request).execute();
                if (response.body() != null) {
                    String responseBody = response.body().string();
                    Log.d("MapsActivity", "Response from Directions API: " + responseBody);
                    return responseBody;
                } else {
                    Log.e("MapsActivity", "No response body from Directions API");
                    return null;
                }
            } catch (IOException e) {
                Log.e("MapsActivity", "Error fetching route data", e);
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result == null || result.isEmpty()) {
                Toast.makeText(MapsActivity.this, "Failed to fetch route data", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                JSONObject jsonObject = new JSONObject(result);
                JSONArray routes = jsonObject.getJSONArray("routes");

                if (routes.length() > 0) {
                    JSONObject route = routes.getJSONObject(0);

                    double totalDistance = 0;
                    long totalDuration = 0;
                    JSONArray legs = route.getJSONArray("legs");
                    for (int i = 0; i < legs.length(); i++) {
                        JSONObject leg = legs.getJSONObject(i);
                        totalDistance += leg.getJSONObject("distance").getDouble("value");
                        totalDuration += leg.getJSONObject("duration").getLong("value");
                    }

                    boolean hasChargingStop = optimalChargerLatLng != null && evChargerCheckbox.isChecked();
                    double initialBatteryLevel = getCurrentBatteryLevel();
                    double consumptionRate = getBatteryConsumptionRate();

                    double estimatedRemainingBattery = calculateEstimatedRemainingBattery(totalDistance, initialBatteryLevel, consumptionRate, hasChargingStop);

                    if (estimatedRemainingBattery <= 0) {
                        Toast.makeText(MapsActivity.this, "Trip is not possible without charge", Toast.LENGTH_LONG).show();
                        return;
                    }

                    JSONObject polyline = route.getJSONObject("overview_polyline");
                    String encodedString = polyline.getString("points");
                    List<LatLng> points = PolyUtil.decode(encodedString);

                    PolylineOptions mPolylineOptions = new PolylineOptions()
                            .addAll(points)
                            .width(16)
                            .color(Color.parseColor("#1976D2"))
                            .geodesic(true)
                            .zIndex(8);
                    currentPolyline = mMap.addPolyline(mPolylineOptions);

                    String distanceInMiles = String.format(Locale.getDefault(), "%.2f mi", totalDistance * 0.000621371);
                    String durationReadable = formatDuration(totalDuration);

                    updateRouteInfo(distanceInMiles, durationReadable);
                    updateChargeIndicator(estimatedRemainingBattery);

                    destinationMarker = mMap.addMarker(new MarkerOptions()
                            .position(destinationLatLng)
                            .title("Destination"));
                } else {
                    Toast.makeText(MapsActivity.this, "No routes found", Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                Toast.makeText(MapsActivity.this, "Error parsing route data", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }

        private void updateChargeIndicator(double remainingBattery) {
            runOnUiThread(() -> {
                TextView chargeIndicator = findViewById(R.id.chargeIndicator);
                chargeIndicator.setText(String.format(Locale.getDefault(), "Charge: %.2f%%", remainingBattery));
            });
        }

        private double getCurrentBatteryLevel() {
            SharedPreferences sharedPreferences = getSharedPreferences("UserSettings", MODE_PRIVATE);
            return Double.parseDouble(sharedPreferences.getString("BatteryPercentage", "100"));
        }

        private double getBatteryConsumptionRate() {
            SharedPreferences sharedPreferences = getSharedPreferences("UserSettings", MODE_PRIVATE);
            return Double.parseDouble(sharedPreferences.getString("ConsumptionRate", "0.5"));
        }

        private String formatDuration(long seconds) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            if (hours > 0) {
                return String.format(Locale.getDefault(), "%d hr %02d min", hours, minutes);
            } else {
                return String.format(Locale.getDefault(), "%d min", minutes);
            }
        }

        private void updateRouteInfo(String distance, String duration) {
            runOnUiThread(() -> {
                TextView distanceIndicator = findViewById(R.id.distanceIndicator);
                TextView timeIndicator = findViewById(R.id.timeIndicator);

                distanceIndicator.setText("Miles: " + distance);
                timeIndicator.setText("Time: " + duration);
            });
        }
    } // Update this?

    private double calculateEstimatedRemainingBattery(double totalDistance, double initialBatteryLevel, double consumptionRate, boolean hasChargingStop) {
        double totalDistanceInMiles = totalDistance * 0.000621371;
        double estimatedBatteryUsed = totalDistanceInMiles * consumptionRate;

        if (hasChargingStop) {
            double distanceAfterCharging = calculateDistance(optimalChargerLatLng, destinationLatLng) * 0.000621371;
            double estimatedBatteryUsedAfterCharging = distanceAfterCharging * consumptionRate;
            return 100 - estimatedBatteryUsedAfterCharging;
        } else {
            return initialBatteryLevel - estimatedBatteryUsed;
        }
    }

    private String getCurrentBatteryLevel() {
        SharedPreferences sharedPreferences = getSharedPreferences("UserSettings", MODE_PRIVATE);
        return sharedPreferences.getString("BatteryPercentage", "0");
    }

    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }

        if (hasLocationPermission()) {
            enableLocationFeatures();
        } else {
            disableLocationFeatures();
            requestLocationPermission();
        }
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void enableLocationFeatures() {
        try {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void disableLocationFeatures() {
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
        mMap.setMyLocationEnabled(false);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    }

    private void fetchEVChargersAndFindOptimalStops(LatLng queryLocation) {
        int radius = 500;
        String url = buildChargerApiUrl(queryLocation, radius);
        makeChargerApiRequest(url, queryLocation, radius);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateLocationUI();
            }
        }
    }
}