plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ****** BEGIN ADDITION ******
// This block should be at the top level of this file,
// NOT inside the android { ... } or plugins { ... } block.
// In a suitable .kt file (e.g., utils.kt or extensions.kt)
typealias KotlinProperties = java.util.Properties

// Then in your code:
val localProperties = KotlinProperties()
// Make sure your local.properties file is in the ROOT of your project,
// not in the app/ directory.
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    try {
        localPropertiesFile.inputStream().use { input ->
            localProperties.load(input)
        }
    } catch (e: java.io.IOException) {
        // Handle error reading the file if necessary, or just log it
        println("Warning: Could not read local.properties: ${e.message}")
    }
}
// ****** END ADDITION ******

android {
    namespace = "com.example.elderlycareapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.elderlycareapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ****** BEGIN ADDITION ******
        // Add this line to define the BuildConfig field
        // It retrieves the value from the localProperties loaded above.
        // If the property is not found, it defaults to an empty string.
        val geminiApiKey: String? = localProperties.getProperty("GEMINI_API_KEY")
        if (!geminiApiKey.isNullOrEmpty()) {
            buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        } else {
            buildConfigField("String", "GEMINI_API_KEY", "\"YOUR_ACTUAL_GEMINI_API_KEY\"")
        }
        // ****** END ADDITION ******
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // You might also want to define buildConfigField for release
            // if you have a different way of handling API keys for release builds
            // e.g., from CI environment variables.
            // buildConfigField(
            //    "String",
            //    "GEMINI_API_KEY",
            //    "\"${System.getenv("PROD_GEMINI_API_KEY") ?: localProperties.getProperty("GEMINI_API_KEY") ?: ""}\""
            // )
        }
        // It's good practice to also define it for debug, even if it's the same
        // or if you want to explicitly override it for debug.
        // If not defined for a build type, it inherits from defaultConfig.
        debug {
            // Inherits GEMINI_API_KEY from defaultConfig by default.
            // If you needed a specific one for debug:
            // buildConfigField(
            //    "String",
            //    "GEMINI_API_KEY",
            //    "\"${localProperties.getProperty("DEBUG_GEMINI_API_KEY") ?: ""}\""
            // )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        // ****** ADD THIS IF NOT ALREADY PRESENT, OR IF YOU SEE ERRORS RELATED TO BUILDCONFIG NOT BEING GENERATED ******
        buildConfig = true
    }
}

dependencies {
    // ... your existing dependencies ...
    implementation(libs.androidx.core.ktx)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.libraries.places:places:3.4.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation(libs.androidx.activity.compose)
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // Gemini AI dependencies
    implementation("com.google.ai.client.generativeai:generativeai:0.7.0")
    
    // Coroutines for asynchronous operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("androidx.camera:camera-extensions:1.3.1")
    
    // ML Kit for real pose detection
    implementation("com.google.mlkit:pose-detection:18.0.0-beta3")
    

    

}

