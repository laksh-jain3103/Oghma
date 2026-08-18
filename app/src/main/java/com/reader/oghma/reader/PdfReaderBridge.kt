package com.reader.oghma.reader

import android.content.Context
import androidx.fragment.app.FragmentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

import java.io.File

class PdfReaderBridge(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private val httpClient =
        DefaultHttpClient()

    private val assetRetriever =
        AssetRetriever(
            contentResolver =
                appContext.contentResolver,
            httpClient =
                httpClient
        )

    private val publicationOpener =
        PublicationOpener(
            publicationParser =
                DefaultPublicationParser(
                    context = appContext,
                    httpClient = httpClient,
                    assetRetriever = assetRetriever,
                    pdfFactory = org.readium.adapter.pdfium.document.PdfiumDocumentFactory(
                        appContext
                    )
                )
        )

    interface Callback {

        fun onSuccess(
            factory: FragmentFactory
        )

        fun onError(
            message: String
        )
    }

    fun openPdf(
        file: File,
        callback: Callback
    ) {

        scope.launch {

            try {

                if (!file.exists()) {

                    withContext(Dispatchers.Main) {

                        callback.onError(
                            "PDF file does not exist:\n${file.absolutePath}"
                        )
                    }

                    return@launch
                }

                val assetResult =
                    assetRetriever.retrieve(file)

                if (assetResult.isFailure) {

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

                    withContext(Dispatchers.Main) {

                        callback.onError(
                            "Readium could not load the PDF:\n$errorMessage"
                        )
                    }

                    return@launch
                }

                val asset =
                    assetResult.getOrNull()

                if (asset == null) {

                    withContext(Dispatchers.Main) {

                        callback.onError(
                            "Readium returned an empty PDF asset."
                        )
                    }

                    return@launch
                }

                val publicationResult =
                    publicationOpener.open(
                        asset = asset,
                        allowUserInteraction = true
                    )

                if (publicationResult.isFailure) {

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

                    withContext(Dispatchers.Main) {

                        callback.onError(
                            "Readium could not open the PDF:\n$errorMessage"
                        )
                    }

                    return@launch
                }

                val publication =
                    publicationResult.getOrNull()

                if (publication == null) {

                    withContext(Dispatchers.Main) {

                        callback.onError(
                            "Readium returned an empty PDF publication."
                        )
                    }

                    return@launch
                }

                val navigatorFactory =
                    PdfNavigatorFactory(
                        publication =
                            publication,

                        pdfEngineProvider =
                            PdfiumEngineProvider()
                    )

                val fragmentFactory =
                    navigatorFactory.createFragmentFactory(
                        initialLocator = null
                    )

                withContext(Dispatchers.Main) {

                    callback.onSuccess(
                        fragmentFactory
                    )
                }

            } catch (e: Exception) {

                e.printStackTrace()

                withContext(Dispatchers.Main) {

                    callback.onError(
                        e.message
                            ?: "Unknown error while opening PDF."
                    )
                }
            }
        }
    }
}