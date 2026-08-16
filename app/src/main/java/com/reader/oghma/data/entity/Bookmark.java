package com.reader.oghma.data.entity;

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
}