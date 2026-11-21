plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    jacoco
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.clikt)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("tcxtocsv.TcxToCsvKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification)
    testLogging {
        events("passed", "failed", "skipped")
    }
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
