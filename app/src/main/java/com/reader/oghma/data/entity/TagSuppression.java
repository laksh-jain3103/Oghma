package com.reader.oghma.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;

@Entity(
        primaryKeys = {"bookId", "normalizedName"},
        foreignKeys = {
                @ForeignKey(
                        entity = Book.class,
                        parentColumns = "bookId",
                        childColumns = "bookId",
                        onDelete = ForeignKey.CASCADE
                )
        }
)
public class TagSuppression {
    public int bookId;

    @NonNull
    public String normalizedName = "";
}
