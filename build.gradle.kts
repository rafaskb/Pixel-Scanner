plugins {
    java
    application
}

group = "com.rafaskoberg.pixelscanner"
version = "1.0-SNAPSHOT"

val _mainClass = "com.rafaskoberg.pixelscanner.Main"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}


application {
    mainClass = _mainClass
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = _mainClass
    }
}

tasks.test {
    useJUnitPlatform()
}