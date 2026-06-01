plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.hilt.android)
}

android {
  namespace = "com.aistudio.sharmakhata.pqmzvk"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.aistudio.sharmakhata.pqmzvk"
    minSdk = 24
    targetSdk = 35
    versionCode = 5
    versionName = "2.0.2"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

  javaCompileOptions {
    annotationProcessorOptions {
      arguments["dagger.hilt.disableModulesHaveInstallInCheck"] = "true"
    }
  }

    val mobileApiKey = run {
      val fromGradleProp = project.findProperty("MOBILE_API_KEY") as String?
      if (!fromGradleProp.isNullOrBlank()) return@run fromGradleProp

      val fromEnv = System.getenv("MOBILE_API_KEY")
      if (!fromEnv.isNullOrBlank()) return@run fromEnv

      val envFile = rootProject.file(".env.android")
      if (envFile.exists()) {
        val line = envFile.readLines()
          .firstOrNull { it.trim().startsWith("MOBILE_API_KEY=") }
        line?.substringAfter("MOBILE_API_KEY=")?.trim()?.trim('"')
      } else ""
    }
    buildConfigField("String", "MOBILE_API_KEY", "\"${mobileApiKey}\"")

    resourceConfigurations += setOf("en")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Use default debug signing provided by the Android Gradle Plugin
      isMinifyEnabled = false
      isShrinkResources = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    isCoreLibraryDesugaringEnabled = true
  }
  kotlin {
    jvmToolchain(11)
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }

  // Room schema export directory for migration validation
  ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
  }
  
  // Optimize build performance and APK size
  bundle {
    language {
      enableSplit = false
    }
    density {
      enableSplit = true
    }
    abi {
      enableSplit = true
    }
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }
    lint {
        disable.add("NullSafeMutableLiveData")
        checkReleaseBuilds = false
    }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  // Use Android-specific env files so server-only vars (like PUBLIC_BASE_URL)
  // don't accidentally get compiled into BuildConfig/resources.
  propertiesFileName = ".env.android"
  defaultPropertiesFileName = ".env.android.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.security.crypto)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.sentry.android)
  implementation(libs.hilt.android)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.androidx.biometric)
  implementation("androidx.paging:paging-runtime-ktx:3.3.5")
  implementation("androidx.paging:paging-compose:3.3.5")
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
   testImplementation(libs.androidx.compose.ui.test.junit4)
   testImplementation(libs.androidx.core)
   testImplementation(libs.androidx.junit)
   testImplementation(libs.junit)
   testImplementation(libs.kotlinx.coroutines.test)
   testImplementation(libs.robolectric)
   testImplementation("androidx.test:core-ktx:1.5.0")
   testImplementation("androidx.test:core:1.5.0")
   testImplementation("androidx.test:runner:1.5.2")
   testImplementation("androidx.test.ext:junit:1.1.5")
   testImplementation(libs.roborazzi)
   testImplementation(libs.roborazzi.compose)
   testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
  "ksp"(libs.hilt.compiler)
}
