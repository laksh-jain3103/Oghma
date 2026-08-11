package com.reader.oghma.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Progress {
    @PrimaryKey
    public int bookId;
    public String locator;
    public float percentage;
}