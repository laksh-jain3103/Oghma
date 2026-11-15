package com.reader.oghma.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        primaryKeys = {
                "bookId",
                "tagId"
        },
        foreignKeys = {
                @ForeignKey(
                        entity = Book.class,
                        parentColumns = "bookId",
                        childColumns = "bookId",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = Tag.class,
                        parentColumns = "tagId",
                        childColumns = "tagId",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index(value = {"tagId"})
        }
)
public class BookTag {

    public int bookId;

    public int tagId;

    @NonNull
    @ColumnInfo(defaultValue = "AUTO")
    public String source = "AUTO";
}