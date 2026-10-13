plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.dorian15.greendothide"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dorian15.greendothide"
        // libxposed api/service 102 declare minSdkVersion 26
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.5.1"

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
        // io.github.libxposed:service is compiled with records / sealed types / switch expressions
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.cardview)
    testImplementation(libs.junit)
    testImplementation(libs.xposed.api)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // Provided by the Xposed framework at runtime; only the hooked process may touch it.
    compileOnly(libs.xposed.api)
    // Bundled: XposedProvider lets the module app talk to the framework.
    implementation(libs.xposed.service)
}
