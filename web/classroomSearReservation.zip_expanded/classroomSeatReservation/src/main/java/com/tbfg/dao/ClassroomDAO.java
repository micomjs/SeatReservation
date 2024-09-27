package com.tbfg.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.tbfg.dto.BanSeatDTO;
import com.tbfg.dto.ClassroomDTO;
import com.tbfg.dto.ReserveList;

@Repository
public class ClassroomDAO {
    @Autowired // JdbcTemplate 객체를 자동으로 주입하기 위한 어노테이션
    private final JdbcTemplate jdbcTemplate; // JdbcTemplate 객체 선언

    // 생성자
    public ClassroomDAO(JdbcTemplate jdbcTemplate) { 
        this.jdbcTemplate = jdbcTemplate; // JdbcTemplate 객체 초기화
    }
    
    // 좌석 예약을 추가하는 메서드
    public int reserveSeat(String userId, String classroomName, String subject, Integer seatNumber, String day, Integer randomNumber) {
        // Reservation 테이블에 예약 정보 (사용자 ID, 강의실 이름, 좌석 번호, 랜덤 번호)를 삽입하는 SQL 쿼리
        String sql = "INSERT INTO Reservation (user_id, classroom_name, subject, reservSeat, day, reservNum) VALUES (?, ?, ?, ?, ?, ?)";
        
        // jdbcTemplate을 사용해 SQL 쿼리를 실행하고, 영향받은 행의 수를 반환
        return jdbcTemplate.update(sql, userId, classroomName, subject, seatNumber, day, randomNumber);
    }

    // 예약 번호와 시간대를 ReservationHour 테이블에 추가하는 메서드
    public void addReservationHour(int reservNum, int hour) {
        // ReservationHour 테이블에 예약 번호와 해당 시간대를 삽입하는 SQL 쿼리
        String sql = "INSERT INTO ReservationHour (reservNum, reservHour) VALUES (?, ?)";
        
        // jdbcTemplate을 사용해 SQL 쿼리를 실행
        jdbcTemplate.update(sql, reservNum, hour);
    }

    // 특정 강의실, 시간대, 요일, 과목명에 따라 예약된 좌석을 가져오는 메서드
    public List<Integer> getReservedSeats(String classroomName, List<Integer> hours, String day, String subject) {
        if (hours == null || hours.isEmpty()) {
            return List.of(); // 빈 리스트 반환
        }
        
        // 예약된 좌석을 가져오는 SQL 쿼리
        String sql = "SELECT r.reservSeat "
                   + "FROM Reservation r "
                   + "JOIN ReservationHour rh ON r.reservNum = rh.reservNum "
                   + "WHERE r.classroom_name = ? "  // 강의실 이름 필터링
                   + "AND rh.reservHour IN (?) "   // 시간대 필터링
                   + "AND r.day = ? "        // 요일 필터링
                   + "AND r.subject = ?";      // 과목명 필터링
        
        // 시간대를 쉼표로 구분된 문자열로 변환
        String hoursParam = hours.stream()
                                 .map(String::valueOf)
                                 .collect(Collectors.joining(","));
        
        // 쿼리 실행하고 결과 반환
        return jdbcTemplate.queryForList(sql, Integer.class, classroomName, hoursParam, day, subject);
    }


    // 모든 강의실의 예약 정보를 가져오는 메서드
    public List<ReserveList> reserveList() {
        // 모든 강의실과 예약 정보를 가져오는 SQL 쿼리
        String sql = "SELECT r.reservNum, r.user_id, r.classroom_name, r.subject, r.reservSeat, r.day, rh.reservHour "
                + "FROM Reservation r "
                + "JOIN ReservationHour rh ON r.reservNum = rh.reservNum";

        // 쿼리를 실행하고 결과를 ReserveList 객체의 리스트로 반환
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            ReserveList reserveList = new ReserveList();
            reserveList.setReservNum(rs.getInt("reservNum"));
            reserveList.setUserId(rs.getString("user_id"));
            reserveList.setClassroomName(rs.getString("classroom_name"));
            reserveList.setSubject(rs.getString("subject"));
            reserveList.setReservSeat(rs.getInt("reservSeat"));
            reserveList.setDay(rs.getString("day"));
            reserveList.setReservHour(rs.getInt("reservHour"));
            return reserveList;
        });
    }
    
    // 강의실별로 예약 정보를 그룹화하고 예약 번호별로 시간대 묶기
    public Map<String, List<ReserveList>> getReserveList(String userId) {
        // 모든 예약 정보를 가져옴
        List<ReserveList> reserveList = reserveList();

        // 예약 번호별로 그룹화된 예약 정보를 저장할 맵
        Map<Integer, ReserveList> groupedByReservNum = new LinkedHashMap<>();

        // 각 예약 정보에 대해 반복
        for (ReserveList reserve : reserveList) {
            // 현재 예약이 로그인한 사용자와 일치하는지 확인
            if (reserve.getUserId().equals(userId)) {
                int reservNum = reserve.getReservNum();  // 현재 예약의 예약 번호를 가져옴

                // 동일한 예약 번호가 이미 맵에 존재하는지 확인
                if (groupedByReservNum.containsKey(reservNum)) {
                    // 동일한 예약 번호가 이미 존재하면, 기존 예약 정보에 현재 시간대를 추가
                    ReserveList existingReservation = groupedByReservNum.get(reservNum);
                    String hoursString = existingReservation.getReservHourString() + "," + reserve.getReservHour();
                    existingReservation.setReservHourString(hoursString);  // 시간대를 문자열로 합침
                } else {
                    // 동일한 예약 번호가 없으면, 현재 예약을 맵에 추가
                    reserve.setReservHourString(String.valueOf(reserve.getReservHour()));  // 초기 시간대 설정
                    groupedByReservNum.put(reservNum, reserve);
                }
            }
        }

        // 예약 번호별로 그룹화된 예약 정보를 강의실 이름별로 다시 그룹화하여 반환
        return groupedByReservNum.values().stream()
                .collect(Collectors.groupingBy(ReserveList::getClassroomName));
    }
    
    // 예약 취소 메서드
    public boolean cancelReservation(int reservNum, String userId) {
        // 예약 삭제를 위한 SQL 쿼리
        String deleteReservationSql = "DELETE FROM Reservation WHERE reservNum = ? AND user_id = ?";
        // 예약 시간 삭제를 위한 SQL 쿼리
        String deleteReservationHourSql = "DELETE FROM ReservationHour WHERE reservNum = ?";

        // Reservation 테이블에서 예약 삭제 시도
        int reservationSql = jdbcTemplate.update(deleteReservationSql, reservNum, userId);

        if (reservationSql > 0) {
            // 예약이 성공적으로 삭제된 경우, ReservationHour 테이블에서 해당 예약 번호에 관련된 모든 시간 삭제
            int reservationHourSql = jdbcTemplate.update(deleteReservationHourSql, reservNum);
            // 시간 삭제 작업이 성공적으로 수행되었는지 확인
            return reservationHourSql >= 0; // 예약 시간 삭제 여부를 확인
        }

        // 예약 삭제가 실패한 경우
        return false;
    }
    
    // 예약 시간 변경 메소드
    public boolean updateReservation(int reservNum, String newHour, String userId) {
        // 예약 시간 업데이트 전에 기존 예약 시간 삭제를 위한 SQL 쿼리
        // 예약이 존재하고 사용자 ID가 일치하는 경우에만 예약 시간 삭제
        String deleteSql = "DELETE FROM ReservationHour WHERE reservNum = ? AND EXISTS (SELECT 1 FROM Reservation WHERE reservNum = ? AND user_id = ?)";
        jdbcTemplate.update(deleteSql, reservNum, reservNum, userId);
        
        // 새로운 시간대 문자열을 쉼표로 분리하여 배열로 변환
        String[] hours = newHour.split(",");
        // 새로운 예약 시간을 삽입하기 위한 SQL 쿼리
        String insertSql = "INSERT INTO ReservationHour (reservNum, reservHour) VALUES (?, ?)";

        int insertHourSql = 0;
        // 새로운 시간대 배열을 순회하며 각각의 시간대에 대해 예약 시간 삽입
        for (String hour : hours) {
        	insertHourSql += jdbcTemplate.update(insertSql, reservNum, Integer.parseInt(hour));
        }

        // 모든 새로운 예약 시간대가 성공적으로 삽입되었는지 확인
        return insertHourSql == hours.length;
    }
    
    // 사용자 아이디를 받아 즐겨찾기한 강의실 목록을 가져오는 메서드
    public List<String> getFavoriteClassrooms(String userId) {
        // MySQL에서 즐겨찾기한 강의실 목록을 가져오기 위한 쿼리문
        String sql = "SELECT classroom_num FROM FavoriteClassrooms WHERE user_id = ?";
        // jdbcTemplate을 사용하여 SQL 쿼리를 실행하고 결과를 리스트로 반환
        return jdbcTemplate.queryForList(sql, String.class, userId);
    }
    
    // 사용자 아이디를 받아 timetable의 강의실 정보를 favoriteClassrooms에 추가하는 메서드
    public void getTimetableClassrooms(String userId) {
        // MySQL에서 즐겨찾기할 강의실 목록을 가져와 favoriteClassrooms에 추가하는 쿼리문
        String sql = "INSERT INTO FavoriteClassrooms (user_id, classroom_num) "
                   + "SELECT DISTINCT ?, classroomName "
                   + "FROM StuTimetable WHERE user_id = ? AND (user_id, classroomName) "
                   + "NOT IN (SELECT user_id, classroom_num FROM FavoriteClassrooms)";

        // jdbcTemplate을 사용하여 SQL 쿼리를 실행
        jdbcTemplate.update(sql, userId, userId);
    }

    // 모든 강의 번호 목록을 가져오는 메서드
    public List<String> getClassNum() {
        // MySQL에서 모든 강의 번호를 가져오기 위한 쿼리문
        String sql = "SELECT DISTINCT LEFT(classroom_name, 1) AS classNum FROM Classrooms";
        // jdbcTemplate을 사용하여 SQL 쿼리를 실행하고 결과를 리스트로 반환
        return jdbcTemplate.queryForList(sql, String.class);
    }
    
    // 건물 번호를 받아 해당하는 강의실 목록을 가져오는 메서드
    public List<String> getClassrooms(String classNum) {
        // 해당 건물 번호로 시작하는 강의실을 가져오기 위한 검색 패턴 생성
        String prefix = classNum + "%";
        // MySQL에서 해당 건물 번호로 시작하는 강의실 목록을 가져오기 위한 쿼리문
        String sql = "SELECT classroom_name FROM Classrooms WHERE classroom_name LIKE ?";
        // jdbcTemplate을 사용하여 SQL 쿼리를 실행하고 결과를 리스트로 반환
        return jdbcTemplate.queryForList(sql, String.class, prefix);
    }
    
    // 강의실 좌석 금지 정보를 데이터베이스에 삽입하는 메서드
    public int insertBanSeat(BanSeatDTO banSeat) {
        // 삽입할 SQL 쿼리
        String sql = "INSERT INTO BanSeat (user_id, classroom_name, subject, banSeat, day) VALUES (?, ?, ?, ?, ?)";
        // 자동 생성된 키(banNum)를 저장하기 위한 KeyHolder 객체 생성
        KeyHolder keyHolder = new GeneratedKeyHolder();

        // jdbcTemplate을 사용하여 쿼리 실행
        jdbcTemplate.update(connection -> {
            // PreparedStatement 생성 및 파라미터 설정
            PreparedStatement ps = connection.prepareStatement(sql, new String[] {"banNum"});
            ps.setString(1, banSeat.getUserId());         // 첫 번째 파라미터: 사용자 ID
            ps.setString(2, banSeat.getClassroomName());  // 두 번째 파라미터: 강의실 이름
            ps.setString(3, banSeat.getSubject());  	  // 세 번째 파라미터: 강의실 이름
            ps.setInt(4, banSeat.getBanSeat());           // 네 번째 파라미터: 금지된 좌석 번호
            ps.setString(5, banSeat.getDay());  		  // 다섯 번째 파라미터: 요일
            return ps; // PreparedStatement 반환
        }, keyHolder);

        // 생성된 banNum 반환
        return keyHolder.getKey().intValue();
    }

    // 특정 banNum에 대해 금지된 시간을 데이터베이스에 삽입하는 메서드
    public void insertBanSeatHour(int banNum, int banHour) {
        // 삽입할 SQL 쿼리
        String sql = "INSERT INTO BanSeatHour (banNum, banHour) VALUES (?, ?)";
        // jdbcTemplate을 사용하여 쿼리 실행
        jdbcTemplate.update(sql, banNum, banHour); // banNum과 banHour를 파라미터로 사용
    }

    @SuppressWarnings("deprecation")
    public List<Integer> getBannedSeats(String classroomName, List<Integer> selectHours, String subject, String day) {
        // SQL 쿼리 작성
        String sql = "SELECT DISTINCT b.banSeat " +
                     "FROM BanSeat b " +
                     "JOIN BanSeatHour h ON b.banNum = h.banNum " +
                     "WHERE b.classroom_name = ? " +
                     "AND h.banHour IN (" + String.join(",", selectHours.stream().map(String::valueOf).toArray(String[]::new)) + ") " +
                     "AND b.subject = ? " +  
                     "AND b.day = ?";

        // 쿼리 실행 및 결과 매핑
        return jdbcTemplate.query(sql, new Object[]{classroomName, subject, day}, new RowMapper<Integer>() {
            @Override
            public Integer mapRow(ResultSet rs, int rowNum) throws SQLException {
                // 쿼리 결과로부터 banSeat 컬럼 값을 Integer로 반환
                return rs.getInt("banSeat");
            }
        });
    }
    
    // 강의실 이름 목록을 데이터베이스에서 가져오는 메서드
    public List<String> getAllClassrooms() {
        String sql = "SELECT classroom_name FROM Classrooms";
        return jdbcTemplate.queryForList(sql, String.class);
    }
 
    public boolean checkSeat(String userId, String classroomName, List<Integer> selectHours, String day) {
        // SQL 쿼리를 작성하여 Reservation과 ReservationHour 테이블을 조인하여 중복 예약 여부 확인
        String sql = "SELECT COUNT(*) FROM Reservation r " +
                     "JOIN ReservationHour rh ON r.reservNum = rh.reservNum " +
                     "WHERE r.user_id = :userId AND r.classroom_name = :classroomName AND r.day = :day " +
                     "AND rh.reservHour IN (:selectHours)";
        
        // MapSqlParameterSource를 사용하여 IN 절에 selectHours 리스트를 전달
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("userId", userId);
        parameters.addValue("classroomName", classroomName);
        parameters.addValue("day", day);
        parameters.addValue("selectHours", selectHours);

        // NamedParameterJdbcTemplate을 사용하여 IN 절에 리스트 값을 바인딩
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        int count = namedParameterJdbcTemplate.queryForObject(sql, parameters, Integer.class);

        return count > 0;  // 중복 예약이 있으면 true 반환
    }
    
    public boolean existByClassroomName(String classroomName) {
        String sql = "SELECT COUNT(*) FROM Classrooms WHERE classroom_name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, classroomName);
        return count != null && count > 0;
    }
    
    public void save(ClassroomDTO classroom) {
        String sql = "INSERT INTO Classrooms (classroom_name, leftCol, leftRow, rightCol, rightRow) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, 
            classroom.getClassroomName(), 
            classroom.getLeftCol(), 
            classroom.getLeftRow(),
            classroom.getRightCol(),
            classroom.getRightRow()
        );
    }

    
    public ClassroomDTO getClassroomInfo(String classroomName) {
        String sql = "SELECT * FROM Classrooms WHERE classroom_name = ?";

        @SuppressWarnings("deprecation")
		List<ClassroomDTO> results = jdbcTemplate.query(sql, new Object[]{classroomName}, (rs, rowNum) -> {
            ClassroomDTO classroom = new ClassroomDTO();
            classroom.setClassroomName(rs.getString("classroom_name"));
            classroom.setLeftCol(rs.getInt("leftRow"));  // Row와 Col 위치 반대로
            classroom.setLeftRow(rs.getInt("leftCol"));
            classroom.setRightCol(rs.getInt("rightRow"));
            classroom.setRightRow(rs.getInt("rightCol"));
            return classroom;
        });

        if (results.isEmpty()) {
            return null;
        } 
        return results.get(0);
    }
    
    @SuppressWarnings("deprecation")
	public List<ReserveList> getReserveListByClassroom(String userId, String classroomName) {
        String sql = "SELECT r.reservNum, r.user_id, r.classroom_name, r.reservSeat, rh.reservHour "
                   + "FROM Reservation r "
                   + "JOIN ReservationHour rh ON r.reservNum = rh.reservNum "
                   + "WHERE r.user_id = ? AND r.classroom_name = ?";
        
        return jdbcTemplate.query(sql, new Object[]{userId, classroomName}, (rs, rowNum) -> {
            ReserveList reserveList = new ReserveList();
            reserveList.setReservNum(rs.getInt("reservNum"));
            reserveList.setUserId(rs.getString("user_id"));
            reserveList.setClassroomName(rs.getString("classroom_name"));
            reserveList.setReservSeat(rs.getInt("reservSeat"));
            reserveList.setReservHour(rs.getInt("reservHour"));
            return reserveList;
        });
    }

}