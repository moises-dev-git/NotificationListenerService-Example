package com.kpbird.nlsexample;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import android.os.Environment;

public class SettingsActivity extends AppCompatActivity {

    public static final String SETTINGS_PREFS = "SettingsPrefs";
    public static final String KEY_IGNORED_APPS = "ignored_apps";

    private EditText editIgnoredApps;
    private Button btnSaveSettings;
    private Button btnExportJson;
    private Button btnShareJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        editIgnoredApps = findViewById(R.id.edit_ignored_apps);
        btnSaveSettings = findViewById(R.id.btn_save_settings);
        btnExportJson = findViewById(R.id.btn_export_json);
        btnShareJson = findViewById(R.id.btn_share_json);

        SharedPreferences settingsPrefs = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        editIgnoredApps.setText(settingsPrefs.getString(KEY_IGNORED_APPS, ""));

        btnSaveSettings.setOnClickListener(v -> {
            settingsPrefs.edit().putString(KEY_IGNORED_APPS, editIgnoredApps.getText().toString()).apply();
            Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
        });

        btnExportJson.setOnClickListener(v -> exportToJson(false));
        btnShareJson.setOnClickListener(v -> exportToJson(true));
    }

    private void exportToJson(boolean share) {
        SharedPreferences sharedPreferences = getSharedPreferences("HistoricoNotificacoes", Context.MODE_PRIVATE);
        String historico = sharedPreferences.getString("log_texto", "");
        
        try {
            JSONArray jsonArray = new JSONArray();
            if (!historico.isEmpty()) {
                String[] linhas = historico.split("\n(?=\\[)");
                for (String linha : linhas) {
                    if (!linha.trim().isEmpty()) {
                        JSONObject obj = new JSONObject();
                        // Tentar separar em timestamp, app, titulo e corpo
                        // Formato: [dd/MM/yyyy HH:mm:ss] App: pacote | Titulo: Corpo
                        String text = linha.trim();
                        obj.put("raw", text);
                        try {
                            if (text.startsWith("[")) {
                                int endDate = text.indexOf("]");
                                obj.put("date", text.substring(1, endDate));
                                
                                int appIndex = text.indexOf("App: ", endDate);
                                int pipeIndex = text.indexOf(" | ", appIndex);
                                if (appIndex != -1 && pipeIndex != -1) {
                                    obj.put("app", text.substring(appIndex + 5, pipeIndex));
                                    obj.put("content", text.substring(pipeIndex + 3));
                                }
                            }
                        } catch (Exception e) {
                            // ignora e fica so com o raw
                        }
                        jsonArray.put(obj);
                    }
                }
            }
            
            File exportFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "notifications_export_" + System.currentTimeMillis() + ".json");
            FileWriter writer = new FileWriter(exportFile);
            writer.write(jsonArray.toString(4));
            writer.flush();
            writer.close();
            
            Toast.makeText(this, "Exported to " + exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();

            if (share) {
                Uri fileUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        exportFile);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/json");
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Share notifications log via..."));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
