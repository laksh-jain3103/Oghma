package com.reader.oghma.reader

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Google ML Kit, running on the phone: translation between ~59 languages, with downloadable
 * language packs (about 30 MB each), plus automatic language detection.
 *
 * Once a pack is downloaded, translating needs no internet.
 */
class MlKitTranslationEngine : TranslationEngine {

    private val modelManager = RemoteModelManager.getInstance()

    private var identifier: LanguageIdentifier? = null


    override fun supportedLanguages(): List<String> = TranslateLanguage.getAllLanguages()


    override fun detectLanguage(text: String, callback: (String?) -> Unit) {

        val client = identifier ?: LanguageIdentification.getClient().also { identifier = it }

        client.identifyLanguage(text)
            .addOnSuccessListener { code -> callback(code) }
            .addOnFailureListener { callback(null) }
    }


    override fun downloadedLanguages(callback: (Set<String>?) -> Unit) {

        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models -> callback(models.map { it.language }.toSet()) }
            .addOnFailureListener { callback(null) }
    }


    override fun download(language: String, callback: (Boolean, String?) -> Unit) {

        if (EasterEgg.blocks(language)) {

            callback(false, EasterEgg.MESSAGE)

            return
        }

        val model = TranslateRemoteModel.Builder(language).build()

        modelManager.download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener { callback(true, null) }
            .addOnFailureListener { callback(false, describe(it)) }
    }


    override fun delete(language: String, callback: (Boolean) -> Unit) {

        val model = TranslateRemoteModel.Builder(language).build()

        modelManager.deleteDownloadedModel(model)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }


    override fun translate(
        text: String,
        source: String,
        target: String,
        callback: (String?, String?) -> Unit
    ) {

        // translate() would quietly download a missing pack, so Hebrew is only allowed if it is
        // already on the device.
        val blocked = listOf(source, target).firstOrNull { EasterEgg.blocks(it) }

        if (blocked != null) {

            val model = TranslateRemoteModel.Builder(blocked).build()

            modelManager.isModelDownloaded(model)
                .addOnSuccessListener { present ->

                    if (present) {
                        translateNow(text, source, target, callback)
                    } else {
                        callback(null, EasterEgg.MESSAGE)
                    }
                }
                .addOnFailureListener { callback(null, EasterEgg.MESSAGE) }

            return
        }

        translateNow(text, source, target, callback)
    }


    private fun translateNow(
        text: String,
        source: String,
        target: String,
        callback: (String?, String?) -> Unit
    ) {

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()

        val translator = Translation.getClient(options)

        // Fetches whatever packs are still missing, then translates on the device.
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {

                translator.translate(text)
                    .addOnSuccessListener { result ->
                        callback(result, null)
                        translator.close()
                    }
                    .addOnFailureListener {
                        callback(null, describe(it))
                        translator.close()
                    }
            }
            .addOnFailureListener {
                callback(null, describe(it))
                translator.close()
            }
    }


    override fun close() {

        try {
            identifier?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        identifier = null
    }


    private fun describe(e: Exception): String =
        e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
}
