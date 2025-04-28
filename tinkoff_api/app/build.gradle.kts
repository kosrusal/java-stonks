plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.myapplication.ishimoku"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myapplication.ishimoku"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.mpandroidchart)

    // Tinkoff Invest API
    implementation(libs.java.sdk.core)

    // Kotlin Coroutines
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    // Lifecycle компоненты
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.database)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("ru.tinkoff.piapi:java-sdk-core:1.0")
    implementation("com.google.firebase:firebase-database:20.0.4")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("org.threeten:threetenbp:1.6.8")

    // TLS/SSL фиксы для Tinkoff API
    implementation("org.conscrypt:conscrypt-android:2.5.2")
    implementation("io.grpc:grpc-okhttp:1.56.0") // или последняя версия

    // Если используете Netty
    implementation("io.grpc:grpc-netty-shaded:1.56.0")
}
