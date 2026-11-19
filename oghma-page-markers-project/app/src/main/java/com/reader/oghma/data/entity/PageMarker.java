package com.reader.oghma.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A place in a book the reader marked, like a bookmark ribbon: tap it later to jump back.
 *
 * Not to be confused with Bookmark, which stores highlights and notes.
 */
@Entity(
        foreignKeys = {
                @ForeignKey(
                        entity = Book.class,
                        parentColumns = "bookId",
                        childColumns = "bookId",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index(value = {"bookId"})
        }
)
public class PageMarker {

    @PrimaryKey(autoGenerate = true)
    public int markerId;

    public int bookId;

    /** EPUB: Readium locator JSON. PDF: {"type":"pdf","page":N,"y":F}. */
    public String locatorJson;

    /** Short text shown in the list: a chapter title or "Page 12". */
    public String label;

    /** How far into the book this place is, 0 to 100. */
    public float percentage;

    public long createdAt;
}
