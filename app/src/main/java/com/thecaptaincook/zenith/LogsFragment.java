package com.thecaptaincook.zenith;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LogsFragment extends Fragment implements LogsAdapter.LogInteractionListener {

    private RecyclerView recyclerLogs;
    private LogsAdapter adapter;
    private MaterialButtonToggleGroup toggleGroupFilters;
    
    private AppDatabase db;
    private ExecutorService executorService;
    private List<LogEntity> allLogs = new ArrayList<>();
    private String currentFilter = "ALL";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_logs, container, false);
        
        db = AppDatabase.getDatabase(requireContext());
        executorService = Executors.newSingleThreadExecutor();

        recyclerLogs = view.findViewById(R.id.recycler_logs);
        recyclerLogs.setLayoutManager(new LinearLayoutManager(getContext()));
        
        boolean isSwipeToDelete = requireActivity().getSharedPreferences("zenith_prefs", android.content.Context.MODE_PRIVATE)
                .getInt("delete_method", 0) == 0;
        
        adapter = new LogsAdapter(this, isSwipeToDelete);
        recyclerLogs.setAdapter(adapter);

        if (isSwipeToDelete) {
            new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    return false;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                    int pos = viewHolder.getAdapterPosition();
                    LogEntity log = adapter.getLogAt(pos);
                    onDeleteLog(log);
                }

                @Override
                public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                    View itemView = viewHolder.itemView;
                    
                    float swipeRatio = Math.min(1.0f, Math.abs(dX) / (float) itemView.getWidth());
                    int startColor = android.graphics.Color.parseColor("#ffcdd2");
                    int endColor = android.graphics.Color.parseColor("#b71c1c");
                    int blendedColor = androidx.core.graphics.ColorUtils.blendARGB(startColor, endColor, swipeRatio);
                    
                    android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
                    background.setColor(blendedColor);
                    float cornerRadiusPx = 20f * itemView.getContext().getResources().getDisplayMetrics().density;
                    background.setCornerRadius(cornerRadiusPx);
                    background.setBounds(itemView.getLeft(), itemView.getTop(), itemView.getRight(), itemView.getBottom());

                    background.draw(c);

                    // Draw an opaque backing behind the moving card to prevent the red background
                    // from showing through the translucent glassmorphic card.
                    c.save();
                    c.translate(dX, dY);
                    android.graphics.drawable.GradientDrawable opaqueBg = new android.graphics.drawable.GradientDrawable();
                    android.util.TypedValue typedValue = new android.util.TypedValue();
                    itemView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true);
                    opaqueBg.setColor(typedValue.data);
                    opaqueBg.setCornerRadius(cornerRadiusPx);
                    opaqueBg.setBounds(itemView.getLeft(), itemView.getTop(), itemView.getRight(), itemView.getBottom());
                    opaqueBg.draw(c);
                    c.restore();

                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                    // Prevent the default ItemTouchHelper behavior from fading out the card
                    viewHolder.itemView.setAlpha(1.0f);
                }
            }).attachToRecyclerView(recyclerLogs);
        }

        toggleGroupFilters = view.findViewById(R.id.toggle_group_filters);
        toggleGroupFilters.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_filter_success) {
                    currentFilter = "SUCCESS";
                } else if (checkedId == R.id.btn_filter_failed) {
                    currentFilter = "FAILED";
                } else {
                    currentFilter = "ALL";
                }
                applyFilter();
            }
        });

        view.findViewById(R.id.btn_clear).setOnClickListener(v -> clearLogs());
        view.findViewById(R.id.btn_export).setOnClickListener(v -> exportLogs());

        // Observe logs in real-time
        db.logDao().getAllLogsLiveData().observe(getViewLifecycleOwner(), logs -> {
            this.allLogs = logs;
            applyFilter();
        });

        return view;
    }

    private void applyFilter() {
        if (currentFilter.equals("ALL")) {
            adapter.setLogs(allLogs);
        } else {
            List<LogEntity> filtered = new ArrayList<>();
            for (LogEntity log : allLogs) {
                if (log.status != null && log.status.equalsIgnoreCase(currentFilter)) {
                    filtered.add(log);
                }
            }
            adapter.setLogs(filtered);
        }
    }

    private void clearLogs() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_rule_summary, null);
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.text_dialog_title);
        android.widget.TextView tvMessage = dialogView.findViewById(R.id.text_dialog_message);
        android.widget.Button btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);
        android.widget.Button btnClear = dialogView.findViewById(R.id.btn_dialog_save);

        tvTitle.setText("Clear Logs");
        tvMessage.setText("Are you sure you want to delete all execution history?");
        btnCancel.setText("Cancel");
        btnClear.setText("Clear");
        btnClear.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#D32F2F")));

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnClear.setOnClickListener(v -> {
            dialog.dismiss();
            executorService.execute(() -> db.logDao().clearAllLogs());
        });

        dialog.show();
    }

    private void exportLogs() {
        executorService.execute(() -> {
            try {
                List<LogEntity> logsToExport = db.logDao().getAllLogsSync();
                JSONArray jsonArray = new JSONArray();
                for (LogEntity log : logsToExport) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", log.id);
                    obj.put("ruleName", log.ruleName);
                    obj.put("timestamp", log.timestamp);
                    obj.put("status", log.status);
                    obj.put("details", log.details);
                    jsonArray.put(obj);
                }

                File exportDir = new File(requireContext().getCacheDir(), "exports");
                if (!exportDir.exists()) exportDir.mkdirs();
                File file = new File(exportDir, "zenith_logs.json");
                
                FileWriter writer = new FileWriter(file);
                writer.write(jsonArray.toString(4));
                writer.flush();
                writer.close();

                requireActivity().runOnUiThread(() -> {
                    ThemePrompt.show(getView(), "Exported to: " + file.getAbsolutePath());
                    // Optional: Setup FileProvider and ACTION_SEND intent here if sharing is desired.
                });

            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    ThemePrompt.show(getView(), "Export failed");
                });
            }
        });
    }

    @Override
    public void onLogClicked(LogEntity log) {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault());
        String timeStr = format.format(new java.util.Date(log.timestamp));

        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_rule_summary, null);
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.text_dialog_title);
        android.widget.TextView tvMessage = dialogView.findViewById(R.id.text_dialog_message);
        android.widget.Button btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);
        android.widget.Button btnClose = dialogView.findViewById(R.id.btn_dialog_save);

        tvTitle.setText("Log Details");
        tvMessage.setText("Rule: " + log.ruleName + "\n" +
                          "Time: " + timeStr + "\n" +
                          "Status: " + log.status + "\n\n" +
                          "Details:\n" + log.details);
        tvMessage.setTextAlignment(android.view.View.TEXT_ALIGNMENT_TEXT_START);

        btnCancel.setVisibility(android.view.View.GONE);
        btnClose.setText("Close");

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    @Override
    public void onDeleteLog(LogEntity log) {
        executorService.execute(() -> db.logDao().deleteLog(log));
    }
}
