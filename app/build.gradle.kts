import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.aalman.glyph.weather"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.aalman.glyph.weather"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// Nothing's Glyph Matrix SDK can't be committed to this repository (GDK EULA §2.1(c)), so it is
// fetched from Nothing's own repository, pinned to a commit and checked against a known hash.
val glyphSdkCommit = "999b1143a6bff0dd79db11d21b92c15c56a346b1"
val glyphSdkSha256 = "be00ee9cd7115f6b11984c6e31fe98e298fb726940d1555063610685ef3bbf29"
val glyphSdk = layout.projectDirectory.file("libs/glyph-matrix-sdk-2.0.aar")

val downloadGlyphSdk by tasks.registering {
    description = "Downloads Nothing's Glyph Matrix SDK into app/libs."
    val target = glyphSdk.asFile
    outputs.file(target)
    onlyIf { !target.exists() || sha256(target) != glyphSdkSha256 }
    doLast {
        val url = "https://raw.githubusercontent.com/Nothing-Developer-Programme/" +
            "GlyphMatrix-Developer-Kit/$glyphSdkCommit/glyph-matrix-sdk-2.0.aar"
        target.parentFile.mkdirs()
        URI(url).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        val actual = sha256(target)
        if (actual != glyphSdkSha256) {
            target.delete()
            throw GradleException("Glyph SDK hash mismatch: expected $glyphSdkSha256, got $actual")
        }
    }
}

fun sha256(file: File): String =
    MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

tasks.named("preBuild") { dependsOn(downloadGlyphSdk) }

dependencies {
    implementation(files(glyphSdk))
    implementation(project(":weather"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
}
