package com.example.proyectopst;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_NOTIF = 1001;

    // UI
    private TextView salaTempTextView, salaHumTextView, salaCoTextView,salaTimeTextView, cocinaTimeTextView;
    private MaterialCardView salaTempCardView, salaHumCardView, salaCoCardView;
    private TextView cocinaTempTextView, cocinaHumTextView, cocinaCoTextView;
    private MaterialCardView cocinaTempCardView, cocinaHumCardView, cocinaCoCardView;

    // Firebase refs
    private DatabaseReference medicionesRef, equiposRef, zonasRef;

    // Colores
    private int colorNormalBlue, colorNormalGreen, colorNormalOrange;
    private int colorWarning, colorDanger;

    // Límites por defecto (se sobrescriben si BD tiene valores)
    private double salaTempMax = 30d, salaHumMax = 80d, salaCoMax = 30d;
    private double cocinaTempMax = 30d, cocinaHumMax = 80d, cocinaCoMax = 30d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initColors();
        initUI();
        initFirebase();

        setInitialTexts();
        requestNotificationPermissionIfNeeded();
        loadLimitsFromFirebase();
        startRealtimeListeners();
        startFirebaseBackgroundServiceIfNeeded();
    }

    private void initColors() {
        colorNormalBlue = ContextCompat.getColor(this, R.color.blue_500);
        colorNormalGreen = ContextCompat.getColor(this, R.color.green_500);
        colorNormalOrange = ContextCompat.getColor(this, R.color.orange_500);
        colorWarning = ContextCompat.getColor(this, R.color.yellow_500);
        colorDanger = ContextCompat.getColor(this, R.color.red);
    }

    private void initUI() {
        salaTempTextView = findViewById(R.id.sala_temp_value);
        salaTimeTextView = findViewById(R.id.sala_last_update);
        salaHumTextView = findViewById(R.id.sala_hum_value);
        salaCoTextView = findViewById(R.id.sala_co_value);
        salaTempCardView = findViewById(R.id.sala_temp_card);
        salaHumCardView = findViewById(R.id.sala_hum_card);
        salaCoCardView = findViewById(R.id.sala_co_card);

        cocinaTempTextView = findViewById(R.id.cocina_temp_value);
        cocinaTimeTextView = findViewById(R.id.cocina_last_update);
        cocinaHumTextView = findViewById(R.id.cocina_hum_value);
        cocinaCoTextView = findViewById(R.id.cocina_co_value);
        cocinaTempCardView = findViewById(R.id.cocina_temp_card);
        cocinaHumCardView = findViewById(R.id.cocina_hum_card);
        cocinaCoCardView = findViewById(R.id.cocina_co_card);
    }

    private void initFirebase() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        medicionesRef = database.getReference("mediciones");
        equiposRef = database.getReference("equipos");
        zonasRef = database.getReference("zonas");
    }

    private void setInitialTexts() {
        if (salaTempTextView != null) salaTempTextView.setText("-- °C");
        if (salaHumTextView != null) salaHumTextView.setText("-- %");
        if (salaCoTextView != null) salaCoTextView.setText("-- ppm");

        if (cocinaTempTextView != null) cocinaTempTextView.setText("-- °C");
        if (cocinaHumTextView != null) cocinaHumTextView.setText("-- %");
        if (cocinaCoTextView != null) cocinaCoTextView.setText("-- ppm");
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_NOTIF
                );
            }
        }
    }

    private void startFirebaseBackgroundServiceIfNeeded() {
        Intent svcIntent = new Intent(this, FirebaseBackgroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svcIntent);
        } else {
            startService(svcIntent);
        }
    }

    private void loadLimitsFromFirebase() {
        if (zonasRef == null) return;

        zonasRef.child("ZonaDeEstar").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                salaTempMax = safeGetDouble(snap, "TempMax", salaTempMax);
                salaHumMax = safeGetDouble(snap, "HumMax", salaHumMax);
                salaCoMax = safeGetDouble(snap, "GasMax", salaCoMax);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Error cargando límites Sala", error.toException());
            }
        });

        zonasRef.child("Cocina").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                cocinaTempMax = safeGetDouble(snap, "TempMax", cocinaTempMax);
                cocinaHumMax = safeGetDouble(snap, "HumMax", cocinaHumMax);
                cocinaCoMax = safeGetDouble(snap, "GasMax", cocinaCoMax);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Error cargando límites Cocina", error.toException());
            }
        });
    }

    // ======= Aquí está la actualización en tiempo real =======
    private void startRealtimeListeners() {
        if (medicionesRef == null) return;

        medicionesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                List<DataSnapshot> medicionesList = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    medicionesList.add(child);
                }

                Collections.reverse(medicionesList); // últimos primero

                boolean cocinaActualizada = false;
                boolean salaActualizada = false;

                for (DataSnapshot medicionSnapshot : medicionesList) {
                    String equipo = medicionSnapshot.child("equipo").getValue(String.class);
                    String tempStr = medicionSnapshot.child("temperatura").getValue(String.class);
                    String humStr = medicionSnapshot.child("humedad").getValue(String.class);
                    String gasStr = medicionSnapshot.child("gas").getValue(String.class);
                    String timeStr = medicionSnapshot.child("timestamp").getValue(String.class);

                    if (equipo != null && tempStr != null && humStr != null && gasStr != null) {
                        try {
                            double temp = Double.parseDouble(tempStr);
                            double hum = Double.parseDouble(humStr);
                            double gas = Double.parseDouble(gasStr);

                            // Actualiza solo si la zona no ha sido actualizada
                            if (equipo.equalsIgnoreCase("E02") && !cocinaActualizada) {
                                updateDashboard(equipo, temp, hum, gas,timeStr);
                                cocinaActualizada = true;
                            } else if (equipo.equalsIgnoreCase("E01") && !salaActualizada) {
                                updateDashboard(equipo, temp, hum, gas,timeStr);
                                salaActualizada = true;
                            }

                            if (cocinaActualizada && salaActualizada) break;

                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Error parsing numbers: " + e.getMessage());
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Error leyendo mediciones", error.toException());
            }
        });
    }
    private String getTimeAgo(String timeStr) {
        try {
            // Parsear el timestamp de la base de datos
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date pastDate = sdf.parse(timeStr);
            long pastTime = pastDate.getTime();

            // Tiempo actual
            long now = System.currentTimeMillis();
            long diff = now - pastTime;

            // Convertir a diferentes unidades de tiempo
            long seconds = TimeUnit.MILLISECONDS.toSeconds(diff);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            long hours = TimeUnit.MILLISECONDS.toHours(diff);
            long days = TimeUnit.MILLISECONDS.toDays(diff);

            // Determinar el mensaje apropiado
            if (seconds < 60) {
                return "hace unos segundos";
            } else if (minutes < 60) {
                return "hace " + minutes + " minuto" + (minutes > 1 ? "s" : "");
            } else if (hours < 24) {
                return "hace " + hours + " hora" + (hours > 1 ? "s" : "");
            } else {
                return "hace " + days + " día" + (days > 1 ? "s" : "");
            }

        } catch (ParseException e) {
            Log.e(TAG, "Error parsing timestamp: " + timeStr, e);
            return "fecha desconocida";
        }
    }

    private void updateDashboard(String equipo, double temp, double hum, double gas, String timeStr) {
        if (equiposRef == null) return;
        String timeAgo = getTimeAgo(timeStr);

        equiposRef.child(equipo).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                // CORREGIR: usar "Zona" con mayúscula
                String zona = snap.child("Zona").getValue(String.class);
                if (zona != null) {
                    if (zona.equalsIgnoreCase("ZonaDeEstar")) {
                        updateSalaUI(temp, hum, gas,timeAgo);
                    } else if (zona.equalsIgnoreCase("Cocina")) {
                        updateCocinaUI(temp, hum, gas,timeAgo);
                    }
                } else {
                    Log.d(TAG, "Zona es null para equipo: " + equipo);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Error leyendo equipo", error.toException());
            }
        });
    }

    private void updateSalaUI(double temp, double hum, double gas, String timeStr) {
        runOnUiThread(() -> {
            salaTempTextView.setText(String.format("%.1f °C", temp));
            salaHumTextView.setText(String.format("%.0f %%", hum));
            salaCoTextView.setText(String.format("%.0f ppm", gas));
            salaTimeTextView.setText(String.format("Última Actualización: %s", timeStr));


            updateCardColor(salaTempCardView, temp, salaTempMax, "temp");
            updateCardColor(salaHumCardView, hum, salaHumMax, "hum");
            updateCardColor(salaCoCardView, gas, salaCoMax, "co");
        });
    }

    private void updateCocinaUI(double temp, double hum, double gas, String timeStr) {
        runOnUiThread(() -> {
            cocinaTempTextView.setText(String.format("%.1f °C", temp));
            cocinaHumTextView.setText(String.format("%.0f %%", hum));
            cocinaCoTextView.setText(String.format("%.0f ppm", gas));
            cocinaTimeTextView.setText(String.format("Última Actualización: %s", timeStr));

            updateCardColor(cocinaTempCardView, temp, cocinaTempMax, "temp");
            updateCardColor(cocinaHumCardView, hum, cocinaHumMax, "hum");
            updateCardColor(cocinaCoCardView, gas, cocinaCoMax, "co");
        });
    }

    private void updateCardColor(MaterialCardView card, double value, double maxThreshold, String sensorType) {
        int normalColor;
        switch (sensorType) {
            case "temp": normalColor = colorNormalBlue; break;
            case "hum": normalColor = colorNormalGreen; break;
            case "co": normalColor = colorNormalOrange; break;
            default: normalColor = colorNormalBlue;
        }

        if (value >= maxThreshold) {
            card.setCardBackgroundColor(colorDanger);
        } else if (value >= maxThreshold * 0.8) {
            card.setCardBackgroundColor(colorWarning);
        } else {
            card.setCardBackgroundColor(normalColor);
        }
    }

    private double safeGetDouble(DataSnapshot snap, String key, double defaultVal) {
        try {
            Object o = snap.child(key).getValue();
            if (o == null) return defaultVal;
            if (o instanceof Number) return ((Number) o).doubleValue();
            return Double.parseDouble(o.toString());
        } catch (Exception e) {
            Log.w(TAG, "safeGetDouble error for key " + key + ": " + e.getMessage());
            return defaultVal;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_NOTIF) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permiso de notificaciones concedido");
            } else {
                Log.w(TAG, "Permiso de notificaciones denegado");
            }
        }
    }
}
