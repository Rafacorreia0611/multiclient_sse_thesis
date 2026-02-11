plugins {
    application
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
}

application {
    mainClass.set("pt.ul.fc58256.demo.server.Server")
}