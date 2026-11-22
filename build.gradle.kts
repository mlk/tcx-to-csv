plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kover)
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)

    jvm()

    macosArm64 {
        binaries.executable {
            entryPoint = "tcxtocsv.main"
            baseName = "tcx-to-csv"
        }
    }
    macosX64 {
        binaries.executable {
            entryPoint = "tcxtocsv.main"
            baseName = "tcx-to-csv"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.clikt)
            implementation(libs.xmlutil.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            implementation(libs.junit.jupiter)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

// Self-contained runnable JVM jar (native binaries are built per-macOS-arch).
tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Builds a self-contained runnable JVM jar (build/libs/tcx-to-csv-all.jar)"
    archiveBaseName.set("tcx-to-csv")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to "tcxtocsv.TcxToCsvKt")
    }
    with(tasks.named<Jar>("jvmJar").get())
    from({ configurations["jvmRuntimeClasspath"].filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
}

kover {
    reports {
        verify {
            rule {
                minBound(80) // >= 80% line coverage, enforced on `check`
            }
        }
    }
}
