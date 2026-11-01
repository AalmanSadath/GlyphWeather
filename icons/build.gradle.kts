import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Desktop-only renderer that turns every icon into GIF/PNG previews for review.
// Kept in its own source set so java.awt/javax.imageio never reach the Android app.
val preview: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

dependencies {
    "previewImplementation"(kotlin("stdlib"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("preview") {
    group = "verification"
    description = "Renders every weather icon to build/icon-preview for visual review."
    classpath = preview.runtimeClasspath
    mainClass.set("dev.aalman.glyph.weather.icons.preview.PreviewKt")
    val out = layout.buildDirectory.dir("icon-preview")
    args(out.get().asFile.absolutePath)
    outputs.dir(out)
}

tasks.register<JavaExec>("appIcons") {
    group = "build"
    description = "Regenerates the app's toy preview and launcher icon drawables from the clear-day icon."
    classpath = preview.runtimeClasspath
    mainClass.set("dev.aalman.glyph.weather.icons.preview.AppIconsKt")
    args(rootProject.file("app/src/main/res/drawable").absolutePath)
}
