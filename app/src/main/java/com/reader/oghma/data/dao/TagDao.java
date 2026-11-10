package com.reader.oghma.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.reader.oghma.data.entity.Tag;

import java.util.List;

@Dao
public interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Tag tag);


    @Delete
    void delete(Tag tag);


    @Query(
            "SELECT * FROM Tag " +
                    "ORDER BY name COLLATE NOCASE ASC"
    )
    List<Tag> getAll();


    @Query(
            "SELECT * FROM Tag " +
                    "WHERE tagId = :tagId"
    )
    Tag getById(int tagId);


    @Query(
            "SELECT * FROM Tag " +
                    "WHERE name = :name " +
                    "LIMIT 1"
    )
    Tag getByName(String name);


    @Query(
            "SELECT * FROM Tag " +
                    "WHERE normalizedName = :normalizedName " +
                    "LIMIT 1"
    )
    Tag getByNormalizedName(String normalizedName);


    @Query(
            "SELECT t.tagId AS tagId, t.name AS name, t.normalizedName AS normalizedName, COUNT(bt.bookId) AS bookCount " +
                    "FROM Tag t INNER JOIN BookTag bt ON t.tagId = bt.tagId " +
                    "GROUP BY t.tagId HAVING bookCount > 0 " +
                    "ORDER BY t.name COLLATE NOCASE ASC"
    )
    LiveData<List<TagWithCount>> getAllWithBookCount();


    @Query(
            "DELETE FROM Tag " +
                    "WHERE tagId NOT IN (SELECT DISTINCT tagId FROM BookTag)"
    )
    void deleteUnusedTags();


    @Query(
            "DELETE FROM Tag " +
                    "WHERE tagId = :tagId"
    )
    void deleteById(int tagId);
}