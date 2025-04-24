import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.bl_ota"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bl_ota"
        minSdk = 26
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    dependencies {
        implementation(libs.androidx.core.splashscreen)
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.appcompat)
        implementation(libs.material)
        implementation(libs.androidx.activity)
        implementation(libs.androidx.constraintlayout)
        implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
        // ✅ MQTT för Android
        implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
        implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.1.1")

        // ✅ Fix för LocalBroadcastManager med AndroidX, pga deprecation måste denna användas för att ersätta gamla localbroadcastmanager som inte finns kvar
        implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)
    }

}