package com.reader.oghma.api.metadata;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class OpenLibraryClient {

    private static final String BASE_URL = "https://openlibrary.org/";

    // TODO: Replace user contact placeholder with your real email/contact info for OpenLibrary API policy compliance
    private static final String USER_AGENT = "Oghma/1.0 (contact: user@example.com)";

    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                Request original = chain.request();
                Request request = original.newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build();
                return chain.proceed(request);
            })
            .build();

    private static final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

    private static final OpenLibraryApi api = retrofit.create(OpenLibraryApi.class);

    public static OpenLibraryApi getApi() {
        return api;
    }
}
