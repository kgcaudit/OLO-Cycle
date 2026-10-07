plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.kgcaudit.olocycle"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kgcaudit.olocycle"
        minSdk = 24
        targetSdk = 35
        versionCode = 51
        versionName = "0.42.0"
    }

    buildTypes {
        release {
            // 코드 최적화: R8 코드 축소 + 리소스 축소(안 쓰는 아이콘·라이브러리 제거 → APK 대폭 축소).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 로컬 확인/배포 편의: 디버그 키로 서명해 최적화 APK를 그대로 설치할 수 있게 한다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    // 스크린샷(구상안·대조) 테스트: Robolectric 이 안드로이드 리소스를 쓰게 한다.
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    testImplementation("junit:junit:4.13.2")

    // 구상안/대조 스크린샷 렌더(로컬 워크플로우). 실제 앱 부품을 그대로 그려 PNG로 저장한다.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
