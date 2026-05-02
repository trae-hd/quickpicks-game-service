package io.qplay.quickpicksgameservice.exception

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

class PlayerNotAllowlistedException : RuntimeException("Service coming soon")

@ControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(PlayerNotAllowlistedException::class)
    fun handlePlayerNotAllowlistedException(e: PlayerNotAllowlistedException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("mode" to "COMING_SOON"))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<ApiErrorEnvelope> {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", e.message ?: "Invalid argument")
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(e: ConstraintViolationException): ResponseEntity<ApiErrorEnvelope> {
        val details = e.constraintViolations.associate { 
            it.propertyPath.toString() to it.message 
        }
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Constraint violation", details)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ApiErrorEnvelope> {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", "Unauthorised")
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(e: Exception): ResponseEntity<ApiErrorEnvelope> {
        log.error("Unhandled exception in request processing", e)
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred")
    }

    private fun buildErrorResponse(
        status: HttpStatus,
        code: String,
        message: String,
        details: Map<String, Any>? = null
    ): ResponseEntity<ApiErrorEnvelope> {
        val detail = ApiErrorDetail(code, message, details)
        return ResponseEntity.status(status).body(ApiErrorEnvelope(detail))
    }
}
