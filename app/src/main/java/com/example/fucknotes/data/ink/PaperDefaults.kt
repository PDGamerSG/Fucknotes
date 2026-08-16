package com.example.fucknotes.data.ink

/**
 * What a page falls back to when the note has stored nothing of its own.
 *
 * A zero in the note's paper columns means "unset", and both the editor and the exporter resolve it
 * here, so a page and its PDF are ruled identically.
 */
const val DEFAULT_RULE_SPACING_DP = 24f

/** The range the New Notebook sheet offers, in points between rules. */
val RuleSpacingRange = 8f..48f
