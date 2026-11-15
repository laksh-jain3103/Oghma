package com.reader.oghma.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.reader.oghma.api.metadata.TagNormalizer;
import com.reader.oghma.data.dao.BookDao;
import com.reader.oghma.data.dao.BookTagDao;
import com.reader.oghma.data.dao.BookTagItem;
import com.reader.oghma.data.dao.TagDao;
import com.reader.oghma.data.dao.TagSuppressionDao;
import com.reader.oghma.data.dao.TagWithCount;
import com.reader.oghma.data.db.OghmaDatabase;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.entity.BookTag;
import com.reader.oghma.data.entity.Tag;
import com.reader.oghma.data.entity.TagSuppression;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BookRepository {

    private final OghmaDatabase database;
    private final BookDao bookDao;
    private final TagDao tagDao;
    private final BookTagDao bookTagDao;
    private final TagSuppressionDao tagSuppressionDao;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();


    public BookRepository(
            Application application
    ) {

        database =
                OghmaDatabase.getInstance(
                        application
                );

        bookDao = database.bookDao();
        tagDao = database.tagDao();
        bookTagDao = database.bookTagDao();
        tagSuppressionDao = database.tagSuppressionDao();
    }


    // ---------------------------------------------------------
    // Get all books
    //
    // IMPORTANT:
    // BookDao returns LiveData<List<Book>>.
    // Do not convert this to List<Book>.
    // ---------------------------------------------------------

    public LiveData<java.util.List<Book>> getAllBooks() {

        return bookDao.getAll();
    }


    public LiveData<java.util.List<Book>> getBookmarkedBooks() {

        return bookDao.getBookmarkedBooks();
    }


    public void updateBookmarkStatus(
            int bookId,
            boolean isBookmarked
    ) {

        executor.execute(() ->
                bookDao.updateBookmarkStatus(
                        bookId,
                        isBookmarked
                )
        );
    }


    // ---------------------------------------------------------
    // Insert book
    // ---------------------------------------------------------

    public void insertBook(
            Book book
    ) {

        executor.execute(() -> {
            long id = bookDao.insert(book);
            book.bookId = (int) id;
        });
    }

    public long insertBookSync(Book book) {
        long id = bookDao.insert(book);
        book.bookId = (int) id;
        return id;
    }


    // ---------------------------------------------------------
    // Get one book
    // ---------------------------------------------------------

    public void getBook(
            int bookId,
            SingleBookCallback callback
    ) {

        executor.execute(() -> {

            Book book =
                    bookDao.getById(
                            bookId
                    );

            callback.onBookLoaded(
                    book
            );
        });
    }

    public Book getBookSync(int bookId) {
        return bookDao.getById(bookId);
    }

    public List<Book> getPendingTagBooksSync() {
        return bookDao.getPendingTagBooks();
    }

    public void schedulePendingTagWorkers(android.content.Context context) {
        executor.execute(() -> {
            List<Book> pending = bookDao.getPendingTagBooks();
            if (pending != null) {
                for (Book b : pending) {
                    com.reader.oghma.library.TagBookWorker.enqueue(context, b.bookId, false);
                }
            }
        });
    }


    // ---------------------------------------------------------
    // Delete book
    // ---------------------------------------------------------

    public void deleteBook(
            Book book
    ) {

        executor.execute(() ->
                bookDao.delete(
                        book
                )
        );
    }


    // ---------------------------------------------------------
    // Tag operations
    // ---------------------------------------------------------

    public void replaceAutoTags(int bookId, List<String> displayNames) {
        executor.execute(() -> replaceAutoTagsSync(bookId, displayNames));
    }

    /** Blocking version, all-or-nothing. Call from a background thread only. */
    public void replaceAutoTagsSync(int bookId, List<String> displayNames) {
        database.runInTransaction(() -> {
            bookTagDao.deleteAutoLinksForBook(bookId);
            if (displayNames != null) {
                for (String name : displayNames) {
                    if (name == null || name.trim().isEmpty()) continue;
                    String normalized = TagNormalizer.normalizeKey(name);
                    if (tagSuppressionDao.isSuppressed(bookId, normalized)) {
                        continue;
                    }
                    int tagId = getOrCreateTagIdSync(name);
                    if (tagId > 0) {
                        BookTag bt = new BookTag();
                        bt.bookId = bookId;
                        bt.tagId = tagId;
                        bt.source = "AUTO";
                        bookTagDao.insert(bt);
                    }
                }
            }
            tagDao.deleteUnusedTags();
        });
    }

    public void updateTitleAuthorSync(int bookId, String title, String author) {
        bookDao.updateTitleAuthor(bookId, title, author);
    }

    public void addUserTag(int bookId, String tagName) {
        executor.execute(() -> {
            if (tagName == null || tagName.trim().isEmpty()) return;
            String normalized = TagNormalizer.normalizeKey(tagName);
            tagSuppressionDao.delete(bookId, normalized);
            int tagId = getOrCreateTagIdSync(tagName);
            if (tagId > 0) {
                BookTag bt = new BookTag();
                bt.bookId = bookId;
                bt.tagId = tagId;
                bt.source = "USER";
                bookTagDao.insert(bt);
            }
        });
    }

    public void removeTag(int bookId, int tagId) {
        executor.execute(() -> {
            Tag tag = tagDao.getById(tagId);
            BookTag link = bookTagDao.getLink(bookId, tagId);
            if (link != null && "AUTO".equalsIgnoreCase(link.source) && tag != null && tag.normalizedName != null) {
                TagSuppression s = new TagSuppression();
                s.bookId = bookId;
                s.normalizedName = tag.normalizedName;
                tagSuppressionDao.insert(s);
            }
            bookTagDao.deleteLink(bookId, tagId);
            tagDao.deleteUnusedTags();
        });
    }

    public void getTagsForBook(int bookId, TagsCallback callback) {
        executor.execute(() -> {
            List<Tag> tags = bookTagDao.tagsForBook(bookId);
            callback.onTagsLoaded(tags);
        });
    }

    public interface TagsCallback {
        void onTagsLoaded(List<Tag> tags);
    }

    public LiveData<List<Tag>> observeTagsForBook(int bookId) {
        return bookTagDao.observeTagsForBook(bookId);
    }

    public LiveData<List<BookTagItem>> observeAllBookTags() {
        return bookTagDao.observeAllBookTags();
    }

    public LiveData<List<TagWithCount>> observeTagsWithCounts() {
        return tagDao.getAllWithBookCount();
    }

    public LiveData<List<Book>> observeBooksForTag(int tagId) {
        return bookTagDao.booksForTag(tagId);
    }

    public void updateTagStatus(int bookId, String status, int attempts) {
        executor.execute(() -> bookDao.updateTagStatus(bookId, status, attempts));
    }

    public void updateTagStatusSync(int bookId, String status, int attempts) {
        bookDao.updateTagStatus(bookId, status, attempts);
    }

    private int getOrCreateTagIdSync(String displayName) {
        String trimmed = displayName != null ? displayName.trim() : "";
        String normalized = TagNormalizer.normalizeKey(displayName);
        Tag existing = tagDao.getByNormalizedName(normalized);
        if (existing != null) {
            return existing.tagId;
        }
        Tag tag = new Tag();
        tag.name = trimmed;
        tag.normalizedName = normalized;
        long newId = tagDao.insert(tag);
        if (newId != -1) {
            return (int) newId;
        }
        Tag retry = tagDao.getByNormalizedName(normalized);
        return retry != null ? retry.tagId : -1;
    }


    // ---------------------------------------------------------
    // Callback for a single book
    // ---------------------------------------------------------

    public interface SingleBookCallback {

        void onBookLoaded(
                Book book
        );
    }
}