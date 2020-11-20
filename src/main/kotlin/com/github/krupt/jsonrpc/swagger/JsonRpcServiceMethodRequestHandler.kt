package com.github.krupt.jsonrpc.swagger

import com.fasterxml.classmate.ResolvedType
import com.fasterxml.classmate.TypeResolver
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.method.HandlerMethod
import org.springframework.web.reactive.result.condition.PatternsRequestCondition
import org.springframework.web.util.pattern.PathPattern
import org.springframework.web.util.pattern.PathPatternParser
import springfox.documentation.RequestHandler
import springfox.documentation.RequestHandlerKey
import springfox.documentation.service.ResolvedMethodParameter
import springfox.documentation.spring.web.WebFluxPatternsRequestConditionWrapper
import springfox.documentation.spring.wrapper.NameValueExpression
import springfox.documentation.spring.wrapper.RequestMappingInfo
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.javaType

@Suppress("TooManyFunctions")
open class JsonRpcServiceMethodRequestHandler(
    private val basePath: String,
    private val beanName: String,
    private val methodName: String,
    private val method: KFunction<*>
) : RequestHandler {

    companion object {
        private val typeResolver = TypeResolver()
        private val requestBodyAnnotation =
            Proxy.newProxyInstance(
                RequestBody::class.java.classLoader,
                arrayOf(RequestBody::class.java)
            ) { _, method, _ ->
                if (method.name == "required") {
                    true
                } else {
                    null
                }
            } as RequestBody
    }

    override fun isAnnotatedWith(annotation: Class<out Annotation>) =
            method.annotations.firstOrNull {
                annotation.isInstance(it)
            } != null

    override fun getPatternsCondition() =
            WebFluxPatternsRequestConditionWrapper(PatternsRequestCondition(
                    PathPatternParser.defaultInstance.parse("/$basePath/json-rpc/$methodName")
            ))


    override fun groupName() = "[JSON-RPC] $beanName"

    override fun getName(): String = method.name

    override fun supportedMethods() = setOf(RequestMethod.POST)

    override fun produces() = setOf(MediaType.APPLICATION_JSON)

    override fun consumes() = setOf(MediaType.APPLICATION_JSON)

    override fun headers(): Set<NameValueExpression<String>> = emptySet()

    override fun params(): Set<NameValueExpression<String>> = emptySet()

    override fun <T : Annotation> findAnnotation(annotation: Class<T>): Optional<T> =
        Optional.ofNullable(method.annotations.firstOrNull {
            annotation.isInstance(it)
        } as T?)

    override fun key() = RequestHandlerKey(
        patternsCondition.patterns,
        supportedMethods(),
        consumes(),
        produces()
    )

    override fun getParameters(): List<ResolvedMethodParameter> {
        val parameter = method.parameters.firstOrNull()
            ?.takeIf {
                it.type != Unit::class.java
            }

        return parameter?.let {
            listOf(
                ResolvedMethodParameter(
                    0,
                    it.name,
                    it.annotations + requestBodyAnnotation,
                    typeResolver.resolve(it.type.javaType)
                )
            )
        } ?: emptyList()
    }

    override fun getReturnType(): ResolvedType =
        typeResolver.resolve(method.javaMethod!!.genericReturnType)

    override fun <T : Annotation> findControllerAnnotation(annotation: Class<T>): Optional<T> =
        Optional.ofNullable(AnnotationUtils.findAnnotation(method.javaMethod!!.declaringClass, annotation))

    override fun declaringClass(): Class<*> = method.javaMethod!!.declaringClass

    override fun toString() =
        "JsonRpcMethod($methodName)"

    override fun getRequestMapping(): RequestMappingInfo<Any> {
        throw NotImplementedError("Deprecated")
    }

    override fun getHandlerMethod(): HandlerMethod {
        throw NotImplementedError("Deprecated")
    }

    override fun combine(other: RequestHandler?) = this
}
