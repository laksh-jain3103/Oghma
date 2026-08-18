package com.reader.oghma.reader

import android.content.Context
import android.graphics.Bitmap
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem

import androidx.fragment.app.FragmentFactory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.json.JSONObject

import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

import java.io.File
import java.io.FileOutputStream


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
                    pdfFactory = null
                )
        )


    // =========================================================
    // Open callback
    // =========================================================

    interface Callback {

        fun onSuccess(
            factory: FragmentFactory
        )

        fun onError(
            message: String
        )

        fun onBookmarkRequested()
    }


    // =========================================================
    // Selection callback
    // =========================================================

    interface SelectionCallback {

        fun onSelection(
            selectedText: String?,
            locatorJson: String?,
            chapterTitle: String?
        )
    }


    // =========================================================
    // Progress callback
    // =========================================================

    interface LocatorCallback {

        fun onLocator(
            locatorJson: String?
        )
    }


    // =========================================================
    // Cover callback
    // =========================================================

    interface CoverCallback {

        fun onSuccess(
            coverFile: File
        )

        fun onError(
            message: String
        )
    }


    // =========================================================
    // Open EPUB
    // =========================================================

    fun openEpub(
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

                    withContext(Dispatchers.Main) {

                        callback.onError(
                            "EPUB file does not exist:\n${file.absolutePath}"
                        )
                    }

                    return@launch
                }


                // -------------------------------------------------
                // Retrieve asset
                // -------------------------------------------------

                val assetResult =
                    assetRetriever.retrieve(
                        file
                    )


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


                // -------------------------------------------------
                // Open publication
                // -------------------------------------------------

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


                // -------------------------------------------------
                // Convert saved JSON into Readium Locator
                // -------------------------------------------------

                val initialLocator =
                    parseLocator(
                        initialLocatorJson
                    )


                // -------------------------------------------------
                // Create navigator
                // -------------------------------------------------

                val navigatorFactory =
                    EpubNavigatorFactory(
                        publication =
                            publication
                    )


                // -------------------------------------------------
                // Initial reading preferences
                //
                // horizontal = paginated
                // vertical   = scrolling
                // -------------------------------------------------

                val initialPreferences =
                    EpubPreferences(
                        scroll =
                            !horizontalReadingMode
                    )


                val fragmentFactory =
                    navigatorFactory.createFragmentFactory(

                        initialLocator =
                            initialLocator,

                        initialPreferences =
                            initialPreferences,

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


                // -------------------------------------------------
                // Return factory
                // -------------------------------------------------

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


    // =========================================================
    // Change reading mode on an existing EPUB navigator
    //
    // horizontal = false -> scrolling
    // horizontal = true  -> paginated
    // =========================================================

    fun setReadingMode(
        navigator: EpubNavigatorFragment,
        horizontalReadingMode: Boolean
    ) {

        val preferences =
            EpubPreferences(
                scroll =
                    !horizontalReadingMode
            )


        navigator.submitPreferences(
            preferences
        )
    }


    // =========================================================
    // Parse Locator JSON
    // =========================================================

    private fun parseLocator(
        locatorJson: String?
    ): Locator? {

        if (
            locatorJson == null
            || locatorJson.trim().isEmpty()
        ) {

            return null
        }


        return try {

            Locator.fromJSON(
                JSONObject(
                    locatorJson
                )
            )

        } catch (e: Exception) {

            e.printStackTrace()

            null
        }
    }


    // =========================================================
    // Get current reading location
    // =========================================================

    fun getCurrentLocator(
        navigator: EpubNavigatorFragment,
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

            } catch (e: Exception) {

                e.printStackTrace()


                callback.onLocator(
                    null
                )
            }
        }
    }


    // =========================================================
    // Get current text selection
    // =========================================================

    fun getCurrentSelection(
        navigator: EpubNavigatorFragment,
        callback: SelectionCallback
    ) {

        scope.launch {

            try {

                val selection =
                    navigator.currentSelection()


                if (selection == null) {

                    withContext(
                        Dispatchers.Main
                    ) {

                        callback.onSelection(
                            null,
                            null,
                            null
                        )
                    }

                    return@launch
                }


                val locator =
                    selection.locator


                val selectedText =
                    locator.text.highlight


                val locatorJson =
                    locator.toJSON()
                        .toString()


                val chapterTitle =
                    locator.title


                withContext(
                    Dispatchers.Main
                ) {

                    callback.onSelection(
                        selectedText,
                        locatorJson,
                        chapterTitle
                    )
                }


            } catch (e: Exception) {

                e.printStackTrace()


                withContext(
                    Dispatchers.Main
                ) {

                    callback.onSelection(
                        null,
                        null,
                        null
                    )
                }
            }
        }
    }


    // =========================================================
    // Extract EPUB cover
    // =========================================================

    fun extractCover(
        file: File,
        outputFile: File,
        callback: CoverCallback
    ) {

        scope.launch {

            try {

                if (!file.exists()) {

                    withContext(
                        Dispatchers.Main
                    ) {

                        callback.onError(
                            "EPUB file does not exist:\n${file.absolutePath}"
                        )
                    }

                    return@launch
                }


                // -------------------------------------------------
                // Retrieve EPUB
                // -------------------------------------------------

                val assetResult =
                    assetRetriever.retrieve(
                        file
                    )


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


                    withContext(
                        Dispatchers.Main
                    ) {

                        callback.onError(
                            "Readium could not load the EPUB:\n$errorMessage"
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
                            "Readium returned an empty EPUB asset."
                        )
                    }

                    return@launch
                }


                // -------------------------------------------------
                // Open EPUB
                // -------------------------------------------------

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


                    withContext(
                        Dispatchers.Main
                    ) {

                        callback.onError(
                            "Readium could not open the EPUB:\n$errorMessage"
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
                            "Readium returned an empty publication."
                        )
                    }

                    return@launch
                }


                // -------------------------------------------------
                // Get cover
                // -------------------------------------------------

                val cover =
                    publication.cover()


                if (cover == null) {

                    withContext(
                        Dispatchers.Main
                    ) {

                        callback.onError(
                            "This EPUB does not provide a usable cover."
                        )
                    }

                    return@launch
                }


                outputFile.parentFile?.mkdirs()


                FileOutputStream(
                    outputFile
                ).use { output ->

                    cover.compress(
                        Bitmap.CompressFormat.JPEG,
                        90,
                        output
                    )
                }


                publication.close()


                withContext(
                    Dispatchers.Main
                ) {

                    callback.onSuccess(
                        outputFile
                    )
                }


            } catch (e: Exception) {

                e.printStackTrace()


                withContext(
                    Dispatchers.Main
                ) {

                    callback.onError(
                        e.message
                            ?: "Unknown error while extracting EPUB cover."
                    )
                }
            }
        }
    }
}