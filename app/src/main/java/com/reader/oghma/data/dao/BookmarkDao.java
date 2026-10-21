package com.reader.oghma.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.reader.oghma.data.entity.Bookmark;

import java.util.List;

@Dao
public interface BookmarkDao {

    @Insert
    void insert(Bookmark bookmark);


    @Delete
    void delete(Bookmark bookmark);


    @Query(
            "SELECT * FROM Bookmark " +
                    "WHERE bookId = :bookId " +
                    "ORDER BY createdAt DESC"
    )
    List<Bookmark> getForBook(int bookId);


    @Query(
            "SELECT * FROM Bookmark " +
                    "ORDER BY createdAt DESC"
    )
    List<Bookmark> getAll();


    @Query(
            "UPDATE Bookmark " +
                    "SET note = :note, color = :color " +
                    "WHERE bookmarkId = :bookmarkId"
    )
    void updateNoteAndColor(
            int bookmarkId,
            String note,
            int color
    );


    @Query(
            "DELETE FROM Bookmark " +
                    "WHERE bookmarkId = :bookmarkId"
    )
    void deleteById(int bookmarkId);
}