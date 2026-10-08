package com.example.zhilu.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import com.example.zhilu.ui.component.ToySurface
import com.example.zhilu.ui.component.DefaultThickness
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.palettePaint
import com.example.zhilu.ui.theme.shade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.zhilu.ui.component.ZhiLuDivider
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.motionSpring

/** 底部导航项：选中 / 未选中用成对图标表达，比单一色值更容易一眼分辨。 */
private data class BottomTab(
    val destination: Destination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/** 导航条可视高度（不含系统手势条内边距）。 */
private val BarHeight = 72.dp

private val IconSize = 20.dp
private val IconLabelGap = 3.dp

private fun bottomTabs(): List<BottomTab> = listOf(
    BottomTab(
        destination = Destination.Home,
        label = "笔记",
        selectedIcon = Icons.AutoMirrored.Filled.Article,
        unselectedIcon = Icons.AutoMirrored.Outlined.Article
    ),
    // 第二格曾是「标签」：标签的终点是筛选，而筛选天然属于搜索，
    // 整格底栏交给"筛选器的管理页"偏重；这里换成复习中心（复习计划 + 提醒）。
    BottomTab(
        destination = Destination.Review,
        label = "复习",
        selectedIcon = Icons.Filled.Style,
        unselectedIcon = Icons.Outlined.Style
    ),
    BottomTab(
        destination = Destination.Assistant,
        label = "助手",
        selectedIcon = Icons.Default.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    ),
    BottomTab(
        destination = Destination.Settings,
        label = "我的",
        selectedIcon = Icons.Default.Person,
        unselectedIcon = Icons.Outlined.Person
    )
)

/**
 * 底部导航。
 *
 * 选中态由**一整颗胶囊**表达，并且把图标与文字一起包进去——指示器不只在图标上出现，
 * 文字同样随选中态变化（颜色从 `onSurfaceVariant` 过渡到 `primary`，字重加粗），
 * 于是「选中」这个信息在整项上都读得出来，而不是只有图标那一小块。
 *
 * **正中央是创建按钮**（设计原型 `zhilu_full_prototype.html`）：新建入口从首页右下角的
 * FAB 移到这里，成为全局唯一入口，同时把导入也收进它弹出的弹层。
 * 放在中间而不是右端，是因为它属于"四个平级页之外的第五件事"——居中才不会被误读成
 * 某个页的附属操作。
 */
@Composable
fun BottomBar(
    navController: NavHostController,
    onCreateClick: () -> Unit,
    createExpanded: Boolean = false
) {
    val tabs = bottomTabs()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column {
        ZhiLuDivider()
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = bottomInset)
                    .height(BarHeight)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.take(2).forEach { tab ->
                    BottomTabItem(
                        tab = tab,
                        selected = currentDestination.isOn(tab.destination),
                        onClick = { navController.navigateTopLevel(tab.destination) }
                    )
                }

                // 中央创建项与四个 tab 同槽位、同权重：不再"越界"上浮，
                // 因此也不必再画在 Surface 之外（见 CenterCreateItem 的说明）。
                CenterCreateItem(expanded = createExpanded, onClick = onCreateClick)

                tabs.drop(2).forEach { tab ->
                    BottomTabItem(
                        tab = tab,
                        selected = currentDestination.isOn(tab.destination),
                        onClick = { navController.navigateTopLevel(tab.destination) }
                    )
                }
            }
        }
    }
}

/**
 * 是否停在某个平级页上。
 *
 * 比的是 [Destination.route]（去掉路由模板的那一份），并按 `?` 之前的部分比：
 * 带参数的页面（如笔记编辑 `note/{noteId}`）拿整串或模板去比都会读不出选中态。
 */
private fun NavDestination?.isOn(destination: Destination): Boolean =
    this?.hierarchy?.any {
        it.route?.substringBefore('?') == destination.route.substringBefore('?')
    } == true

/**
 * 中央创建项：一枚与底栏**平齐**的小圆角按钮，里面只有一个 ＋（无文字）。
 *
 * 形态参照主流短视频/社交应用的底栏：白色小圆角块 + 深色加号，与四个 tab 同一行、同一高度，
 * 越不出导航条。这里用主题的主色当底、`onPrimary` 当加号色 —— 关系与参考图一致
 * （浅底深字 / 深底浅字），同时沿用「一色一义」里"强调色 = 主要动作"的既有约定。
 *
 * 走到这一步的原因：最初的 62dp 悬浮大圆钮够醒目，但越界压住列表内容（真机反馈）；
 * 中间版本改成"图标 + 新建文字"与 tab 完全同构，又丢了这个入口该有的分量。
 * 现在这样既不出界，也不跟四个平级页混成一样。
 *
 * 展开弹层时原地旋转 45°，＋ 变 ✕，顺带表达"点这里可以收起"。
 */
@Composable
private fun RowScope.CenterCreateItem(
    expanded: Boolean,
    onClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = motionSpring(),
        label = "create_item_rotation"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val paint = palettePaint(LocalThemePalette.current)
    val isAnimal = paint.componentBorder != Color.Unspecified
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            // 与 tab 一样关掉水波纹：这排按钮点得最频繁，整块泛灰比图标本身的变化抢眼
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // 动森：把 ＋ 做成**有厚度的实体按钮**（参考仓库 `AnimalButton` 的手法）——
        // 底下压一层同色当厚度、面抬起来，按下时面落到厚度上。纸墨保持原来的平面方块。
        // 动森用全胶囊（`Radius.Chip` 是百分比），纸墨保持它自己的 12dp 方块角。
        val shape = if (isAnimal) {
            RoundedCornerShape(Radius.Chip)
        } else {
            RoundedCornerShape(CreateButtonCorner)
        }
        val faceModifier = Modifier.size(
            width = CreateButtonWidth,
            height = CreateButtonHeight
        )
        ToySurface(
            // 高度显式给死（ToySurface 不再用 matchParentSize，见其 KDoc）
            faceHeight = CreateButtonHeight,
            modifier = Modifier.width(CreateButtonWidth),
            shape = shape,
            faceColor = MaterialTheme.colorScheme.primary,
            // 厚度 = 面色同色相压暗一档（参考仓库的 ShadowBtn 就是这个关系）
            thicknessColor = shade(MaterialTheme.colorScheme.primary),
            thickness = if (isAnimal) DefaultThickness else 0.dp,
            pressed = pressed
        ) {
            Box(
                modifier = faceModifier.background(MaterialTheme.colorScheme.primary, shape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (expanded) "收起新建菜单" else "新建或导入",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(IconSize)
                        .graphicsLayer { rotationZ = rotation }
                )
            }
        }
    }
}

/** 中央创建按钮的尺寸与圆角（比图标略大一圈，刚好包住 ＋）。 */
private val CreateButtonWidth = 46.dp
private val CreateButtonHeight = 32.dp
private val CreateButtonCorner = 12.dp

/**
 * 一个平级页 tab。
 *
 * 选中态**只改颜色**（设计原型 `.nav .tab.cur{color:var(--accent)}`）——
 * 原型里没有选中胶囊/色块，所以这里不画任何背景。
 * 图标另做「实心 ↔ 描边」的成对切换：光靠颜色表达选中，对色觉障碍与灰度屏是失效的，
 * 而图标形状的变化不改变版式，是这一条件下最省的冗余编码。
 */
@Composable
private fun RowScope.BottomTabItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "bottom_bar_content"
    )

    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            // 关掉水波纹：整块 tab 泛灰比"图标变了颜色"抢眼得多，而底栏是最高频的点击区，
            // 每次切页闪一下灰块很吵。
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(IconSize)
            )
            Spacer(modifier = Modifier.height(IconLabelGap))
            Text(
                text = tab.label,
                style = ZhiLuType.label,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
