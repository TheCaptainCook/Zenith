package com.thecaptaincook.zenith;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SavedFragment extends Fragment implements RulesAdapter.RuleInteractionListener {

    private RecyclerView recyclerRules;
    private RulesAdapter adapter;
    private EditText editSearch;
    private Spinner spinnerFolderFilter;
    
    private AppDatabase db;
    private ExecutorService executorService;

    private List<RuleEntity> allRules = new ArrayList<>();
    private String currentFolderFilter = "All Folders";
    private String currentSearchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_saved, container, false);
        
        db = AppDatabase.getDatabase(requireContext());
        executorService = Executors.newSingleThreadExecutor();
        
        editSearch = view.findViewById(R.id.edit_search);
        spinnerFolderFilter = view.findViewById(R.id.spinner_folder_filter);
        recyclerRules = view.findViewById(R.id.recycler_rules);
        
        recyclerRules.setLayoutManager(new LinearLayoutManager(getContext()));
        
        boolean isSwipeToDelete = requireActivity().getSharedPreferences("zenith_prefs", android.content.Context.MODE_PRIVATE)
                .getInt("delete_method", 0) == 0;
        
        adapter = new RulesAdapter(this, isSwipeToDelete);
        recyclerRules.setAdapter(adapter);

        if (isSwipeToDelete) {
            new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    return false;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                    int pos = viewHolder.getAdapterPosition();
                    RuleEntity rule = adapter.getRuleAt(pos);
                    onDeleteRule(rule, pos);
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
            }).attachToRecyclerView(recyclerRules);
        }
        
        loadRules();

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        spinnerFolderFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFolderFilter = parent.getItemAtPosition(position).toString();
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        return view;
    }

    private void loadRules() {
        executorService.execute(() -> {
            allRules = db.ruleDao().getAllRules();
            requireActivity().runOnUiThread(() -> {
                populateFolderSpinner();
                applyFilters();
            });
        });
    }

    private void populateFolderSpinner() {
        Set<String> uniqueFolders = new HashSet<>();
        uniqueFolders.add("All Folders");
        for (RuleEntity rule : allRules) {
            if (rule.folderName != null && !rule.folderName.trim().isEmpty()) {
                uniqueFolders.add(rule.folderName);
            }
        }
        
        List<String> folderList = new ArrayList<>(uniqueFolders);
        // Ensure "All Folders" is always first
        folderList.remove("All Folders");
        folderList.sort(String::compareToIgnoreCase);
        folderList.add(0, "All Folders");

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, folderList);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        // Preserve selection if it still exists
        int selectedPosition = folderList.indexOf(currentFolderFilter);
        spinnerFolderFilter.setAdapter(spinnerAdapter);
        if (selectedPosition >= 0) {
            spinnerFolderFilter.setSelection(selectedPosition);
        } else {
            spinnerFolderFilter.setSelection(0);
            currentFolderFilter = "All Folders";
        }
    }

    private void applyFilters() {
        List<RuleEntity> filtered = new ArrayList<>();
        String queryLower = currentSearchQuery.toLowerCase();

        for (RuleEntity rule : allRules) {
            boolean matchesFolder = "All Folders".equals(currentFolderFilter) || currentFolderFilter.equals(rule.folderName);
            boolean matchesSearch = queryLower.isEmpty() || rule.ruleName.toLowerCase().contains(queryLower);
            
            if (matchesFolder && matchesSearch) {
                filtered.add(rule);
            }
        }
        
        adapter.setRules(filtered);
    }

    @Override
    public void onToggleRule(RuleEntity rule, boolean isEnabled) {
        rule.isEnabled = isEnabled;
        executorService.execute(() -> {
            db.ruleDao().update(rule);
            requireActivity().runOnUiThread(this::reloadAutomationService);
        });
    }

    @Override
    public void onEditRule(RuleEntity rule) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToConfigEdit(rule.id);
        }
    }

    @Override
    public void onDeleteRule(RuleEntity rule, int position) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Delete Rule")
            .setMessage("Are you sure you want to delete '" + rule.ruleName + "'?")
            .setPositiveButton("Yes", (dialog, which) -> {
                executorService.execute(() -> {
                    db.ruleDao().delete(rule);
                    loadRules(); // Refresh list
                    requireActivity().runOnUiThread(this::reloadAutomationService);
                });
            })
            .setNegativeButton("No", (dialog, which) -> {
                adapter.notifyItemChanged(position);
            })
            .show();
    }

    private void reloadAutomationService() {
        android.content.Intent serviceIntent = new android.content.Intent(getContext(), AutomationService.class);
        serviceIntent.setAction("RELOAD_RULES");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent);
        } else {
            requireContext().startService(serviceIntent);
        }
    }
}
