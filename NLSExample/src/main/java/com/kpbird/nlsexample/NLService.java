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

        // Chamada de exemplo para remover a notificação se necessário
        verificarERemoverNotificacao(sbn, pacote, titulo, corpo);

        // 3. Áudios do WhatsApp: dispara antes dos filtros, para que nenhuma notificação
        //    ignorada impeça a importação (a notificação chega antes do download terminar)
        SharedPreferences settingsPrefs = getSharedPreferences(SettingsActivity.SETTINGS_PREFS, Context.MODE_PRIVATE);
        if ((pacote.equals("com.whatsapp") || pacote.equals("com.whatsapp.w4b"))
                && settingsPrefs.getBoolean(SettingsActivity.KEY_VOICE_NOTES_ENABLED, false)) {
            WhatsAppVoiceNotesUtils.onWhatsAppNotification(this);
        }

        // 4. Verifica aplicativos, palavras e expressões ignoradas
        String ignoredAppsStr = settingsPrefs.getString(SettingsActivity.KEY_IGNORED_APPS, "");
        if (!ignoredAppsStr.isEmpty()) {
            String[] ignoredApps = ignoredAppsStr.split(",");
            String textoCompleto = (pacote + " " + titulo + " " + corpo).toLowerCase(Locale.getDefault());
            for (String ignoredApp : ignoredApps) {
                String filter = ignoredApp.trim().toLowerCase(Locale.getDefault());
                if (!filter.isEmpty() && textoCompleto.contains(filter)) {
                    Log.i(TAG, "Notificação ignorada pelo filtro (" + filter + "): " + pacote);
                    return;
                }
            }
        }

        // 5. Salva localmente no banco (o método checará duplicatas)
        salvarNoHistorico(payload);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.i(TAG, "********** onNotificationRemoved");
    }

    /**
     * Verifica as regras e remove (cancela) a notificação da barra de status do
     * Android, se necessário.
     */
    private void verificarERemoverNotificacao(StatusBarNotification sbn, String pacote, String titulo, String corpo) {
        SharedPreferences settingsPrefs = getSharedPreferences(SettingsActivity.SETTINGS_PREFS, Context.MODE_PRIVATE);
        
        // 1. Remover por pacotes bloqueados
        String cancelPackagesStr = settingsPrefs.getString(SettingsActivity.KEY_CANCEL_PACKAGES, "");
        if (!cancelPackagesStr.isEmpty()) {
            String[] cancelPackages = cancelPackagesStr.split(",");
            for (String p : cancelPackages) {
                if (pacote.trim().equalsIgnoreCase(p.trim())) {
                    cancelNotification(sbn.getKey());
                    Log.i(TAG, "Notificação removida: Pacote bloqueado (" + pacote + ")");
                    return; // Já cancelou, não precisa testar palavras
                }
            }
        }

        // 2. Remover por palavras proibidas
        String cancelWordsStr = settingsPrefs.getString(SettingsActivity.KEY_CANCEL_WORDS, "");
        if (!cancelWordsStr.isEmpty()) {
            String[] cancelWords = cancelWordsStr.split(",");
            String textoCompleto = (titulo + " " + corpo).toLowerCase();
            for (String word : cancelWords) {
                String w = word.trim().toLowerCase();
                if (!w.isEmpty() && textoCompleto.contains(w)) {
                    cancelNotification(sbn.getKey());
                    Log.i(TAG, "Notificação removida: Palavra proibida encontrada (" + w + ")");
                    return; // Já cancelou
                }
            }
        }
    }

    // Método responsável por gravar a notificação no banco (NotificationDb)
    private void salvarNoHistorico(String payload) {
        try {
            NotificationDb db = NotificationDb.get(this);

            // Verifica se a última notificação do histórico é idêntica à atual
            if (db.ultima().endsWith(payload)) {
                Log.d(TAG, "Notificação duplicada ignorada.");
                return;
            }

            // Cria um timestamp formatado
            String horario = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());

            // Junta o timestamp com o payload
            String novoTexto = String.format("[%s] %s", horario, payload);

            // Insere apenas a nova linha (não regrava o histórico inteiro)
            if (db.inserir(novoTexto) == -1) {
                Log.e(TAG, "Falha ao inserir notificação no banco");
                return;
            }

            // Toast rápido confirmando o salvamento local
            Toast.makeText(getApplicationContext(), "Notificação salva localmente!", Toast.LENGTH_SHORT).show();

            // Log para conferência no Logcat
            Log.d(TAG, "Salvo no banco: " + novoTexto);

            // Avisa a MainActivity que há uma nova notificação
            Intent intent = new Intent("com.kpbird.nlsexample.NOTIFICATION_LISTENER_EXAMPLE");
            intent.setPackage(getPackageName());
            intent.putExtra("nova_notificacao", novoTexto);
            sendBroadcast(intent);

        } catch (Exception e) {
            Log.e(TAG, "Erro ao salvar no banco", e);
        }
    }
}
