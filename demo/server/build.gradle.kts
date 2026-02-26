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
    implementation(project(":demo:common"))
}

application {
    mainClass.set("pt.ul.fc58256.demo.server.Server")
}