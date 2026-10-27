package io.github.moisesdevgit.notificacoes;

import android.content.Context;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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

        // Botão de busca do teclado também aplica o filtro
        TextView.OnEditorActionListener filtrarNoTeclado = (view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyFilter();
                return true;
            }
            return false;
        };
        editFilterApp.setOnEditorActionListener(filtrarNoTeclado);
        editFilterDate.setOnEditorActionListener(filtrarNoTeclado);
        editFilterText.setOnEditorActionListener(filtrarNoTeclado);

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
        tvMatchCount.setText(getString(R.string.filter_match_count, filteredItems.size(), allItems.size()));
    }

    private void confirmDelete() {
        if (filteredItems.isEmpty()) {
            Toast.makeText(this, R.string.filter_nothing_to_delete, Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_delete_title)
                .setMessage(getResources().getQuantityString(R.plurals.filter_delete_message, filteredItems.size(), filteredItems.size()))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> executeDelete())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void executeDelete() {
        // Apaga apenas as linhas filtradas no banco
        NotificationDb.get(this).apagar(filteredItems);

        Toast.makeText(this, getResources().getQuantityString(R.plurals.filter_items_deleted, filteredItems.size(), filteredItems.size()), Toast.LENGTH_SHORT).show();

        loadAllItems();
    }
}
