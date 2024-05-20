package com.example.plugmapuk;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {
    private EditText currentChargeInput, miPerKwhInput;
    private Set<String> selectedSocketTypes = new HashSet<>();

    private static final String[] ALL_SOCKET_TYPES = {
            "Avcon Connector", "BS1363 3 Pin 13 Amp", "Blue Commando (2P+E)", "CCS (Type 1)", "CCS (Type 2)", "CEE 3 Pin", "CEE 5 Pin", "CEE 7/4 - Schuko - Type F", "CEE 7/5", "CEE+ 7 Pin", "CHAdeMO", "Europlug 2-Pin (CEE 7/16)", "GB-T AC - GB/T 20234.2 (Socket)", "GB-T AC - GB/T 20234.2 (Tethered Cable)", "GB-T DC - GB/T 20234.3", "IEC 60309 3-pin", "IEC 60309 5-pin", "LP Inductive", "NACS / Tesla Supercharger", "NEMA 14-30", "NEMA 14-50", "NEMA 5-15R", "NEMA 5-20R", "NEMA 6-15", "NEMA 6-20", "NEMA TT-30R", "SCAME Type 3A (Low Power)", "SCAME Type 3C (Schneider-Legrand)", "SP Inductive", "T13 - SEC1011 ( Swiss domestic 3-pin ) - Type J", "Tesla (Model S/X)", "Tesla (Roadster)", "Tesla Battery Swap", "Type 1 (J1772)", "Type 2 (Socket Only)", "Type 2 (Tethered Connector)", "Type I (AS 3112)", "Type M"
    };
    private boolean[] checkedSocketTypes = new boolean[ALL_SOCKET_TYPES.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        currentChargeInput = findViewById(R.id.currentChargeInput);
        miPerKwhInput = findViewById(R.id.miPerKwhInput);

        TextView socketTypeSelection = findViewById(R.id.socketTypeSelection);
        socketTypeSelection.setOnClickListener(v -> showSocketTypeSelectionDialog());

        Button saveSettingsButton = findViewById(R.id.saveSettingsButton);
        saveSettingsButton.setOnClickListener(v -> saveSettings());

        Button closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> finish());

        loadSavedSettings();
    }

    private void showSocketTypeSelectionDialog() {
        String[] socketTypeNames = SOCKET_TYPE_IDS.keySet().toArray(new String[0]);
        boolean[] checkedItems = new boolean[socketTypeNames.length];

        for (int i = 0; i < socketTypeNames.length; i++) {
            Integer typeId = SOCKET_TYPE_IDS.get(socketTypeNames[i]);
            checkedItems[i] = selectedSocketTypes.contains(String.valueOf(typeId));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Socket Types")
                .setMultiChoiceItems(socketTypeNames, checkedItems, (dialog, which, isChecked) -> {
                    String typeName = socketTypeNames[which];
                    Integer typeId = SOCKET_TYPE_IDS.get(typeName);
                    if (isChecked) {
                        selectedSocketTypes.add(String.valueOf(typeId));
                    } else {
                        selectedSocketTypes.remove(String.valueOf(typeId));
                    }
                })
                .setPositiveButton("OK", (dialog, which) -> {})
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static final Map<String, Integer> SOCKET_TYPE_IDS = new HashMap<>();
    static {
        SOCKET_TYPE_IDS.put("Type 1 (J1772)",1);
        SOCKET_TYPE_IDS.put("CHAdeMO",2);
        SOCKET_TYPE_IDS.put("BS1363 3 Pin 13 Amp",3);
        SOCKET_TYPE_IDS.put("Blue Commando (2P+E)",4);
        SOCKET_TYPE_IDS.put("LP Inductive",5);
        SOCKET_TYPE_IDS.put("SP Inductive",6);
        SOCKET_TYPE_IDS.put("Avcon Connector",7);
        SOCKET_TYPE_IDS.put("Tesla (Roadster)",8);
        SOCKET_TYPE_IDS.put("NEMA 5-20R",9);
        SOCKET_TYPE_IDS.put("NEMA 14-30",10);
        SOCKET_TYPE_IDS.put("NEMA 14-50",11);
        SOCKET_TYPE_IDS.put("Europlug 2-Pin (CEE 7/16)",13);
        SOCKET_TYPE_IDS.put("NEMA 6-20",14);
        SOCKET_TYPE_IDS.put("NEMA 6-15",15);
        SOCKET_TYPE_IDS.put("CEE 3 Pin",16);
        SOCKET_TYPE_IDS.put("CEE 5 Pin",17);
        SOCKET_TYPE_IDS.put("CEE+ 7 Pin",18);
        SOCKET_TYPE_IDS.put("CCS (Type 2)", 33);
        SOCKET_TYPE_IDS.put("Type 2 (Socket Only)",25);
        SOCKET_TYPE_IDS.put("Type 2 (Tethered Connector)",1036);
        SOCKET_TYPE_IDS.put("NACS / Tesla Supercharger",27);
        SOCKET_TYPE_IDS.put("CCS (Type 1)",32);
        SOCKET_TYPE_IDS.put("GB-T DC - GB/T 20234.3",1040);
        SOCKET_TYPE_IDS.put("CEE 7/4 - Schuko - Type F",28);
        SOCKET_TYPE_IDS.put("Tesla (Model S/X)",30);
        SOCKET_TYPE_IDS.put("Type M",1043);
        SOCKET_TYPE_IDS.put("CEE 7/5",23);
        SOCKET_TYPE_IDS.put("GB-T AC - GB/T 20234.2 (Socket)",1038);
        SOCKET_TYPE_IDS.put("GB-T AC - GB/T 20234.2 (Tethered Cable)",1039);
        SOCKET_TYPE_IDS.put("IEC 60309 3-pin",34);
        SOCKET_TYPE_IDS.put("IEC 60309 5-pin",35);
        SOCKET_TYPE_IDS.put("Type I (AS 3112)",29);
        SOCKET_TYPE_IDS.put("T13 - SEC1011 ( Swiss domestic 3-pin ) - Type J",1037);
        SOCKET_TYPE_IDS.put("SCAME Type 3C (Schneider-Legrand)",26);
        SOCKET_TYPE_IDS.put("SCAME Type 3A (Low Power)",36);
        SOCKET_TYPE_IDS.put("NEMA TT-30R",1042);
        SOCKET_TYPE_IDS.put("NEMA 5-15R",22);
    }

    private void saveSettings() {
        String batteryPercentage = currentChargeInput.getText().toString();
        String milesPerKwh = miPerKwhInput.getText().toString();

        SharedPreferences sharedPreferences = getSharedPreferences("UserSettings", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putStringSet("SocketTypes", selectedSocketTypes);
        editor.putString("BatteryPercentage", batteryPercentage);
        editor.putString("MilesPerKwh", milesPerKwh);

        editor.apply();

        Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show();
    }

    private void loadSavedSettings() {
        SharedPreferences sharedPreferences = getSharedPreferences("UserSettings", MODE_PRIVATE);

        Set<String> savedSocketTypes = sharedPreferences.getStringSet("SocketTypes", new HashSet<>());
        selectedSocketTypes.clear();
        selectedSocketTypes.addAll(savedSocketTypes);

        String batteryPercentage = sharedPreferences.getString("BatteryPercentage", "");
        String milesPerKwh = sharedPreferences.getString("MilesPerKwh", "");
        currentChargeInput.setText(batteryPercentage);
        miPerKwhInput.setText(milesPerKwh);
    }
}
