package com.thecaptaincook.zenith;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface RuleDao {
    @Insert
    void insert(RuleEntity rule);

    @Update
    void update(RuleEntity rule);

    @Delete
    void delete(RuleEntity rule);

    @Query("SELECT * FROM rules")
    List<RuleEntity> getAllRules();

    @Query("SELECT * FROM rules WHERE ruleName LIKE '%' || :searchQuery || '%'")
    List<RuleEntity> searchRules(String searchQuery);

    @Query("SELECT * FROM rules WHERE id = :id LIMIT 1")
    RuleEntity getRuleById(int id);
}
