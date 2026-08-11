package com.reader.oghma.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.reader.oghma.data.entity.Progress;

@Dao
public interface ProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(Progress progress);

    @Query("SELECT * FROM Progress WHERE bookId = :bookId")
    Progress getByBookId(int bookId);
}