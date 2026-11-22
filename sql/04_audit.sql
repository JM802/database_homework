USE university_coursework;
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Exact row totals for every physical table, including the auxiliary counter.
SELECT 'account' AS table_name, COUNT(*) AS rows_found FROM account
UNION ALL SELECT 'student', COUNT(*) FROM student
UNION ALL SELECT 'teacher', COUNT(*) FROM teacher
UNION ALL SELECT 'course', COUNT(*) FROM course
UNION ALL SELECT 'academic_term', COUNT(*) FROM academic_term
UNION ALL SELECT 'class_number_counter', COUNT(*) FROM class_number_counter
UNION ALL SELECT 'teaching_class', COUNT(*) FROM teaching_class
UNION ALL SELECT 'weekly_time', COUNT(*) FROM weekly_time
UNION ALL SELECT 'classroom', COUNT(*) FROM classroom
UNION ALL SELECT 'class_schedule', COUNT(*) FROM class_schedule
UNION ALL SELECT 'teaching_assignment', COUNT(*) FROM teaching_assignment
UNION ALL SELECT 'enrollment', COUNT(*) FROM enrollment
UNION ALL SELECT 'grade', COUNT(*) FROM grade
UNION ALL SELECT 'grade_revision', COUNT(*) FROM grade_revision;

-- All counts below must be zero for the supplied fixture.
WITH occupied AS (
    SELECT c.academic_year, c.term_no, s.class_no, s.room_id, w.*
    FROM teaching_class AS c JOIN class_schedule AS s USING (class_no)
    JOIN weekly_time AS w USING (time_id)
), overlapping AS (
    SELECT l.class_no AS l_class, r.class_no AS r_class, l.room_id AS l_room, r.room_id AS r_room
    FROM occupied AS l JOIN occupied AS r
      ON l.academic_year = r.academic_year AND l.term_no = r.term_no AND l.weekday_no = r.weekday_no
     AND l.start_period <= r.end_period AND r.start_period <= l.end_period
     AND (l.class_no < r.class_no OR (l.class_no = r.class_no AND l.time_id < r.time_id))
), hours_per_class AS (
    SELECT c.class_no, c.course_no, c.academic_year, c.term_no, SUM(w.end_period-w.start_period+1) AS hours
    FROM teaching_class AS c JOIN class_schedule AS s USING (class_no)
    JOIN weekly_time AS w USING (time_id)
    GROUP BY c.class_no,c.course_no,c.academic_year,c.term_no
), event_times AS (
    SELECT e.*, c.academic_year, c.term_no, c.course_no, t.start_date,
           DATE_ADD(e.selected_at, INTERVAL 8 HOUR) AS selected_local,
           DATE_ADD(e.withdrawn_at, INTERVAL 8 HOUR) AS withdrawn_local,
           DATE_ADD(t.start_date, INTERVAL IF(c.term_no=3, 3, 21) DAY) AS regular_end
    FROM enrollment AS e JOIN teaching_class AS c USING (class_no)
    JOIN academic_term AS t USING (academic_year,term_no)
), revisions AS (
    SELECT v.*, g.first_recorded_at, g.score,
           LAG(v.new_score) OVER (PARTITION BY v.enrollment_id ORDER BY v.changed_at, v.revision_id) AS previous_score,
           ROW_NUMBER() OVER (PARTITION BY v.enrollment_id ORDER BY v.changed_at DESC, v.revision_id DESC) AS latest
    FROM grade_revision AS v JOIN grade AS g USING (enrollment_id)
), year_terms AS (
    SELECT t.*, DATE_ADD(t.start_date, INTERVAL IF(t.term_no=3,28,126) DAY) AS next_date
    FROM academic_term AS t
)
SELECT 'term_count_per_year' AS audit_rule, COUNT(*) AS violations FROM (
    SELECT academic_year FROM academic_term GROUP BY academic_year HAVING COUNT(*)<>3
) AS bad
UNION ALL SELECT 'summer_start', COUNT(*) FROM academic_term AS summer
LEFT JOIN academic_term AS spring ON spring.academic_year=summer.academic_year AND spring.term_no=2
WHERE summer.term_no=3 AND (spring.term_no IS NULL OR summer.start_date<>DATE_ADD(spring.start_date,INTERVAL 126 DAY))
UNION ALL SELECT 'term_date_overlap', COUNT(*) FROM year_terms AS l
JOIN year_terms AS r ON (l.academic_year,l.term_no)<(r.academic_year,r.term_no)
 AND l.start_date<r.next_date AND r.start_date<l.next_date
UNION ALL SELECT 'classroom_inventory', ABS(CAST(COUNT(*) AS SIGNED)-2430) FROM classroom
UNION ALL SELECT 'account_binding', COUNT(*) FROM account AS a
LEFT JOIN student AS s USING(account_name) LEFT JOIN teacher AS t USING(account_name)
WHERE (a.role_code='S' AND s.student_no IS NULL) OR (a.role_code='T' AND t.teacher_no IS NULL)
UNION ALL SELECT 'class_arrangements', COUNT(*) FROM teaching_class AS c
WHERE NOT EXISTS (SELECT 1 FROM class_schedule AS s WHERE s.class_no=c.class_no)
   OR NOT EXISTS (SELECT 1 FROM teaching_assignment AS a WHERE a.class_no=c.class_no)
UNION ALL SELECT 'same_course_weekly_hours', COUNT(*) FROM (
    SELECT course_no,academic_year,term_no FROM hours_per_class
    GROUP BY course_no,academic_year,term_no HAVING MIN(hours)<>MAX(hours)
) AS bad
UNION ALL SELECT 'class_or_room_overlap', COUNT(*) FROM overlapping
WHERE l_class=r_class OR l_room=r_room
UNION ALL SELECT 'teacher_overlap', COUNT(*) FROM overlapping AS p
WHERE p.l_class<>p.r_class AND EXISTS (
    SELECT 1 FROM teaching_assignment AS a JOIN teaching_assignment AS b USING (teacher_no)
    WHERE a.class_no=p.l_class AND b.class_no=p.r_class
)
UNION ALL SELECT 'student_current_overlap', COUNT(*) FROM overlapping AS p
WHERE p.l_class<>p.r_class AND EXISTS (
    SELECT 1 FROM enrollment AS a JOIN enrollment AS b USING (student_no)
    WHERE a.class_no=p.l_class AND b.class_no=p.r_class AND a.withdrawn_at IS NULL AND b.withdrawn_at IS NULL
)
UNION ALL SELECT 'active_count_cache', COUNT(*) FROM teaching_class AS c
WHERE c.active_count <> (SELECT COUNT(*) FROM enrollment AS e WHERE e.class_no=c.class_no AND e.withdrawn_at IS NULL)
UNION ALL SELECT 'effective_course_unique', COUNT(*) FROM (
    SELECT student_no, academic_year, term_no, course_no
    FROM event_times WHERE withdrawn_at IS NULL
    GROUP BY student_no,academic_year,term_no,course_no HAVING COUNT(*)>1
) AS bad
UNION ALL SELECT 'selection_window', COUNT(*) FROM event_times
WHERE selected_local < start_date OR selected_local >= regular_end
UNION ALL SELECT 'withdrawal_window', COUNT(*) FROM event_times
WHERE withdrawn_local IS NOT NULL AND NOT (withdrawn_local >= start_date AND withdrawn_local < regular_end)
  AND NOT (term_no IN (1,2) AND withdrawn_local >= DATE_ADD(start_date, INTERVAL 63 DAY)
       AND withdrawn_local < DATE_ADD(start_date, INTERVAL 67 DAY))
UNION ALL SELECT 'midterm_limit', COUNT(*) FROM (
    SELECT student_no, academic_year, term_no FROM event_times
    WHERE term_no IN (1,2) AND withdrawn_local >= DATE_ADD(start_date,INTERVAL 63 DAY)
      AND withdrawn_local < DATE_ADD(start_date,INTERVAL 67 DAY)
    GROUP BY student_no,academic_year,term_no HAVING COUNT(*)>1
) AS bad
UNION ALL SELECT 'grade_eligibility', COUNT(*) FROM grade AS g JOIN enrollment AS e USING (enrollment_id)
WHERE e.withdrawn_at IS NOT NULL OR g.first_recorded_at<e.selected_at
UNION ALL SELECT 'revision_eligibility', COUNT(*) FROM revisions AS v JOIN enrollment AS e USING(enrollment_id)
WHERE v.changed_at < v.first_recorded_at OR v.changed_at > DATE_ADD(v.first_recorded_at,INTERVAL 168 HOUR)
   OR (v.previous_score IS NOT NULL AND v.previous_score<>v.old_score)
   OR (v.latest=1 AND v.new_score<>v.score)
   OR NOT EXISTS (SELECT 1 FROM teaching_assignment AS a WHERE a.class_no=e.class_no AND a.teacher_no=v.editor_no)
UNION ALL SELECT 'class_number_counter', COUNT(*) FROM teaching_class AS c
LEFT JOIN class_number_counter AS n USING(academic_year,term_no)
WHERE n.term_no IS NULL OR n.last_serial < CAST(SUBSTRING(c.class_no,13) AS UNSIGNED);
