/*
 * Copyright 2020 BQ
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.masmovil.poeditor.gradle

import com.masmovil.poeditor.gradle.network.api.PoEditorApi
import com.masmovil.poeditor.gradle.utils.DateJsonAdapter
import com.bq.poeditor.gradle.network.PoEditorApiControllerImpl
import com.masmovil.poeditor.gradle.utils.TABLET_REGEX_STRING
import com.masmovil.poeditor.gradle.ktx.downloadUrlToString
import com.masmovil.poeditor.gradle.utils.logger
import com.masmovil.poeditor.gradle.xml.AndroidXmlWriter
import com.masmovil.poeditor.gradle.xml.XmlPostProcessor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.*

/**
 * Main class that does the XML download, parsing and saving from PoEditor files.
 */
object PoEditorStringsImporter {
    private const val POEDITOR_API_URL = "https://poeditor.com/api/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .add(Date::class.java, DateJsonAdapter())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
            override fun log(message: String) {
                logger.debug(message)
            }
        })
            .setLevel(HttpLoggingInterceptor.Level.HEADERS))
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(POEDITOR_API_URL.toHttpUrl())
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val poEditorApi: PoEditorApi = retrofit.create(PoEditorApi::class.java)

    private val xmlPostProcessor = XmlPostProcessor()

    private val xmlWriter = AndroidXmlWriter()

    fun importPoEditorStrings(apiToken: String,
                              projectId: Int,
                              defaultLang: String,
                              resDirPath: String) {
        try {
            val poEditorApiController = PoEditorApiControllerImpl(apiToken, poEditorApi)

            // Retrieve available languages from PoEditor
            logger.lifecycle("Retrieving project languages...")
            val projectLanguages = poEditorApiController.getProjectLanguages(projectId)

            // Iterate over every available language
            logger.lifecycle("Available languages: [${projectLanguages.joinToString(", ") { it.code }}]")
            projectLanguages.forEach { languageData ->
                val languageCode = languageData.code

                // Retrieve translation file URL for the given language and for the "android_strings" type
                logger.lifecycle("Retrieving translation file URL for language code: $languageCode")
                val translationFileUrl = poEditorApiController.getTranslationFileUrl(
                    projectId = projectId,
                    code = languageCode,
                    type = "android_strings")

                // Download translation File to in-memory string
                logger.debug("Downloading file from URL: $translationFileUrl")
                val translationFile = okHttpClient.downloadUrlToString(translationFileUrl)

                // Extract final files from downloaded translation XML
                val postProcessedXmlDocumentMap =
                    xmlPostProcessor.postProcessTranslationXml(
                        translationFile, listOf(TABLET_REGEX_STRING))

                xmlWriter.saveXml(
                    resDirPath, postProcessedXmlDocumentMap, defaultLang, languageCode)
            }
        } catch (e: Exception) {
            logger.error("An error happened when retrieving strings from project. " +
                         "Please review the plug-in's input parameters and try again")
            throw e
        }
    }
}