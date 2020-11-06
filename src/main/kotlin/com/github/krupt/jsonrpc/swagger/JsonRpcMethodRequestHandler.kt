package com.github.krupt.jsonrpc.swagger

import com.github.krupt.jsonrpc.JsonRpcMethod
import org.springframework.core.annotation.AnnotationUtils
import java.util.*
import kotlin.reflect.full.functions

class JsonRpcMethodRequestHandler(
    basePath: String,
    private val fullMethodName: String,
    private val instance: JsonRpcMethod<*, *>
) : JsonRpcServiceMethodRequestHandler(
    basePath,
    fullMethodName.split('.').first(),
    fullMethodName,
    instance::class.functions.first {
        it.name == "invoke"
    }
) {

    override fun getName(): String = fullMethodName.split('.').last()

    override fun <T : Annotation> findControllerAnnotation(annotation: Class<T>): Optional<T> =
        Optional.ofNullable(AnnotationUtils.findAnnotation(instance.javaClass, annotation))

    override fun declaringClass(): Class<*> = instance.javaClass
}
