package com.autobook.cn.core.data.parser

import java.util.ArrayDeque

/**
 * 轻量级 AC 自动机实现。
 *
 * 用途说明：
 * - 统一承载通知解析里大量“多关键词批量匹配”的场景，避免对同一段文本反复 `contains` 扫描。
 * - 当前主要服务于置信度规则重构，包括来源规则、方向词、风险词和语义分组词表。
 *
 * 参数说明：
 * - `T`：每个关键词命中后要回传的载荷类型；可以是枚举、规则对象或任意轻量标记。
 */
internal class AhoCorasickAutomaton<T> private constructor(
    private val nodes: List<Node<T>>,
) {
    /**
     * 扫描整段文本并返回所有命中结果。
     *
     * 参数说明：
     * - text：待扫描文本；调用方负责在进入自动机前完成大小写归一等预处理。
     */
    fun find(text: String): List<Match<T>> {
        if (text.isEmpty()) {
            return emptyList()
        }

        val matches = mutableListOf<Match<T>>()
        var state = ROOT_INDEX
        text.forEachIndexed { index, char ->
            // 当前字符无法继续沿 trie 前进时，按 fail 指针回退到最长可复用后缀状态。
            while (state != ROOT_INDEX && char !in nodes[state].children) {
                state = nodes[state].failure
            }
            state = nodes[state].children[char] ?: ROOT_INDEX
            if (nodes[state].outputs.isEmpty()) {
                return@forEachIndexed
            }

            // 一个状态可能同时命中多个关键词，例如“利息到账”既可以是收入词，也可以是已结算收益词。
            nodes[state].outputs.forEach { output ->
                matches += Match(
                    pattern = output.pattern,
                    value = output.value,
                    startIndex = index - output.pattern.length + 1,
                    endIndex = index,
                )
            }
        }
        return matches
    }

    /**
     * 仅返回去重后的业务载荷，适合“是否命中某类词”这类特征抽取场景。
     *
     * 参数说明：
     * - text：待扫描文本。
     */
    fun distinctValues(text: String): Set<T> = find(text).mapTo(linkedSetOf(), Match<T>::value)

    /**
     * 返回所有命中的原始关键词文本，便于拼接调试原因。
     *
     * 参数说明：
     * - text：待扫描文本。
     */
    fun distinctPatterns(text: String): Set<String> = find(text).mapTo(linkedSetOf(), Match<T>::pattern)

    /**
     * 单次命中结果。
     *
     * 参数说明：
     * - pattern：命中的原始关键词。
     * - value：与该关键词绑定的业务载荷。
     * - startIndex：命中起始下标，包含。
     * - endIndex：命中结束下标，包含。
     */
    data class Match<T>(
        val pattern: String,
        val value: T,
        val startIndex: Int,
        val endIndex: Int,
    )

    /**
     * 自动机构建器。
     *
     * 用途说明：
     * - 先注册多组关键词，再一次性构建 fail 指针，构造结束后得到只读自动机实例。
     */
    class Builder<T> {
        private val nodes = mutableListOf(Node<T>())

        /**
         * 注册一个关键词及其业务载荷。
         *
         * 参数说明：
         * - pattern：关键词原文；空字符串会被忽略，避免污染根节点。
         * - value：命中该关键词时要回传的业务载荷。
         */
        fun add(pattern: String, value: T): Builder<T> {
            if (pattern.isEmpty()) {
                return this
            }

            var state = ROOT_INDEX
            pattern.forEach { char ->
                state = nodes[state].children.getOrPut(char) {
                    nodes += Node()
                    nodes.lastIndex
                }
            }
            nodes[state].outputs += Output(pattern = pattern, value = value)
            return this
        }

        /**
         * 构建只读自动机。
         *
         * 用途说明：
         * - 按 BFS 顺序补齐每个节点的 fail 指针。
         * - 同时把 fail 链上的输出合并到当前节点，确保运行时无需再递归追溯。
         */
        fun build(): AhoCorasickAutomaton<T> {
            val queue = ArrayDeque<Int>()
            nodes[ROOT_INDEX].children.values.forEach { child ->
                nodes[child].failure = ROOT_INDEX
                queue += child
            }

            while (queue.isNotEmpty()) {
                val state = queue.removeFirst()
                nodes[state].children.forEach { (char, child) ->
                    queue += child

                    var fallback = nodes[state].failure
                    while (fallback != ROOT_INDEX && char !in nodes[fallback].children) {
                        fallback = nodes[fallback].failure
                    }

                    val failureState = nodes[fallback].children[char] ?: ROOT_INDEX
                    nodes[child].failure = failureState
                    if (nodes[failureState].outputs.isNotEmpty()) {
                        nodes[child].outputs += nodes[failureState].outputs
                    }
                }
            }

            return AhoCorasickAutomaton(nodes = nodes.map(Node<T>::freeze))
        }
    }

    /**
     * trie 节点。
     *
     * 参数说明：
     * - children：当前字符转移表。
     * - failure：失配时要跳转的状态。
     * - outputs：当前状态已经收敛出的全部输出。
     */
    private data class Node<T>(
        val children: MutableMap<Char, Int> = mutableMapOf(),
        var failure: Int = ROOT_INDEX,
        val outputs: MutableList<Output<T>> = mutableListOf(),
    ) {
        fun freeze(): Node<T> = Node(
            children = children.toMutableMap(),
            failure = failure,
            outputs = outputs.toMutableList(),
        )
    }

    /** 叶子节点输出。 */
    private data class Output<T>(
        val pattern: String,
        val value: T,
    )

    private companion object {
        private const val ROOT_INDEX = 0
    }
}
