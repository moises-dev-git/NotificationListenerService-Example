package com.kpbird.nlsexample;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Utilitário para importar voice notes do WhatsApp usando SAF (Storage Access Framework).
 *
 * Estratégia:
 * - Ao habilitar a feature e selecionar a pasta, todos os .opus existentes são
 *   registrados (seeded) sem cópia, para que apenas voice notes NOVOS sejam importados.
 * - A cada notificação do WhatsApp, varre as 2 pastas semanais mais recentes (YYYYWW)
 *   e importa qualquer .opus cujo nome ainda não esteja no registro.
 * - Usa DigestInputStream para calcular SHA-256 durante a cópia (single-pass),
 *   evitando duplicatas mesmo que o WhatsApp renomeie o arquivo.
 */
public class WhatsAppVoiceNotesUtils {

    private static final String TAG = "WhatsAppVoiceNotes";
    private static final String HASH_PREFS = "VoiceNoteHashes";
    private static final String VOICE_NOTES_DIR = "whatsapp_voice_notes";
    private static final int RECENT_FOLDERS_TO_SCAN = 2;

    /**
     * Importa todos os voice notes novos que ainda não foram registrados.
     * @return número de arquivos importados nesta execução
     */
    public static int importAllNewVoiceNotes(Context context) {
        SharedPreferences settingsPrefs = context.getSharedPreferences(
                SettingsActivity.SETTINGS_PREFS, Context.MODE_PRIVATE);
        String uriString = settingsPrefs.getString(SettingsActivity.KEY_VOICE_NOTES_URI, null);

        if (uriString == null) {
            Log.w(TAG, "No voice notes URI configured");
            return 0;
        }

        Uri treeUri = Uri.parse(uriString);
        DocumentFile tree = DocumentFile.fromTreeUri(context, treeUri);

        if (tree == null || !tree.exists()) {
            Log.w(TAG, "Voice notes tree URI is invalid or inaccessible");
            return 0;
        }

        List<DocumentFile> opusFiles = collectRecentOpusFiles(tree);
        if (opusFiles.isEmpty()) {
            return 0;
        }

        SharedPreferences hashPrefs = context.getSharedPreferences(HASH_PREFS, Context.MODE_PRIVATE);

        File destDir = new File(context.getFilesDir(), VOICE_NOTES_DIR);
        if (!destDir.exists()) destDir.mkdirs();

        int importedCount = 0;

        for (DocumentFile opusFile : opusFiles) {
            String fileName = opusFile.getName();
            if (fileName == null) continue;

            // Check rápido pelo nome — O(1), sem I/O
            if (hashPrefs.contains(fileName)) {
                continue;
            }

            try {
                // Single-pass: copia + calcula hash simultaneamente via DigestInputStream
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                InputStream rawStream = context.getContentResolver().openInputStream(opusFile.getUri());
                if (rawStream == null) continue;

                File tempFile = new File(destDir, fileName + ".tmp");

                try (DigestInputStream dis = new DigestInputStream(rawStream, digest);
                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = dis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }

                String hash = bytesToHex(digest.digest());

                // Verifica se o mesmo conteúdo já existe com outro nome
                if (hashPrefs.getAll().containsValue(hash)) {
                    tempFile.delete();
                    // Registra o nome para pular rapidamente nas próximas execuções
                    hashPrefs.edit().putString(fileName, hash).apply();
                    Log.d(TAG, "Skipped duplicate content: " + fileName);
                    continue;
                }

                // Renomeia de .tmp para o nome final
                File destFile = new File(destDir, fileName);
                if (!tempFile.renameTo(destFile)) {
                    // Fallback: se renameTo falhar, o .tmp fica e será sobrescrito depois
                    Log.w(TAG, "renameTo failed for " + fileName);
                }

                // Registra nome + hash
                hashPrefs.edit().putString(fileName, hash).apply();
                importedCount++;
                Log.i(TAG, "Imported: " + fileName + " (hash=" + hash.substring(0, 12) + "...)");

            } catch (Exception e) {
                Log.e(TAG, "Error importing " + fileName, e);
            }
        }

        return importedCount;
    }

    /**
     * Coleta arquivos .opus das N subpastas mais recentes (formato YYYYWW).
     * Como YYYYWW é lexicograficamente ordenável, basta pegar as últimas N pastas.
     */
    private static List<DocumentFile> collectRecentOpusFiles(DocumentFile root) {
        List<DocumentFile> result = new ArrayList<>();
        DocumentFile[] children = root.listFiles();
        if (children == null) return result;

        // Filtra apenas diretórios e ordena por nome (YYYYWW é cronológico)
        List<DocumentFile> dirs = new ArrayList<>();
        for (DocumentFile child : children) {
            if (child.isDirectory() && child.getName() != null) {
                dirs.add(child);
            }
        }

        Collections.sort(dirs, new Comparator<DocumentFile>() {
            @Override
            public int compare(DocumentFile a, DocumentFile b) {
                String nameA = a.getName() != null ? a.getName() : "";
                String nameB = b.getName() != null ? b.getName() : "";
                return nameA.compareTo(nameB);
            }
        });

        // Pega apenas as N pastas mais recentes
        int start = Math.max(0, dirs.size() - RECENT_FOLDERS_TO_SCAN);
        for (int i = start; i < dirs.size(); i++) {
            collectOpusFromFolder(dirs.get(i), result);
        }

        return result;
    }

    /**
     * Lista os .opus de uma única pasta YYYYWW (não recursivo).
     */
    private static void collectOpusFromFolder(DocumentFile folder, List<DocumentFile> result) {
        DocumentFile[] files = folder.listFiles();
        if (files == null) return;
        for (DocumentFile file : files) {
            if (file.isFile()) {
                String name = file.getName();
                if (name != null && name.toLowerCase(Locale.US).endsWith(".opus")) {
                    result.add(file);
                }
            }
        }
    }

    /**
     * Registra todos os .opus existentes SEM copiá-los.
     * Deve ser chamado uma única vez, quando o usuário seleciona a pasta pela primeira vez.
     * Assim, apenas voice notes que chegarem DEPOIS serão importados.
     */
    public static int seedExistingFiles(Context context) {
        SharedPreferences settingsPrefs = context.getSharedPreferences(
                SettingsActivity.SETTINGS_PREFS, Context.MODE_PRIVATE);
        String uriString = settingsPrefs.getString(SettingsActivity.KEY_VOICE_NOTES_URI, null);
        if (uriString == null) return 0;

        Uri treeUri = Uri.parse(uriString);
        DocumentFile tree = DocumentFile.fromTreeUri(context, treeUri);
        if (tree == null || !tree.exists()) return 0;

        // Scan completo (recursivo) — feito apenas uma vez
        List<DocumentFile> allOpus = collectAllOpusFiles(tree);

        SharedPreferences hashPrefs = context.getSharedPreferences(HASH_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = hashPrefs.edit();

        int seededCount = 0;
        for (DocumentFile file : allOpus) {
            String name = file.getName();
            if (name != null && !hashPrefs.contains(name)) {
                // Registra com marcador "SEEDED" sem calcular hash (evita I/O pesado)
                editor.putString(name, "SEEDED");
                seededCount++;
            }
        }

        editor.apply();
        Log.i(TAG, "Seeded " + seededCount + " existing voice notes");
        return seededCount;
    }

    /**
     * Coleta TODOS os .opus recursivamente. Usado apenas no seeding inicial.
     */
    private static List<DocumentFile> collectAllOpusFiles(DocumentFile folder) {
        List<DocumentFile> result = new ArrayList<>();
        DocumentFile[] children = folder.listFiles();
        if (children == null) return result;

        for (DocumentFile child : children) {
            if (child.isDirectory()) {
                result.addAll(collectAllOpusFiles(child));
            } else if (child.isFile()) {
                String name = child.getName();
                if (name != null && name.toLowerCase(Locale.US).endsWith(".opus")) {
                    result.add(child);
                }
            }
        }
        return result;
    }

    /**
     * Retorna a lista de voice notes salvos localmente, ordenados do mais recente para o mais antigo.
     */
    public static List<File> getSavedVoiceNotes(Context context) {
        File destDir = new File(context.getFilesDir(), VOICE_NOTES_DIR);
        if (!destDir.exists() || !destDir.isDirectory()) {
            return Collections.emptyList();
        }

        File[] files = destDir.listFiles();
        if (files == null) return Collections.emptyList();

        List<File> result = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".opus")) {
                result.add(file);
            }
        }

        // Ordena: mais recente primeiro
        Collections.sort(result, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });

        return result;
    }

    /**
     * Converte byte array em string hexadecimal.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }
}
