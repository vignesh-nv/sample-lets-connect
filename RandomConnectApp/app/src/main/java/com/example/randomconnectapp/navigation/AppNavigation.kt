package com.example.randomconnectapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.randomconnectapp.ui.auth.OTPScreen
import com.example.randomconnectapp.ui.auth.PhoneNumberScreen
import com.example.randomconnectapp.ui.call.CallScreen // Import CallScreen
import com.example.randomconnectapp.ui.home.HomeScreen 
import com.example.randomconnectapp.ui.user.UserDetailsScreen 
import com.example.randomconnectapp.viewmodel.AuthViewModel

object AppDestinations {
    const val PHONE_NUMBER_INPUT = "phone_number_input"
    const val OTP_VERIFICATION = "otp_verification/{phoneNumber}" 
    const val USER_DETAILS_INPUT = "user_details_input" 
    const val HOME = "home" 
    // Added call_screen destination with arguments
    const val CALL_SCREEN = "call_screen?callId={callId}&targetUserId={targetUserId}&offerSdp={offerSdp}&offerType={offerType}"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel() 
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        when (authState) {
            AuthViewModel.AuthState.NEEDS_DETAILS -> {
                navController.navigate(AppDestinations.USER_DETAILS_INPUT) {
                    popUpTo(AppDestinations.PHONE_NUMBER_INPUT) { inclusive = true }
                }
                 authViewModel.resetVerificationState() 
            }
            AuthViewModel.AuthState.AUTHENTICATED -> {
                navController.navigate(AppDestinations.HOME) {
                    popUpTo(AppDestinations.PHONE_NUMBER_INPUT) { inclusive = true }
                }
                 authViewModel.resetVerificationState()
            }
            AuthViewModel.AuthState.IDLE -> {
                if (navController.currentDestination?.route != AppDestinations.PHONE_NUMBER_INPUT &&
                    navController.currentDestination?.route != AppDestinations.OTP_VERIFICATION && // Don't pop if on OTP screen
                    navController.currentDestination?.route != AppDestinations.USER_DETAILS_INPUT // Don't pop if on UserDetails
                   ) {
                    navController.navigate(AppDestinations.PHONE_NUMBER_INPUT) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            }
        }
    }


    NavHost(navController = navController, startDestination = AppDestinations.PHONE_NUMBER_INPUT) {
        composable(AppDestinations.PHONE_NUMBER_INPUT) {
            PhoneNumberScreen(navController = navController, authViewModel = authViewModel)
        }
        composable(
            route = AppDestinations.OTP_VERIFICATION,
            arguments = listOf(navArgument("phoneNumber") { type = NavType.StringType })
        ) { backStackEntry ->
            val phoneNumber = backStackEntry.arguments?.getString("phoneNumber")
            requireNotNull(phoneNumber) { "Phone number cannot be null" }
            OTPScreen(
                navController = navController,
                authViewModel = authViewModel,
                phoneNumber = phoneNumber
            )
        }
        composable(AppDestinations.USER_DETAILS_INPUT) {
            UserDetailsScreen(navController = navController) 
        }
        composable(AppDestinations.HOME) {
            // Pass NavController to HomeScreen so it can navigate to CallScreen
            HomeScreen(navController = navController) 
        }
        composable(
            route = AppDestinations.CALL_SCREEN,
            arguments = listOf(
                navArgument("callId") { nullable = true; type = NavType.StringType },
                navArgument("targetUserId") { nullable = true; type = NavType.StringType },
                navArgument("offerSdp") { nullable = true; type = NavType.StringType },
                navArgument("offerType") { nullable = true; type = NavType.StringType }
            )
        ) { backStackEntry ->
            CallScreen(
                navController = navController,
                callId = backStackEntry.arguments?.getString("callId"),
                targetUserId = backStackEntry.arguments?.getString("targetUserId"),
                offerSdpString = backStackEntry.arguments?.getString("offerSdp"),
                offerSdpType = backStackEntry.arguments?.getString("offerType")
            )
        }
    }
}
