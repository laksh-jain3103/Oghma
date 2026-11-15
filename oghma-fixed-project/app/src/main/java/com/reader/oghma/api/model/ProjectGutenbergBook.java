package com.reader.oghma.api.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A book parsed from Project Gutenberg's official OPDS catalogue.
 */
public class ProjectGutenbergBook {
    public int id;
    public String title;
    public String publishedDate;
    public String epubUrl;
    public String coverUrl;
    public String bookPageUrl;
    public String rights;
    public boolean publicDomainInUs;
    public final List<String> authors = new ArrayList<>();
}
