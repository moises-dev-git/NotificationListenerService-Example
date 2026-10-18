package com.kpbird.nlsexample;

import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class EditActivity extends AppCompatActivity {

    private EditText editFilterApp;
    private EditText editFilterDate;
    private EditText editFilterText;
    private Button btnApplyFilter;
    private Button btnDeleteFiltered;
    private TextView tvMatchCount;
    private RecyclerView recyclerFiltered;
    private NotificationAdapter adapter;


    private List<NotificationItem> allItems = new ArrayList<>();
    private List<NotificationItem> filteredItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        editFilterApp = findViewById(R.id.edit_filter_app);
        editFilterDate = findViewById(R.id.edit_filter_date);
        editFilterText = findViewById(R.id.edit_filter_text);
        btnApplyFilter = findViewById(R.id.btn_apply_filter);
        btnDeleteFiltered = findViewById(R.id.btn_delete_filtered);
        tvMatchCount = findViewById(R.id.tv_match_count);
        recyclerFiltered = findViewById(R.id.recycler_filtered);

        recyclerFiltered.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(new ArrayList<>());
        recyclerFiltered.setAdapter(adapter);

        loadAllItems();

        btnApplyFilter.setOnClickListener(v -> applyFilter());

        btnDeleteFiltered.setOnClickListener(v -> confirmDelete());
    }

    private void loadAllItems() {
        allItems.clear();
        allItems.addAll(NotificationDb.get(this).listarTodas());
        applyFilter();
    }

    private void applyFilter() {
        String filterApp = editFilterApp.getText().toString().toLowerCase().trim();
        String filterDate = editFilterDate.getText().toString().toLowerCase().trim();
        String filterText = editFilterText.getText().toString().toLowerCase().trim();

        filteredItems.clear();

        for (NotificationItem item : allItems) {
            String raw = item.rawText.toLowerCase();
            
            boolean matchesApp = filterApp.isEmpty() || raw.contains("app: " + filterApp) || raw.contains(filterApp);
            boolean matchesDate = filterDate.isEmpty() || raw.contains("[" + filterDate); // Basic date match
            boolean matchesText = filterText.isEmpty() || raw.contains(filterText);

            if (matchesApp && matchesDate && matchesText) {
                filteredItems.add(item);
            }
        }

        adapter.updateData(new ArrayList<>(filteredItems));
        tvMatchCount.setText("Matches: " + filteredItems.size() + " of " + allItems.size());
    }

    private void confirmDelete() {
        if (filteredItems.isEmpty()) {
            Toast.makeText(this, "No items to delete.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to delete all " + filteredItems.size() + " filtered items?")
                .setPositiveButton("Delete", (dialog, which) -> executeDelete())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeDelete() {
        // Apaga apenas as linhas filtradas no banco
        NotificationDb.get(this).apagar(filteredItems);

        Toast.makeText(this, "Deleted " + filteredItems.size() + " items.", Toast.LENGTH_SHORT).show();

        loadAllItems();
    }
}
