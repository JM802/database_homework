-- 为任务四的 S001 样例补充 3 门历史课程；不会重建表或删除已有记录。
-- 适用于 02_seed.sql 的示例数据：2025-2026 春季 003、004、005 班。
-- 选课时间采用该学期开学后第 5 天 09:00（北京时间），属于历史测试数据。
-- 如已有有效选课、同课程冲突、时间冲突、班级满员或对象不存在，对应班级跳过。
-- 可重复执行：已有效选中的课程不会重复插入。
USE university_coursework;
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO enrollment (student_no, class_no, selected_at, withdrawn_at)
SELECT 'S001', c.class_no,
       TIMESTAMP(DATE_ADD(t.start_date, INTERVAL 4 DAY), '01:00:00'), NULL
FROM teaching_class AS c
JOIN academic_term AS t ON t.academic_year=c.academic_year AND t.term_no=c.term_no
JOIN student AS student ON student.student_no='S001'
WHERE c.class_no IN ('2025-2026-2-003','2025-2026-2-004','2025-2026-2-005')
  AND c.academic_year='2025-2026' AND c.term_no=2
  AND c.active_count<c.capacity
  AND NOT EXISTS (
      SELECT 1 FROM enrollment AS e
      JOIN teaching_class AS existing ON existing.class_no=e.class_no
      WHERE e.student_no='S001' AND e.withdrawn_at IS NULL
        AND existing.academic_year=c.academic_year AND existing.term_no=c.term_no
        AND existing.course_no=c.course_no
  )
  AND NOT EXISTS (
      SELECT 1 FROM enrollment AS e
      JOIN teaching_class AS existing ON existing.class_no=e.class_no
      JOIN class_schedule AS old_schedule ON old_schedule.class_no=existing.class_no
      JOIN weekly_time AS old_time ON old_time.time_id=old_schedule.time_id
      JOIN class_schedule AS new_schedule ON new_schedule.class_no=c.class_no
      JOIN weekly_time AS new_time ON new_time.time_id=new_schedule.time_id
      WHERE e.student_no='S001' AND e.withdrawn_at IS NULL
        AND existing.academic_year=c.academic_year AND existing.term_no=c.term_no
        AND old_time.weekday_no=new_time.weekday_no
        AND old_time.start_period<=new_time.end_period
        AND new_time.start_period<=old_time.end_period
  );

-- 按有效记录重新计算这三个班的人数缓存，与选课写入在同一事务中提交。
UPDATE teaching_class AS c
SET c.active_count=(SELECT COUNT(*) FROM enrollment AS e
                    WHERE e.class_no=c.class_no AND e.withdrawn_at IS NULL)
WHERE c.class_no IN ('2025-2026-2-003','2025-2026-2-004','2025-2026-2-005');
COMMIT;

-- 查看这些课程是否已成功选中；有记录才表示加入成功。
SELECT c.course_no, co.course_name, c.class_no, e.student_no, e.selected_at
FROM teaching_class AS c
JOIN course AS co ON co.course_no=c.course_no
LEFT JOIN enrollment AS e ON e.class_no=c.class_no AND e.student_no='S001'
                            AND e.withdrawn_at IS NULL
WHERE c.class_no IN ('2025-2026-2-003','2025-2026-2-004','2025-2026-2-005')
ORDER BY c.class_no;
