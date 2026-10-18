package com.company.fieldincidentreporter.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.company.fieldincidentreporter.ui.detail.IncidentDetailScreen
import com.company.fieldincidentreporter.ui.form.IncidentFormScreen
import com.company.fieldincidentreporter.ui.list.IncidentListScreen
import kotlinx.serialization.Serializable

@Serializable
data object ListRoute

@Serializable
data class FormRoute(val id: String = "")

@Serializable
data class DetailRoute(val id: String)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    NavHost(navController = navController, startDestination = ListRoute) {
        composable<ListRoute> {
            IncidentListScreen(
                onCreate = { navController.navigate(FormRoute()) },
                onOpen = { id -> navController.navigate(DetailRoute(id)) },
            )
        }
        composable<FormRoute> { entry ->
            val route = entry.toRoute<FormRoute>()
            IncidentFormScreen(
                onExit = { navController.popBackStack() },
                onSaved = { id ->
                    Toast.makeText(context, if (route.id.isBlank()) "Report submitted" else "Report updated", Toast.LENGTH_SHORT).show()
                    if (route.id.isBlank()) {
                        navController.navigate(DetailRoute(id)) {
                            popUpTo<FormRoute> { inclusive = true }
                        }
                    } else navController.popBackStack()
                },
                onQueued = {
                    navController.navigate(ListRoute) { popUpTo<ListRoute> { inclusive = true } }
                },
            )
        }
        composable<DetailRoute> { entry ->
            val route = entry.toRoute<DetailRoute>()
            IncidentDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(FormRoute(route.id)) },
                onDeleted = {
                    Toast.makeText(context, "Report deleted", Toast.LENGTH_SHORT).show()
                    navController.navigate(ListRoute) {
                        popUpTo<ListRoute> { inclusive = true }
                    }
                },
            )
        }
    }
}
