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
import com.reader.oghma.data.entity.Bookmark
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression

class EpubReaderBridge(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    // Used for UI-thread work (selection, decorations, progress). Cancelled in close().
    private val uiScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main
        )

    // The publication currently shown by the reader. Closed in close().
    private var openPublication: Publication? = null

    /** The book currently open in the reader (needed for text-to-speech). */
    val currentPublication: Publication?
        get() = openPublication

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


    interface Callback {
        fun onSuccess(factory: FragmentFactory)
        fun onError(message: String)

        fun onBookmarkRequested()

        fun onCopyRequested()

        fun onPlayRequested()

        fun onDictionaryRequested()

        fun onTranslateRequested()
    }

    interface ProgressListener {
        fun onProgress(
            locatorJson: String,
            percentage: Float
        )
    }

    interface LookupListener {

        /**
         * The selected text and where it is on screen. [top] and [bottom] are pixels inside the
         * reader view, or NaN if Readium could not tell.
         */
        fun onLookup(text: String, locatorJson: String?, top: Float, bottom: Float)
    }

    interface LocatorListener {

        /** The locator (as JSON) that was jumped to, or null if the position could not be found. */
        fun onLocator(locatorJson: String?)
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


    /**
     * @param initialLocatorJson Where to open the book (a saved highlight or the last reading
     * position). Null opens at the start.
     * @param initialFontSize Text scale, 1.0 = 100%.
     */
    @OptIn(ExperimentalReadiumApi::class)
    fun openEpub(
        file: File,
        initialLocatorJson: String?,
        initialFontSize: Double,
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


                openPublication?.close()
                openPublication = publication

                val initialLocator =
                    parseLocator(initialLocatorJson)

                val navigatorFactory =
                    EpubNavigatorFactory(
                        publication =
                            publication
                    )


                val fragmentFactory =
                    navigatorFactory.createFragmentFactory(
                        initialLocator = initialLocator,
                        initialPreferences = EpubPreferences(
                            fontSize = initialFontSize
                        ),
                        configuration =
                            EpubNavigatorFragment.Configuration(
                                selectionActionModeCallback =
                                    object : ActionMode.Callback {

                                        override fun onCreateActionMode(
                                            mode: ActionMode,
                                            menu: Menu
                                        ): Boolean {

                                            // Readium replaces the system selection menu with
                                            // this one, so Copy has to be provided here too.
                                            menu.add(
                                                MENU_HIGHLIGHT
                                            )

                                            menu.add(
                                                MENU_COPY
                                            )

                                            menu.add(
                                                MENU_DICTIONARY
                                            )

                                            menu.add(
                                                MENU_TRANSLATE
                                            )

                                            // Read aloud, starting at the selected text.
                                            menu.add(
                                                MENU_PLAY
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

                                            val title =
                                                item.title.toString()

                                            if (
                                                title.equals(
                                                    MENU_HIGHLIGHT,
                                                    ignoreCase = true
                                                )
                                            ) {

                                                callback.onBookmarkRequested()

                                                mode.finish()

                                                return true
                                            }

                                            if (
                                                title.equals(
                                                    MENU_COPY,
                                                    ignoreCase = true
                                                )
                                            ) {

                                                callback.onCopyRequested()

                                                mode.finish()

                                                return true
                                            }

                                            if (
                                                title.equals(
                                                    MENU_DICTIONARY,
                                                    ignoreCase = true
                                                )
                                            ) {

                                                callback.onDictionaryRequested()

                                                mode.finish()

                                                return true
                                            }

                                            if (
                                                title.equals(
                                                    MENU_TRANSLATE,
                                                    ignoreCase = true
                                                )
                                            ) {

                                                callback.onTranslateRequested()

                                                mode.finish()

                                                return true
                                            }

                                            if (
                                                title.equals(
                                                    MENU_PLAY,
                                                    ignoreCase = true
                                                )
                                            ) {

                                                callback.onPlayRequested()

                                                mode.finish()

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

        uiScope.launch {

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

    // ---------------------------------------------------------------------
    // Progress, highlights, cleanup
    // ---------------------------------------------------------------------

    /**
     * Reports the reading position every time it changes. The listener is called on the
     * main thread; the caller is expected to throttle database writes.
     */
    fun observeProgress(
        navigator: EpubNavigatorFragment,
        listener: ProgressListener
    ) {

        uiScope.launch {

            navigator.currentLocator.collect { locator ->

                val percentage =
                    ((locator.locations.totalProgression ?: 0.0) * 100.0)
                        .toFloat()

                listener.onProgress(
                    locator.toJSON().toString(),
                    percentage
                )
            }
        }
    }

    /**
     * Draws every saved highlight of the book. Safe to call again after adding or
     * removing one: Readium diffs against what is already on screen.
     */
    fun applyHighlights(
        navigator: EpubNavigatorFragment,
        bookmarks: List<Bookmark>
    ) {

        uiScope.launch {

            try {

                val decorations =
                    bookmarks.mapNotNull { bookmark ->

                        val locator =
                            parseLocator(bookmark.locatorJson)
                                ?: return@mapNotNull null

                        Decoration(
                            id = "bookmark-${bookmark.bookmarkId}",
                            locator = locator,
                            style = Decoration.Style.Highlight(
                                tint = HighlightColors.resolve(
                                    bookmark.color
                                )
                            )
                        )
                    }

                navigator.applyDecorations(
                    decorations,
                    HIGHLIGHT_GROUP
                )

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }
    }

    /** The selected text plus where it sits on screen, for the dictionary popup. */
    fun getSelectionForLookup(
        navigator: EpubNavigatorFragment,
        listener: LookupListener
    ) {

        uiScope.launch {

            try {

                val selection = navigator.currentSelection()

                if (selection == null) {

                    listener.onLookup("", null, Float.NaN, Float.NaN)

                    return@launch
                }

                val rect = selection.rect

                listener.onLookup(
                    selection.locator.text.highlight ?: "",
                    selection.locator.toJSON().toString(),
                    rect?.top ?: Float.NaN,
                    rect?.bottom ?: Float.NaN
                )

            } catch (e: Exception) {

                e.printStackTrace()

                listener.onLookup("", null, Float.NaN, Float.NaN)
            }
        }
    }

    /** Underlines the word being looked up, so it stays easy to spot behind the popup. */
    fun showLookupMark(
        navigator: EpubNavigatorFragment,
        locatorJson: String?
    ) {

        val locator = parseLocator(locatorJson) ?: return

        uiScope.launch {

            try {

                navigator.applyDecorations(
                    listOf(
                        Decoration(
                            id = "lookup",
                            locator = locator,
                            style = Decoration.Style.Underline(
                                tint = LOOKUP_COLOR
                            )
                        )
                    ),
                    LOOKUP_GROUP
                )

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }
    }

    fun clearLookupMark(navigator: EpubNavigatorFragment) {

        uiScope.launch {

            try {

                navigator.applyDecorations(emptyList(), LOOKUP_GROUP)

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }
    }

    /**
     * Jumps to a point in the book given as 0.0 (start) .. 1.0 (end).
     * [listener] receives the resulting locator so text-to-speech can jump there too.
     */
    fun seekToProgression(
        navigator: EpubNavigatorFragment,
        progression: Double,
        listener: LocatorListener
    ) {

        val publication = openPublication

        if (publication == null) {
            listener.onLocator(null)
            return
        }

        uiScope.launch {

            val locator =
                try {
                    publication.locateProgression(progression.coerceIn(0.0, 1.0))
                } catch (e: Exception) {
                    null
                }

            if (locator != null) {

                try {
                    navigator.go(locator, animated = false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            listener.onLocator(locator?.toJSON()?.toString())
        }
    }

    /** Changes the text size of the open book (1.0 = 100%). Readium re-flows the pages. */
    @OptIn(ExperimentalReadiumApi::class)
    fun setFontSize(
        navigator: EpubNavigatorFragment,
        size: Double
    ) {

        navigator.submitPreferences(
            EpubPreferences(
                fontSize = size
            )
        )
    }

    fun clearSelection(
        navigator: EpubNavigatorFragment
    ) {

        navigator.clearSelection()
    }

    /** Returns null for anything that is not a valid Readium locator (e.g. PDF highlights). */
    private fun parseLocator(
        json: String?
    ): Locator? {

        if (json.isNullOrBlank()) {
            return null
        }

        return try {

            Locator.fromJSON(JSONObject(json))

        } catch (e: Exception) {

            null
        }
    }

    /** Releases the publication and stops every coroutine started by this bridge. */
    fun close() {

        uiScope.cancel()
        scope.cancel()

        try {
            openPublication?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        openPublication = null
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

    private companion object {

        const val MENU_HIGHLIGHT = "Highlight"

        const val MENU_COPY = "Copy"

        const val MENU_DICTIONARY = "Dictionary"

        const val MENU_TRANSLATE = "Translate"

        const val MENU_PLAY = "Play"

        const val LOOKUP_GROUP = "lookup"

        // Warm orange underline for the word being looked up (user highlights are yellow,
        // the word being read aloud is blue).
        const val LOOKUP_COLOR = 0xFFFAB387.toInt()

        const val HIGHLIGHT_GROUP = "user-highlights"
    }
}
