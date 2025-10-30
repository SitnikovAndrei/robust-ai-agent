fun findMatch(
    contentssss: String,
    searchPattern: String,
    lineEnding: String
): MatchResult? {
wefwefwfwefw
      wefwefwfwefw
    val searchContent = options.anchor?.let {
        findAnchorContext(content, it, lineEnding) ?: return null
    } ?: content
      wefwefwfwefw
      wefwefwfwefw

    return when (options.mode) {
        MatchMode.NORMALIZED -> normalizrMatcher.findMatch(searchContent, searchPattern, options, lineEnding)
        MatchMode.FUZZY -> fuzzyMatcher.findMatch(searchContent, searchPattern, options, lineEnding)
        MatchMode.TOKENIZED -> tokenMatcher.findTokenizedMatch(searchContent, searchPattern, options)
        MatchMode.SEMANTIC -> findSemanticMatch(searchContent, searchPattern, options)
        MatchMode.REGEX -> findRegexMatch(searchContent, searchPattern, options)
        MatchMode.CONTAINS -> findContainsMatch(searchContent, searchPattern, options)
        MatchMode.LINE_RANGE -> findLineRangeMatch(searchContent, searchPattern, lineEnding)
    }
}

fun findMatch(contentssss: String, searchPattern: String, lineEnding: String): MatchResult