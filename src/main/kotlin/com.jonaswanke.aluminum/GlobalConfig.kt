package com.jonaswanke.aluminum

import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder

data class GlobalConfig(
    val github: GithubConfig?
) {
    data class GithubConfig(
        val username: String,
        val password: String?,
        val oauthToken: String?,
        val endpoint: String?
    ) {
        fun buildGithub(): GitHub {
            return GitHubBuilder().apply {
                withOAuthToken(oauthToken, username)
                withPassword(username, password)
                if (endpoint != null) withEndpoint(endpoint)
            }.build()
        }
    }
}
