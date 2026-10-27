package io.github.moisesdevgit.notificacoes;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class NotificationsApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Android 12+: usa as cores do papel de parede (Material You) em todas as telas
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
