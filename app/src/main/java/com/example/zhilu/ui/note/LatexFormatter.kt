package com.example.zhilu.ui.note

object LatexFormatter {
    fun format(source: String): String {
        return source
            .replaceFractions()
            .replaceSqrt()
            .replaceCommands()
            .replaceScripts()
            .trim()
    }

    private fun String.replaceFractions(): String =
        replace(Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}""")) { match ->
            "${match.groupValues[1]}\u2044${match.groupValues[2]}"
        }

    private fun String.replaceSqrt(): String =
        replace(Regex("""\\sqrt\{([^{}]+)\}""")) { match ->
            "\u221a(${match.groupValues[1]})"
        }

    private fun String.replaceCommands(): String {
        var output = this
        commandMap.forEach { (command, replacement) ->
            output = output.replace(command, replacement)
        }
        return output
    }

    private fun String.replaceScripts(): String {
        var output = replace(Regex("""\^\{([^{}]+)\}""")) { match ->
            match.groupValues[1].toSuperscript()
        }
        output = output.replace(Regex("""_\{([^{}]+)\}""")) { match ->
            match.groupValues[1].toSubscript()
        }
        output = output.replace(Regex("""\^([A-Za-z0-9+\-=()])""")) { match ->
            match.groupValues[1].toSuperscript()
        }
        output = output.replace(Regex("""_([A-Za-z0-9+\-=()])""")) { match ->
            match.groupValues[1].toSubscript()
        }
        return output
    }

    private fun String.toSuperscript(): String = map { superscriptMap[it] ?: it }.joinToString("")

    private fun String.toSubscript(): String = map { subscriptMap[it] ?: it }.joinToString("")

    private val commandMap = linkedMapOf(
        "\\alpha" to "\u03b1",
        "\\beta" to "\u03b2",
        "\\gamma" to "\u03b3",
        "\\delta" to "\u03b4",
        "\\epsilon" to "\u03b5",
        "\\theta" to "\u03b8",
        "\\lambda" to "\u03bb",
        "\\mu" to "\u03bc",
        "\\pi" to "\u03c0",
        "\\sigma" to "\u03c3",
        "\\omega" to "\u03c9",
        "\\sum" to "\u2211",
        "\\int" to "\u222b",
        "\\infty" to "\u221e",
        "\\leq" to "\u2264",
        "\\geq" to "\u2265",
        "\\neq" to "\u2260",
        "\\times" to "\u00d7",
        "\\cdot" to "\u00b7",
        "\\rightarrow" to "\u2192",
        "\\to" to "\u2192"
    )

    private val superscriptMap = mapOf(
        '0' to '\u2070',
        '1' to '\u00b9',
        '2' to '\u00b2',
        '3' to '\u00b3',
        '4' to '\u2074',
        '5' to '\u2075',
        '6' to '\u2076',
        '7' to '\u2077',
        '8' to '\u2078',
        '9' to '\u2079',
        '+' to '\u207a',
        '-' to '\u207b',
        '=' to '\u207c',
        '(' to '\u207d',
        ')' to '\u207e',
        'n' to '\u207f'
    )

    private val subscriptMap = mapOf(
        '0' to '\u2080',
        '1' to '\u2081',
        '2' to '\u2082',
        '3' to '\u2083',
        '4' to '\u2084',
        '5' to '\u2085',
        '6' to '\u2086',
        '7' to '\u2087',
        '8' to '\u2088',
        '9' to '\u2089',
        '+' to '\u208a',
        '-' to '\u208b',
        '=' to '\u208c',
        '(' to '\u208d',
        ')' to '\u208e'
    )
}
