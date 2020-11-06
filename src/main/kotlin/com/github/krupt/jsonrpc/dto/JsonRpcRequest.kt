package com.github.krupt.jsonrpc.dto

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Pattern

data class JsonRpcRequest<T>(
    @field:NotBlank
    val id: String,
    @field:NotBlank
    val method: String,
    val params: T?,
    @JsonProperty("jsonrpc")
    @field:Pattern(regexp = """^2\.0$""")
    val jsonRpc: String
)
