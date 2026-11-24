package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.auth.signOutUser
import com.example.data.PcbAiRepository
import com.example.data.SchematicRepository
import com.example.firebase.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.bom.BomExportScreen
import com.example.ui.editor.SchematicEditorScreen
import com.example.ui.projects.ProjectsScreen
import com.example.ui.theme.TraceLabTheme
import com.example.viewmodel.TraceLabViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_PROJECTS = "projects"
const val ROUTE_EDITOR = "editor"
const val ROUTE_BOM = "bom"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before Compose UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            TraceLabTheme(darkTheme = isDarkTheme) {
                AppContent(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

@Composable
fun AppContent(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = user.uid
        key(uid) {
            TraceLabWorkspace(
                currentUserId = uid,
                userEmail = user.email,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
            )
        }
    }
}

@Composable
private fun TraceLabWorkspace(
    currentUserId: String,
    userEmail: String?,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()

    val viewModel: TraceLabViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                TraceLabViewModel(
                    repository = SchematicRepository(db),
                    aiRepository = PcbAiRepository(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val validationResult by viewModel.validationResult.collectAsStateWithLifecycle()
    val selectedComponentUid by viewModel.selectedComponentUid.collectAsStateWithLifecycle()
    val selectedWireUid by viewModel.selectedWireUid.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val pendingWireStart by viewModel.pendingWireStart.collectAsStateWithLifecycle()
    val wireLayer by viewModel.wireLayer.collectAsStateWithLifecycle()
    val wireTraceWidthMm by viewModel.wireTraceWidthMm.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val syncErrorMessage by viewModel.syncErrorMessage.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val quickCheckState by viewModel.quickCheckState.collectAsStateWithLifecycle()
    val deepAuditState by viewModel.deepAuditState.collectAsStateWithLifecycle()
    val bomAdvisorState by viewModel.bomAdvisorState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = ROUTE_PROJECTS
    ) {
        composable(ROUTE_PROJECTS) {
            ProjectsScreen(
                projectsState = projectsState,
                userEmail = userEmail,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onOpenProject = { project ->
                    viewModel.openProject(project)
                    navController.navigate(ROUTE_EDITOR)
                },
                onCreateProject = { name, desc, template, layers, onError ->
                    viewModel.createProject(
                        name = name,
                        description = desc,
                        template = template,
                        layerCount = layers,
                        onCreated = {
                            navController.navigate(ROUTE_EDITOR)
                        },
                        onError = onError
                    )
                },
                onDeleteProject = { id, onError ->
                    viewModel.deleteProject(id, onError)
                },
                onSignOut = {
                    signOutUser(credentialManager, scope)
                }
            )
        }

        composable(ROUTE_EDITOR) {
            val project = activeProject
            if (project == null) {
                ProjectsScreen(
                    projectsState = projectsState,
                    userEmail = userEmail,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onOpenProject = { p -> viewModel.openProject(p) },
                    onCreateProject = { name, desc, template, layers, onError ->
                        viewModel.createProject(name, desc, template, layers, {}, onError)
                    },
                    onDeleteProject = { id, onError -> viewModel.deleteProject(id, onError) },
                    onSignOut = { signOutUser(credentialManager, scope) }
                )
            } else {
                SchematicEditorScreen(
                    project = project,
                    validationResult = validationResult,
                    selectedComponentUid = selectedComponentUid,
                    selectedWireUid = selectedWireUid,
                    activeTool = activeTool,
                    pendingWireStart = pendingWireStart,
                    wireLayer = wireLayer,
                    wireTraceWidthMm = wireTraceWidthMm,
                    syncStatus = syncStatus,
                    syncErrorMessage = syncErrorMessage,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    quickCheckState = quickCheckState,
                    deepAuditState = deepAuditState,
                    isDarkTheme = isDarkTheme,
                    onBack = {
                        viewModel.syncActiveProjectNow()
                        navController.popBackStack()
                    },
                    onOpenBom = {
                        navController.navigate(ROUTE_BOM)
                    },
                    onSyncNow = { viewModel.syncActiveProjectNow() },
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onSetTool = { viewModel.setEditorTool(it) },
                    onSetWireLayer = { viewModel.setWireLayer(it) },
                    onSetWireWidth = { viewModel.setWireTraceWidth(it) },
                    onSelectComponent = { viewModel.selectComponent(it) },
                    onSelectWire = { viewModel.selectWire(it) },
                    onAddComponentAtGrid = { type, gx, gy -> viewModel.addComponentAtGrid(type, gx, gy) },
                    onStartDragComponent = { viewModel.startComponentDrag() },
                    onDragComponentToGrid = { uid, gx, gy -> viewModel.moveComponentToGrid(uid, gx, gy) },
                    onEndDragComponent = { viewModel.endComponentDrag() },
                    onTapPin = { compUid, pinIdx -> viewModel.tapPinForWire(compUid, pinIdx) },
                    onRotateComponent = { viewModel.rotateSelectedComponent() },
                    onDeleteSelected = { viewModel.deleteSelectedElement() },
                    onUpdateComponent = { ref, lbl, valTxt, fp, mpn, price, volt ->
                        viewModel.updateSelectedComponent(ref, lbl, valTxt, fp, mpn, price, volt)
                    },
                    onUpdateWire = { net, w, lyr -> viewModel.updateSelectedWire(net, w, lyr) },
                    onUpdateBoardSettings = { n, d, w, h, l ->
                        viewModel.updateBoardSettings(n, d, w, h, l)
                    },
                    onRunQuickCheck = { viewModel.runQuickComponentCheck() },
                    onApplyQuickCheck = { viewModel.applyQuickCheckSuggestion(it) },
                    onRunDeepAudit = { viewModel.runDeepSchematicAudit() }
                )
            }
        }

        composable(ROUTE_BOM) {
            val project = activeProject
            if (project != null) {
                BomExportScreen(
                    project = project,
                    bomAdvisorState = bomAdvisorState,
                    onBack = { navController.popBackStack() },
                    onEnrichBom = { viewModel.enrichBomWithAi() },
                    onApplySuggestions = { viewModel.applyBomSuggestions(it) }
                )
            }
        }
    }
}
