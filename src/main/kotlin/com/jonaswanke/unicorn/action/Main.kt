package com.jonaswanke.unicorn.action

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.ajalt.clikt.parameters.options.option
import com.jonaswanke.unicorn.commands.BaseCommand
import com.jonaswanke.unicorn.script.Git
import com.jonaswanke.unicorn.script.GitHub
import com.jonaswanke.unicorn.script.Unicorn
import java.io.File

fun main(args: Array<String>) {
    MainCommand.main(args)
}

private object MainCommand : BaseCommand() {
    val repoToken: String? by option()

    override fun run() {
        super.run()
        val repoToken = repoToken ?: throwError("Input repo-token is required")

        val gh = GitHub.authenticateWithToken(repoToken)
            ?: throwError("Invalid repo-token")
        val repo = gh.currentRepoIfExists(prefix) ?: throwError("Not in a valid repository")
        val projectConfig = Unicorn.projectConfig

        val eventFile = System.getenv("GITHUB_EVENT_PATH")?.let { File(it) }
            ?: throwError("GITHUB_EVENT_PATH not set")
        val payload = ObjectMapper(JsonFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(eventFile, WebhookPayload::class.java)
        payload.pullRequest ?: throwError("Unicorn only supports pull_request events")

        val git = Git(prefix)
        val branch = git.flow.currentBranch(gh) as? Git.Flow.IssueBranch
            ?: throwError("Current branch is not a valid issue branch")
        branch.issue
        val pr = repo.getPullRequest(payload.pullRequest.number)
        println(pr)
    }
}

data class WebhookPayload(
    val pullRequest: PullRequest? = null
) {
    data class PullRequest(
        val number: Int,
        val htmlUrl: String? = null,
        val body: String? = null
    )
}
