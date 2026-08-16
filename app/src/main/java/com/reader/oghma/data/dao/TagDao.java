package com.reader.oghma.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.reader.oghma.data.entity.Tag;

import java.util.List;

@Dao
public interface TagDao {

    @Insert
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
            "DELETE FROM Tag " +
                    "WHERE tagId = :tagId"
    )
    void deleteById(int tagId);
}