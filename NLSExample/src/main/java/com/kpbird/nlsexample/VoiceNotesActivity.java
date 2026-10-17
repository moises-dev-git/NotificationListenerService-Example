package com.kpbird.nlsexample;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class VoiceNotesActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private VoiceNotesAdapter adapter;
    private MediaPlayer mediaPlayer;
    private File currentPlayingFile = null;

    private CheckBox cbSelectAll;
    private Button btnDeleteSelected;
    private TextView tvEmptyState;
    private boolean isUpdatingSelectAllCheck = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_notes);
        setTitle("WhatsApp Voice Notes");

        cbSelectAll = findViewById(R.id.cb_select_all);
        btnDeleteSelected = findViewById(R.id.btn_delete_selected);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        recyclerView = findViewById(R.id.recycler_voice_notes);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<File> savedFiles = WhatsAppVoiceNotesUtils.getSavedVoiceNotes(this);
        adapter = new VoiceNotesAdapter(savedFiles, new VoiceNotesAdapter.OnVoiceNoteClickListener() {
            @Override
            public void onPlayClick(File file) {
                if (file.equals(currentPlayingFile) && mediaPlayer != null && mediaPlayer.isPlaying()) {
                    stopPlaying();
                } else {
                    playFile(file);
                }
            }

            @Override
            public void onShareClick(File file) {
                shareFile(file);
            }

            @Override
            public void onDeleteClick(File file) {
                confirmDelete(file);
            }
        });

        adapter.setOnSelectionChangeListener((selectedCount, totalCount) -> updateHeaderUI(selectedCount, totalCount));

        recyclerView.setAdapter(adapter);

        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingSelectAllCheck) return;
            if (isChecked) {
                adapter.selectAll();
            } else {
                adapter.clearSelection();
            }
        });

        btnDeleteSelected.setOnClickListener(v -> confirmDeleteSelected());

        updateHeaderUI(adapter.getSelectedCount(), savedFiles.size());
    }

    private void updateHeaderUI(int selectedCount, int totalCount) {
        isUpdatingSelectAllCheck = true;
        cbSelectAll.setEnabled(totalCount > 0);
        cbSelectAll.setChecked(totalCount > 0 && selectedCount == totalCount);
        isUpdatingSelectAllCheck = false;

        btnDeleteSelected.setText("Apagar (" + selectedCount + ")");
        btnDeleteSelected.setEnabled(selectedCount > 0);

        if (totalCount == 0) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void confirmDelete(File file) {
        new AlertDialog.Builder(this)
                .setTitle("Apagar Voice Note")
                .setMessage("Tem certeza que deseja apagar " + file.getName() + "?")
                .setPositiveButton("Apagar", (dialog, which) -> {
                    if (file.equals(currentPlayingFile)) {
                        stopPlaying();
                    }
                    if (file.delete()) {
                        Toast.makeText(VoiceNotesActivity.this, "Arquivo apagado.", Toast.LENGTH_SHORT).show();
                        refreshData();
                    } else {
                        Toast.makeText(VoiceNotesActivity.this, "Erro ao apagar arquivo.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmDeleteSelected() {
        List<File> selectedFiles = adapter.getSelectedFiles();
        if (selectedFiles.isEmpty()) return;

        String message = selectedFiles.size() == 1
                ? "Tem certeza que deseja apagar 1 Voice Note selecionada?"
                : "Tem certeza que deseja apagar " + selectedFiles.size() + " Voice Notes selecionadas?";

        new AlertDialog.Builder(this)
                .setTitle("Apagar Voice Notes")
                .setMessage(message)
                .setPositiveButton("Apagar", (dialog, which) -> {
                    int deletedCount = 0;
                    for (File file : selectedFiles) {
                        if (file.equals(currentPlayingFile)) {
                            stopPlaying();
                        }
                        if (file.delete()) {
                            deletedCount++;
                        }
                    }
                    Toast.makeText(VoiceNotesActivity.this, deletedCount + " Voice Note(s) apagada(s).", Toast.LENGTH_SHORT).show();
                    refreshData();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void refreshData() {
        List<File> savedFiles = WhatsAppVoiceNotesUtils.getSavedVoiceNotes(this);
        adapter.updateData(savedFiles);
        updateHeaderUI(adapter.getSelectedCount(), savedFiles.size());
    }

    private void playFile(File file) {
        stopPlaying();
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.setOnCompletionListener(mp -> stopPlaying());
            mediaPlayer.prepare();
            mediaPlayer.start();
            currentPlayingFile = file;
            adapter.setPlayingFile(file);
        } catch (Exception e) {
            stopPlaying();
            Toast.makeText(this, "Erro ao reproduzir o áudio.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPlaying() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
        currentPlayingFile = null;
        if (adapter != null) {
            adapter.setPlayingFile(null);
        }
    }

    private void shareFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("audio/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Compartilhar Voice Note"));
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao compartilhar.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPlaying();
    }
}
