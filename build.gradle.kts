val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project


plugins {
    application
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "2.3.7"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
//    mainClass.set("com.example.Main")
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
    maven {
        url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-freemarker:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")

    implementation("org.pcap4j:pcap4j-core:1.8.2")
    implementation("org.pcap4j:pcap4j-packetfactory-static:1.8.2")
//    implementation("org.pcap4j:pcap4j-core:2.0.0-alpha")
//    implementation("org.pcap4j:pcap4j-packetfactory-static:2.0.0-alpha")
//    implementation("com.ardikars.pcap:pcap-spi:1.5.1")
//    implementation("io.ktor:ktor-jackson:$ktor_version")
//    implementation("ch.qos.logback:logback-classic:$logback_version")
//    implementation("org.pcap4j:pcap4j-core:$pcap_version")
//    implementation("org.pcap4j:pcap4j-packetfactory-static:$pcap_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}

tasks {
    shadowJar {
        archiveBaseName.set("my-app")
        archiveClassifier.set(null as String?)
        archiveVersion.set("0.1.0")
        mergeServiceFiles()
//        manifest { attributes["Main-Class"] = "com.example.ApplicationKt" }
        manifest { attributes["Main-Class"] = "com.example.TrafficStatKt" }
    }
    build {
        dependsOn(shadowJar)
    }
}