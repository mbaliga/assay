plugins {
    kotlin("jvm") version "2.1.20"
    application
}

version = "1.1.0"

repositories { mavenCentral() }

kotlin { jvmToolchain(17) }

application { mainClass.set("dev.assay.MainKt") }

tasks.register<JavaExec>("acceptanceTest") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.assay.AcceptanceTestKt")
}

tasks.register<JavaExec>("candidateAcceptanceTest") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.assay.CandidateAcceptanceTestKt")
}

tasks.register<JavaExec>("candidatePersistenceAcceptanceTest") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.assay.CandidatePersistenceAcceptanceTestKt")
}

tasks.register<JavaExec>("candidateCliAcceptanceTest") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.assay.CandidateCliAcceptanceTestKt")
}

tasks.register<JavaExec>("gitBusPublisherAcceptanceTest") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.assay.GitBusPublisherAcceptanceTestKt")
}

tasks.register<JavaExec>("mobSfExecutionAcceptanceTest") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.assay.MobSfExecutionAcceptanceTestKt")
}

tasks.register<JavaExec>("integrationProjectionAcceptanceTest") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.assay.IntegrationProjectionAcceptanceTestKt")
}

tasks.register<JavaExec>("runnerPreflightAcceptanceTest") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.assay.RunnerPreflightAcceptanceTestKt")
}

tasks.register<JavaExec>("assayContractV1AcceptanceTest") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.assay.AssayContractV1AcceptanceTestKt")
}

tasks.register<JavaExec>("assayOutputPublisherAcceptanceTest") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.assay.AssayOutputPublisherAcceptanceTestKt")
}

tasks.named("check") {
    dependsOn(
        "acceptanceTest",
        "candidateAcceptanceTest",
        "candidatePersistenceAcceptanceTest",
        "candidateCliAcceptanceTest",
        "gitBusPublisherAcceptanceTest",
        "mobSfExecutionAcceptanceTest",
        "integrationProjectionAcceptanceTest",
        "runnerPreflightAcceptanceTest",
        "assayContractV1AcceptanceTest",
        "assayOutputPublisherAcceptanceTest",
    )
}
