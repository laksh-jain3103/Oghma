package com.reader.oghma.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Bookmark {

    @PrimaryKey(autoGenerate = true)
    public int bookmarkId;

    public int bookId;

    public String selectedText;

    public String locatorJson;

    public String chapterTitle;

    public long createdAt;

    /** Highlight colour as ARGB. 0 means "default" (yellow), so old rows keep working. */
    @ColumnInfo(defaultValue = "0")
    public int color;

    /** Optional note the reader wrote for this highlight. */
    public String note;
}