package io.github.moisesdevgit.notificacoes;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * Base das telas: põe a barra de título (MaterialToolbar) acima do layout da tela.
 *
 * No Android 15+ o app é desenhado atrás das barras do sistema (edge-to-edge obrigatório),
 * então a moldura recebe o espaço da barra de status, da barra de navegação e do teclado.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(R.layout.activity_base);
        ViewGroup content = findViewById(R.id.base_content);
        getLayoutInflater().inflate(layoutResID, content, true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            View root = findViewById(R.id.base_root);
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                Insets bars = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
                // Já aplicado aqui: evita que a barra inferior some o espaço de novo
                return WindowInsetsCompat.CONSUMED;
            });
        }
    }
}
