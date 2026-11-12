plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.miralay.duzceanmastudio"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.miralay.duzceanmastudio"
        minSdk = 29
        targetSdk = 35
        val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = 600000 + buildNumber
        versionName = "0.6.$buildNumber"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
dependencies {
    implementation("androidx.media3:media3-transformer:1.6.1")
    implementation("androidx.media3:media3-effect:1.6.1")
    implementation("androidx.media3:media3-common:1.6.1")
}
