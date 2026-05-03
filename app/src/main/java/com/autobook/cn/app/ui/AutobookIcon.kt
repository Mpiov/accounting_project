package com.autobook.cn.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.autobook.cn.R

/**
 * 用途说明：
 * - 统一声明 App 内部 UI 使用的 IconPark 本地矢量图标，保证底栏、设置页、会员、快速记账等入口风格一致。
 * - 所有资源均来自 `@icon-park/svg` 的 outline SVG，并已转换为 Android VectorDrawable。
 *
 * 参数说明：
 * - resId：对应 `app/src/main/res/drawable` 下的 VectorDrawable 资源。
 */
internal enum class AutobookIconAsset(
    @DrawableRes val resId: Int,
) {
    Add(R.drawable.ic_ui_add),
    Overview(R.drawable.ic_ui_overview),
    Ledger(R.drawable.ic_ui_ledger),
    Charts(R.drawable.ic_ui_charts),
    Pending(R.drawable.ic_ui_pending),
    Settings(R.drawable.ic_ui_settings),
    Back(R.drawable.ic_ui_back),
    ChevronLeft(R.drawable.ic_ui_chevron_left),
    ChevronRight(R.drawable.ic_ui_chevron_right),
    Down(R.drawable.ic_ui_down),
    Search(R.drawable.ic_ui_search),
    Permissions(R.drawable.ic_ui_permissions),
    Message(R.drawable.ic_ui_message),
    Screenshot(R.drawable.ic_ui_screenshot),
    Notifications(R.drawable.ic_ui_notifications),
    Source(R.drawable.ic_ui_source),
    Storage(R.drawable.ic_ui_storage),
    Account(R.drawable.ic_ui_account),
    Sparkle(R.drawable.ic_ui_sparkle),
    Camera(R.drawable.ic_ui_camera),
    CloudSync(R.drawable.ic_ui_cloud_sync),
    Crown(R.drawable.ic_ui_crown),
    DoneAll(R.drawable.ic_ui_done_all),
    CalendarToday(R.drawable.ic_ui_calendar_today),
    Backspace(R.drawable.ic_ui_backspace),
    Income(R.drawable.ic_ui_income),
    Expense(R.drawable.ic_ui_expense),
    CatFood(R.drawable.ic_cat_food),
    CatShopping(R.drawable.ic_cat_shopping),
    CatDaily(R.drawable.ic_cat_daily),
    CatTransport(R.drawable.ic_cat_transport),
    CatVegetable(R.drawable.ic_cat_vegetable),
    CatFruit(R.drawable.ic_cat_fruit),
    CatSnack(R.drawable.ic_cat_snack),
    CatSport(R.drawable.ic_cat_sport),
    CatFun(R.drawable.ic_cat_fun),
    CatPhone(R.drawable.ic_cat_phone),
    CatClothes(R.drawable.ic_cat_clothes),
    CatBeauty(R.drawable.ic_cat_beauty),
    CatHouse(R.drawable.ic_cat_house),
    CatHome(R.drawable.ic_cat_home),
    CatChild(R.drawable.ic_cat_child),
    CatElder(R.drawable.ic_cat_elder),
    CatSocial(R.drawable.ic_cat_social),
    CatTravel(R.drawable.ic_cat_travel),
    CatWine(R.drawable.ic_cat_wine),
    CatDigital(R.drawable.ic_cat_digital),
    CatCar(R.drawable.ic_cat_car),
    CatMedical(R.drawable.ic_cat_medical),
    CatBook(R.drawable.ic_cat_book),
    CatStudy(R.drawable.ic_cat_study),
    CatPet(R.drawable.ic_cat_pet),
    CatCashGift(R.drawable.ic_cat_cash_gift),
    CatGift(R.drawable.ic_cat_gift),
    CatOffice(R.drawable.ic_cat_office),
    CatRepair(R.drawable.ic_cat_repair),
    CatDonate(R.drawable.ic_cat_donate),
    CatLottery(R.drawable.ic_cat_lottery),
    CatFamily(R.drawable.ic_cat_family),
    CatOther(R.drawable.ic_cat_other),
    CatSalary(R.drawable.ic_cat_salary),
    CatBonus(R.drawable.ic_cat_bonus),
    CatInvest(R.drawable.ic_cat_invest),
    CatPartTime(R.drawable.ic_cat_parttime),
    CatRedPacket(R.drawable.ic_cat_redpacket),
    CatReimburse(R.drawable.ic_cat_reimburse),
    CatRefund(R.drawable.ic_cat_refund),
    CatReceive(R.drawable.ic_cat_receive),
}

/**
 * 用途说明：
 * - 用统一入口渲染 IconPark VectorDrawable，并保留 Material `Icon` 的着色和无障碍语义。
 *
 * 参数说明：
 * - asset：要展示的 IconPark 图标枚举。
 * - contentDescription：无障碍描述；纯装饰图标传空。
 * - modifier：外层修饰符，控制图标尺寸和位置。
 * - tint：图标着色，默认跟随当前内容色。
 */
@Composable
internal fun AutobookIcon(
    asset: AutobookIconAsset,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(id = asset.resId),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
