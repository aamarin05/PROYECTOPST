package com.example.proyectopst;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import com.google.firebase.database.*;

import java.util.HashMap;
import java.util.Map;

public class FirebaseBackgroundService extends Service {

    private static final String CHANNEL_ID = "mediciones_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "FirebaseBGService";

    private DatabaseReference measurementsRef;
    private NotificationManager notificationManager;

    // Guardar última medición por zona
    private Map<String, String> lastMeasurements = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        notificationManager = getSystemService(NotificationManager.class);
        startForeground(NOTIFICATION_ID, buildNotification("Mediciones en tiempo real", "Esperando datos..."));
        setupFirebaseListener();
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Mediciones Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String title, String message) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build();
    }

    private void setupFirebaseListener() {
        measurementsRef = FirebaseDatabase.getInstance().getReference("mediciones");

        // Solo traemos la última medición de cada sensor
        measurementsRef.orderByChild("timestamp").limitToLast(50)
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                        updateMeasurement(snapshot);
                    }

                    @Override
                    public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                        updateMeasurement(snapshot);
                    }

                    @Override
                    public void onChildRemoved(DataSnapshot snapshot) {}

                    @Override
                    public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Firebase error: " + error.getMessage());
                    }
                });
    }

    private void updateMeasurement(DataSnapshot snapshot) {
        if (snapshot.exists()) {
            String equipo = snapshot.child("equipo").getValue(String.class);
            String temperatura = snapshot.child("temperatura").getValue(String.class);
            String humedad = snapshot.child("humedad").getValue(String.class);

            Log.d(TAG, "Medición recibida - equipo: " + equipo + ", temp: " + temperatura + ", hum: " + humedad);

            if (equipo != null) {
                // Obtener DIRECTAMENTE la Zona del equipo
                DatabaseReference zonaRef = FirebaseDatabase.getInstance()
                        .getReference("equipos").child(equipo).child("Zona");

                zonaRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        String zona = "N/A";
                        if (dataSnapshot.exists()) {
                            zona = dataSnapshot.getValue(String.class);
                            Log.d(TAG, "Zona obtenida: " + zona);
                        } else {
                            Log.d(TAG, "No se encontró Zona para el equipo: " + equipo);
                        }

                        // Construir mensaje de notificación con todos los datos
                        String mensaje = "Equipo: " + equipo +
                                "\nZona: " + zona +
                                "\nTemp: " + (temperatura != null ? temperatura : "N/A") +
                                "\nHum: " + (humedad != null ? humedad : "N/A");

                        // Llamar a tu función que muestra/actualiza la notificación
                        showNotification("Medición actual", mensaje);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Error al obtener Zona: " + databaseError.getMessage());
                    }
                });

            }
        }
    }

    private void showNotification(String title, String message) {
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(title, message));
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
}
