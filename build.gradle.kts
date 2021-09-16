import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.30"
}

group = "lol.dimensional"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://m2.dv8tion.net/releases")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2-native-mt")
    implementation("moe.kyokobot.koe:core:koe-v2-SNAPSHOT")
    implementation("dev.kord:kord-core:0.8.0-M5")
    implementation("com.sedmelluq:lavaplayer:1.3.78")
    implementation("ch.qos.logback:logback-classic:1.2.5")
    implementation("io.github.microutils:kotlin-logging-jvm:2.0.6")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}
