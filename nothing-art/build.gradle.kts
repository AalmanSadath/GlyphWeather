import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Nothing's weather animations, converted for the Glyph Matrix. Not covered by the GPL; see
// NOTICE.md. Everything owned by Nothing lives in src/main/resources/nothing-art/.
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

dependencies {
    api(project(":icons"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
