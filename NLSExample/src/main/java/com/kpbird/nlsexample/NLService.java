package com.kpbird.nlsexample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NLService extends NotificationListenerService {
    private String TAG = this.getClass().getSimpleName();
    private NLServiceReceiver nlservicereciver;

    @Override
    public void onCreate() {
        super.onCreate();
        nlservicereciver = new NLServiceReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.kpbird.nlsexample.NOTIFICATION_LISTENER_SERVICE_EXAMPLE");
        ContextCompat.registerReceiver(this, nlservicereciver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(nlservicereciver);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.i(TAG, "********** onNotificationPosted");
        Log.i(TAG, "ID :" + sbn.getId() + "\t" + sbn.getNotification().tickerText + "\t" + sbn.getPackageName());

        Intent i = new Intent("com.kpbird.nlsexample.NOTIFICATION_LISTENER_EXAMPLE");
        i.putExtra("notification_event", "onNotificationPosted :" + sbn.getPackageName() + "\n");
        sendBroadcast(i);

        // Captura o texto da notificação ou o nome do aplicativo caso o texto seja nulo
        CharSequence tickerText = sbn.getNotification().tickerText;
        String textoParaEnviar = (tickerText != null) ? tickerText.toString()
                : "Nova notificação de: " + sbn.getPackageName();

        // CHAMADA DA SUA FUNÇÃO
        // sendToInvertexto(textoParaEnviar);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.i(TAG, "********** onNOtificationRemoved");
        Log.i(TAG, "ID :" + sbn.getId() + "\t" + sbn.getNotification().tickerText + "\t" + sbn.getPackageName());
        Intent i = new Intent("com.kpbird.nlsexample.NOTIFICATION_LISTENER_EXAMPLE");
        i.putExtra("notification_event", "onNotificationRemoved :" + sbn.getPackageName() + "\n");
        sendBroadcast(i);
        // sendToInvertexto(sbn.getNotification().tickerText.toString());
    }

    class NLServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getStringExtra("command") != null && intent.getStringExtra("command").equals("clearall")) {
                NLService.this.cancelAllNotifications();
            } else if (intent.getStringExtra("command") != null && intent.getStringExtra("command").equals("list")) {
                Intent i1 = new Intent("com.kpbird.nlsexample.NOTIFICATION_LISTENER_EXAMPLE");
                i1.putExtra("notification_event", "=====================");
                sendBroadcast(i1);
                int i = 1;
                for (StatusBarNotification sbn : NLService.this.getActiveNotifications()) {
                    Intent i2 = new Intent("com.kpbird.nlsexample.NOTIFICATION_LISTENER_EXAMPLE");
                    i2.putExtra("notification_event", i + " " + sbn.getPackageName() + "\n");
                    sendBroadcast(i2);
                    i++;
                }
                Intent i3 = new Intent("com.kpbird.nlsexample.NOTIFICATION_LISTENER_EXAMPLE");
                i3.putExtra("notification_event", "===== Notification List ====");
                sendBroadcast(i3);
            }
        }
    }

    private void sendToInvertexto(String text) {
        ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
        networkExecutor.execute(() -> {
            try {
                java.net.URL url = new java.net.URL("https://www.invertexto.com/ajax/notepad.php");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String postData = "userId=idhz21&token=y90RcoSBxwpSRDtCWK6IQ3oEOyXSH8w9&saved_text="
                        + java.net.URLEncoder.encode(text, "UTF-8");

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = postData.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();

                // Cria um Handler para exibir o Toast na Thread Principal (Main Thread)
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (responseCode == 200) {
                        Toast.makeText(NLService.this, "Texto enviado com sucesso!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(NLService.this, "Erro ao enviar: " + responseCode, Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e("NetworkError", "Erro ao enviar texto", e);
                new Handler(Looper.getMainLooper())
                        .post(() -> Toast.makeText(NLService.this, "Falha na conexão", Toast.LENGTH_SHORT).show());
            }
        });
    }
}
