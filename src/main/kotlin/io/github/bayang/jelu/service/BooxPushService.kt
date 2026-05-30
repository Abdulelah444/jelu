package io.github.bayang.jelu.service

import com.aliyun.oss.OSSClientBuilder
import com.aliyun.oss.common.auth.DefaultCredentials
import com.aliyun.oss.common.auth.DefaultCredentialProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class BooxPushService {

    private val configPath = "/config/boox.properties"

    private fun loadConfig(): Properties {
        val props = Properties()
        val file = File(configPath)
        if (file.exists()) {
            FileInputStream(file).use { props.load(it) }
        }
        return props
    }

    private fun saveConfig(props: Properties) {
        File(configPath).parentFile?.mkdirs()
        FileOutputStream(configPath).use { props.store(it, "Boox Push Config") }
    }

    fun getStatus(): Map<String, Any> {
        val props = loadConfig()
        return mapOf(
            "configured" to !props.getProperty("token", "").isNullOrBlank(),
            "email" to props.getProperty("email", ""),
        )
    }

    fun requestVerificationCode(email: String, cloud: String = "push.boox.com"): Map<String, Any> {
        val client = WebClient.create("https://$cloud")
        val response = client.post()
            .uri("/api/1/users/sendMobileCode")
            .header("Content-Type", "application/json;charset=utf-8")
            .bodyValue(mapOf("mobi" to email))
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()

        logger.info { "Verification code requested for $email: $response" }
        return mapOf("success" to true, "response" to (response ?: emptyMap<String, Any>()))
    }

    fun obtainToken(email: String, code: String, cloud: String = "push.boox.com"): Map<String, Any> {
        val client = WebClient.create("https://$cloud")
        val response = client.post()
            .uri("/api/1/users/signupByPhoneOrEmail")
            .header("Content-Type", "application/json;charset=utf-8")
            .bodyValue(mapOf("mobi" to email, "code" to code))
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()

        @Suppress("UNCHECKED_CAST")
        val data = response?.get("data") as? Map<String, Any>
        val token = data?.get("token") as? String

        if (token != null) {
            val props = Properties()
            props.setProperty("email", email)
            props.setProperty("cloud", cloud)
            props.setProperty("token", token)
            saveConfig(props)
            logger.info { "Boox token obtained and saved for $email" }
            return mapOf("success" to true)
        }

        return mapOf("success" to false, "error" to "Failed to obtain token: $response")
    }

    fun sendFile(filePath: String): Map<String, Any> {
        val props = loadConfig()
        val token = props.getProperty("token") ?: return mapOf("success" to false, "error" to "Not configured")
        val cloud = props.getProperty("cloud", "push.boox.com")

        val client = WebClient.builder()
            .baseUrl("https://$cloud")
            .defaultHeader("Authorization", "Bearer $token")
            .defaultHeader("Content-Type", "application/json;charset=utf-8")
            .build()

        // Step 1: Get user ID
        val meResponse = client.get().uri("/api/1/users/me").retrieve().bodyToMono(Map::class.java).block()
        @Suppress("UNCHECKED_CAST")
        val userId = (meResponse?.get("data") as? Map<String, Any>)?.get("uid") as? String
            ?: return mapOf("success" to false, "error" to "Could not get user ID")

        // Step 2: Get device info (required by API)
        client.get().uri("/api/1/users/getDevice").retrieve().bodyToMono(Map::class.java).block()

        // Step 3: Get cloud bucket info
        val bucketsResponse = client.get().uri("/api/1/config/buckets").retrieve().bodyToMono(Map::class.java).block()
        @Suppress("UNCHECKED_CAST")
        val onyxCloud = ((bucketsResponse?.get("data") as? Map<String, Any>)?.get("onyx-cloud") as? Map<String, Any>)
            ?: return mapOf("success" to false, "error" to "Could not get bucket info")
        val bucketName = onyxCloud["bucket"] as? String ?: return mapOf("success" to false, "error" to "No bucket name")
        val endpoint = onyxCloud["aliEndpoint"] as? String ?: return mapOf("success" to false, "error" to "No endpoint")

        // Step 4: Get STS credentials
        val stssResponse = client.get().uri("/api/1/config/stss").retrieve().bodyToMono(Map::class.java).block()
        @Suppress("UNCHECKED_CAST")
        val stssData = stssResponse?.get("data") as? Map<String, Any>
            ?: return mapOf("success" to false, "error" to "Could not get STS credentials")
        val accessKeyId = stssData["AccessKeyId"] as? String ?: return mapOf("success" to false, "error" to "No AccessKeyId")
        val accessKeySecret = stssData["AccessKeySecret"] as? String ?: return mapOf("success" to false, "error" to "No AccessKeySecret")
        val securityToken = stssData["SecurityToken"] as? String ?: return mapOf("success" to false, "error" to "No SecurityToken")

        // Step 5: Upload to Alibaba OSS
        val file = File(filePath)
        val extension = file.extension
        val remoteName = "$userId/push/${UUID.randomUUID()}.$extension"

        val credentials = DefaultCredentials(accessKeyId, accessKeySecret, securityToken)
        val credentialProvider = DefaultCredentialProvider(credentials)
        val ossClient = OSSClientBuilder().build(endpoint, credentialProvider)

        try {
            ossClient.putObject(bucketName, remoteName, file)
            logger.info { "File uploaded to OSS: $remoteName" }
        } finally {
            ossClient.shutdown()
        }

        // Step 6: Register with push.boox.com
        val filename = file.name
        val pushResponse = client.post()
            .uri("/api/1/push/saveAndPush")
            .bodyValue(mapOf(
                "data" to mapOf(
                    "bucket" to bucketName,
                    "name" to filename,
                    "parent" to null,
                    "resourceDisplayName" to filename,
                    "resourceKey" to remoteName,
                    "resourceType" to "txt",
                    "title" to filename,
                )
            ))
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()

        logger.info { "Push response: $pushResponse" }
        return mapOf("success" to true, "response" to (pushResponse ?: emptyMap<String, Any>()))
    }
}
