package plus.rua.project

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform