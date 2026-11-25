plugins {
    java
    jacoco
    id("io.spring.dependency-management") version "1.1.6"
    `maven-publish`
}

group = "com.minesunny.permission"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Javadoc> {
    options {
        this as StandardJavadocDocletOptions
        addStringOption("Xdoclint:none", "-quiet")
        addStringOption("encoding", "UTF-8")
        addStringOption("charSet", "UTF-8")
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.apache.commons:commons-text:${property("commonsTextVersion")}")

    compileOnly("com.minesunny.jpa:base-jpa:${property("baseJpaVersion")}")
    compileOnly("org.jetbrains:annotations:${property("jetbrainsAnnotationsVersion")}")

    compileOnly("org.projectlombok:lombok:${property("lombokVersion")}")
    annotationProcessor("org.projectlombok:lombok:${property("lombokVersion")}")

    testCompileOnly("org.projectlombok:lombok:${property("lombokVersion")}")
    testAnnotationProcessor("org.projectlombok:lombok:${property("lombokVersion")}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2:${property("h2Version")}")
    testImplementation("org.postgresql:postgresql:42.7.4")
    testImplementation("com.minesunny.jpa:base-jpa:${property("baseJpaVersion")}")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            val releaseUrl = uri("https://packages.aliyun.com/6125a66357e7cd986dfac90b/maven/2131514-release-kucx67")
            val snapshotUrl = uri("https://packages.aliyun.com/6125a66357e7cd986dfac90b/maven/2131514-snapshot-xjndpc")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotUrl else releaseUrl
            credentials {
                username = providers.environmentVariable("MAVEN_USERNAME").getOrElse("")
                password = providers.environmentVariable("MAVEN_PASSWORD").getOrElse("")
            }
        }
    }
}

tasks.register("deploy") {
    group = "publishing"
    description = "Publishes the artifact to the local Maven cache and the configured remote Maven repository (mvn deploy equivalent)."
    dependsOn("publish", "publishToMavenLocal")
}
