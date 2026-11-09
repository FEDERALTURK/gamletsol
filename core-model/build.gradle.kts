// Saf Kotlin/JVM modülü — Android bağımlılığı yok, bu sayede Android SDK
// olmadan da (ör. CI öncesi yerel kum havuzunda) derlenip test edilebilir.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// NOT: Hedef toolchain 17 (bkz. android modüllerindeki compileOptions). Bu kum
// havuzunda yalnız JDK 21 kurulu olduğundan burada sabitlenmedi; CI'da
// (ubuntu-latest, birden çok JDK önceden kurulu) 17 ile de doğrulanacak.

dependencies {
    testImplementation(libs.junit)
}
