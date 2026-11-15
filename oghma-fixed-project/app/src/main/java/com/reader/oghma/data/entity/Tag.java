package com.reader.oghma.data.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        indices = {
                @Index(value = {"normalizedName"}, unique = true)
        }
)
public class Tag {

    @PrimaryKey(autoGenerate = true)
    public int tagId;

    public String name;

    public String normalizedName;
}