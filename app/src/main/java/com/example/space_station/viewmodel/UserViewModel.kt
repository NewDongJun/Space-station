package com.example.space_station.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.space_station.firebase.FirebaseManager
import com.example.space_station.model.UserData
import com.example.space_station.model.UserSettingData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class UserViewModel: ViewModel() {
    private var _userData = MutableStateFlow(UserData())
    var userData :StateFlow<UserData> = _userData.asStateFlow()

    private var _userSettingData = MutableStateFlow(UserSettingData())
    var userSettingData :StateFlow<UserSettingData> = _userSettingData.asStateFlow()

    fun updateUid(uid:String){
        _userData.update {
            it.copy(
                uid = uid
            )
        }
    }



    fun updateUserSettingData(userSettingData: UserSettingData){
        _userSettingData.update {
            it.copy(

                bookmarks = userSettingData.bookmarks,
                pushAvailable = userSettingData.pushAvailable,
                nickname = userSettingData.nickname,
                room = userSettingData.room,
                timetable = userSettingData.timetable
            )

        }
        FirebaseManager.instance.updateUserSettingData(_userData.value.uid,_userSettingData.value)

    }

    fun updatePushSetting(isPushAvailable:Boolean){
        _userSettingData.update {
            it.copy(

                pushAvailable = isPushAvailable,

            )

        }
        FirebaseManager.instance.updateUserSettingData(_userData.value.uid,_userSettingData.value)
    }

    fun updateNickname(nickname :String){
        _userSettingData.update {
            it.copy(

                nickname = nickname,

                )

        }
        FirebaseManager.instance.updateUserSettingData(_userData.value.uid,_userSettingData.value)

    }

    fun updateTimeTable(timetable : List<String>){
        _userSettingData.update {
            it.copy(

                timetable = timetable

                )

        }
        FirebaseManager.instance.updateUserSettingData(_userData.value.uid,_userSettingData.value)
    }

    fun updateBookmark(bookmarks:Map<String,Boolean>){

        _userSettingData.update {
            it.copy(
                bookmarks = bookmarks
            )
        }

        FirebaseManager.instance.updateUserSettingData(_userData.value.uid,_userSettingData.value)
    }

    fun updateCheckInRoom(checkedInRoom: CheckedInRoom?){
        _userSettingData.update {
            it.copy(
                room = if(checkedInRoom!=null)checkedInRoom else CheckedInRoom()
            )
        }

        FirebaseManager.instance.updateUserSettingData(_userData.value.uid,_userSettingData.value)
    }

    fun updateCheckInRoomPushID(uuid: UUID?){
        if(uuid != null){
            _userSettingData.update {
                it.copy(
                    uuid = uuid.toString()
                )
            }
        }
        else{
            _userSettingData.update {
                it.copy(
                    uuid = ""
                )
            }
        }
        FirebaseManager.instance.updateUserSettingData(_userData.value.uid,_userSettingData.value)
    }

}