import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasReleaseSigningConfig = keystorePropertiesFile.exists()

if (hasReleaseSigningConfig) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach { propertyName ->
        check(!keystoreProperties.getProperty(propertyName).isNullOrBlank()) {
            "Missing $propertyName in ${keystorePropertiesFile.name}"
        }
    }
}

android {
    namespace = "com.foliore.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.foliore.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    bundle { language { enableSplit = false } }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.recyclerview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.coil.compose)
    implementation(libs.android.svg)
    implementation(libs.google.play.services.auth)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.haze)
    implementation(libs.haze.blur)

    testImplementation(kotlin("test-junit"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Do not silently produce an unsigned upload artifact.
tasks.matching { it.name == "bundleRelease" }.configureEach {
    inputs.property("releaseSigningConfigured", hasReleaseSigningConfig)
    doFirst {
        check(inputs.properties["releaseSigningConfigured"] == true) { "Configure keystore.properties with the upload key before building the Play Store bundle." }
    }
}

val publicationPropertiesFile = rootProject.file("publication.properties")
val publicationProperties = Properties().apply {
    if (publicationPropertiesFile.exists()) publicationPropertiesFile.inputStream().use(::load)
}
val publicationReady = listOf("privacyPublished", "reportingEndpointVerified", "developerIdentityConfirmed", "supportMailboxVerified")
    .all { publicationProperties.getProperty(it) == "true" }
val validatePlayRelease by tasks.registering {
    inputs.property("publicationReady", publicationReady)
    doLast {
        check(inputs.properties["publicationReady"] == true) { "Finish publication.properties checks: public privacy policy, developer identity, support mailbox and working in-app reports." }
    }
}
tasks.matching { it.name == "bundleRelease" }.configureEach { dependsOn(validatePlayRelease) }
