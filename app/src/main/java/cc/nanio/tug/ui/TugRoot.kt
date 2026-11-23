package cc.nanio.tug.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cc.nanio.tug.R
import cc.nanio.tug.TugApp
import cc.nanio.tug.WebViewActivity
import cc.nanio.tug.net.InspectedTarget
import cc.nanio.tug.net.UrlInspector
import cc.nanio.tug.ui.screens.AddTaskScreen
import cc.nanio.tug.ui.screens.HomeScreen
import cc.nanio.tug.ui.screens.SettingsScreen
import cc.nanio.tug.ui.screens.SettingsSection
import cc.nanio.tug.ui.screens.SettingsSectionScreen
import cc.nanio.tug.ui.screens.TaskDetailScreen
import cc.nanio.tug.ui.screens.TasksScreen

private object Routes {
    const val HOME = "home"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"
    const val TASK_DETAIL = "task/{taskId}"
    const val SETTINGS_SECTION = "settings/{section}"

    fun taskDetail(id: Long): String = "task/$id"

    fun settingsSection(section: SettingsSection): String = "settings/${section.route}"
}

private data class BottomDest(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val bottomDestinations = listOf(
    BottomDest(Routes.HOME, R.string.nav_home, Icons.Outlined.Dashboard),
    BottomDest(Routes.TASKS, R.string.nav_tasks, Icons.Outlined.Download),
    BottomDest(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings),
)

@Composable
fun TugRoot() {
    val app = LocalContext.current.applicationContext as TugApp
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var addInitialUrl by rememberSaveable { mutableStateOf("") }
    var checkingLink by remember { mutableStateOf(false) }

    fun openAdd(url: String? = null) {
        addInitialUrl = url.orEmpty()
        showAddSheet = true
    }

    val context = LocalContext.current
    val webViewLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val downloadUrl = result.data
                ?.getStringExtra(WebViewActivity.EXTRA_DOWNLOAD_URL)
                ?.trim()
            if (!downloadUrl.isNullOrEmpty()) openAdd(downloadUrl)
        }
    }

    // 其他应用分享/打开进来的链接：直链或磁力直接新建下载，网页则交给 WebView。
    val pendingShare by app.pendingShare.collectAsState()
    LaunchedEffect(pendingShare) {
        val raw = pendingShare ?: return@LaunchedEffect
        checkingLink = true
        val target = runCatching { UrlInspector.inspect(raw) }
            .getOrElse { InspectedTarget.Failed(it.message ?: "URL preflight failed") }
        checkingLink = false
        when (target) {
            is InspectedTarget.Download -> openAdd(target.url)
            is InspectedTarget.WebPage -> {
                webViewLauncher.launch(WebViewActivity.intent(context, target.url))
            }
            is InspectedTarget.Unsupported -> {
                snackbarHostState.showSnackbar(context.getString(R.string.scan_unsupported))
            }
            is InspectedTarget.Failed -> {
                snackbarHostState.showSnackbar(target.message)
            }
        }
        // 必须在检查完成后再清空，否则 key 变化会取消当前 LaunchedEffect。
        app.pendingShare.value = null
    }

    // tug://task/{id} 深链 → 直达任务详情
    val pendingTaskId by app.pendingTaskId.collectAsState()
    LaunchedEffect(pendingTaskId) {
        val id = pendingTaskId ?: return@LaunchedEffect
        app.pendingTaskId.value = null
        navController.navigate(Routes.taskDetail(id))
    }

    val selectedRoute = when {
        currentRoute == Routes.HOME -> Routes.HOME
        currentRoute == Routes.TASKS -> Routes.TASKS
        currentRoute == Routes.SETTINGS || currentRoute == Routes.SETTINGS_SECTION -> Routes.SETTINGS
        else -> null
    }
    val showChrome = selectedRoute != null

    if (checkingLink) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(stringResource(R.string.scan_checking)) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showChrome) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                ) {
                    bottomDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = selectedRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(dest.icon, contentDescription = stringResource(dest.labelRes))
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.HOME || currentRoute == Routes.TASKS) {
                ExtendedFloatingActionButton(
                    onClick = { openAdd() },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.fab_new)) },
                )
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.padding(padding),
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            onAdd = { openAdd() },
                            onTorrentAdd = { url -> openAdd(url) },
                            onTasks = { navController.navigate(Routes.TASKS) },
                            onTaskClick = { id -> navController.navigate(Routes.taskDetail(id)) },
                        )
                    }
                    composable(Routes.TASKS) {
                        TasksScreen(
                            onTaskClick = { id -> navController.navigate(Routes.taskDetail(id)) },
                            onAdd = { openAdd() },
                        )
                    }
                    composable(
                        route = Routes.TASK_DETAIL,
                        arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
                    ) { entry ->
                        val taskId = entry.arguments?.getLong("taskId") ?: 0L
                        TaskDetailScreen(taskId = taskId, onBack = { navController.popBackStack() })
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onSection = { section ->
                                navController.navigate(Routes.settingsSection(section))
                            },
                        )
                    }
                    composable(Routes.SETTINGS_SECTION) { entry ->
                        val route = entry.arguments?.getString("section")
                        val section = SettingsSection.entries.firstOrNull { it.route == route }
                            ?: SettingsSection.Download
                        SettingsSectionScreen(
                            section = section,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }

                if (showAddSheet) {
                    AddTaskScreen(
                        initialUrl = addInitialUrl,
                        onDone = {
                            showAddSheet = false
                            addInitialUrl = ""
                        },
                        onCancel = {
                            showAddSheet = false
                            addInitialUrl = ""
                        },
                    )
                }
            }
        }
    }
}
