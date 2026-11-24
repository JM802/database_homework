-- Windows PowerShell: run chcp 65001, then mysql --default-character-set=utf8mb4 -u root -p.
-- SET NAMES sets the connection character set; it does not change the terminal encoding.
USE university_coursework;
SET NAMES utf8mb4;

-- Q01 单表条件查询。按教室名称前缀列出 3 区 3 教一层教室。
SELECT room_id, room_name
FROM classroom WHERE room_name LIKE '3区3教1%' ORDER BY room_name LIMIT 5;

-- Q02 单表条件查询。春季容量不少于 18 人的班，按余量排序。
SELECT class_no, capacity, active_count, capacity - active_count AS remaining
FROM teaching_class
WHERE academic_year = '2025-2026' AND term_no = 2 AND capacity >= 18
ORDER BY remaining DESC, class_no;

-- Q03 INNER JOIN。按教学班看每条排课时间、教室与课程。
SELECT c.class_no, co.course_name, w.weekday_no, w.start_period, w.end_period, r.room_name
FROM teaching_class AS c
INNER JOIN course AS co ON co.course_no = c.course_no
INNER JOIN class_schedule AS cs ON cs.class_no = c.class_no
INNER JOIN weekly_time AS w ON w.time_id = cs.time_id
INNER JOIN classroom AS r ON r.room_id = cs.room_id
WHERE c.class_no = '2025-2026-2-001'
ORDER BY w.weekday_no, w.start_period;

-- Q04 LEFT JOIN。学生个人修读史，保留尚未录入和已退出记录。
SELECT e.enrollment_id, e.class_no, co.course_name,
       CASE
           WHEN e.withdrawn_at IS NULL THEN '有效'
           WHEN c.term_no IN (1, 2)
             AND DATE_ADD(e.withdrawn_at, INTERVAL 8 HOUR) >= DATE_ADD(term.start_date, INTERVAL 63 DAY)
             AND DATE_ADD(e.withdrawn_at, INTERVAL 8 HOUR) < DATE_ADD(term.start_date, INTERVAL 67 DAY) THEN '期中退课'
           ELSE '常规退课'
       END AS enrollment_state,
       COALESCE(CAST(g.score AS CHAR), '暂无成绩') AS score
FROM enrollment AS e
JOIN teaching_class AS c ON c.class_no = e.class_no
JOIN course AS co ON co.course_no = c.course_no
JOIN academic_term AS term ON term.academic_year = c.academic_year AND term.term_no = c.term_no
LEFT JOIN grade AS g ON g.enrollment_id = e.enrollment_id
WHERE e.student_no = 'S014'
ORDER BY e.selected_at, e.enrollment_id;

-- Q05 GROUP BY / HAVING。至少有两条选课事件的班。
SELECT class_no, COUNT(*) AS enrollment_events,
       SUM(withdrawn_at IS NULL) AS active_students,
       SUM(withdrawn_at IS NOT NULL) AS withdrawals
FROM enrollment GROUP BY class_no HAVING COUNT(*) >= 2
ORDER BY enrollment_events DESC, class_no;

-- Q06 IN 子查询。按任一合授教师筛班。
SELECT c.class_no, c.academic_year, c.term_no
FROM teaching_class AS c
WHERE c.class_no IN (
    SELECT a.class_no FROM teaching_assignment AS a
    JOIN teacher AS t ON t.teacher_no = a.teacher_no
    WHERE t.teacher_name = '合授教师'
)
ORDER BY c.academic_year, c.term_no, c.class_no;

-- Q07 EXISTS / NOT EXISTS 相关子查询。有有效选课但还没有成绩的学生。
SELECT s.student_no, s.student_name
FROM student AS s
WHERE EXISTS (
    SELECT 1 FROM enrollment AS e
    WHERE e.student_no = s.student_no AND e.withdrawn_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM grade AS g WHERE g.enrollment_id = e.enrollment_id)
)
ORDER BY s.student_no;

-- Q08 复杂业务查询。按课程和学期聚合，避免合授、排课、改分历史扩大计数。
WITH scoped_classes AS (
    SELECT class_no, course_no, academic_year, term_no FROM teaching_class
    WHERE academic_year = '2025-2026' AND term_no = 2
), enroll_totals AS (
    SELECT c.course_no, c.academic_year, c.term_no,
           COUNT(*) AS enroll_count,
           SUM(e.withdrawn_at IS NOT NULL) AS withdraw_count,
           COUNT(DISTINCT CASE WHEN e.withdrawn_at IS NULL THEN e.student_no END) AS active_students,
           AVG(CASE WHEN e.withdrawn_at IS NULL THEN g.score END) AS mean_score,
           AVG(CASE WHEN e.withdrawn_at IS NULL AND g.enrollment_id IS NOT NULL THEN
               CASE WHEN g.score < 60 THEN 0 WHEN g.score < 70 THEN 1 WHEN g.score < 80 THEN 2
                    WHEN g.score < 90 THEN 3 ELSE 4 END
           END) AS mean_gpa,
           SUM(CASE WHEN e.withdrawn_at IS NULL AND g.score >= 60 THEN 1 ELSE 0 END) AS passes,
           SUM(CASE WHEN e.withdrawn_at IS NULL AND g.score >= 90 THEN 1 ELSE 0 END) AS full_gpa
    FROM scoped_classes AS c
    JOIN enrollment AS e ON e.class_no = c.class_no
    LEFT JOIN grade AS g ON g.enrollment_id = e.enrollment_id
    GROUP BY c.course_no, c.academic_year, c.term_no
), teacher_totals AS (
    SELECT c.course_no, c.academic_year, c.term_no, COUNT(*) AS teaching_assignments
    FROM scoped_classes AS c
    JOIN teaching_assignment AS a ON a.class_no = c.class_no
    GROUP BY c.course_no, c.academic_year, c.term_no
), scoped_courses AS (
    SELECT DISTINCT course_no, academic_year, term_no FROM scoped_classes
)
SELECT c.course_no, co.course_name, c.academic_year, c.term_no,
       COALESCE(e.enroll_count, 0) AS enroll_count,
       COALESCE(e.withdraw_count, 0) AS withdraw_count,
       COALESCE(t.teaching_assignments, 0) AS teaching_assignments,
       COALESCE(e.active_students, 0) AS active_students,
       COALESCE(CAST(ROUND(e.mean_score, 2) AS CHAR), '暂无成绩') AS mean_score,
       COALESCE(CAST(ROUND(e.mean_gpa, 2) AS CHAR), '暂无成绩') AS mean_gpa,
       COALESCE(e.passes, 0) AS passes,
       COALESCE(e.full_gpa, 0) AS full_gpa
FROM scoped_courses AS c
JOIN course AS co ON co.course_no = c.course_no
LEFT JOIN enroll_totals AS e ON e.course_no = c.course_no AND e.academic_year = c.academic_year AND e.term_no = c.term_no
LEFT JOIN teacher_totals AS t ON t.course_no = c.course_no AND t.academic_year = c.academic_year AND t.term_no = c.term_no
ORDER BY c.course_no;
