import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionCode = providers.gradleProperty("appVersionCode").map(String::toInt).orElse(1)
val appVersionName = providers.gradleProperty("appVersionName").orElse("0.1.0")
val releaseKeystorePath = providers.gradleProperty("releaseKeystorePath").orNull
val releaseStorePassword = providers.gradleProperty("releaseStorePassword").orNull
val releaseKeyAlias = providers.gradleProperty("releaseKeyAlias").orNull
val releaseKeyPassword = providers.gradleProperty("releaseKeyPassword").orNull
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.tajertrack.phone"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tajertrack.phone"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode.get()
        versionName = appVersionName.get()

        val configuredBaseUrl = providers.gradleProperty("tajertrackBaseUrl")
            .orElse(providers.environmentVariable("TAJERTRACK_BASE_URL"))
            .orElse("https://tajertrack.vercel.app")
            .get()
        val escapedBaseUrl = configuredBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "TAJERTRACK_BASE_URL", "\"$escapedBaseUrl\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}


val readexFontBase64 = layout.projectDirectory.file("fonts/ReadexPro.ttf.base64")
val generatedReadexResources = layout.buildDirectory.dir("generated/readex-font/res")

val generateReadexFont by tasks.registering {
    inputs.file(readexFontBase64)
    outputs.dir(generatedReadexResources)

    doLast {
        val fontDirectory = generatedReadexResources.get().dir("font").asFile
        fontDirectory.mkdirs()
        val fontBytes = Base64.getMimeDecoder().decode(readexFontBase64.asFile.readText())
        fontDirectory.resolve("readex_pro.ttf").writeBytes(fontBytes)
    }
}

android.sourceSets.getByName("main").res.srcDir(generatedReadexResources)

tasks.named("preBuild").configure {
    dependsOn(generateReadexFont)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
