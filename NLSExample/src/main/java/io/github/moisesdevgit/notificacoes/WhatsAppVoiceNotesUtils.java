package io.github.moisesdevgit.notificacoes;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Utilitário para importar voice notes do WhatsApp usando SAF (Storage Access Framework).
 *
 * Estratégia:
 * - Ao habilitar a feature e selecionar a pasta, todos os .opus existentes são
 *   registrados (seeded) sem cópia, para que apenas voice notes NOVOS sejam importados.
 * - A notificação do WhatsApp costuma chegar ANTES de o áudio terminar de baixar.
 *   Por isso, cada notificação inicia uma janela de vigilância: a pasta é varrida a cada
 *   POLL_INTERVAL_MS durante WATCH_WINDOW_MS (renovada a cada nova notificação).
 * - Cada varredura usa consultas diretas ao DocumentsContract (uma para listar as
 *   pastas semanais YYYYWW e uma por pasta recente), em vez de DocumentFile, que faz
 *   uma chamada ao sistema para cada arquivo e cada atributo.
 * - Um áudio novo é copiado IMEDIATAMENTE, mesmo que ainda esteja baixando, para que
 *   não se perca se o remetente apagar. Se o tamanho mudar, é copiado de novo; só é
 *   registrado como importado quando o tamanho fica estável entre duas varreduras.
 * - Usa DigestInputStream para calcular SHA-256 durante a cópia (single-pass),
 *   evitando duplicatas mesmo que o WhatsApp renomeie o arquivo. Os hashes conhecidos
 *   ficam num Set em memória, carregado uma vez do SharedPreferences.
 */
public class WhatsAppVoiceNotesUtils {

    private static final String TAG = "WhatsAppVoiceNotes";
    private static final String HASH_PREFS = "VoiceNoteHashes";
    private static final String SEEDED = "SEEDED";
    private static final String VOICE_NOTES_DIR = "whatsapp_voice_notes";
    private static final int RECENT_FOLDERS_TO_SCAN = 2;

    private static final long POLL_INTERVAL_MS = 1500;
    private static final long WATCH_WINDOW_MS = 90_000;

    // Todas as varreduras rodam nesta única thread, então o estado abaixo não tem concorrência
    private static HandlerThread watcherThread;
    private static Handler watcherHandler;
    private static long watchUntil = 0;
    private static boolean pollScheduled = false;

    // Hashes de conteúdo já importados (sem os marcadores SEEDED)
    private static Set<String> knownHashes;

    // Áudios copiados mas ainda não registrados: nome -> {tamanho copiado, hash}
    private static final Map<String, String[]> pending = new HashMap<>();

    /** Arquivo .opus listado no SAF. */
    private static class OpusEntry {
        final String name;
        final Uri uri;
        final long size;

        OpusEntry(String name, Uri uri, long size) {
            this.name = name;
            this.uri = uri;
            this.size = size;
        }
    }

    /**
     * Chamado a cada notificação do WhatsApp: varre agora e continua varrendo
     * por WATCH_WINDOW_MS, até o áudio terminar de baixar.
     */
    public static synchronized void onWhatsAppNotification(Context context) {
        final Context appContext = context.getApplicationContext();
        if (watcherThread == null) {
            watcherThread = new HandlerThread("VoiceNoteWatcher");
            watcherThread.start();
            watcherHandler = new Handler(watcherThread.getLooper());
        }
        watchUntil = SystemClock.elapsedRealtime() + WATCH_WINDOW_MS;
        if (!pollScheduled) {
            pollScheduled = true;
            watcherHandler.post(() -> poll(appContext));
        }
    }

    private static void poll(Context context) {
        try {
            long start = SystemClock.elapsedRealtime();
            int imported = importAllNewVoiceNotes(context);
            if (imported > 0) {
                Log.i(TAG, "Imported " + imported + " new voice notes in "
                        + (SystemClock.elapsedRealtime() - start) + " ms");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error importing voice notes", e);
        }
        synchronized (WhatsAppVoiceNotesUtils.class) {
            if (SystemClock.elapsedRealtime() < watchUntil) {
                watcherHandler.postDelayed(() -> poll(context), POLL_INTERVAL_MS);
            } else {
                pollScheduled = false;
            }
        }
    }

    /**
     * Importa todos os voice notes novos que ainda não foram registrados.
     * Deve rodar sempre na thread do watcher.
     * @return número de arquivos registrados como importados nesta execução
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
        List<OpusEntry> opusFiles;
        try {
            opusFiles = collectRecentOpusFiles(context, treeUri);
        } catch (Exception e) {
            Log.w(TAG, "Voice notes tree URI is invalid or inaccessible", e);
            return 0;
        }
        if (opusFiles.isEmpty()) {
            return 0;
        }

        SharedPreferences hashPrefs = context.getSharedPreferences(HASH_PREFS, Context.MODE_PRIVATE);
        if (knownHashes == null) {
            knownHashes = new HashSet<>();
            for (Object value : hashPrefs.getAll().values()) {
                if (value instanceof String && !SEEDED.equals(value)) {
                    knownHashes.add((String) value);
                }
            }
        }

        File destDir = new File(context.getFilesDir(), VOICE_NOTES_DIR);
        if (!destDir.exists()) destDir.mkdirs();

        int importedCount = 0;
        for (OpusEntry opus : opusFiles) {
            // Check rápido pelo nome — O(1), sem I/O
            if (hashPrefs.contains(opus.name)) {
                continue;
            }

            String[] state = pending.get(opus.name);
            if (state != null && Long.parseLong(state[0]) == opus.size && opus.size > 0) {
                // Tamanho estável desde a última cópia: download concluído, registra
                String hash = state[1];
                pending.remove(opus.name);
                if (knownHashes.contains(hash)) {
                    // Mesmo conteúdo já existe com outro nome
                    new File(destDir, opus.name).delete();
                    Log.d(TAG, "Skipped duplicate content: " + opus.name);
                } else {
                    knownHashes.add(hash);
                    importedCount++;
                    Log.i(TAG, "Imported: " + opus.name + " (hash=" + hash.substring(0, 12) + "...)");
                }
                // Registra o nome para pular rapidamente nas próximas execuções
                hashPrefs.edit().putString(opus.name, hash).apply();
                continue;
            }

            // Novo ou ainda crescendo: copia já, para não perder se for apagado
            try {
                String hash = copyWithHash(context, opus, destDir);
                pending.put(opus.name, new String[]{String.valueOf(opus.size), hash});
                Log.d(TAG, "Copied (pending): " + opus.name + " size=" + opus.size);
            } catch (Exception e) {
                Log.e(TAG, "Error importing " + opus.name, e);
            }
        }

        return importedCount;
    }

    /**
     * Copia o áudio para a pasta interna (via .tmp + rename) calculando o SHA-256.
     * @return hash hexadecimal do conteúdo copiado
     */
    private static String copyWithHash(Context context, OpusEntry opus, File destDir) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream rawStream = context.getContentResolver().openInputStream(opus.uri);
        if (rawStream == null) {
            throw new IOException("Could not open " + opus.name);
        }

        File tempFile = new File(destDir, opus.name + ".tmp");
        try (DigestInputStream dis = new DigestInputStream(rawStream, digest);
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = dis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }

        File destFile = new File(destDir, opus.name);
        if (!tempFile.renameTo(destFile)) {
            throw new IOException("renameTo failed for " + opus.name);
        }
        return bytesToHex(digest.digest());
    }

    /**
     * Lista os .opus das N subpastas mais recentes (formato YYYYWW).
     * Como YYYYWW é lexicograficamente ordenável, basta pegar as últimas N pastas.
     */
    private static List<OpusEntry> collectRecentOpusFiles(Context context, Uri treeUri) {
        ContentResolver resolver = context.getContentResolver();
        String rootId = DocumentsContract.getTreeDocumentId(treeUri);

        List<String[]> dirs = new ArrayList<>(); // {nome, documentId}
        try (Cursor c = queryChildren(resolver, treeUri, rootId)) {
            while (c != null && c.moveToNext()) {
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2)) && c.getString(1) != null) {
                    dirs.add(new String[]{c.getString(1), c.getString(0)});
                }
            }
        }
        Collections.sort(dirs, (a, b) -> a[0].compareTo(b[0]));

        List<OpusEntry> result = new ArrayList<>();
        // Pega apenas as N pastas mais recentes
        for (int i = Math.max(0, dirs.size() - RECENT_FOLDERS_TO_SCAN); i < dirs.size(); i++) {
            try (Cursor c = queryChildren(resolver, treeUri, dirs.get(i)[1])) {
                while (c != null && c.moveToNext()) {
                    String name = c.getString(1);
                    if (name != null && name.toLowerCase(Locale.US).endsWith(".opus")
                            && !DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))) {
                        Uri uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0));
                        result.add(new OpusEntry(name, uri, c.isNull(3) ? -1 : c.getLong(3)));
                    }
                }
            }
        }
        return result;
    }

    private static Cursor queryChildren(ContentResolver resolver, Uri treeUri, String parentId) {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        return resolver.query(childrenUri, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
        }, null, null, null);
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
                editor.putString(name, SEEDED);
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
