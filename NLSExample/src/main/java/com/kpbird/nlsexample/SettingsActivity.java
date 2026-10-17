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
    public static final String KEY_CANCEL_PACKAGES = "cancel_packages";
    public static final String KEY_CANCEL_WORDS = "cancel_words";
    public static final String KEY_VOICE_NOTES_ENABLED = "voice_notes_enabled";
    public static final String KEY_VOICE_NOTES_URI = "voice_notes_uri";
    private static final int REQUEST_CODE_WHATSAPP_FOLDER = 4231;

    private EditText editIgnoredApps;
    private EditText editCancelPackages;
    private EditText editCancelWords;
    private Button btnSaveSettings;
    private Button btnExportJson;
    private Button btnShareJson;

    private androidx.appcompat.widget.SwitchCompat switchVoiceNotes;
    private Button btnSelectFolder;
    private Button btnViewVoiceNotes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        editIgnoredApps = findViewById(R.id.edit_ignored_apps);
        editCancelPackages = findViewById(R.id.edit_cancel_packages);
        editCancelWords = findViewById(R.id.edit_cancel_words);
        btnSaveSettings = findViewById(R.id.btn_save_settings);
        btnExportJson = findViewById(R.id.btn_export_json);
        btnShareJson = findViewById(R.id.btn_share_json);

        SharedPreferences settingsPrefs = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        editIgnoredApps.setText(settingsPrefs.getString(KEY_IGNORED_APPS, ""));
        editCancelPackages.setText(settingsPrefs.getString(KEY_CANCEL_PACKAGES, ""));
        editCancelWords.setText(settingsPrefs.getString(KEY_CANCEL_WORDS, ""));

        btnSaveSettings.setOnClickListener(v -> {
            settingsPrefs.edit()
                    .putString(KEY_IGNORED_APPS, editIgnoredApps.getText().toString())
                    .putString(KEY_CANCEL_PACKAGES, editCancelPackages.getText().toString())
                    .putString(KEY_CANCEL_WORDS, editCancelWords.getText().toString())
                    .apply();
            Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
        });

        btnExportJson.setOnClickListener(v -> exportToJson(false));
        btnShareJson.setOnClickListener(v -> exportToJson(true));

        switchVoiceNotes = findViewById(R.id.switch_voice_notes);
        btnSelectFolder = findViewById(R.id.btn_select_voice_notes_folder);
        btnViewVoiceNotes = findViewById(R.id.btn_view_voice_notes);

        switchVoiceNotes.setChecked(settingsPrefs.getBoolean(KEY_VOICE_NOTES_ENABLED, false));

        switchVoiceNotes.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsPrefs.edit().putBoolean(KEY_VOICE_NOTES_ENABLED, isChecked).apply();
            if (isChecked && settingsPrefs.getString(KEY_VOICE_NOTES_URI, null) == null) {
                Toast.makeText(this, "Por favor, selecione a pasta do WhatsApp", Toast.LENGTH_LONG).show();
            }
        });

        btnSelectFolder.setOnClickListener(v -> openWhatsappFolderPicker());

        btnViewVoiceNotes.setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, VoiceNotesActivity.class));
        });
    }

    private void openWhatsappFolderPicker() {
        try {
            String caminhoWhatsApp = "content://com.android.externalstorage.documents/document/" +
                    "primary%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2FWhatsApp%20Voice%20Notes";
            Uri uriBase = Uri.parse(caminhoWhatsApp);

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.putExtra("android.provider.extra.INITIAL_URI", uriBase);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivityForResult(intent, REQUEST_CODE_WHATSAPP_FOLDER);
        } catch (Exception e) {
            Toast.makeText(this, "Error opening folder picker", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_WHATSAPP_FOLDER) {
            if (resultCode == RESULT_OK && data != null) {
                Uri treeUri = data.getData();
                if (treeUri != null) {
                    try {
                        int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                    } catch (Exception e) {
                        // ignore
                    }

                    SharedPreferences prefs = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
                    prefs.edit().putString(KEY_VOICE_NOTES_URI, treeUri.toString()).apply();
                    
                    Toast.makeText(this, "Pasta configurada. Processando arquivos existentes...", Toast.LENGTH_SHORT).show();
                    
                    new Thread(() -> {
                        WhatsAppVoiceNotesUtils.seedExistingFiles(getApplicationContext());
                    }).start();
                }
            }
        }
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
