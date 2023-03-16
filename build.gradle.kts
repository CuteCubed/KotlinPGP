import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        jcenter()
    }
}

plugins {
    kotlin("jvm") version "1.3.21"
    id 'maven-publish'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.bouncycastle:bcpg-jdk15on:1.61")
    implementation("org.bouncycastle:bcprov-jdk15on:1.61")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
    testImplementation(group = "org.slf4j", name = "slf4j-simple", version = "1.7.26")
    testImplementation("com.google.guava:guava:27.1-jre")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val test by tasks.getting(Test::class) {
    useJUnitPlatform { }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = "https://maven.pkg.github.com/CuteCubed/KotlinPGP"
            credentials {
                username = System.getenv("USERNAME")
                password = System.getenv("TOKEN")
            }
        }
    }
}