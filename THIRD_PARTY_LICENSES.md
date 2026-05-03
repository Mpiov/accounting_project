# 第三方资源授权

## IconPark

- 用途：全项目 UI 图标，包括底部导航、设置页、会员中心、通用操作按钮、首次引导页和快速记账页分类图标。
- 来源：`@icon-park/svg`，版本 `1.4.2`。
- 项目主页：https://github.com/bytedance/IconPark
- 授权协议：Apache License 2.0。
- 处理方式：从官方 npm 包下载 outline SVG 后，机械转换为 Android `VectorDrawable`，资源文件位于 `app/src/main/res/drawable/ic_ui_*.xml`、`app/src/main/res/drawable/ic_cat_*.xml` 和 `feature/onboarding/src/main/res/drawable/ic_onboarding_*.xml`。
