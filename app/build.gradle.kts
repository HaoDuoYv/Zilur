plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
}

// release 签名：临时复用 debug keystore。凭据从 local.properties 读取（已被 .gitignore 忽略）。
val keystoreProps = mutableMapOf<String, String>()
val keystorePropFile = rootProject.file("local.properties")
if (keystorePropFile.exists()) {
    keystorePropFile.readLines().forEach { line ->
        val t = line.trim()
        if (t.isNotEmpty() && !t.startsWith("#") && t.contains("=")) {
            val eq = t.indexOf("=")
            keystoreProps[t.substring(0, eq).trim()] = t.substring(eq + 1).trim()
        }
    }
}

android {
    namespace = "com.example.zhilu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.zhilu"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }

    // 打开 Robolectric 的 Android 资源加载：公式渲染（jlatexmath）要从 assets 里读
    // `org/scilab/forge/jlatexmath/TeXFormulaSettings.xml` 与内置字体。不打开这一项时
    // `TeXFormula` 的静态初始化会抛 FileNotFoundException，于是所有"公式导出成图片"的断言
    // 都会静默走到 `<code>` 降级分支 —— 看起来像功能没做，其实只是测试环境缺资源。
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // 每个测试类单开一个 JVM。**必须的，不是优化**：
        // `TeXFormula` 的静态初始化只要失败一次（例如某个**纯 JUnit 类**在
        // Robolectric 沙箱之外碰到它，那时没有 Context），这个类就永久不可用，
        // 之后所有渲染都抛 `NoClassDefFoundError` 并被导出器降级成 `<code>`。
        // 表现极具迷惑性：`HtmlExporterFormulaTest` **单独跑绿、跟别的类一起跑红**。
        unitTests.all { it.forkEvery = 1 }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProps["RELEASE_STORE_FILE"]
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = keystoreProps["RELEASE_STORE_PASSWORD"]
                keyAlias = keystoreProps["RELEASE_KEY_ALIAS"]
                keyPassword = keystoreProps["RELEASE_KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.1")

    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("ru.noties:jlatexmath-android:0.2.0")
    implementation("ru.noties:jlatexmath-android-font-greek:0.2.0")

    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    implementation("com.jakewharton.timber:timber:5.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("org.robolectric:robolectric:4.13")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
