package com.reader.oghma.reader

import android.content.Context

import androidx.fragment.app.FragmentFactory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider

import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.pdf.PdfNavigatorFactory

import org.readium.r2.shared.publication.Locator

import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient

import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

import org.json.JSONObject

import java.io.File


class PdfReaderBridge(
    context: Context
) {

    // =========================================================
    // Application context
    // =========================================================

    private val appContext =
        context.applicationContext


    // =========================================================
    // Coroutine scope
    // =========================================================

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        )


    // =========================================================
    // HTTP client
    // =========================================================

    private val httpClient =
        DefaultHttpClient()


    // =========================================================
    // Asset retriever
    // =========================================================

    private val assetRetriever =
        AssetRetriever(
            contentResolver =
                appContext.contentResolver,

            httpClient =
                httpClient
        )


    // =========================================================
    // Publication opener
    // =========================================================

    private val publicationOpener =
        PublicationOpener(
            publicationParser =
                DefaultPublicationParser(
                    context =
                        appContext,

                    httpClient =
                        httpClient,

                    assetRetriever =
                        assetRetriever,

                    pdfFactory =
                        org.readium.adapter.pdfium.document.PdfiumDocumentFactory(
                            appContext
                        )
                )
        )


    // =========================================================
    // Callback
    // =========================================================

    interface Callback {

        fun onSuccess(
            factory: FragmentFactory
        )

        fun onError(
            message: String
        )
    }


    // =========================================================
    // Locator callback
    // =========================================================

    interface LocatorCallback {

        fun onLocator(
            locatorJson: String?
        )
    }


    // =========================================================
    // OPEN PDF
    //
    // initialLocatorJson:
    //
    // null
    //     Open normally.
    //
    // JSON string
    //     Restore the previous reading position.
    //
    // horizontalReadingMode:
    //
    // true
    //     Horizontal scrolling.
    //
    // false
    //     Vertical scrolling.
    // =========================================================

    fun openPdf(
        file: File,
        initialLocatorJson: String?,
        horizontalReadingMode: Boolean,
        callback: Callback
    ) {

        scope.launch {

            try {

                // -------------------------------------------------
                // Check file
                // -------------------------------------------------

                if (!file.exists()) {

                    withContext(
                        Dispatchers.Main
                    ) {

                        callback.onError(
                            "PDF file does not exist:\n" +
                                    file.absolutePath
                        )
                    }

                    return@launch
                }


                // -------------------------------------------------
                // Retrieve PDF asset
                // -------------------------------------------------

                val assetResult =
                    assetRetriever.retrieve(
                        file
                    )


                if (
                    assetResult.isFailure
                ) {

                    val errorMessage =
                        assetResult.fold(
                            onSuccess = {
                                "Unknown Readium error."
                            },

                            onFailure = { error ->
                                error.message
                                    ?: error.toString()
                            }
                        )


                    withContext(
                        Dispatchers.Main
                    ) {

                        callback.onError(
                            "Readium could not load the PDF:\n" +
                                    errorMessage
                        )
                    }

                    return@launch
                }


                val asset =
                    assetResult.getOrNull()


                if (asset == null) {

                    withContext(
                        Dispatchers.Main
                    ) {

                        callback.onError(
                            "Readium returned an empty PDF asset."
                        )
                    }

                    return@launch
                }


                // -------------------------------------------------
                // Open publication
                // -------------------------------------------------

                val publicationResult =
                    publicationOpener.open(
                        asset =
                            asset,

                        allowUserInteraction =
                            true
                    )


                if (
                    publicationResult.isFailure
                ) {

                    val errorMessage =
                        publicationResult.fold(
                            onSuccess = {
                                "Unknown Readium error."
                            },

                            onFailure = { error ->
                                error.message
                                    ?: error.toString()
                            }
                        )


                    withContext(
                        Dispatchers.Main
                    ) {

                        callback.onError(
                            "Readium could not open the PDF:\n" +
                                    errorMessage
                        )
                    }

                    return@launch
                }


                val publication =
                    publicationResult.getOrNull()


                if (publication == null) {

                    withContext(
                        Dispatchers.Main
                    ) {

                        callback.onError(
                            "Readium returned an empty PDF publication."
                        )
                    }

                    return@launch
                }


                // -------------------------------------------------
                // Restore previous locator
                // -------------------------------------------------

                val initialLocator =
                    parseLocator(
                        initialLocatorJson
                    )


                // -------------------------------------------------
                // PDFium engine
                // -------------------------------------------------

                val pdfEngineProvider =
                    PdfiumEngineProvider()


                // -------------------------------------------------
                // Create PDF navigator factory
                // -------------------------------------------------

                val navigatorFactory =
                    PdfNavigatorFactory(
                        publication =
                            publication,

                        pdfEngineProvider =
                            pdfEngineProvider
                    )


                // -------------------------------------------------
                // Create navigator FragmentFactory
                // -------------------------------------------------

                val fragmentFactory =
                    navigatorFactory.createFragmentFactory(
                        initialLocator =
                            initialLocator
                    )


                // -------------------------------------------------
                // Return factory to ReaderFragment
                // -------------------------------------------------

                withContext(
                    Dispatchers.Main
                ) {

                    callback.onSuccess(
                        fragmentFactory
                    )
                }

            } catch (
                e: Exception
            ) {

                e.printStackTrace()


                withContext(
                    Dispatchers.Main
                ) {

                    callback.onError(
                        e.message
                            ?: "Unknown error while opening PDF."
                    )
                }
            }
        }
    }


    // =========================================================
    // PARSE LOCATOR
    //
    // Converts the JSON string stored by the app back into
    // a Readium Locator.
    // =========================================================

    private fun parseLocator(
        locatorJson: String?
    ): Locator? {

        if (
            locatorJson == null ||
            locatorJson.trim().isEmpty()
        ) {

            return null
        }


        return try {

            Locator.fromJSON(
                JSONObject(
                    locatorJson
                )
            )

        } catch (
            e: Exception
        ) {

            e.printStackTrace()

            null
        }
    }


    // =========================================================
    // GET CURRENT PDF LOCATION
    //
    // VisualNavigator exposes currentLocator as a StateFlow.
    //
    // We only need VisualNavigator here, which avoids having
    // to specify PdfNavigatorFragment's generic parameters.
    // =========================================================

    fun getCurrentLocator(
        navigator: VisualNavigator,
        callback: LocatorCallback
    ) {

        CoroutineScope(
            Dispatchers.Main
        ).launch {

            try {

                val locator =
                    navigator
                        .currentLocator
                        .value


                val locatorJson =
                    locator
                        .toJSON()
                        .toString()


                callback.onLocator(
                    locatorJson
                )

            } catch (
                e: Exception
            ) {

                e.printStackTrace()


                callback.onLocator(
                    null
                )
            }
        }
    }
}