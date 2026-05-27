package com.thecaptaincook.zenith;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogsAdapter extends RecyclerView.Adapter<LogsAdapter.LogViewHolder> {

    public interface LogInteractionListener {
        void onLogClicked(LogEntity log);
        void onDeleteLog(LogEntity log);
    }

    private List<LogEntity> logs = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
    private final LogInteractionListener listener;
    private final boolean isSwipeToDelete;

    public LogsAdapter(LogInteractionListener listener, boolean isSwipeToDelete) {
        this.listener = listener;
        this.isSwipeToDelete = isSwipeToDelete;
    }

    public void setLogs(List<LogEntity> logs) {
        this.logs = logs;
        notifyDataSetChanged();
    }

    public LogEntity getLogAt(int position) {
        return logs.get(position);
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        LogEntity log = logs.get(position);
        holder.textLogRuleName.setText(log.ruleName);
        holder.textLogTimestamp.setText(dateFormat.format(new Date(log.timestamp)));
        holder.textLogDetails.setText(log.details);
        holder.textLogStatus.setText(log.status);

        if ("SUCCESS".equalsIgnoreCase(log.status)) {
            holder.textLogStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
        } else {
            holder.textLogStatus.setTextColor(Color.parseColor("#F44336")); // Red
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLogClicked(log);
            }
        });

        if (isSwipeToDelete) {
            holder.btnDelete.setVisibility(View.GONE);
        } else {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteLog(log);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView textLogRuleName;
        TextView textLogTimestamp;
        TextView textLogStatus;
        TextView textLogDetails;
        com.google.android.material.button.MaterialButton btnDelete;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            textLogRuleName = itemView.findViewById(R.id.text_log_rule_name);
            textLogTimestamp = itemView.findViewById(R.id.text_log_timestamp);
            textLogStatus = itemView.findViewById(R.id.text_log_status);
            textLogDetails = itemView.findViewById(R.id.text_log_details);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
