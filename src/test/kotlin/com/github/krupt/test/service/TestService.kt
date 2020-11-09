package com.github.krupt.test.service

import com.github.krupt.jsonrpc.annotation.JsonRpcService
import com.github.krupt.test.dto.TestRequest
import com.github.krupt.test.dto.TestResponse
import com.github.krupt.test.exception.ReThrowingException
import com.github.krupt.test.exception.TestException
import com.github.krupt.test.model.TestState
import com.github.krupt.test.model.TestUser
import org.springframework.cache.annotation.Cacheable
import java.util.UUID

@JsonRpcService
class TestService(
    private val testRunnable: Runnable
) {

    @Cacheable("users")
    suspend fun get(userId: UUID) = TestUser(userId)

    suspend fun process(request: TestRequest): TestResponse {
        testRunnable.run()

        return TestResponse(1567)
    }

    suspend fun processAsync(request: TestRequest) {
        testRunnable.run()
    }

    suspend fun call() {
        testRunnable.run()
    }

    suspend fun jsonRpcException(request: TestRequest) {
        throw TestException(TestState("krupt"))
    }

    suspend fun exception(request: TestRequest) {
        throw IllegalStateException("Invalid service state")
    }

    suspend fun list(count: Int): List<TestUser> = emptyList()

    suspend fun reThrowingException() {
        throw ReThrowingException("Error")
    }
}
