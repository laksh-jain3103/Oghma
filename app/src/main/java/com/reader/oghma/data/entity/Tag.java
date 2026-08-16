package com.reader.oghma.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Tag {

    @PrimaryKey(autoGenerate = true)
    public int tagId;

    public String name;
}