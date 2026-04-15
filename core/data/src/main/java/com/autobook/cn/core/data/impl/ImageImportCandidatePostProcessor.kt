package com.autobook.cn.core.data.impl

import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.model.ImageImportCandidate
import com.autobook.cn.core.model.ImageImportSuggestion
import com.autobook.cn.core.model.ImageImportTransactionState
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.ParseConfidence
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图片导入候选后处理器。
 *
 * 用途说明：
 * - 把云端或端侧模型返回的多候选结果收口到一条统一的本地过滤流程。
 * - 负责过滤已退还/已退款/已撤销记录、结合本地账本做历史判重，并从剩余候选中选出当前最值得展示的一条。
 *
 * 参数说明：
 * - ledgerRepository：用于执行本地历史判重查询。
 */
@Singleton
class ImageImportCandidatePostProcessor @Inject constructor(
    private val ledgerRepository: LedgerRepository,
) {
    /**
     * 对一次图片分析结果执行本地过滤和择优。
     *
     * 参数说明：
     * - suggestion：上游分析器返回的原始图片建议；可能只有一个顶层建议，也可能已经携带候选列表。
     */
    suspend fun postProcess(suggestion: ImageImportSuggestion): ImageImportSuggestion {
        val rawCandidates = suggestion.candidates.ifEmpty {
            listOf(suggestion.toSyntheticCandidate())
        }

        var duplicateFilteredCount = 0
        var inactiveFilteredCount = 0
        val keptCandidates = mutableListOf<ImageImportCandidate>()
        val seenKeys = mutableSetOf<String>()

        for (candidate in rawCandidates) {
            val inactiveReason = inactiveFilterReason(candidate)
            if (inactiveReason != null) {
                inactiveFilteredCount += 1
                continue
            }

            val amount = candidate.amount
            val candidateKey = buildCandidateDedupKey(candidate)
            if (candidateKey != null && !seenKeys.add(candidateKey)) {
                duplicateFilteredCount += 1
                continue
            }
            if (amount != null && amount > 0.0 && candidate.merchant.isNotBlank()) {
                val duplicate = ledgerRepository.findPotentialImageImportDuplicate(
                    orderId = candidate.orderId,
                    amount = amount,
                    direction = candidate.direction,
                    merchant = candidate.merchant,
                )
                if (duplicate != null) {
                    duplicateFilteredCount += 1
                    continue
                }
            }

            keptCandidates += candidate
        }

        val sortedCandidates = keptCandidates.sortedWith(compareByDescending<ImageImportCandidate> { it.confidence.score }
            .thenByDescending { !it.orderId.isNullOrBlank() }
            .thenByDescending { it.amount != null }
            .thenByDescending { it.direction != MoneyDirection.UNKNOWN })

        val filteredOutCount = duplicateFilteredCount + inactiveFilteredCount
        val selectedCandidate = sortedCandidates.firstOrNull()
        if (selectedCandidate == null) {
            return suggestion.copy(
                amount = null,
                direction = MoneyDirection.UNKNOWN,
                merchant = "",
                category = "待分类",
                note = "本地过滤后未保留有效候选。",
                orderId = null,
                confidence = ParseConfidence(0f),
                message = buildFilteredMessage(
                    baseMessage = suggestion.message,
                    totalCount = rawCandidates.size,
                    keptCount = 0,
                    duplicateFilteredCount = duplicateFilteredCount,
                    inactiveFilteredCount = inactiveFilteredCount,
                ),
                requiresManualConfirmation = true,
                candidates = emptyList(),
                filteredOutCandidateCount = filteredOutCount,
                duplicateFilteredCount = duplicateFilteredCount,
                inactiveFilteredCount = inactiveFilteredCount,
            )
        }

        return suggestion.copy(
            amount = selectedCandidate.amount,
            direction = selectedCandidate.direction,
            merchant = selectedCandidate.merchant,
            category = selectedCandidate.category,
            note = selectedCandidate.note,
            orderId = selectedCandidate.orderId,
            confidence = selectedCandidate.confidence,
            message = buildFilteredMessage(
                baseMessage = suggestion.message,
                totalCount = rawCandidates.size,
                keptCount = sortedCandidates.size,
                duplicateFilteredCount = duplicateFilteredCount,
                inactiveFilteredCount = inactiveFilteredCount,
            ),
            requiresManualConfirmation = true,
            candidates = sortedCandidates,
            filteredOutCandidateCount = filteredOutCount,
            duplicateFilteredCount = duplicateFilteredCount,
            inactiveFilteredCount = inactiveFilteredCount,
        )
    }

    /**
     * 判断一条候选是否属于“已退还/已退款/已撤销”类无效记录。
     *
     * 参数说明：
     * - candidate：待判断候选。
     */
    private fun inactiveFilterReason(candidate: ImageImportCandidate): String? {
        return when (candidate.transactionState) {
            ImageImportTransactionState.REFUNDED -> "候选已退款"
            ImageImportTransactionState.RETURNED -> "候选已退还"
            ImageImportTransactionState.REVERSED -> "候选已撤销"
            ImageImportTransactionState.CANCELLED -> "候选已取消"
            ImageImportTransactionState.ACTIVE,
            ImageImportTransactionState.UNKNOWN,
            -> null
        }
    }
}

/**
 * 为图片候选生成批内去重键。
 *
 * 参数说明：
 * - candidate：待生成去重键的候选。
 */
private fun buildCandidateDedupKey(candidate: ImageImportCandidate): String? {
    return when {
        !candidate.orderId.isNullOrBlank() -> "order:${candidate.orderId}"
        candidate.amount != null && candidate.merchant.isNotBlank() -> {
            "amount:${candidate.amount}|direction:${candidate.direction.name}|merchant:${candidate.merchant}"
        }
        else -> null
    }
}

/**
 * 当上游仍只给出一条顶层建议时，临时合成一个候选，兼容旧接口。
 *
 * 参数说明：
 * - suggestion：待兼容的顶层图片建议。
 */
private fun ImageImportSuggestion.toSyntheticCandidate(): ImageImportCandidate {
    return ImageImportCandidate(
        amount = amount,
        direction = direction,
        merchant = merchant,
        category = category,
        note = note,
        orderId = orderId,
        confidence = confidence,
        transactionState = ImageImportTransactionState.ACTIVE,
        stateReason = "",
        evidence = message,
    )
}

/**
 * 拼装过滤后的用户提示文案。
 *
 * 参数说明：
 * - baseMessage：上游分析器原始说明。
 * - totalCount：原始候选总数。
 * - keptCount：过滤后保留候选数。
 * - duplicateFilteredCount：重复过滤数量。
 * - inactiveFilteredCount：无效状态过滤数量。
 */
private fun buildFilteredMessage(
    baseMessage: String,
    totalCount: Int,
    keptCount: Int,
    duplicateFilteredCount: Int,
    inactiveFilteredCount: Int,
): String {
    val suffix = buildList {
        add("共识别 $totalCount 条候选")
        add("保留 $keptCount 条")
        if (duplicateFilteredCount > 0) {
            add("去除重复 $duplicateFilteredCount 条")
        }
        if (inactiveFilteredCount > 0) {
            add("去除已退还/已撤销 $inactiveFilteredCount 条")
        }
    }.joinToString(separator = "，")
    return if (baseMessage.isBlank()) suffix else "$baseMessage；$suffix。"
}
