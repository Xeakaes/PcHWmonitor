package com.Obscrum.pchwmonitor.data

enum class ServerField { NAME, IP, PORT }

enum class IssueCode { EMPTY_IP, INVALID_PORT, DUPLICATE_NAME }

enum class IssueSeverity { ERROR, WARNING }

data class FieldIssue(val field: ServerField, val code: IssueCode, val severity: IssueSeverity)

object ServerValidator {

    fun validate(
        ip: String,
        port: Int?,
        name: String,
        hostname: String? = null,
        existingNames: List<String> = emptyList(),
    ): List<FieldIssue> {
        val issues = mutableListOf<FieldIssue>()
        if (ip.isBlank() && hostname.isNullOrBlank()) {
            issues += FieldIssue(ServerField.IP, IssueCode.EMPTY_IP, IssueSeverity.ERROR)
        }
        if (port == null || port !in 1..65535) {
            issues += FieldIssue(ServerField.PORT, IssueCode.INVALID_PORT, IssueSeverity.ERROR)
        }
        if (existingNames.any { it.trim().equals(name.trim(), ignoreCase = true) }) {
            issues += FieldIssue(ServerField.NAME, IssueCode.DUPLICATE_NAME, IssueSeverity.WARNING)
        }
        return issues
    }
}
