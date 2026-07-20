plugins {
    java
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.minesunny.permission"
version = "0.0.2"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
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
    implementation("org.apache.directory.fortress:fortress-core:${property("fortressCoreVersion")}")

    compileOnly("com.minesunny.jpa:base-jpa:${property("baseJpaVersion")}")
    compileOnly("org.jetbrains:annotations:${property("jetbrainsAnnotationsVersion")}")

    compileOnly("org.projectlombok:lombok:${property("lombokVersion")}")
    annotationProcessor("org.projectlombok:lombok:${property("lombokVersion")}")

    testCompileOnly("org.projectlombok:lombok:${property("lombokVersion")}")
    testAnnotationProcessor("org.projectlombok:lombok:${property("lombokVersion")}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2")
    testImplementation("junit:junit")
    testImplementation("com.minesunny.jpa:base-jpa:${property("baseJpaVersion")}")
}

tasks.test {
    useJUnitPlatform()
}
