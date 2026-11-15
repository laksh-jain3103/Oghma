package com.reader.oghma.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.reader.oghma.data.entity.TagSuppression;

@Dao
public interface TagSuppressionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(TagSuppression suppression);

    @Query("DELETE FROM TagSuppression WHERE bookId = :bookId AND normalizedName = :normalizedName")
    void delete(int bookId, String normalizedName);

    @Query("SELECT COUNT(*) > 0 FROM TagSuppression WHERE bookId = :bookId AND normalizedName = :normalizedName")
    boolean isSuppressed(int bookId, String normalizedName);
}
