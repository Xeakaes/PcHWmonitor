package com.Obscrum.pchwmonitor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocaleParityTest {

    @Test
    fun everyLocale_hasExactlyTheBaseKeys() {
        val baseKeys = keysIn(File(findResDir(), "values/strings.xml"))
        assertTrue("base values/strings.xml must contain keys", baseKeys.isNotEmpty())

        val report = StringBuilder()
        for (dir in localeDirs()) {
            val localeKeys = keysIn(File(dir, "strings.xml"))
            val missing = baseKeys - localeKeys
            val extra = localeKeys - baseKeys
            if (missing.isNotEmpty() || extra.isNotEmpty()) {
                report.append("${dir.name}: missing=$missing, extra=$extra\n")
            }
        }
        assertTrue("Locale key-set parity failures:\n$report", report.isEmpty())
    }

    @Test
    fun everyLocale_hasMatchingFormatSpecifiers() {
        val baseSpecs = specifiersByKey(File(findResDir(), "values/strings.xml"))

        val report = StringBuilder()
        for (dir in localeDirs()) {
            val localeSpecs = specifiersByKey(File(dir, "strings.xml"))
            for ((key, base) in baseSpecs) {
                val locale = localeSpecs[key] ?: continue
                if (base.isEmpty() && locale.isEmpty()) continue
                if (base != locale) {
                    report.append("${dir.name} key=$key: base=$base, locale=$locale\n")
                }
            }
        }
        assertTrue("Locale format-specifier parity failures:\n$report", report.isEmpty())
    }

    private fun keysIn(file: File): Set<String> = entriesIn(file).keys

    private fun entriesIn(file: File): Map<String, String> {
        val regex = Regex("<string\\b[^>]*name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        val result = LinkedHashMap<String, String>()
        for (match in regex.findAll(file.readText())) {
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    private fun specifiersByKey(file: File): Map<String, List<String>> =
        entriesIn(file).mapValues { (_, value) -> specifiersIn(value) }

    private fun specifiersIn(value: String): List<String> {
        val cleaned = value.replace("%%", "")
        return SPEC_REGEX.findAll(cleaned).map { it.value }.sorted().toList()
    }

    private fun localeDirs(): List<File> {
        val res = findResDir()
        val problems = EXPECTED_LOCALES.flatMap { name ->
            val dir = File(res, name)
            when {
                !dir.isDirectory -> listOf("$name/ (directory missing)")
                !File(dir, "strings.xml").exists() -> listOf("$name/strings.xml (file missing)")
                else -> emptyList()
            }
        }
        assertTrue("Expected locale resources missing:\n${problems.joinToString("\n")}", problems.isEmpty())
        return EXPECTED_LOCALES.map { File(res, it) }
    }

    private fun findResDir(): File {
        val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
        return candidates.firstOrNull { File(it, "values/strings.xml").exists() }
            ?: error("values/strings.xml not found relative to ${System.getProperty("user.dir")}")
    }

    private companion object {
        val SPEC_REGEX = Regex("%(\\d+\\$)?[-#+,0]*(\\d+)?(\\.\\d+)?[a-zA-Z%]")

        val EXPECTED_LOCALES = listOf(
            "values-de", "values-es", "values-fr", "values-it", "values-ja",
            "values-nl", "values-pl", "values-pt", "values-pt-rBR", "values-ru",
            "values-tr", "values-zh", "values-zh-rTW",
        )
    }
}
