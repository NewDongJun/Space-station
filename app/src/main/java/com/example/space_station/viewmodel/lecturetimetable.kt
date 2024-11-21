package com.example.space_station.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.time.Duration


class LectureTimetable: ViewModel() {

    var data = mutableStateOf<List<List<String>>>(emptyList())
        private set

    var buildings = mutableStateOf<List<String>>(emptyList())
        private set

    var selectedBuilding = mutableStateOf<String>("")
        private set
    fun setSelectedBuilding(building: String) {
        selectedBuilding.value = building
    }

    var selectedFloor = mutableStateOf<String>("")
        private set
    var selectedFloorRooms = mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var selectedFloorUsedRooms = mutableStateOf<List<List<String>>>(emptyList())
        private set

    fun setSelectedFloor(
        floor: String,
        rooms: List<Pair<String, String>>,
        usedRooms: List<List<String>>
    ) {
        selectedFloor.value = floor
        selectedFloorRooms.value = rooms
        selectedFloorUsedRooms.value = usedRooms
    }

    // 디비에 저장 해야 하는 변수값
    var checkedInRooms = mutableStateOf<CheckedInRoom?>(null)
        private set
    fun CheckInCheck(building: String, floor: String, room: String): Boolean {
        val currentRoom = checkedInRooms.value
        return (currentRoom?.building == building
                && currentRoom.floor == floor &&
                currentRoom.room == room)
    }
    fun CheckInRoom(building: String, floor: String, room: String, checkOutTime: String) {
        checkedInRooms.value = CheckedInRoom(building, floor, room, checkOutTime)
    }
    fun CheckOutRoom(context: Context) {
        // 푸시 알림을 취소 함
        latestPushWorkId.value?.let {
            explicitlyCancelled.value = true
            WorkManager.getInstance(context).cancelWorkById(it)
            latestPushWorkId.value = null
        }
    }
    private fun checkOutRoom() {
        checkedInRooms.value = null
    }

    // 외부에서 전달받은 작업 ID로 작업 상태를 관찰
    // 디비에 저장 해야 하는 변수 값
    var latestPushWorkId = mutableStateOf<UUID?>(null)
        private set
    var explicitlyCancelled = mutableStateOf(false)
        private set
    fun observeWorkCompletion(workId: UUID, context: Context) {
        latestPushWorkId.value = workId
        WorkManager.getInstance(context).getWorkInfoByIdLiveData(workId).observeForever { workInfo ->
            if (workInfo != null) {
                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        // 성공적으로 완료된 경우
                        checkOutRoom()
                        latestPushWorkId.value = null
                    }
                    WorkInfo.State.CANCELLED -> {
                        if (explicitlyCancelled.value) {
                            // 명시적인 취소인 경우에만 체크아웃
                            // 덮어씌워져서 취소 된 경우는 다른 룸으로 체크인한 경우이므로 체크아웃하지 않음
                            checkOutRoom()
                            explicitlyCancelled.value = false
                        }
                    }
                    else -> {
                        // 기타 상태 처리 가능
                    }
                }
            }
        }
    }


    // 데이터를 비동기로 로드
    fun loadExcelData(context: Context, fileName: String = "lecturetimetable.xlsx") {
        viewModelScope.launch(Dispatchers.IO) {
            context.assets.open(fileName).use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)
                val newData = mutableListOf<List<String>>()

                // 첫 번째 행을 제외하고 데이터를 로드
                val columnCount = sheet.getRow(0).lastCellNum.toInt() // 첫 번째 행의 열 개수 기준으로 고정


                for (row in sheet.drop(1)) {
                    // 각 행을 고정된 열 개수로 맞춰서 빈 셀이 있으면 ""을 추가
                    val rowData = MutableList(columnCount) { index ->
                        row.getCell(index)?.toString() ?: ""
                    }
                    newData.add(rowData)
                }

                // 데이터 로드 완료 후 상태 업데이트
                data.value = newData
            }
        }
//        다빈치 캠퍼스 건물이 떠서 310관 이하로 가져가야 함
        buildings.value =
            data.value.mapNotNull { it.getOrNull(11) }.distinct().sorted().filter { it <= "310관" }
    }


    private fun extractionFloor(roomNumber: String): String {
        // 층수 추출 로직
        val floor = when {
            roomNumber.startsWith("B") -> roomNumber.take(2) // 'B'로 시작하면 'B'와 다음 숫자 포함
            roomNumber.takeWhile { it.isDigit() }.length >= 4 -> roomNumber.take(2) // 네 자리 숫자는 처음 두 자리 사용
            roomNumber.takeWhile { it.isDigit() }.length == 3 -> roomNumber.take(1) // 세 자리 숫자는 첫 자리만 사용
            else -> roomNumber.takeWhile { it.isDigit() } // 그 외의 숫자만 남김
        }
        return floor
    }

    fun getAllRoomsByBuilding(buildingName: String): Map<String, List<Pair<String, String>>> {
        return data.value
            .filter { it[11] == buildingName } // 특정 건물명과 일치하는 행 필터링
            .mapNotNull { row ->
                val roomNumber = row[12]
                val floor = extractionFloor(roomNumber)
                if (floor.isNotEmpty()) floor to roomNumber else null
            }
            .distinct()
            .sortedWith(compareBy { it.first.toIntOrNull() ?: Int.MAX_VALUE }) // 숫자로 변환하여 정렬
            .groupByTo(LinkedHashMap()) { it.first } // LinkedHashMap으로 그룹화하여 순서 보장
    }


    fun getUsedRooms(
        building: String,
        week: String,
        time: String
    ): Map<String, List<List<String>>> {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val targetTime = LocalTime.parse(time, formatter)

        val filteredData = data.value.filter { row ->
            // 건물, 요일 일치 여부 확인
            row[11] == building && row[9] == week &&
                    // 수업 시간이 타겟 시간과 겹치는지 확인
                    isTimeInRange(row[10], targetTime, formatter)
        }

        return filteredData.groupBy { row ->
            val roomNumber = row[12]
            val floor = extractionFloor(roomNumber)
            floor
        }
    }


    fun getNowKoreanDayOfWeek(): String {
        val currentDateTime = LocalDateTime.now()
        return when (currentDateTime.dayOfWeek) {
            DayOfWeek.MONDAY -> "월"
            DayOfWeek.TUESDAY -> "화"
            DayOfWeek.WEDNESDAY -> "수"
            DayOfWeek.THURSDAY -> "목"
            DayOfWeek.FRIDAY -> "금"
            DayOfWeek.SATURDAY -> "토"
            DayOfWeek.SUNDAY -> "일"
        }
    }

    fun getNowKoreanTime(): String {
        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        return currentDateTime.format(formatter)
    }

    fun getNextLectureTime(
        buildings: String,
        floor: String,
        room: String,
        time: String,
        week: String
    ): String? {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val targetTime = LocalTime.parse(time, formatter)

        val filteredData = data.value.filter { row ->
            row[11] == buildings &&
                    row[12] == room &&
                    row[9] == week &&
                    LocalTime.parse(row[10].substringBefore("~"), formatter).isAfter(targetTime)
        }

        // 가장 가까운 이후 시간의 강의 찾기
        val nextLecture = filteredData.minByOrNull {
            LocalTime.parse(it[10].substringBefore("~"), formatter)
        }

        // 다음 강의의 시작 시간을 반환 (HH:mm~HH:mm 형식에서 시작 시간만 반환 없으면 널 반환)
        return nextLecture?.get(10)?.substringBefore("~")
    }

    private fun isTimeInRange(
        timeRange: String,
        targetTime: LocalTime,
        formatter: DateTimeFormatter
    ): Boolean {
        // 수업 시간 범위를 시작 시간과 종료 시간으로 분리
        val (start, end) = timeRange.split("~").map { it.trim() }
        val startTime = LocalTime.parse(start, formatter)
        val endTime = LocalTime.parse(end, formatter)

        // 타겟 시간이 시작 시간과 종료 시간 사이에 있는지 확인
        return targetTime.isAfter(startTime) && targetTime.isBefore(endTime)
    }


    fun getMinuteDifference(timeString: String): Long {
        // "HH:mm" 형식의 문자열을 LocalTime 객체로 변환
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val inputTime = LocalTime.parse(timeString, formatter)

        // 현재 시간을 LocalTime으로 가져옴
        val now = LocalTime.now()

        // inputTime - 현재 시간 차이를 분 단위로 계산
        return Duration.between(now, inputTime).toMinutes()
    }


//    // 여기부터 시간표 부분을 위한 코드
//    private val _subjects = MutableLiveData<List<TimetableSubject>>()
//    val subjects: LiveData<List<TimetableSubject>> = _subjects
//
//    fun getLecturesBySubject(query: String, searchType: String): List<Lecture> {
//        return data.value
//            .groupBy { it[3] } // courseCode로 그룹화
//            .map { (_, rows) ->
//                val firstRow = rows.first()
//                Lecture(
//                    courseCode = firstRow[3],
//                    courseName = firstRow[4],
//                    professor = firstRow[6],
//                    buildingInfo = firstRow[11],
//                    roomInfo = firstRow[12],
//                    schedules = rows.map { row ->
//                        Schedule(
//                            day = row[9],
//                            time = row[10]
//                        )
//                    }
//                )
//            }
//            .filter { lecture ->
//                when (searchType) {
//                    "과목명" -> lecture.courseName.contains(query, ignoreCase = true)
//                    "교수명" -> lecture.professor.contains(query, ignoreCase = true)
//                    "장소명" -> {
//                        lecture.buildingInfo.contains(query, ignoreCase = true) ||
//                                lecture.roomInfo.contains(query, ignoreCase = true)
//                    }
//                    "코드번호" -> lecture.courseCode.contains(query, ignoreCase = true)
//                    else -> false
//                }
//            }
//    }
//
//    fun loadExcelData2(context: Context) {
//        // 데이터를 로드하는 로직을 구현한 뒤 subjects에 데이터를 저장
//        _subjects.value = listOf(
//            TimetableSubject("11-1", "월", 9, 30, 11, 0, "모바일 앱 개발", "100관", "100호"),
//            TimetableSubject("222", "화", 10, 0, 12, 0, "IOT 프로젝트","100관", "100호"),
//            TimetableSubject("333", "수", 13, 30, 15, 30, "ACT", "100관", "100호")
//        )
//    }
//
//    fun splitTimeString(timeString: String): List<Int> {
//        val regex = Regex("[~:]") // ~ 또는 :를 기준으로 분리
//        return regex.split(timeString).mapNotNull { it.toIntOrNull() } // 각 부분을 정수로 변환, 변환 실패시 null 반환하여 제외
//    }
//
//    fun Lecture.toTimetableSubjects(): List<TimetableSubject> {
//        return schedules.map { schedule ->
//            val timeParts = splitTimeString(schedule.time)
//            TimetableSubject(
//                courseCode = this.courseCode,
//                day = schedule.day,
//                startHour = timeParts[0],
//                startMinute = timeParts[1],
//                endHour = timeParts[2],
//                endMinute = timeParts[3],
//                name = this.courseName,
//                roomInfo = this.roomInfo,
//                buildingInfo = this.buildingInfo
//            )
//        }
//    }
//
//    fun addSubject(lecture: Lecture): Boolean {
//        val newSubjects = lecture.toTimetableSubjects()
//        // 모든 새로운 강의 시간에 대해 겹치는지 확인
//        val isConflicting = newSubjects.any { newSubject ->
//            _subjects.value.orEmpty().any { existingSubject ->
//                existingSubject.day == newSubject.day &&
//                        !(newSubject.endHour < existingSubject.startHour ||
//                                (newSubject.endHour == existingSubject.startHour && newSubject.endMinute <= existingSubject.startMinute) ||
//                                newSubject.startHour > existingSubject.endHour ||
//                                (newSubject.startHour == existingSubject.endHour && newSubject.startMinute >= existingSubject.endMinute))
//            }
//        }
//
//        return if (isConflicting) {
//            println("겹치는 시간이 있습니다. 다른 시간에 추가해주세요.")
//            false
//        } else {
//            // 겹치지 않으면 모든 시간을 시간표에 추가
//            val updatedList = _subjects.value.orEmpty().toMutableList()
//            updatedList.addAll(newSubjects)
//            _subjects.value = updatedList
//            true
//        }
//    }
//
//    fun removeSubject(subjectToRemove: TimetableSubject) {
//        viewModelScope.launch {
//            // 삭제할 과목의 이름과 동일한 모든 과목을 필터링하여 제거
//            _subjects.value = _subjects.value?.filter { it.courseCode != subjectToRemove.courseCode }
//        }
//    }
//
//    fun getNextSubjectStartTime(): String? {
//        val currentDateTime = LocalDateTime.now()
//        val currentDay = getNowKoreanDayOfWeek()
//        val currentTime = LocalTime.of(currentDateTime.hour, currentDateTime.minute)
//
//        val todaySubjects = _subjects.value.orEmpty().filter { it.day == currentDay }
//
//        val nextSubject = todaySubjects
//            .filter { subject ->
//                val subjectStartTime = LocalTime.of(subject.startHour, subject.startMinute)
//                subjectStartTime.isAfter(currentTime)
//            }
//            .minByOrNull { subject ->
//                // 가장 가까운 시작 시간을 찾기 위해 정렬 기준 설정
//                LocalTime.of(subject.startHour, subject.startMinute)
//            }
//
//        return nextSubject?.let {
//            "%02d:%02d".format(it.startHour, it.startMinute)
//        }
//    }
//
//    fun getPresentSubject() : TimetableSubject? {
//        val currentDateTime = LocalDateTime.now()
//        val currentDay = getNowKoreanDayOfWeek()
//        val currentTime = LocalTime.of(currentDateTime.hour, currentDateTime.minute)
//
//        val todaySubjects = _subjects.value.orEmpty().filter { it.day == currentDay }
//
//        return todaySubjects.find { subject ->
//            val subjectStartTime = LocalTime.of(subject.startHour, subject.startMinute)
//            val subjectEndTime = LocalTime.of(subject.endHour, subject.endMinute)
//            currentTime.isAfter(subjectStartTime) && currentTime.isBefore(subjectEndTime)
//        }
//    }
//
//    // db에 저장할 정보 return
//    fun updateUserSubjectToDB() : List<String> {
//        val subjectList = _subjects.value ?: return emptyList()
//        return subjectList.map { it.courseCode }
//    }
//
//    // db에서 정보 받아서 사용자 시간표 로드
//    fun loadUserTimeTableFromDB(courseCodeList : List<String>) {
//        val updatedSubjects = mutableListOf<TimetableSubject>()
//
//        courseCodeList.forEach { courseCode ->
//            val lectures = getLecturesBySubject(courseCode, "코드번호")
//
//            lectures.forEach { lecture ->
//                updatedSubjects.addAll(lecture.toTimetableSubjects())
//            }
//        }
//        _subjects.value = updatedSubjects
//    }

}

data class CheckedInRoom(
    val building: String ="",
    val floor: String="",
    val room: String="",
    val checkOutTime: String="",
)
