plugins {
    java
}

group = "com.winter"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    //객체를 응답할때는 JSON으로 변환해야 하기 때문에 필요
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

    // JUnit Jupiter
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")


}

tasks.test {
    useJUnitPlatform()
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}