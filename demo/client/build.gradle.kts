plugins {
    id("application")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation(project(":sse"))
    implementation(project(":demo:common"))
    // Source: https://mvnrepository.com/artifact/commons-validator/commons-validator
    implementation("commons-validator:commons-validator:1.10.1")
}

application {
    mainClass.set("pt.ul.fc58256.demo.client.Client")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
