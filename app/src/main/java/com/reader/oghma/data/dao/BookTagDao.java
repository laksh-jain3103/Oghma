package com.reader.oghma.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.entity.BookTag;
import com.reader.oghma.data.entity.Tag;

import java.util.List;

@Dao
public interface BookTagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(BookTag bookTag);


    @Query(
            "SELECT * FROM BookTag " +
                    "WHERE bookId = :bookId"
    )
    List<BookTag> getForBook(int bookId);


    @Query(
            "SELECT t.* FROM Tag t INNER JOIN BookTag bt ON t.tagId = bt.tagId " +
                    "WHERE bt.bookId = :bookId ORDER BY t.name COLLATE NOCASE ASC"
    )
    List<Tag> tagsForBook(int bookId);


    @Query(
            "SELECT t.* FROM Tag t INNER JOIN BookTag bt ON t.tagId = bt.tagId " +
                    "WHERE bt.bookId = :bookId ORDER BY t.name COLLATE NOCASE ASC"
    )
    LiveData<List<Tag>> observeTagsForBook(int bookId);


    @Query(
            "SELECT b.* FROM Book b INNER JOIN BookTag bt ON b.bookId = bt.bookId " +
                    "WHERE bt.tagId = :tagId ORDER BY b.addedAt DESC"
    )
    LiveData<List<Book>> booksForTag(int tagId);


    @Query(
            "DELETE FROM BookTag " +
                    "WHERE bookId = :bookId AND source = 'AUTO'"
    )
    void deleteAutoLinksForBook(int bookId);


    @Query(
            "DELETE FROM BookTag " +
                    "WHERE bookId = :bookId AND tagId = :tagId"
    )
    void deleteLink(int bookId, int tagId);


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
