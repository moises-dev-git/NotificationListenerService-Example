package com.kpbird.nlsexample;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

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
            holder.tvAppName.setText(item.appName);
            holder.tvAppName.setVisibility(View.VISIBLE);
        } else {
            holder.tvAppName.setVisibility(View.GONE);
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

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAppName, tvTitle, tvBody, tvTime;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvBody = itemView.findViewById(R.id.tv_body);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }

}
