package com.researchex.userportal

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** User Portal 서비스의 Spring Boot 진입점이다. */
@SpringBootApplication
class UserPortalApplication

fun main(args: Array<String>) {
    runApplication<UserPortalApplication>(*args)
}
