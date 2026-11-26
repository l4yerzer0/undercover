import java.util.Properties

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("io.ktor:ktor-client-core:2.3.9")
    implementation("io.ktor:ktor-client-cio:2.3.9")
    implementation("org.json:json:20240303")
}

val versionProps = Properties().apply {
    val propFile = file("../android/version.properties").takeIf { it.exists() } ?: rootProject.file("version.properties")
    if (propFile.exists()) {
        load(propFile.inputStream())
    }
}
val versionName: String = versionProps.getProperty("VERSION_NAME", "0.10")
val semverVersion = if (versionName.count { ch: Char -> ch == '.' } == 1) "$versionName.0" else versionName

compose.desktop {
    application {
        mainClass = "com.undercover.vpn.desktop.MainKt"
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "undercover"
            packageVersion = semverVersion
            macOS {
                packageVersion = "1.0.0"
                dmgPackageVersion = "1.0.0"
            }
            description = "Undercover VPN Client"
            copyright = "© 2026 Undercover VPN"
            vendor = "Undercover"
        }
    }
}
