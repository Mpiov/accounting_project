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
 *
 * 参数说明：
 * - category：收入分类文本。
 */
private fun resolveIncomeLedgerCategoryIcon(category: String): AutobookIconAsset {
    return when {
        category.containsAnyLedgerCategoryKeyword("工资", "薪资", "薪水") -> AutobookIconAsset.CatSalary
        category.containsAnyLedgerCategoryKeyword("奖金", "绩效", "提成") -> AutobookIconAsset.CatBonus
        category.containsAnyLedgerCategoryKeyword("投资", "理财", "收益", "利息") -> AutobookIconAsset.CatInvest
        category.containsAnyLedgerCategoryKeyword("兼职", "副业", "劳务") -> AutobookIconAsset.CatPartTime
        category.containsAnyLedgerCategoryKeyword("红包") -> AutobookIconAsset.CatRedPacket
        category.containsAnyLedgerCategoryKeyword("报销") -> AutobookIconAsset.CatReimburse
        category.containsAnyLedgerCategoryKeyword("退款", "退回", "退费") -> AutobookIconAsset.CatRefund
        category.containsAnyLedgerCategoryKeyword("收款", "到账", "转入", "入账") -> AutobookIconAsset.CatReceive
        else -> AutobookIconAsset.CatReceive
    }
}

/**
 * 用途说明：
 * - 为支出分类匹配对应图标，和快速记账支出分类保持一致。
 *
 * 参数说明：
 * - category：支出分类文本。
 */
private fun resolveExpenseLedgerCategoryIcon(category: String): AutobookIconAsset {
    return when {
        category.containsAnyLedgerCategoryKeyword("餐饮", "吃", "饭", "餐", "食", "外卖", "饮", "奶茶", "咖啡") -> AutobookIconAsset.CatFood
        category.containsAnyLedgerCategoryKeyword("购物", "淘宝", "京东", "拼多多", "超市") -> AutobookIconAsset.CatShopping
        category.containsAnyLedgerCategoryKeyword("日用", "生活", "用品", "洗护", "纸巾") -> AutobookIconAsset.CatDaily
        category.containsAnyLedgerCategoryKeyword("交通", "公交", "地铁", "打车", "出租", "高铁", "火车", "机票") -> AutobookIconAsset.CatTransport
        category.containsAnyLedgerCategoryKeyword("蔬菜", "买菜") -> AutobookIconAsset.CatVegetable
        category.containsAnyLedgerCategoryKeyword("水果") -> AutobookIconAsset.CatFruit
        category.containsAnyLedgerCategoryKeyword("零食", "小吃") -> AutobookIconAsset.CatSnack
        category.containsAnyLedgerCategoryKeyword("运动", "健身") -> AutobookIconAsset.CatSport
        category.containsAnyLedgerCategoryKeyword("娱乐", "电影", "游戏") -> AutobookIconAsset.CatFun
        category.containsAnyLedgerCategoryKeyword("通讯", "话费", "流量") -> AutobookIconAsset.CatPhone
        category.containsAnyLedgerCategoryKeyword("服饰", "衣服", "鞋", "帽") -> AutobookIconAsset.CatClothes
        category.containsAnyLedgerCategoryKeyword("美容", "美妆", "护肤") -> AutobookIconAsset.CatBeauty
        category.containsAnyLedgerCategoryKeyword("住房", "房租", "房贷", "物业") -> AutobookIconAsset.CatHouse
        category.containsAnyLedgerCategoryKeyword("居家", "家居", "家具", "水电", "燃气") -> AutobookIconAsset.CatHome
        category.containsAnyLedgerCategoryKeyword("孩子", "儿童", "宝宝") -> AutobookIconAsset.CatChild
        category.containsAnyLedgerCategoryKeyword("长辈", "父母", "老人") -> AutobookIconAsset.CatElder
        category.containsAnyLedgerCategoryKeyword("社交", "朋友", "聚会") -> AutobookIconAsset.CatSocial
        category.containsAnyLedgerCategoryKeyword("旅行", "旅游", "酒店") -> AutobookIconAsset.CatTravel
        category.containsAnyLedgerCategoryKeyword("烟酒", "香烟", "酒") -> AutobookIconAsset.CatWine
        category.containsAnyLedgerCategoryKeyword("数码", "电子", "电脑", "耳机") -> AutobookIconAsset.CatDigital
        category.containsAnyLedgerCategoryKeyword("汽车", "停车", "加油", "洗车", "保养") -> AutobookIconAsset.CatCar
        category.containsAnyLedgerCategoryKeyword("医疗", "医院", "药") -> AutobookIconAsset.CatMedical
        category.containsAnyLedgerCategoryKeyword("书籍", "图书") -> AutobookIconAsset.CatBook
        category.containsAnyLedgerCategoryKeyword("学习", "教育", "课程", "培训") -> AutobookIconAsset.CatStudy
        category.containsAnyLedgerCategoryKeyword("宠物") -> AutobookIconAsset.CatPet
        category.containsAnyLedgerCategoryKeyword("礼金", "份子") -> AutobookIconAsset.CatCashGift
        category.containsAnyLedgerCategoryKeyword("礼物") -> AutobookIconAsset.CatGift
        category.containsAnyLedgerCategoryKeyword("办公") -> AutobookIconAsset.CatOffice
        category.containsAnyLedgerCategoryKeyword("维修", "修理") -> AutobookIconAsset.CatRepair
        category.containsAnyLedgerCategoryKeyword("捐赠", "公益") -> AutobookIconAsset.CatDonate
        category.containsAnyLedgerCategoryKeyword("彩票") -> AutobookIconAsset.CatLottery
        category.containsAnyLedgerCategoryKeyword("亲友", "家人") -> AutobookIconAsset.CatFamily
        else -> AutobookIconAsset.CatOther
    }
}

/**
 * 用途说明：
 * - 提供分类图标匹配用的关键词判断，避免在每个分支重复写 `any + contains`。
 *
 * 参数说明：
 * - keywords：当前分类可能命中的关键词集合。
 */
private fun String.containsAnyLedgerCategoryKeyword(vararg keywords: String): Boolean {
    // 分类文本通常很短，顺序匹配足够轻量，也不会引入正则的额外复杂度。
    return keywords.any { keyword -> contains(keyword) }
}
