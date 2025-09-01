package com.example.proyectopst;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;

import com.google.firebase.database.*;

public class FirebaseBackgroundService extends Service {

    private static final String CHANNEL_ID = "mediciones_channel";
    private static final int NOTIFICATION_ID = 1;
    private DatabaseReference measurementsRef;
    private NotificationManager notificationManager;

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
                .setSmallIcon(android.R.drawable.ic_dialog_alert) // Icono genérico
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true) // Mantiene la notificación visible
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .build();
    }

    private void setupFirebaseListener() {
        measurementsRef = FirebaseDatabase.getInstance().getReference("mediciones");

        // Usar ValueEventListener para obtener siempre la última medición
        measurementsRef.orderByChild("timestamp").limitToLast(1)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot child : snapshot.getChildren()) {
                                updateNotification(child);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        System.out.println("Firebase error: " + error.getMessage());
                    }
                });
    }

    private void updateNotification(DataSnapshot snapshot) {
        if (snapshot.exists()) {
            // Convertir a String para asegurar compatibilidad
            String temperatura = String.valueOf(snapshot.child("temperatura").getValue());
            String humedad = String.valueOf(snapshot.child("humedad").getValue());
            String gas = String.valueOf(snapshot.child("gas").getValue());

            String message = "Temperatura: " + temperatura + "°C, Humedad: " + humedad + "%, Gas: " + gas + " ppm";
            showNotification("Nueva medición recibida", message);
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
