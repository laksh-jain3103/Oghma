import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { 
        localProperties.load(it) 
    }
}

val googleBooksApiKey = localProperties.getProperty("google.books.api.key") ?: ""
val googleBooksCert = localProperties.getProperty("google.books.cert") ?: ""

android {
    namespace = "com.reader.oghma"
    compileSdk {
        version = release(37)
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.reader.oghma"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
;
        buildConfigField("String", "GOOGLE_BOOKS_API_KEY", "\"${googleBooksApiKey}\"")
        buildConfigField("String", "GOOGLE_BOOKS_CERT", "\"${googleBooksCert}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.material)
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // Readium
    implementation("org.readium.kotlin-toolkit:readium-shared:3.3.0")
    implementation("org.readium.kotlin-toolkit:readium-streamer:3.3.0")
    implementation("org.readium.kotlin-toolkit:readium-navigator:3.3.0")
    // Read aloud for EPUB (experimental Readium module, built on Media3)
    implementation("org.readium.kotlin-toolkit:readium-navigator-media-tts:3.3.0")
    implementation("androidx.media3:media3-common:1.10.0")
    // PDFs are rendered by PDF.js (see assets/pdfjs), not by Readium's PDFium adapter,
    // because PDFium only draws bitmaps and cannot select or highlight text.

    // Java 11+ API desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    // Google authentication
    implementation("com.google.android.gms:play-services-auth:20.4.0")

    // Google sign-in button
    implementation("com.github.TutorialsAndroid:GButton:v1.0.19")

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    //
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
}
