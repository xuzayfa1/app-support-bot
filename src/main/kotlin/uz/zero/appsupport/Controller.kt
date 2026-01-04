package uz.zero.appsupport

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uz.zero.appsupport.services.UserService

@RestController
@RequestMapping("/api/admin")
class AdminController(private val userService: UserService) {

    @PostMapping("/promote/{telegramId}")
    fun promoteToOperator(@PathVariable telegramId: Long): ResponseEntity<String> {
        val result = userService.promoteToOperator(telegramId)
        return ResponseEntity.ok(result)
    }
}