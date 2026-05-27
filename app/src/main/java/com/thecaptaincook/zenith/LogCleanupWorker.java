package com.thecaptaincook.zenith;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class LogCleanupWorker extends Worker {

    public LogCleanupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context context = getApplicationContext();
            SharedPreferences prefs = context.getSharedPreferences("zenith_prefs", Context.MODE_PRIVATE);
            int retentionSelection = prefs.getInt("retention", 2); // Default to 30 days (index 2)

            long retentionMs = -1;
            switch (retentionSelection) {
                case 0: // 7 Days
                    retentionMs = 7L * 24 * 60 * 60 * 1000;
                    break;
                case 1: // 14 Days
                    retentionMs = 14L * 24 * 60 * 60 * 1000;
                    break;
                case 2: // 30 Days
                    retentionMs = 30L * 24 * 60 * 60 * 1000;
                    break;
                case 3: // Forever
                    return Result.success();
            }

            if (retentionMs > 0) {
                long cutoffTime = System.currentTimeMillis() - retentionMs;
                AppDatabase db = AppDatabase.getDatabase(context);
                db.logDao().deleteOldLogs(cutoffTime);
            }

            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        }
    }
}
