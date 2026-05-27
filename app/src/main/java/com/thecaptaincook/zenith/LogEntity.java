package com.thecaptaincook.zenith;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "logs")
public class LogEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String ruleName;
    public long timestamp;
    public String status; // "SUCCESS" or "FAILED"
    public String details;
}
