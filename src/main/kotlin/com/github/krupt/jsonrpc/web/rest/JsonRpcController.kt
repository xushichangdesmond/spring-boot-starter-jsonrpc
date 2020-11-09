package com.github.krupt.jsonrpc.web.rest

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.github.krupt.jsonrpc.JsonRpcMethod
import com.github.krupt.jsonrpc.JsonRpcServiceMethodFactory
import com.github.krupt.jsonrpc.TraceId
import com.github.krupt.jsonrpc.dto.JsonRpcError
import com.github.krupt.jsonrpc.dto.JsonRpcRequest
import com.github.krupt.jsonrpc.dto.JsonRpcResponse
import com.github.krupt.jsonrpc.exception.JsonRpcExceptionHandler
import io.swagger.annotations.ApiOperation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.validation.Validator
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.lang.StringBuilder
import java.lang.reflect.InvocationTargetException
import java.util.regex.Pattern
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaType

@ExperimentalStdlibApi
@RestController
class JsonRpcController(
    jsonRpcServiceMethodFactory: JsonRpcServiceMethodFactory,
    jsonRpcMethodImpls: Map<String, JsonRpcMethod<*, *>>,
    private val exceptionHandler: JsonRpcExceptionHandler,
    private val objectMapper: ObjectMapper,
    private val validator: Validator
) {

    @Suppress("UNCHECKED_CAST")
    private val methods =
        jsonRpcServiceMethodFactory.methods.mapValues {
            val (_, instance, method) = it.value

            ServiceMethodInvocation(
                method,
                instance
            )
        }

    @RequestMapping(method = [RequestMethod.GET, RequestMethod.POST], value = ["\${spring.jsonrpc.path:}"])
    @ApiOperation(
        "The endpoint that handles all JSON-RPC requests",
        notes = """Read more about <a href="https://www.jsonrpc.org/specification">JSON-RPC 2.0 Specification</a>"""
    )
    @Suppress("LongMethod", "ReturnCount")
    suspend fun handle(@RequestBody @Validated request: JsonRpcRequest<Any>): ResponseEntity<JsonRpcResponse<Any>> {
        val methodName = request.method.substringBefore("/")
        val path = "/" + request.method.substringAfter("/", "")

        val method = methods[methodName]
            ?: return buildResponse(
                request.id,
                JsonRpcError(JsonRpcError.METHOD_NOT_FOUND, JsonRpcError.METHOD_NOT_FOUND_MESSAGE)
            )

        if (request.params == null && method.inputType != null) {
            return buildResponse(
                request.id,
                JsonRpcError(
                    JsonRpcError.INVALID_PARAMS,
                    JsonRpcError.INVALID_PARAMS_MESSAGE,
                    "Params can't be null"
                )
            )
        }

        val params = request.params?.let {
            try {
                objectMapper.convertValue(it, method.inputType)
            } catch (e: IllegalArgumentException) {
                return buildResponse(
                    request.id,
                    JsonRpcError(JsonRpcError.INVALID_PARAMS, JsonRpcError.INVALID_PARAMS_MESSAGE, e.toString())
                )
            }
        }

        // Validate
        val bindException = params?.let {
            BindException(params, method.inputType!!.simpleName)
        }
        bindException?.run {
            validator.validate(params, bindException)
        }
        if (bindException?.hasErrors() == true) {
            return buildResponse(
                request.id,
                JsonRpcError(
                    JsonRpcError.INVALID_PARAMS,
                    "Request didn't pass validation",
                    bindException.bindingResult.fieldErrors
                        .map { fieldError ->
                            fieldError.toString()
                        }
                )
            )
        }

        val pathVariables: PathVariablesResolver = createPathVariablesResolver(path, method.method.findAnnotation<RequestMapping>()?.value?.get(0))

        try {
            val result = withContext(currentCoroutineContext() + TraceId(request.id)) {
                method.invoke(pathVariables, params)
            }

            return buildResponse(
                request.id,
                result = result,
                error = null
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            val exception = if (e is InvocationTargetException) {
                e.cause!!
            } else {
                e
            }
            return buildResponse(
                request.id,
                exceptionHandler.handle(exception)
            )
        }
    }

    private fun createPathVariablesResolver(path: String, mapping: String?): PathVariablesResolver {
        if (mapping == null) return object: PathVariablesResolver {
            override fun resolve(name: String): String? = null
        }

        val nameToGroup = mutableMapOf<String, Int>()

        val matchingPatternStringBuilder = StringBuilder().append("^")

        val m = Pattern.compile("/\\{(.+?)}/").matcher(mapping)
        var previousMatchEnd = 0
        var groupIndex = 1
        while (m.find()) {
            matchingPatternStringBuilder.append(mapping.substring(previousMatchEnd, m.start(1) - 1))
            nameToGroup.put(m.group(1), groupIndex)
            previousMatchEnd = m.end(1) + 1
            groupIndex++
            matchingPatternStringBuilder.append("(.+?)")
        }
        matchingPatternStringBuilder.append(mapping.substring(previousMatchEnd)).append("$")
        val p = Pattern.compile(matchingPatternStringBuilder.toString())

        return object: PathVariablesResolver{
            override fun resolve(name: String): String? {
                val groupIndex = nameToGroup[name]
                if (groupIndex == null) return null
                val m = p.matcher(path)
                if (!m.matches()) return null
                return m.group(groupIndex)
            }
        }
    }

    private fun buildResponse(
        requestId: Any?,
        error: JsonRpcError?,
        result: Any? = null
    ): ResponseEntity<JsonRpcResponse<Any>> {
        return requestId?.let {
            ResponseEntity.ok(JsonRpcResponse(it, result, error))
        } ?: ResponseEntity.ok().build()
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleJsonParseException(
        exception: HttpMessageNotReadableException
    ): JsonRpcResponse<Void> =
        when (val cause = exception.cause) {
            is JsonParseException -> JsonRpcResponse(
                error = JsonRpcError(
                    JsonRpcError.PARSE_ERROR,
                    JsonRpcError.PARSE_ERROR_MESSAGE
                )
            )
            is MissingKotlinParameterException -> JsonRpcResponse(
                // TODO extract and pass here id from request
                error = JsonRpcError(
                    JsonRpcError.INVALID_REQUEST,
                    JsonRpcError.INVALID_REQUEST_MESSAGE,
                    cause.message
                )
            )
            else -> JsonRpcResponse(
                error = JsonRpcError(
                    JsonRpcError.INTERNAL_ERROR,
                    "Internal error"
                )
            )
        }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(exception: MethodArgumentNotValidException) =
        JsonRpcResponse<Void>(
            id = (exception.bindingResult.target as JsonRpcRequest<*>).id ?: 1,
            error = JsonRpcError(
                JsonRpcError.INVALID_REQUEST,
                JsonRpcError.INVALID_REQUEST_MESSAGE,
                exception.bindingResult.fieldErrors
                    .map {
                        it.toString()
                    }
            )
        )
}

interface MethodInvocation {

    val inputType: Class<Any>?

    suspend fun invoke(pathVariables: PathVariablesResolver, args: Any?): Any?
}

class ServiceMethodInvocation(
    val method: KFunction<*>,
    private val instance: Any
) : MethodInvocation {

    override val inputType: Class<Any>? = method.valueParameters.firstOrNull {
        it.hasAnnotation<RequestBody>()
    }?.type?.javaType as Class<Any>?

    override suspend fun invoke(pathVariables: PathVariablesResolver, args: Any?): Any? {
        return method.callSuspend(
                instance,
                *method.valueParameters
                        .map {
                            if (it.hasAnnotation<RequestBody>()) { args }
                            else {
                                val ann = it.findAnnotation<PathVariable>()!!
                                val resolved = pathVariables.resolve(ann.value)
                                if (resolved != null) {
                                    resolved
                                } else if (ann.required) {
                                    throw IllegalArgumentException("path variable ${ann.value} required but not provided")
                                } else {
                                    Unit
                                }
                            }
                        }.toTypedArray()
        )
    }
}

interface PathVariablesResolver {
    fun resolve(name: String): String?
}
