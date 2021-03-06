package com.github.krupt.test

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean

@SpringBootApplication
@EnableCaching
internal class TestApplication {

    @Bean
    fun runnable(): Runnable = Runnable {
    }
}

fun main() {
    runApplication<TestApplication>()
}
