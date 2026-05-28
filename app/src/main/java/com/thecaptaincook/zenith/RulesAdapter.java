package com.thecaptaincook.zenith;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class RulesAdapter extends RecyclerView.Adapter<RulesAdapter.RuleViewHolder> {

    private List<RuleEntity> rules = new ArrayList<>();
    private final RuleInteractionListener listener;
    private final boolean isSwipeToDelete;

    public interface RuleInteractionListener {
        void onToggleRule(RuleEntity rule, boolean isEnabled);
        void onEditRule(RuleEntity rule);
        void onDeleteRule(RuleEntity rule, int position);
    }

    public RulesAdapter(RuleInteractionListener listener, boolean isSwipeToDelete) {
        this.listener = listener;
        this.isSwipeToDelete = isSwipeToDelete;
    }

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    public void setRules(List<RuleEntity> rules) {
        this.rules = rules;
        notifyDataSetChanged();
    }

    public RuleEntity getRuleAt(int position) {
        return rules.get(position);
    }

    @NonNull
    @Override
    public RuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rule, parent, false);
        return new RuleViewHolder(view);
    }

    @android.annotation.SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull RuleViewHolder holder, int position) {
        RuleEntity rule = rules.get(position);
        holder.textRuleName.setText(rule.ruleName);
        String actionDisplay = "No Actions";
        try {
            if (rule.actionsJson != null && !rule.actionsJson.isEmpty()) {
                org.json.JSONArray actionsArray = new org.json.JSONArray(rule.actionsJson);
                if (actionsArray.length() > 0) {
                    actionDisplay = actionsArray.getString(0).split("\\|")[0];
                    if (actionsArray.length() > 1) {
                        actionDisplay += " (+" + (actionsArray.length() - 1) + " more)";
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("RulesAdapter", "Error parsing actionsJson", e);
        }

        String triggerDisplay = "Unknown Trigger";
        try {
            if (rule.triggersJson != null && !rule.triggersJson.isEmpty()) {
                org.json.JSONArray triggersArray = new org.json.JSONArray(rule.triggersJson);
                if (triggersArray.length() > 0) {
                    triggerDisplay = triggersArray.getString(0);
                    if (triggersArray.length() > 1) {
                        triggerDisplay += " (+" + (triggersArray.length() - 1) + " " + rule.triggerLogic + ")";
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("RulesAdapter", "Error parsing triggersJson", e);
        }

        holder.textTriggerAction.setText(triggerDisplay + " -> " + actionDisplay);
        
        // Show folder name if it's not the default "Default"
        if (rule.folderName != null && !rule.folderName.isEmpty() && !rule.folderName.equals("Default")) {
            holder.textTriggerAction.append(" | Folder: " + rule.folderName);
        }

        // Remove listener temporarily to avoid recursive triggers during bind
        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(rule.isEnabled);
        
        holder.switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> listener.onToggleRule(rule, isChecked));

        holder.btnEdit.setOnClickListener(v -> listener.onEditRule(rule));
        
        if (isSwipeToDelete) {
            holder.btnDelete.setVisibility(View.GONE);
        } else {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION) {
                    listener.onDeleteRule(rule, adapterPos);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return rules.size();
    }

    public static class RuleViewHolder extends RecyclerView.ViewHolder {
        TextView textRuleName;
        TextView textTriggerAction;
        MaterialSwitch switchEnabled;
        com.google.android.material.button.MaterialButton btnEdit;
        com.google.android.material.button.MaterialButton btnDelete;

        public RuleViewHolder(@NonNull View itemView) {
            super(itemView);
            textRuleName = itemView.findViewById(R.id.text_rule_name);
            textTriggerAction = itemView.findViewById(R.id.text_trigger_action);
            switchEnabled = itemView.findViewById(R.id.switch_enabled);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
