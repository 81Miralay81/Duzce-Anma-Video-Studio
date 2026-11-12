plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    val signingStorePath = System.getenv("KANATIX_SIGNING_STORE_FILE")
    val stableSigningEnabled = !signingStorePath.isNullOrBlank()
    namespace = "com.miralay.duzceanmastudio"
    compileSdk = 35
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "com.miralay.duzceanmastudio"
        minSdk = 29
        targetSdk = 35
        val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = 600000 + buildNumber
        versionName = "0.6.$buildNumber"
        buildConfigField("boolean", "KANATIX_STABLE_SIGNING", stableSigningEnabled.toString())
    }
    if (stableSigningEnabled) {
        signingConfigs.create("kanatix") {
            storeFile = file(signingStorePath!!)
            storePassword = System.getenv("KANATIX_SIGNING_PASSWORD")
            keyAlias = "kanatix"
            keyPassword = System.getenv("KANATIX_SIGNING_PASSWORD")
        }
    }
    buildTypes.getByName("debug") {
        if (stableSigningEnabled) signingConfig = signingConfigs.getByName("kanatix")
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
