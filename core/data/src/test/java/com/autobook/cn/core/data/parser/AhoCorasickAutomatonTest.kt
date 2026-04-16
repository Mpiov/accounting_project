package com.autobook.cn.core.data.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 验证轻量 AC 自动机能够正确处理多模式匹配与重叠输出。 */
class AhoCorasickAutomatonTest {
    @Test
    fun `returns overlapped outputs from one scan`() {
        val automaton = AhoCorasickAutomaton.Builder<String>()
            .add("到账", "income")
            .add("利息到账", "finance")
            .build()

        val matches = automaton.find("零钱通利息到账0.01元")

        assertEquals(setOf("income", "finance"), matches.mapTo(linkedSetOf(), AhoCorasickAutomaton.Match<String>::value))
        assertTrue(matches.any { it.pattern == "利息到账" })
    }

    @Test
    fun `distinct patterns de duplicate repeated hits`() {
        val automaton = AhoCorasickAutomaton.Builder<String>()
            .add("到账", "income")
            .build()

        val patterns = automaton.distinctPatterns("到账通知，到账成功")

        assertEquals(setOf("到账"), patterns)
    }
}
