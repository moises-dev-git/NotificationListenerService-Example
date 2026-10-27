package io.github.moisesdevgit.notificacoes;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Banco SQLite do histórico de notificações.
 *
 * Substitui a antiga string única no SharedPreferences ("HistoricoNotificacoes"/"log_texto"),
 * que era regravada inteira a cada notificação. Cada notificação agora é uma linha,
 * então inserir e apagar não depende mais do tamanho do histórico.
 *
 * Na primeira abertura, o histórico antigo é migrado automaticamente para o banco.
 */
public class NotificationDb extends SQLiteOpenHelper {
    private static final String TAG = "NotificationDb";

    private static final String DB_NAME = "notificacoes.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE = "notificacoes";
    private static final String COL_ID = "_id";
    private static final String COL_RAW = "raw";

    // Armazenamento antigo (usado apenas na migração)
    private static final String OLD_PREFS_NAME = "HistoricoNotificacoes";
    private static final String OLD_KEY_LOG = "log_texto";

    private static NotificationDb instance;

    public static synchronized NotificationDb get(Context context) {
        if (instance == null) {
            instance = new NotificationDb(context.getApplicationContext());
            instance.migrarDoSharedPreferences(context.getApplicationContext());
        }
        return instance;
    }

    private NotificationDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_RAW + " TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Sem upgrades por enquanto
    }

    /**
     * Copia o histórico antigo do SharedPreferences para o banco, numa única transação.
     * A chave antiga só é removida depois que a transação foi concluída com sucesso.
     */
    private void migrarDoSharedPreferences(Context context) {
        SharedPreferences oldPrefs = context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE);
        String historico = oldPrefs.getString(OLD_KEY_LOG, "");
        if (historico.isEmpty()) {
            return;
        }

        SQLiteDatabase db = getWritableDatabase();
        int migradas = 0;
        db.beginTransaction();
        try {
            SQLiteStatement insert = db.compileStatement(
                    "INSERT INTO " + TABLE + " (" + COL_RAW + ") VALUES (?)");
            // Mesmo critério de separação usado antes: quebra de linha seguida por '['
            for (String linha : historico.split("\n(?=\\[)")) {
                String texto = linha.trim();
                if (texto.isEmpty()) continue;
                insert.bindString(1, texto);
                insert.executeInsert();
                migradas++;
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao migrar histórico; dados antigos mantidos", e);
            return;
        } finally {
            db.endTransaction();
        }

        oldPrefs.edit().remove(OLD_KEY_LOG).commit();
        Log.i(TAG, "Histórico migrado do SharedPreferences: " + migradas + " notificações");
    }

    /**
     * Insere uma notificação. Retorna o id da linha, ou -1 em caso de erro.
     */
    public long inserir(String raw) {
        ContentValues values = new ContentValues();
        values.put(COL_RAW, raw);
        return getWritableDatabase().insert(TABLE, null, values);
    }

    /**
     * Texto da notificação mais recente, ou "" se o histórico estiver vazio.
     */
    public String ultima() {
        try (Cursor c = getReadableDatabase().query(TABLE, new String[]{COL_RAW},
                null, null, null, null, COL_ID + " DESC", "1")) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }

    /**
     * Todas as notificações, da mais antiga para a mais recente.
     */
    public List<NotificationItem> listarTodas() {
        List<NotificationItem> items = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE, new String[]{COL_ID, COL_RAW},
                null, null, null, null, COL_ID + " ASC")) {
            while (c.moveToNext()) {
                NotificationItem item = new NotificationItem(c.getString(1));
                item.id = c.getLong(0);
                items.add(item);
            }
        }
        return items;
    }

    /**
     * Apaga as notificações informadas numa única transação.
     * @return quantidade de linhas apagadas
     */
    public int apagar(List<NotificationItem> items) {
        SQLiteDatabase db = getWritableDatabase();
        int apagadas = 0;
        db.beginTransaction();
        try {
            SQLiteStatement delete = db.compileStatement(
                    "DELETE FROM " + TABLE + " WHERE " + COL_ID + " = ?");
            for (NotificationItem item : items) {
                delete.bindLong(1, item.id);
                apagadas += delete.executeUpdateDelete();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return apagadas;
    }
}
