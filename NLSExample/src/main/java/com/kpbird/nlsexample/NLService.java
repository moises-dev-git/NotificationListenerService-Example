package com.kpbird.nlsexample;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NLService extends NotificationListenerService {
    private String TAG = this.getClass().getSimpleName();

    // Nome do arquivo de preferências
    private static final String PREFS_NAME = "HistoricoNotificacoes";
    private static final String KEY_LOG = "log_texto";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.i(TAG, "********** onNotificationPosted");

        // 1. Captura avançada do Título e Corpo da notificação (evita retornos nulos)
        Bundle extras = sbn.getNotification().extras;
        String titulo = extras.getString(android.app.Notification.EXTRA_TITLE, "Sem Título");
        CharSequence corpoChar = extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
        String corpo = (corpoChar != null) ? corpoChar.toString() : "Sem Conteúdo";
        String pacote = sbn.getPackageName();

        // 2. Monta a string de payload que será salva (sem timestamp)
        String payload = String.format("App: %s | %s: %s", pacote, titulo, corpo);

        // 3. Verifica aplicativos ignorados
        SharedPreferences settingsPrefs = getSharedPreferences(SettingsActivity.SETTINGS_PREFS, Context.MODE_PRIVATE);
        String ignoredAppsStr = settingsPrefs.getString(SettingsActivity.KEY_IGNORED_APPS, "");
        if (!ignoredAppsStr.isEmpty()) {
            String[] ignoredApps = ignoredAppsStr.split(",");
            for (String ignoredApp : ignoredApps) {
                if (pacote.trim().equalsIgnoreCase(ignoredApp.trim())) {
                    Log.i(TAG, "Notificação ignorada do app: " + pacote);
                    return;
                }
            }
        }

        // 4. Salva localmente via SharedPreferences (o método checará duplicatas)
        salvarNoSharedPreferences(payload);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.i(TAG, "********** onNotificationRemoved");
    }

    // Método responsável por acumular os textos no SharedPreferences
    private void salvarNoSharedPreferences(String payload) {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();

            // Recupera o que já estava salvo (se não houver nada, retorna uma string vazia)
            String historicoAntigo = sharedPreferences.getString(KEY_LOG, "");

            // Verifica se a última notificação do histórico é idêntica à atual
            if (historicoAntigo.endsWith(payload)) {
                Log.d(TAG, "Notificação duplicada ignorada.");
                return;
            }

            // Cria um timestamp formatado
            String horario = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());

            // Junta o timestamp com o payload
            String novoTexto = String.format("[%s] %s", horario, payload);

            // Junta o histórico antigo com a nova linha
            String historicoAtualizado;
            if (historicoAntigo.isEmpty()) {
                historicoAtualizado = novoTexto;
            } else {
                historicoAtualizado = historicoAntigo + "\n" + novoTexto;
            }

            // Salva de forma assíncrona (apply não trava a thread principal)
            editor.putString(KEY_LOG, historicoAtualizado);
            editor.commit();

            // Toast rápido confirmando o salvamento local
            Toast.makeText(getApplicationContext(), "Notificação salva localmente!", Toast.LENGTH_SHORT).show();

            // Log para conferência no Logcat
            Log.d(TAG, "Salvo no SharedPreferences: " + novoTexto);

            // Avisa a MainActivity que há uma nova notificação
            Intent intent = new Intent("com.kpbird.nlsexample.NOTIFICATION_LISTENER_EXAMPLE");
            intent.setPackage(getPackageName());
            intent.putExtra("nova_notificacao", novoTexto);
            sendBroadcast(intent);

        } catch (Exception e) {
            Log.e(TAG, "Erro ao salvar no SharedPreferences", e);
        }
    }
}
