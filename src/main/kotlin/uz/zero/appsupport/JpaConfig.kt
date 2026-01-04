package uz.zero.appsupport

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(
    basePackages = ["uz.zero.appsupport"],
    repositoryBaseClass = BaseRepositoryImpl::class
)
class JpaConfig