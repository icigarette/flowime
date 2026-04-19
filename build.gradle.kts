import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        create(
            providers.gradleProperty("platformType").get(),
            providers.gradleProperty("platformVersion").get(),
        )

        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

kotlin {
    jvmToolchain(17)
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        compilerOptions.freeCompilerArgs.add("-Xjvm-default=all")
    }

    test {
        useJUnitPlatform()
    }

    wrapper {
        gradleVersion = "8.10.2"
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "FlowIME"
        version = providers.gradleProperty("pluginVersion").get()

        ideaVersion {
            sinceBuild = providers.gradleProperty("platformSinceBuild").get()
            untilBuild = providers.gradleProperty("platformUntilBuild").get()
        }

        vendor {
            name = "JustCodeIt"
        }

        description = """
            FlowIME 是一个面向 IntelliJ IDEA 的上下文感知输入法切换插件。
            它会根据代码、注释、字符串等编辑场景自动切换中英文输入法，
            当前重点支持 Java、Kotlin、XML，并提供诊断窗口、结构化报告、
            im-select 与自定义命令适配器。
        """.trimIndent()

        changeNotes = """
            <p>0.1.0</p>
            <ul>
                <li>完成首版上下文识别、策略状态机、设置页与诊断窗口</li>
                <li>支持 im-select 与自定义命令适配器</li>
                <li>原生 macOS / Windows 适配器调整为实验性开关，降低闪退风险</li>
            </ul>
        """.trimIndent()
    }
}
