package com.kpbird.nlsexample;

import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class VoiceNotesAdapter extends RecyclerView.Adapter<VoiceNotesAdapter.ViewHolder> {

    private List<File> files;
    private final Set<File> selectedFiles = new HashSet<>();
    private final OnVoiceNoteClickListener listener;
    private OnSelectionChangeListener selectionChangeListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private File playingFile = null;

    public interface OnVoiceNoteClickListener {
        void onPlayClick(File file);
        void onShareClick(File file);
        void onDeleteClick(File file);
    }

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int selectedCount, int totalCount);
    }

    public VoiceNotesAdapter(List<File> files, OnVoiceNoteClickListener listener) {
        this.files = files;
        this.listener = listener;
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener selectionChangeListener) {
        this.selectionChangeListener = selectionChangeListener;
    }

    public void updateData(List<File> newFiles) {
        this.files = newFiles;
        this.selectedFiles.retainAll(newFiles);
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void setPlayingFile(File file) {
        this.playingFile = file;
        notifyDataSetChanged();
    }

    public void selectAll() {
        selectedFiles.clear();
        selectedFiles.addAll(files);
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void clearSelection() {
        selectedFiles.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public List<File> getSelectedFiles() {
        return new ArrayList<>(selectedFiles);
    }

    public int getSelectedCount() {
        return selectedFiles.size();
    }

    public boolean isAllSelected() {
        return !files.isEmpty() && selectedFiles.size() == files.size();
    }

    private void notifySelectionChanged() {
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedFiles.size(), files.size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_voice_note, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = files.get(position);
        Context context = holder.itemView.getContext();

        holder.tvFileDate.setText("Áudio de " + dateFormat.format(new Date(file.lastModified())));
        holder.tvFileSize.setText(Formatter.formatFileSize(context, file.length()));
        holder.tvFileName.setText(file.getName());

        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setChecked(selectedFiles.contains(file));
        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedFiles.add(file);
            } else {
                selectedFiles.remove(file);
            }
            notifySelectionChanged();
        });

        holder.itemView.setOnClickListener(v -> holder.cbSelect.toggle());

        if (file.equals(playingFile)) {
            holder.btnPlay.setIconResource(R.drawable.ic_pause);
            holder.btnPlay.setContentDescription("Pausar");
        } else {
            holder.btnPlay.setIconResource(R.drawable.ic_play);
            holder.btnPlay.setContentDescription("Tocar");
        }

        holder.btnPlay.setOnClickListener(v -> listener.onPlayClick(file));
        holder.btnShare.setOnClickListener(v -> listener.onShareClick(file));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(file));
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileName, tvFileDate, tvFileSize;
        CheckBox cbSelect;
        MaterialButton btnPlay, btnShare, btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cbSelect = itemView.findViewById(R.id.cb_select);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvFileDate = itemView.findViewById(R.id.tv_file_date);
            tvFileSize = itemView.findViewById(R.id.tv_file_size);
            btnPlay = itemView.findViewById(R.id.btn_play);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
