package io.github.moisesdevgit.notificacoes;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;

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

        // Chamada de exemplo para remover a notificação se necessário
        verificarERemoverNotificacao(sbn, pacote, titulo, corpo);

        // 2. Áudios do WhatsApp: dispara antes dos filtros, para que nenhuma notificação
        //    ignorada impeça a importação (a notificação chega antes do download terminar)
        SharedPreferences settingsPrefs = getSharedPreferences(SettingsActivity.SETTINGS_PREFS, Context.MODE_PRIVATE);
        if ((pacote.equals("com.whatsapp") || pacote.equals("com.whatsapp.w4b"))
                && settingsPrefs.getBoolean(SettingsActivity.KEY_VOICE_NOTES_ENABLED, false)) {
            WhatsAppVoiceNotesUtils.onWhatsAppNotification(this);
        }

        String[] ignorados = settingsPrefs.getString(SettingsActivity.KEY_IGNORED_APPS, "").split(",");

        // 3. Conversas (WhatsApp, Telegram, SMS...): a notificação traz a lista das mensagens
        //    não lidas, mas EXTRA_TEXT só tem a última. Salva cada mensagem separadamente,
        //    com o horário em que foi enviada; as já salvas são puladas, pois o app repete
        //    as mensagens não lidas a cada nova notificação.
        NotificationCompat.MessagingStyle conversa =
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.getNotification());
        if (conversa != null && !conversa.getMessages().isEmpty()) {
            boolean grupo = conversa.isGroupConversation() && conversa.getConversationTitle() != null;
            String nomeGrupo = grupo ? semContador(conversa.getConversationTitle().toString()) : null;
            int salvas = 0;

            for (NotificationCompat.MessagingStyle.Message msg : conversa.getMessages()) {
                if (msg.getText() == null) continue;
                Person pessoa = (msg.getPerson() != null) ? msg.getPerson() : conversa.getUser();
                String remetente = (pessoa.getName() != null) ? pessoa.getName().toString() : titulo;
                String texto = msg.getText().toString();

                String msgTitulo = grupo ? nomeGrupo : remetente;
                String msgCorpo = grupo ? remetente + ": " + texto : texto;
                if (ignorada(ignorados, pacote, msgTitulo, msgCorpo)) continue;

                long quando = (msg.getTimestamp() > 0) ? msg.getTimestamp() : System.currentTimeMillis();
                String linha = String.format("[%s] App: %s | %s: %s", formatarHorario(quando), pacote, msgTitulo, msgCorpo);
                if (salvarNoHistorico(linha, true)) salvas++;
            }

            if (salvas > 0) {
                Toast.makeText(getApplicationContext(), R.string.notification_saved, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // 4. Demais notificações: título e texto, com o horário em que chegaram
        if (ignorada(ignorados, pacote, titulo, corpo)) return;

        String payload = String.format("App: %s | %s: %s", pacote, titulo, corpo);

        // Verifica se a última notificação do histórico é idêntica à atual
        if (NotificationDb.get(this).ultima().endsWith(payload)) {
            Log.d(TAG, "Notificação duplicada ignorada.");
            return;
        }

        String linha = String.format("[%s] %s", formatarHorario(System.currentTimeMillis()), payload);
        if (salvarNoHistorico(linha, false)) {
            Toast.makeText(getApplicationContext(), R.string.notification_saved, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Verifica aplicativos, palavras e expressões que o usuário não quer no histórico.
     */
    private boolean ignorada(String[] ignorados, String pacote, String titulo, String corpo) {
        String textoCompleto = (pacote + " " + titulo + " " + corpo).toLowerCase(Locale.getDefault());
        for (String ignorado : ignorados) {
            String filter = ignorado.trim().toLowerCase(Locale.getDefault());
            if (!filter.isEmpty() && textoCompleto.contains(filter)) {
                Log.i(TAG, "Notificação ignorada pelo filtro (" + filter + "): " + pacote);
                return true;
            }
        }
        return false;
    }

    /**
     * Remove o contador que alguns apps põem no nome do grupo, ex.: "Família (3 mensagens)".
     */
    private static String semContador(String nome) {
        return nome.replaceFirst("\\s*\\(\\d+ \\p{L}+\\)$", "");
    }

    private static String formatarHorario(long millis) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date(millis));
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

    /**
     * Grava a linha no banco (NotificationDb) e avisa a tela principal.
     * Com evitarRepetida, pula a linha se ela já estiver entre as notificações recentes.
     * Retorna true se a linha foi salva.
     */
    private boolean salvarNoHistorico(String novoTexto, boolean evitarRepetida) {
        try {
            NotificationDb db = NotificationDb.get(this);

            if (evitarRepetida && db.existeRecente(novoTexto)) {
                return false;
            }

            // Insere apenas a nova linha (não regrava o histórico inteiro)
            if (db.inserir(novoTexto) == -1) {
                Log.e(TAG, "Falha ao inserir notificação no banco");
                return false;
            }

            // Log para conferência no Logcat
            Log.d(TAG, "Salvo no banco: " + novoTexto);

            // Avisa a MainActivity que há uma nova notificação
            Intent intent = new Intent("io.github.moisesdevgit.notificacoes.NOTIFICATION_LISTENER_EXAMPLE");
            intent.setPackage(getPackageName());
            intent.putExtra("nova_notificacao", novoTexto);
            sendBroadcast(intent);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Erro ao salvar no banco", e);
            return false;
        }
    }
}
