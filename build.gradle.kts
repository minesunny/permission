plugins {
    java
    id("io.spring.dependency-management") version "1.1.6" apply false
}

allprojects {
    repositories {
        mavenLocal()
        maven { url = uri("https://maven.aliyun.com/nexus/content/groups/public") }
        maven {
            url = uri("https://packages.aliyun.com/6125a66357e7cd986dfac90b/maven/2131514-release-kucx67")
            credentials {
                username = providers.environmentVariable("MAVEN_USERNAME").getOrElse("")
                password = providers.environmentVariable("MAVEN_PASSWORD").getOrElse("")
            }
        }
        maven {
            url = uri("https://packages.aliyun.com/6125a66357e7cd986dfac90b/maven/2131514-snapshot-xjndpc")
            credentials {
                username = providers.environmentVariable("MAVEN_USERNAME").getOrElse("")
                password = providers.environmentVariable("MAVEN_PASSWORD").getOrElse("")
            }
        }
        mavenCentral()
    }
}
