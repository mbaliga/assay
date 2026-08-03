plugins {
    kotlin("jvm") version "2.1.20"
    application
}

repositories { mavenCentral() }

kotlin { jvmToolchain(17) }

application { mainClass.set("dev.assay.MainKt") }

tasks.register<JavaExec>("acceptanceTest") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.assay.AcceptanceTestKt")
}

tasks.named("check") { dependsOn("acceptanceTest") }
