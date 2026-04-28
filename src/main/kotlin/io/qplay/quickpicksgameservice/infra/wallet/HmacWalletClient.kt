package io.qplay.quickpicksgameservice.infra.wallet

import com.fasterxml.jackson.databind.ObjectMapper
import io.qplay.quickpicksgameservice.tenant.persistence.Tenant
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.HexFormat

enum class WalletResponseClassification {
    SUCCESS, KNOWN_FAILURE, AMBIGUOUS
}

data class WalletRequest(
    val playerId: String,
    val amountPence: Long,
    val currency: String,
    val transactionId: String,
    val reference: String,
    val metadata: Map<String, String> = emptyMap()
)

data class WalletResponse(
    val status: String,
    val providerTransactionId: String?,
    val errorCode: String?,
    val errorMessage: String?
)

@Component
class HmacWalletClient(private val objectMapper: ObjectMapper) {
    private val restClient = RestClient.builder().build()

    fun debit(tenant: Tenant, request: WalletRequest): WalletResponse {
        val body = request
        val signature = computeSignature(tenant.walletHmacSecret, objectMapper.writeValueAsString(body))

        return try {
            restClient.post()
                .uri("${tenant.walletBaseUrl}/debit")
                .header("X-HMAC-Signature", signature)
                .body(body)
                .retrieve()
                .body(WalletResponse::class.java) ?: throw RuntimeException("Empty response from wallet")
        } catch (e: Exception) {
            WalletResponse("AMBIGUOUS", null, "HTTP_ERROR", e.message)
        }
    }

    fun credit(tenant: Tenant, request: WalletRequest): WalletResponse {
        val body = request
        val signature = computeSignature(tenant.walletHmacSecret, objectMapper.writeValueAsString(body))

        return try {
            restClient.post()
                .uri("${tenant.walletBaseUrl}/credit")
                .header("X-HMAC-Signature", signature)
                .body(body)
                .retrieve()
                .body(WalletResponse::class.java) ?: throw RuntimeException("Empty response from wallet")
        } catch (e: Exception) {
            WalletResponse("AMBIGUOUS", null, "HTTP_ERROR", e.message)
        }
    }

    private fun computeSignature(secret: String, payload: String): String {
        val sha256Hmac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        sha256Hmac.init(secretKey)
        return HexFormat.of().formatHex(sha256Hmac.doFinal(payload.toByteArray()))
    }

    fun classify(response: WalletResponse): WalletResponseClassification {
        return when (response.status) {
            "SUCCESS" -> WalletResponseClassification.SUCCESS
            "FAILED" -> WalletResponseClassification.KNOWN_FAILURE
            else -> WalletResponseClassification.AMBIGUOUS
        }
    }
}
