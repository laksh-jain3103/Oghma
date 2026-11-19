package com.reader.oghma.api;

import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class GoogleBooksClient {

    private static final String BASE_URL =
            "https://www.googleapis.com/books/v1/";

    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final Retrofit retrofit =
            new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(
                            GsonConverterFactory.create()
                    )
                    .build();

    private static final GoogleBooksApi api =
            retrofit.create(GoogleBooksApi.class);

    public static GoogleBooksApi getApi() {
        return api;
    }
}