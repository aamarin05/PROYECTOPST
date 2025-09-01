package com.example.proyectopst;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";
    private static final String CHANNEL_ID = "default_channel";
    private static final int NOTIF_ID = 1000;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannelIfNeeded();
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // Mensaje con payload de datos
        if (remoteMessage.getData() != null && !remoteMessage.getData().isEmpty()) {
            Map<String, String> data = remoteMessage.getData();
            Log.d(TAG, "FCM data payload: " + data);

            // Ejemplo de campos esperados en data: equipo, gas, humedad, temperatura, timestamp
            String equipo = data.get("equipo");
            String gasStr = data.get("gas");
            String humStr = data.get("humedad");
            String tempStr = data.get("temperatura");
            String title = data.getOrDefault("title", "Alerta de medición");
            String body = data.getOrDefault("body", "Nueva medición recibida");

            // Mostrar notificación
            showNotification(title, body);

            // Si vienen valores numéricos, intenta procesarlos y actualizar zona
            try {
                double gas = gasStr != null ? Double.parseDouble(gasStr) : Double.NaN;
                double hum = humStr != null ? Double.parseDouble(humStr) : Double.NaN;
                double temp = tempStr != null ? Double.parseDouble(tempStr) : Double.NaN;

                if (equipo != null && !Double.isNaN(gas) && !Double.isNaN(hum) && !Double.isNaN(temp)) {
                    // Actualiza los booleans en la zona correspondiente según tu BD
                    updateZoneStatusFromEquipo(equipo, gas, hum, temp);
                }
            } catch (NumberFormatException e) {
                Log.w(TAG, "No se pudieron parsear mediciones del payload: " + e.getMessage());
            }
        }

        // Mensaje con payload de notificación (campo notification)
        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle() != null
                    ? remoteMessage.getNotification().getTitle() : "Notificación";
            String body = remoteMessage.getNotification().getBody() != null
                    ? remoteMessage.getNotification().getBody() : "";
            showNotification(title, body);
        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "Nueva token FCM: " + token);
        // TODO: enviar token a tu servidor si hace falta (o guardarlo en DB)
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Notificaciones";
            String description = "Canal por defecto para notificaciones de mediciones";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void showNotification(String title, String body) {
        // PendingIntent para abrir MainActivity al tocar la notificación
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert) // cambia por tu icono R.drawable.ic_notification
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat nm = NotificationManagerCompat.from(this);

        // En Android 13+ el permiso POST_NOTIFICATIONS debe haberse solicitado en la Activity;
        // si no se concedió, la notificación puede no mostrarse, pero la llamada no lanzará excepción aquí.
        try {
            nm.notify(NOTIF_ID, builder.build());
        } catch (SecurityException se) {
            Log.w(TAG, "No se pudo mostrar notificación por permiso: " + se.getMessage());
        }
    }

    /**
     * Consulta 'equipos/<equipo>/zona' y luego 'zonas/<zona>' para obtener límites,
     * compara y actualiza GasOK/HumOK/TempOK en la BD.
     */
    private void updateZoneStatusFromEquipo(String equipoId, double gas, double hum, double temp) {
        DatabaseReference equiposRef = FirebaseDatabase.getInstance().getReference("equipos");
        DatabaseReference zonasRef = FirebaseDatabase.getInstance().getReference("zonas");

        equiposRef.child(equipoId).child("zona").get().addOnSuccessListener(zonaSnap -> {
            String zona = zonaSnap.getValue(String.class);
            if (zona == null) {
                Log.w(TAG, "Equipo " + equipoId + " sin zona asociada.");
                return;
            }

            zonasRef.child(zona).get().addOnSuccessListener(zonaData -> {
                double gasMax = safeGetDouble(zonaData, "GasMax", Double.NaN);
                double humMax = safeGetDouble(zonaData, "HumMax", Double.NaN);
                double tempMax = safeGetDouble(zonaData, "TempMax", Double.NaN);

                boolean gasOK = !Double.isNaN(gasMax) ? gas <= gasMax : true;
                boolean humOK = !Double.isNaN(humMax) ? hum <= humMax : true;
                boolean tempOK = !Double.isNaN(tempMax) ? temp <= tempMax : true;

                // Actualizar en la BD
                zonasRef.child(zona).child("GasOK").setValue(gasOK);
                zonasRef.child(zona).child("HumOK").setValue(humOK);
                zonasRef.child(zona).child("TempOK").setValue(tempOK);

                Log.d(TAG, "Zona " + zona + " actualizada: GasOK=" + gasOK + " HumOK=" + humOK + " TempOK=" + tempOK);
            }).addOnFailureListener(e -> Log.w(TAG, "Error al leer 'zonas/" + zona + "': " + e.getMessage()));
        }).addOnFailureListener(e -> Log.w(TAG, "Error al leer 'equipos/" + equipoId + "/zona': " + e.getMessage()));
    }

    private double safeGetDouble(DataSnapshot snap, String childKey, double defaultVal) {
        try {
            Object o = snap.child(childKey).getValue();
            if (o == null) return defaultVal;
            if (o instanceof Number) return ((Number) o).doubleValue();
            String s = o.toString();
            return Double.parseDouble(s);
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
