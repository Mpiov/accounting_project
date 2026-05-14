package com.autobook.cn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autobook.cn.core.model.MoneyDirection

/**
 * 用途说明：
 * - 为账单列表、最近记录和详细记录提供统一的圆形分类图标节点。
 * - 分类图标只按账单分类匹配，不读取标题、备注或通知原文，避免商户名等噪声造成误判。
 *
 * 参数说明：
 * - category：账单分类文本，用于决定展示哪个语义图标。
 * - direction：账单收支方向，用于决定兜底图标和图标颜色。
 * - modifier：外层修饰符，方便不同列表控制位置。
 * - nodeSize：圆形节点整体大小。
 * - iconSize：节点内部图标大小。
 */
@Composable
internal fun LedgerCategoryIconNode(
    category: String,
    direction: MoneyDirection,
    modifier: Modifier = Modifier,
    nodeSize: Dp = 40.dp,
    iconSize: Dp = 21.dp,
) {
    val accentColor = if (direction == MoneyDirection.INCOME) {
        AutobookIncomeColor
    } else {
        AutobookExpenseColor
    }

    Surface(
        modifier = modifier.size(nodeSize),
        shape = CircleShape,
        border = autobookCardBorder(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(accentColor.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // 圆形节点内部只放分类图标，保持列表扫视时的信息密度。
            AutobookIcon(
                asset = resolveLedgerCategoryIcon(category, direction),
                contentDescription = category.ifBlank { "分类" },
                modifier = Modifier.size(iconSize),
                tint = accentColor,
            )
        }
    }
}

/**
 * 用途说明：
 * - 根据分类和收支方向解析账单分类图标，供所有账单列表复用。
 *
 * 参数说明：
 * - category：账单分类文本。
 * - direction：账单收支方向，用于区分收入类和支出类分类集合。
 */
internal fun resolveLedgerCategoryIcon(
    category: String,
    direction: MoneyDirection,
): AutobookIconAsset {
    return if (direction == MoneyDirection.INCOME) {
        resolveIncomeLedgerCategoryIcon(category)
    } else {
        resolveExpenseLedgerCategoryIcon(category)
    }
}

/**
 * 用途说明：
 * - 为收入分类匹配对应图标，和快速记账收入分类保持一致。
 * - 这里只做完整分类名匹配，避免“到账”等通知语义覆盖用户手动选择的标准分类。
 *
 * 参数说明：
 * - category：收入分类文本。
 */
private fun resolveIncomeLedgerCategoryIcon(category: String): AutobookIconAsset {
    val normalizedCategory = category.trim()

    return when (normalizedCategory) {
        // 分类名称来自快速记账收入分类，完整匹配可以保证编辑后的图标立即跟随分类变化。
        "工资" -> AutobookIconAsset.CatSalary
        "奖金" -> AutobookIconAsset.CatBonus
        "投资" -> AutobookIconAsset.CatInvest
        "兼职" -> AutobookIconAsset.CatPartTime
        "红包" -> AutobookIconAsset.CatRedPacket
        "报销" -> AutobookIconAsset.CatReimburse
        "退款" -> AutobookIconAsset.CatRefund
        "收款" -> AutobookIconAsset.CatReceive
        "其他" -> AutobookIconAsset.CatOther
        else -> AutobookIconAsset.CatOther
    }
}

/**
 * 用途说明：
 * - 为支出分类匹配对应图标，和快速记账支出分类保持一致。
 * - 这里只按账单的标准分类字段做完整匹配，不再用关键词包含，避免“零食”被“餐饮”的“食”误判。
 *
 * 参数说明：
 * - category：支出分类文本。
 */
private fun resolveExpenseLedgerCategoryIcon(category: String): AutobookIconAsset {
    val normalizedCategory = category.trim()

    return when (normalizedCategory) {
        // 分类名称来自快速记账支出分类，完整匹配可以避免不同分类之间互相抢图标。
        "餐饮" -> AutobookIconAsset.CatFood
        "购物" -> AutobookIconAsset.CatShopping
        "日用" -> AutobookIconAsset.CatDaily
        "交通" -> AutobookIconAsset.CatTransport
        "蔬菜" -> AutobookIconAsset.CatVegetable
        "水果" -> AutobookIconAsset.CatFruit
        "零食" -> AutobookIconAsset.CatSnack
        "运动" -> AutobookIconAsset.CatSport
        "娱乐" -> AutobookIconAsset.CatFun
        "通讯" -> AutobookIconAsset.CatPhone
        "服饰" -> AutobookIconAsset.CatClothes
        "美容" -> AutobookIconAsset.CatBeauty
        "住房" -> AutobookIconAsset.CatHouse
        "居家" -> AutobookIconAsset.CatHome
        "孩子" -> AutobookIconAsset.CatChild
        "长辈" -> AutobookIconAsset.CatElder
        "社交" -> AutobookIconAsset.CatSocial
        "旅行" -> AutobookIconAsset.CatTravel
        "烟酒" -> AutobookIconAsset.CatWine
        "数码" -> AutobookIconAsset.CatDigital
        "汽车" -> AutobookIconAsset.CatCar
        "医疗" -> AutobookIconAsset.CatMedical
        "书籍" -> AutobookIconAsset.CatBook
        "学习" -> AutobookIconAsset.CatStudy
        "宠物" -> AutobookIconAsset.CatPet
        "礼金" -> AutobookIconAsset.CatCashGift
        "礼物" -> AutobookIconAsset.CatGift
        "办公" -> AutobookIconAsset.CatOffice
        "维修" -> AutobookIconAsset.CatRepair
        "捐赠" -> AutobookIconAsset.CatDonate
        "彩票" -> AutobookIconAsset.CatLottery
        "亲友" -> AutobookIconAsset.CatFamily
        "其他" -> AutobookIconAsset.CatOther
        else -> AutobookIconAsset.CatOther
    }
}
