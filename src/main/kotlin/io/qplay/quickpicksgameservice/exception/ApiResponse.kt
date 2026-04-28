package io.qplay.quickpicksgameservice.exception

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val data: T? = null,
    val meta: Map<String, Any>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiErrorEnvelope(
    val error: ApiErrorDetail
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiErrorDetail(
    val code: String,
    val message: String,
    val details: Map<String, Any>? = null
)
