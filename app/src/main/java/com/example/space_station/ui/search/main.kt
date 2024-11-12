package com.example.space_station.ui.search

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.space_station.ui.timetable.AddTimetablePage
import com.example.space_station.ui.timetable.TimetablePage

import com.example.space_station.viewmodel.LectureTimetable

//서치페이지의 메인 페이지


@Composable
fun SearchMain(
    lectureTimetable: LectureTimetable,
) {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "buildings"
    ) {
        composable(route = "buildings") {
            Buildings(
                lectureTimetable = lectureTimetable,
                navigator = { navController.navigate("floors") }
            )
        }
        composable(route = "floors") {
            Floors(
                lectureTimetable = lectureTimetable,
                backNavigator = { navController.navigateUp() },
                navigator = { navController.navigate("rooms") }
            )
        }
        composable(route = "rooms") {
            Rooms(
                lectureTimetable = lectureTimetable,
                backNavigator = { navController.navigateUp() }
            )
        }
        composable(route = "timetable") {
            TimetablePage(
                onAddButtonClicked = {navController.navigate("addTimetable")},
                onSettingClicked = {}
            )
        }
        composable(route = "addTimetable") {
            AddTimetablePage(
                backNavigator = { navController.navigateUp() }
            )
        }
    }

}