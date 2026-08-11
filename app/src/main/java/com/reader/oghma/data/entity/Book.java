package com.reader.oghma.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Book {
    @PrimaryKey(autoGenerate = true)
    public int bookId;
    public String title;
    public String author;
    public String filePath;
    public String coverPath;
    public String format;
    public long addedAt;
}