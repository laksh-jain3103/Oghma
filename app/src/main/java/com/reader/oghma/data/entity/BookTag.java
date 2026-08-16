package com.reader.oghma.data.entity;

import androidx.room.Entity;

@Entity(
        primaryKeys = {
                "bookId",
                "tagId"
        }
)
public class BookTag {

    public int bookId;

    public int tagId;
}