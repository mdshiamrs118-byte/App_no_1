package com.openbrows.app.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.openbrows.app.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(viewModel: BrowserViewModel) {
    val settings by viewModel.settings.collectAsState(initial = BrowserSettings())
    val tabs by viewModel.tabs.collectAsState()
    val currentTabId by viewModel.currentTabId.collectAsState()

    var isToolbarVisible by remember { mutableStateOf(true) }
    var isToolbarLocked by remember { mutableStateOf(false) }
    var isVerticalTabExpanded by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val activeTab = tabs.find { it.id == currentTabId } ?: tabs.first()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF121212)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (settings.tabsPosition == TabsPosition.LEFT) {
                    VerticalTabPanel(
                        tabs = tabs,
                        currentTabId = currentTabId,
                        alwaysShowIcons = settings.verticalTabsAlwaysShowIcons,
                        isExpanded = isVerticalTabExpanded,
                        onToggleExpand = { isVerticalTabExpanded = !isVerticalTabExpanded },
                        onSelectTab = { viewModel.selectTab(it) },
                        onAddTab = { viewModel.addTab() }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        url?.let { activeTab.url = it }
                                    }
                                }
                                settings.javaScriptEnabled = true
                            }
                        },
                        update = { webView ->
                            if (webView.url != activeTab.url) {
                                webView.loadUrl(activeTab.url)
                            }
                            webView.settings.textZoom = settings.textSize.toInt()
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (settings.hideToolbarMode == HideToolbarMode.ON_AND_LOCK && !isToolbarVisible) {
                        FloatingActionButton(
                            onClick = {
                                isToolbarVisible = true
                                isToolbarLocked = false
                            },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_up),
                                contentDescription = "Show Toolbar"
                            )
                        }
                    }
                }

                if (settings.tabsPosition == TabsPosition.RIGHT) {
                    VerticalTabPanel(
                        tabs = tabs,
                        currentTabId = currentTabId,
                        alwaysShowIcons = settings.verticalTabsAlwaysShowIcons,
                        isExpanded = isVerticalTabExpanded,
                        onToggleExpand = { isVerticalTabExpanded = !isVerticalTabExpanded },
                        onSelectTab = { viewModel.selectTab(it) },
                        onAddTab = { viewModel.addTab() }
                    )
                }
            }

            AnimatedVisibility(
                visible = isToolbarVisible,
                enter = slideInVertically(initialOffsetY = { if (settings.urlPosition == UrlPosition.TOP) -it else it }),
                exit = slideOutVertically(targetOffsetY = { if (settings.urlPosition == UrlPosition.TOP) -it else it }),
                modifier = Modifier.align(if (settings.urlPosition == UrlPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    if (settings.tabsPosition == TabsPosition.BOTTOM) {
                        HorizontalTabsBar(
                            tabs = tabs,
                            currentTabId = currentTabId,
                            visibleCount = settings.tabsVisibleCountAtOnce,
                            onSelectTab = { viewModel.selectTab(it) },
                            onAddTab = { viewModel.addTab() }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    GlassBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeTab.url,
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )

                            IconButton(onClick = { showSettingsSheet = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_more),
                                    contentDescription = "Options",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            SettingsSheetContent(
                settings = settings,
                onUpdate = { viewModel.updateSettings(it) }
            )
        }
    }
}

@Composable
fun HorizontalTabsBar(
    tabs: List<TabModel>,
    currentTabId: String,
    visibleCount: Int,
    onSelectTab: (String) -> Unit,
    onAddTab: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val tabWidth = (screenWidth - 32.dp) / visibleCount.coerceAtLeast(1)

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tabs) { tab ->
            GlassBox(
                modifier = Modifier
                    .width(tabWidth)
                    .height(40.dp)
                    .clickable { onSelectTab(tab.id) }
            ) {
                Text(
                    text = tab.title,
                    color = if (tab.id == currentTabId) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        item {
            GlassBox(
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp)
                    .clickable { onAddTab() }
            ) {
                Text("+", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun VerticalTabPanel(
    tabs: List<TabModel>,
    currentTabId: String,
    alwaysShowIcons: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectTab: (String) -> Unit,
    onAddTab: () -> Unit
) {
    val showFull = isExpanded || !alwaysShowIcons
    val width = if (showFull && isExpanded) 160.dp else 48.dp

    GlassBox(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onToggleExpand) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_up),
                    contentDescription = "Expand",
                    tint = Color.White
                )
            }
            if (alwaysShowIcons || isExpanded) {
                tabs.forEach { tab ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clickable { onSelectTab(tab.id) }
                    ) {
                        Text(
                            text = if (showFull && isExpanded) tab.title else "T",
                            color = if (tab.id == currentTabId) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                IconButton(onClick = onAddTab) {
                    Text("+", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SettingsSheetContent(
    settings: BrowserSettings,
    onUpdate: (BrowserSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text("Browser Preferences", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Toolbar Position")
        Row {
            FilterChip(
                selected = settings.urlPosition == UrlPosition.BOTTOM,
                onClick = { onUpdate(settings.copy(urlPosition = UrlPosition.BOTTOM)) },
                label = { Text("Bottom") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = settings.urlPosition == UrlPosition.TOP,
                onClick = { onUpdate(settings.copy(urlPosition = UrlPosition.TOP)) },
                label = { Text("Top") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Tabs Position")
        Row {
            TabsPosition.values().forEach { pos ->
                FilterChip(
                    selected = settings.tabsPosition == pos,
                    onClick = { onUpdate(settings.copy(tabsPosition = pos)) },
                    label = { Text(pos.name) }
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Hide Toolbar Behavior")
        Column {
            HideToolbarMode.values().forEach { mode ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.hideToolbarMode == mode,
                        onClick = { onUpdate(settings.copy(hideToolbarMode = mode)) }
                    )
                    Text(mode.name)
                }
            }
        }
    }
}
