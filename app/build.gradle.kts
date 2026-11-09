plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.blackout.emergency"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.blackout.emergency"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.play.services.nearby)
    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)
    implementation(libs.bouncycastle)
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
