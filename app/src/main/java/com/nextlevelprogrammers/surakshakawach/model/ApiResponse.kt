import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse(
    val success: Boolean, // ✅ Ensure this field exists
    val message: String, // ✅ Ensure this field exists
    @Contextual val data: Any? = null// ✅ Optional field for response data
)

@Serializable
data class ContactData(
    val contact_id: String,
    val user_id: String,
    val contact_firebase_uid: String?,
    val name: String,
    val phone_number: String,
    val relationship: String?,
    val email: String,
    val updatedAt: String,
    val createdAt: String
)