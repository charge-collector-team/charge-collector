plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "kr.chargebadge"
    compileSdk = 35
    useLibrary("android.car")
    defaultConfig {
        applicationId = "kr.chargebadge"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    buildTypes {
        // 시연용: 디버그 빌드는 Compose가 느려 첫 화면이 몇 초 비어 보인다. 코드 축소는 SDK 리플렉션 확인 전이라 끈다
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
}
