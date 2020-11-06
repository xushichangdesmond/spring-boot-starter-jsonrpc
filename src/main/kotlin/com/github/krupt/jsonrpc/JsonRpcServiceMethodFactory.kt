package com.github.krupt.jsonrpc

import com.github.krupt.jsonrpc.annotation.JsonRpcService
import com.github.krupt.jsonrpc.annotation.NoJsonRpcMethod
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.stereotype.Component
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.reflect.KFunction
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions

@Component
class JsonRpcServiceMethodFactory(
    beanFactory: ListableBeanFactory
) {

    // Map<methodName, jsonRpcMethodDefinition>
    @ExperimentalStdlibApi
    val methods =
        beanFactory.getBeansWithAnnotation(JsonRpcService::class.java)
            .map {
                val t = it.value
                val k = AopUtils.getTargetClass(it.value)
                val kc = k.kotlin
                val f = kc.memberFunctions
                AopUtils.getTargetClass(it.value).kotlin.memberFunctions
                    .filter {
                        it.isSuspend
                                && it.parameters.size <= 2
                                && !it.hasAnnotation<NoJsonRpcMethod>()
                    }.map { method ->
                        "${it.key}.${method.name}" to JsonRpcServiceMethodDefinition(it.key, it.value, method)
                    }
            }.flatten().toMap()
}

data class JsonRpcServiceMethodDefinition(
    val beanName: String,
    val beanInstance: Any,
    val method: KFunction<*>
)
