package com.reader.oghma.reader

import android.content.Context
import android.graphics.Bitmap
import androidx.fragment.app.FragmentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.io.FileOutputStream
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory

class EpubReaderBridge(
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
                    pdfFactory = PdfiumDocumentFactory(appContext)
                )
        )


    interface Callback {
        fun onSuccess(factory: FragmentFactory)
        fun onError(message: String)

        fun onBookmarkRequested()
    }

    interface SelectionCallback {
        fun onSelection(
            selectedText: String?,
            locatorJson: String?,
            chapterTitle: String?
        )
    }

    interface CoverCallback {

        fun onSuccess(
            coverFile: File
        )

        fun onError(
            message: String
        )
    }


    fun openEpub(
        file: File,
        callback: Callback
    ) {

        scope.launch {

            try {

                if (!file.exists()) {

                    withContext(Dispatchers.Main) {

                        callback.onError(
                            "EPUB file does not exist:\n${file.absolutePath}"
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
                            "Readium could not load the EPUB:\n$errorMessage"
                        )
                    }

                    return@launch
                }


                val asset =
                    assetResult.getOrNull()


                if (asset == null) {

                    withContext(Dispatchers.Main) {

                        callback.onError(
                            "Readium returned an empty EPUB asset."
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
                            "Readium could not open the EPUB:\n$errorMessage"
                        )
                    }

                    return@launch
                }


                val publication =
                    publicationResult.getOrNull()


                if (publication == null) {

                    withContext(Dispatchers.Main) {

                        callback.onError(
                            "Readium returned an empty publication."
                        )
                    }

                    return@launch
                }


                val navigatorFactory =
                    EpubNavigatorFactory(
                        publication =
                            publication
                    )


                val fragmentFactory =
                    navigatorFactory.createFragmentFactory(
                        initialLocator = null,
                        configuration =
                            EpubNavigatorFragment.Configuration(
                                selectionActionModeCallback =
                                    object : ActionMode.Callback {

                                        override fun onCreateActionMode(
                                            mode: ActionMode,
                                            menu: Menu
                                        ): Boolean {

                                            menu.add(
                                                "Bookmark"
                                            )

                                            return true
                                        }


                                        override fun onPrepareActionMode(
                                            mode: ActionMode,
                                            menu: Menu
                                        ): Boolean {

                                            return false
                                        }


                                        override fun onActionItemClicked(
                                            mode: ActionMode,
                                            item: MenuItem
                                        ): Boolean {

                                            if (
                                                item.title
                                                    .toString()
                                                    .equals(
                                                        "Bookmark",
                                                        ignoreCase = true
                                                    )
                                            ) {

                                                callback.onBookmarkRequested()

                                                return true
                                            }

                                            return false
                                        }


                                        override fun onDestroyActionMode(
                                            mode: ActionMode
                                        ) {
                                        }
                                    }
                            )
                    )


                withContext(Dispatchers.Main) {

                    callback.onSuccess(
                        fragmentFactory
                    )
                }


            } catch (e: Exception) {

                withContext(Dispatchers.Main) {

                    callback.onError(
                        e.message
                            ?: "Unknown error while opening EPUB."
                    )
                }
            }
        }
    }

    fun getCurrentSelection(
        navigator: EpubNavigatorFragment,
        callback: SelectionCallback
    ) {

        CoroutineScope(Dispatchers.Main).launch {

            try {

                val selection =
                    navigator.currentSelection()

                if (selection == null) {

                    callback.onSelection(
                        null,
                        null,
                        null
                    )

                    return@launch
                }

                val locator =
                    selection.locator

                val selectedText =
                    locator.text.highlight

                val locatorJson =
                    locator.toJSON().toString()

                val chapterTitle =
                    locator.title

                callback.onSelection(
                    selectedText,
                    locatorJson,
                    chapterTitle
                )

            } catch (e: Exception) {

                e.printStackTrace()

                Toast.makeText(
                    appContext,
                    "Selection error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                callback.onSelection(
                    null,
                    null,
                    null
                )
            }
        }
    }

    fun extractCover(
        file: File,
        outputFile: File,
        callback: CoverCallback
    ) {

        scope.launch {

            try {

                if (!file.exists()) {

                    withContext(Dispatchers.Main) {

                        callback.onError(
                            "EPUB file does not exist:\n${file.absolutePath}"
                        )
                    }

                    return@launch
                }


                // Give the EPUB to Readium.

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
                            "Readium could not load the EPUB:\n$errorMessage"
                        )
                    }

                    return@launch
                }


                val asset =
                    assetResult.getOrNull()


                if (asset == null) {

                    withContext(Dispatchers.Main) {

                        callback.onError(
                            "Readium returned an empty EPUB asset."
                        )
                    }

                    return@launch
                }


                // Open the EPUB as a Publication.

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
                            "Readium could not open the EPUB:\n$errorMessage"
                        )
                    }

                    return@launch
                }


                val publication =
                    publicationResult.getOrNull()


                if (publication == null) {

                    withContext(Dispatchers.Main) {

                        callback.onError(
                            "Readium returned an empty publication."
                        )
                    }

                    return@launch
                }


                // Ask Readium for a cover.
                //
                // 600 x 900 is plenty for our library
                // thumbnails and avoids storing huge images.

                val cover =
                    publication.cover()


                if (cover == null) {

                    withContext(Dispatchers.Main) {

                        callback.onError(
                            "This EPUB does not provide a usable cover."
                        )
                    }

                    return@launch
                }


                // Make sure the covers directory exists.

                outputFile.parentFile?.mkdirs()


                // Save the Bitmap as JPEG.

                FileOutputStream(
                    outputFile
                ).use { output ->

                    cover.compress(
                        Bitmap.CompressFormat.JPEG,
                        90,
                        output
                    )
                }


                // Close the Readium publication.

                publication.close()


                withContext(Dispatchers.Main) {

                    callback.onSuccess(
                        outputFile
                    )
                }


            } catch (e: Exception) {

                e.printStackTrace()

                withContext(Dispatchers.Main) {

                    callback.onError(
                        e.message
                            ?: "Unknown error while extracting EPUB cover."
                    )
                }
            }
        }
    }
}