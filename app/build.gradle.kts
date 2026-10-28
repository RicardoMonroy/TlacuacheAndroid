import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

// -Ptlacuache.abis=arm64-v8a limita las ABI para iterar rápido.
val rustAbis =
    providers
        .gradleProperty("tlacuache.abis")
        .map { it.split(',').map(String::trim) }
        .orElse(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))

android {
    namespace = "io.github.ricardomonroy.tlacuache"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        // ADR-A12: no se puede cambiar una vez publicado.
        applicationId = "io.github.RicardoMonroy.Tlacuache"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-dev"
        // Solo las ABI que compila tlacuache-ffi; JNA trae más (x86, mips…) que fallarían al cargar el core.
        ndk { abiFilters += rustAbis.get() }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        disable +=
            listOf(
                // Falso positivo: Lint no evalúa la lista dinámica de abiFilters, que sí incluye x86_64.
                "ChromeOsAbiSupport",
                // Avisan de versiones nuevas publicadas: romperían el build por la fecha, no por el código.
                // Las versiones se suben a propósito (CLAUDE.md), con su verificación de compatibilidad.
                "AndroidGradlePluginVersion",
                "GradleDependency",
            )
    }

    testOptions {
        unitTests.all {
            // Las pruebas JVM cargan libtlacuache_ffi.so compilada para el host (ARCHITECTURE §2).
            it.systemProperty("jna.library.path", rootProject.file("rust/target/debug").absolutePath)
            it.dependsOn("buildRustDebug")
        }
    }
}

ktlint {
    version.set(libs.versions.ktlint.engine)
    android.set(true)
    filter {
        // Los bindings de UniFFI son generados.
        exclude { it.file.path.contains("/build/") }
    }
}

/** Compila tlacuache-ffi con `scripts/build-rust.sh` y deja los .so y los bindings Kotlin en sus salidas. */
abstract class BuildRustTask
    @Inject
    constructor(
        private val execOps: ExecOperations,
    ) : DefaultTask() {
        @get:Input abstract val profile: Property<String>

        @get:Input abstract val abis: ListProperty<String>

        @get:Input abstract val ndkDir: Property<String>

        @get:InputFile abstract val script: RegularFileProperty

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val rustSources: ConfigurableFileCollection

        @get:OutputDirectory abstract val jniLibsDir: DirectoryProperty

        @get:OutputDirectory abstract val kotlinDir: DirectoryProperty

        @TaskAction
        fun build() {
            execOps.exec {
                environment("ANDROID_NDK_HOME", ndkDir.get())
                commandLine(
                    listOf(
                        script.get().asFile.absolutePath,
                        profile.get(),
                        jniLibsDir.get().asFile.absolutePath,
                        kotlinDir.get().asFile.absolutePath,
                    ) + abis.get(),
                )
            }
        }
    }

val rustDir = rootProject.layout.projectDirectory.dir("rust")

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar(Char::uppercase)
        val task =
            tasks.register<BuildRustTask>("buildRust$name") {
                profile.set(if (variant.buildType == "release") "release" else "debug")
                abis.set(rustAbis)
                ndkDir.set(androidComponents.sdkComponents.ndkDirectory.map { it.asFile.absolutePath })
                script.set(rootProject.layout.projectDirectory.file("scripts/build-rust.sh"))
                rustSources.from(
                    rustDir.files("Cargo.toml", "Cargo.lock"),
                    rustDir.dir("tlacuache-ffi"),
                )
            }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(task, BuildRustTask::jniLibsDir)
        variant.sources.kotlin?.addGeneratedSourceDirectory(task, BuildRustTask::kotlinDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    // Los bindings de UniFFI cargan libtlacuache_ffi.so con JNA (AAR con sus .so nativos).
    implementation(libs.jna) { artifact { type = "aar" } }
    // En la JVM, JNA necesita el jar con libjnidispatch para el host.
    testImplementation(libs.jna)
    testImplementation(libs.junit)
}
