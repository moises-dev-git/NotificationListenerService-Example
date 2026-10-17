package com.kpbird.nlsexample;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "nlsexample_channel";
    private RecyclerView recyclerView;
    private NotificationAdapter adapter;
    private NotificationReceiver nReceiver;

    // Devem ser exatamente os mesmos nomes usados no NLService
    private static final String PREFS_NAME = "HistoricoNotificacoes";
    private static final String KEY_LOG = "log_texto";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new NotificationAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        nReceiver = new NotificationReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.kpbird.nlsexample.NOTIFICATION_LISTENER_EXAMPLE");

        ContextCompat.registerReceiver(
                this,
                nReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);

        // Cria o canal de notificação (necessário no Android 8+)
        createNotificationChannel();

        // Carrega o histórico salvo automaticamente ao abrir o app
        mostrarHistoricoSalvo();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.action_view_saved) {
                mostrarHistoricoSalvo();
                return true;

            } else if (itemId == R.id.action_settings) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                return true;

            } else if (itemId == R.id.action_edit) {
                startActivity(new Intent(MainActivity.this, EditActivity.class));
                return true;
                
            } else if (itemId == R.id.action_voice_notes) {
                startActivity(new Intent(MainActivity.this, VoiceNotesActivity.class));
                return true;
            }

            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Verifica permissão toda vez que o app volta ao primeiro plano
        if (!isNotificationListenerEnabled()) {
            showEnableNotificationListenerDialog();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (nReceiver != null) {
            unregisterReceiver(nReceiver);
        }
    }

    /**
     * Verifica se o NotificationListenerService tem permissão concedida.
     */
    private boolean isNotificationListenerEnabled() {
        String pkgName = getPackageName();

        String flat = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners");

        if (!TextUtils.isEmpty(flat)) {
            String[] names = flat.split(":");

            for (String name : names) {
                ComponentName cn = ComponentName.unflattenFromString(name);

                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Exibe diálogo solicitando que o usuário conceda acesso às notificações.
     */
    private void showEnableNotificationListenerDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permissão necessária")
                .setMessage(
                        "Este app precisa de acesso às notificações para funcionar.\n\n" +
                                "Toque em \"Configurações\", ative a opção para este app e volte.")
                .setPositiveButton("Configurações", (dialog, which) -> startActivity(
                        new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNegativeButton("Cancelar", null)
                .setCancelable(false)
                .show();
    }

    /**
     * Cria o canal de notificação para Android 8+.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "NLS Example Channel",
                    NotificationManager.IMPORTANCE_DEFAULT);

            channel.setDescription(
                    "Canal de teste para o NotificationListenerService");

            NotificationManager nm = getSystemService(NotificationManager.class);

            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void mostrarHistoricoSalvo() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String historico = sharedPreferences.getString(KEY_LOG, "");

        List<NotificationItem> items = new ArrayList<>();

        if (!historico.isEmpty()) {

            // Separa por quebra de linha seguida por '['
            String[] linhas = historico.split("\n(?=\\[)");

            for (String linha : linhas) {
                if (!linha.trim().isEmpty()) {
                    items.add(new NotificationItem(linha.trim()));
                }
            }
        }

        if (adapter != null) {
            adapter.updateData(items);

            if (!items.isEmpty()) {
                recyclerView.scrollToPosition(items.size() - 1);
            }
        }
    }

    private void settings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    class NotificationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            String texto = intent.getStringExtra("nova_notificacao");

            android.util.Log.d(
                    "MainActivity",
                    "Recebeu broadcast. texto=" + texto);

            if (texto != null) {

                adapter.addItem(
                        new NotificationItem(texto));

                recyclerView.scrollToPosition(
                        adapter.getItemCount() - 1);
            }
        }
    }
}