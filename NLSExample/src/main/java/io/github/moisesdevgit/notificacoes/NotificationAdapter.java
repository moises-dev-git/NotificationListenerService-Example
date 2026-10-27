package io.github.moisesdevgit.notificacoes;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<NotificationItem> items;

    public NotificationAdapter(List<NotificationItem> items) {
        this.items = items;
    }

    public void updateData(List<NotificationItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public void addItem(NotificationItem item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationItem item = items.get(position);

        if (item.appName != null && !item.appName.isEmpty()) {
            AppInfo info = getAppInfo(holder.itemView.getContext(), item.appName);
            holder.tvAppName.setText(info.label);
            holder.tvAppName.setVisibility(View.VISIBLE);
            holder.ivAppIcon.setImageDrawable(info.icon);
        } else {
            holder.tvAppName.setVisibility(View.GONE);
            holder.ivAppIcon.setImageDrawable(getFallbackIcon(holder.itemView.getContext()));
        }

        if (item.title != null && !item.title.isEmpty()) {
            holder.tvTitle.setText(item.title);
            holder.tvTitle.setVisibility(View.VISIBLE);
        } else {
            holder.tvTitle.setVisibility(View.GONE);
        }

        if (item.body != null && !item.body.isEmpty()) {
            holder.tvBody.setText(item.body);
            holder.tvBody.setVisibility(View.VISIBLE);
        } else {
            holder.tvBody.setVisibility(View.GONE);
        }

        if (item.time != null && !item.time.isEmpty()) {
            holder.tvTime.setText(item.time);
            holder.tvTime.setVisibility(View.VISIBLE);
        } else {
            holder.tvTime.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** Nome e ícone de um app, resolvidos pelo pacote. */
    private static class AppInfo {
        final String label;
        final Drawable icon;

        AppInfo(String label, Drawable icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    // Cache por pacote: o PackageManager é consultado uma vez por app, não a cada linha
    private final Map<String, AppInfo> appInfoCache = new HashMap<>();
    private Drawable fallbackIcon;

    private AppInfo getAppInfo(Context context, String packageName) {
        AppInfo info = appInfoCache.get(packageName);
        if (info == null) {
            PackageManager pm = context.getPackageManager();
            try {
                ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                info = new AppInfo(pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai));
            } catch (PackageManager.NameNotFoundException e) {
                // App desinstalado (ou sem visibilidade): mostra o pacote e um ícone genérico
                info = new AppInfo(packageName, getFallbackIcon(context));
            }
            appInfoCache.put(packageName, info);
        }
        return info;
    }

    private Drawable getFallbackIcon(Context context) {
        if (fallbackIcon == null) {
            fallbackIcon = AppCompatResources.getDrawable(context, R.drawable.ic_notifications);
        }
        return fallbackIcon;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAppName, tvTitle, tvBody, tvTime;
        ImageView ivAppIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvBody = itemView.findViewById(R.id.tv_body);
            tvTime = itemView.findViewById(R.id.tv_time);
            ivAppIcon = itemView.findViewById(R.id.iv_app_icon);
        }
    }

}
