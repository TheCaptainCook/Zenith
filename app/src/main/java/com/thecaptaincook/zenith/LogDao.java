package com.thecaptaincook.zenith;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;

import java.util.List;

@Dao
public interface LogDao {
    @Insert
    void insert(LogEntity log);

    @Delete
    void deleteLog(LogEntity log);

    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    LiveData<List<LogEntity>> getAllLogsLiveData();

    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    List<LogEntity> getAllLogsSync(); // For export

    @Query("DELETE FROM logs")
    void clearAllLogs();

    @Query("DELETE FROM logs WHERE timestamp < :cutoffTime")
    void deleteOldLogs(long cutoffTime);
}
