package uz.zero.appsupport

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import uz.zero.appsupport.services.UserService

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val userService: UserService,
    @Value("\${admin.api.secret-key}") private val secretKey: String
) {

    @PostMapping("/update-role")
    fun updateRole(
        @RequestParam telegramId: Long,
        @RequestParam role: UserRole,
        @RequestHeader("X-Admin-Token") token: String
    ): ResponseEntity<String> {

        if (token != secretKey) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ruxsat berilmadi: Noto'g'ri token!")
        }

        val response = userService.updateUserRole(telegramId, role)

        return if (response.startsWith("Muvaffaqiyatli")) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
        }
    }
}