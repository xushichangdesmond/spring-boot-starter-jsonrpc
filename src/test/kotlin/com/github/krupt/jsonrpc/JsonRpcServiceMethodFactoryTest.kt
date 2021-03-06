package com.github.krupt.jsonrpc

import com.github.krupt.jsonrpc.annotation.JsonRpcService
import com.github.krupt.jsonrpc.annotation.NoJsonRpcMethod
import com.github.krupt.test.dto.TestRequest
import io.kotlintest.matchers.maps.shouldContainKey
import io.kotlintest.matchers.maps.shouldNotContainKey
import io.kotlintest.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@SpringBootTest(classes = [
    JsonRpcServiceMethodFactory::class,
    ServiceWithoutMethods::class,
    ServiceWithOnlyHiddenMethods::class,
    TestServiceWithCustomComponentName::class
])
@ExperimentalStdlibApi
internal class JsonRpcServiceMethodFactoryTest {

    @Autowired
    private lateinit var jsonRpcServiceMethodFactory: JsonRpcServiceMethodFactory

    @Test
    fun `factory doesn't collect hidden methods`() {
        jsonRpcServiceMethodFactory.methods shouldNotContainKey "serviceWithOnlyHiddenMethods.test"
    }

    @Test
    fun `factory doesn't fail when collecting services without suitable methods`() {
        jsonRpcServiceMethodFactory.methods shouldContainKey "customTestService.test"
        jsonRpcServiceMethodFactory.methods.size shouldBe 1
    }
}

@JsonRpcService
class ServiceWithoutMethods

@JsonRpcService
class ServiceWithOnlyHiddenMethods {

    @NoJsonRpcMethod
    suspend fun hidden(request: TestRequest) {
        // Test
    }

    private suspend fun internal(request: TestRequest) {
        // Test
    }
}

@JsonRpcService("customTestService")
class TestServiceWithCustomComponentName {

    companion object {
        val CONSTANT: UUID = UUID.randomUUID()
    }

    suspend fun test(request: TestRequest) {
        run {
            CONSTANT
        }
    }
}
