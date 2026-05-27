package com.thecaptaincook.zenith;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "rules")
public class RuleEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String ruleName;
    public String folderName = "Default";
    public String triggersJson; // Storing array of triggers
    public String triggerLogic = "ANY"; // "ANY" or "ALL"
    public String actionsJson; // Storing array of actions
    public boolean isEnabled = true; // Default to true

    public java.util.List<String> getTriggersList() {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (triggersJson == null || triggersJson.isEmpty()) return list;
        try {
            org.json.JSONArray array = new org.json.JSONArray(triggersJson);
            for (int i = 0; i < array.length(); i++) {
                list.add(array.getString(i));
            }
        } catch (Exception e) {}
        return list;
    }
}
