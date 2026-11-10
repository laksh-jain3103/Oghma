package com.reader.oghma.api.metadata;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenLibraryApi {

    @GET("search.json")
    Call<OpenLibraryResponse> search(
            @Query("q") String query,
            @Query("title") String title,
            @Query("author") String author,
            @Query("isbn") String isbn,
            @Query("fields") String fields,
            @Query("limit") int limit
    );
}
