package com.reader.oghma.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.reader.oghma.data.entity.BookTag;

import java.util.List;

@Dao
public interface BookTagDao {

    @Insert
    void insert(BookTag bookTag);


    @Query(
            "SELECT * FROM BookTag " +
                    "WHERE bookId = :bookId"
    )
    List<BookTag> getForBook(int bookId);


    @Query(
            "DELETE FROM BookTag " +
                    "WHERE bookId = :bookId " +
                    "AND tagId = :tagId"
    )
    void removeTagFromBook(
            int bookId,
            int tagId
    );


    @Query(
            "DELETE FROM BookTag " +
                    "WHERE bookId = :bookId"
    )
    void removeAllTagsFromBook(int bookId);
}
