-- Task 4, MySQL 8.0.41 and InnoDB. Run once against a fresh database.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
CREATE DATABASE university_coursework CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
USE university_coursework;

CREATE TABLE account (
    account_name VARCHAR(7) PRIMARY KEY,
    role_code CHAR(1) NOT NULL,
    password_hash VARBINARY(32) NOT NULL,
    CONSTRAINT ck_account_role CHECK (
        (role_code = 'A' AND account_name = 'admin')
        OR (role_code = 'S' AND REGEXP_LIKE(account_name, '^s[0-9]{6}$', 'c'))
        OR (role_code = 'T' AND REGEXP_LIKE(account_name, '^t[0-9]{6}$', 'c'))
    ),
    CONSTRAINT ck_account_hash CHECK (OCTET_LENGTH(password_hash) = 32)
) ENGINE=InnoDB;

CREATE TABLE student (
    student_no VARCHAR(24) PRIMARY KEY,
    student_name VARCHAR(64) NOT NULL,
    account_name VARCHAR(7) NOT NULL UNIQUE,
    FOREIGN KEY (account_name) REFERENCES account(account_name),
    CONSTRAINT ck_student_identity CHECK (
        CHAR_LENGTH(TRIM(student_no)) > 0 AND CHAR_LENGTH(TRIM(student_name)) > 0
        AND REGEXP_LIKE(account_name, '^s[0-9]{6}$', 'c')
    )
) ENGINE=InnoDB;

CREATE TABLE teacher (
    teacher_no VARCHAR(24) PRIMARY KEY,
    teacher_name VARCHAR(64) NOT NULL,
    account_name VARCHAR(7) NOT NULL UNIQUE,
    FOREIGN KEY (account_name) REFERENCES account(account_name),
    CONSTRAINT ck_teacher_identity CHECK (
        CHAR_LENGTH(TRIM(teacher_no)) > 0 AND CHAR_LENGTH(TRIM(teacher_name)) > 0
        AND REGEXP_LIKE(account_name, '^t[0-9]{6}$', 'c')
    ),
    INDEX ix_teacher_name (teacher_name, teacher_no)
) ENGINE=InnoDB;

CREATE TABLE course (
    course_no VARCHAR(24) PRIMARY KEY,
    course_name VARCHAR(100) NOT NULL,
    CONSTRAINT ck_course_identity CHECK (
        CHAR_LENGTH(TRIM(course_no)) > 0 AND CHAR_LENGTH(TRIM(course_name)) > 0
    )
) ENGINE=InnoDB;

CREATE TABLE academic_term (
    academic_year CHAR(9) NOT NULL,
    term_no TINYINT UNSIGNED NOT NULL,
    start_date DATE NOT NULL,
    PRIMARY KEY (academic_year, term_no),
    CONSTRAINT ck_term_year CHECK (
        REGEXP_LIKE(academic_year, '^[0-9]{4}-[0-9]{4}$', 'c')
        AND CAST(RIGHT(academic_year, 4) AS SIGNED) = CAST(LEFT(academic_year, 4) AS SIGNED) + 1
    ),
    CONSTRAINT ck_term_number CHECK (term_no BETWEEN 1 AND 3),
    CONSTRAINT ck_term_weekday CHECK (DAYOFWEEK(start_date) = 1)
) ENGINE=InnoDB;

CREATE TABLE class_number_counter (
    academic_year CHAR(9) NOT NULL,
    term_no TINYINT UNSIGNED NOT NULL,
    last_serial INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (academic_year, term_no),
    FOREIGN KEY (academic_year, term_no) REFERENCES academic_term(academic_year, term_no)
) ENGINE=InnoDB;

CREATE TABLE teaching_class (
    class_no VARCHAR(22) PRIMARY KEY,
    course_no VARCHAR(24) NOT NULL,
    academic_year CHAR(9) NOT NULL,
    term_no TINYINT UNSIGNED NOT NULL,
    capacity INT UNSIGNED NOT NULL,
    active_count INT UNSIGNED NOT NULL DEFAULT 0,
    FOREIGN KEY (course_no) REFERENCES course(course_no),
    FOREIGN KEY (academic_year, term_no) REFERENCES academic_term(academic_year, term_no),
    CONSTRAINT ck_class_capacity CHECK (capacity > 0 AND active_count <= capacity),
    CONSTRAINT ck_class_number CHECK (
        LEFT(class_no, 12) = CONCAT(academic_year, '-', term_no, '-')
        AND REGEXP_LIKE(SUBSTRING(class_no, 13), '^([0-9]{3}|[1-9][0-9]{3,9})$', 'c')
        AND CAST(SUBSTRING(class_no, 13) AS UNSIGNED) BETWEEN 1 AND 4294967295
    ),
    INDEX ix_class_course_term (course_no, academic_year, term_no),
    INDEX ix_class_term_course (academic_year, term_no, course_no)
) ENGINE=InnoDB;

CREATE TABLE weekly_time (
    time_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    weekday_no TINYINT UNSIGNED NOT NULL,
    start_period TINYINT UNSIGNED NOT NULL,
    end_period TINYINT UNSIGNED NOT NULL,
    UNIQUE (weekday_no, start_period, end_period),
    CONSTRAINT ck_time_periods CHECK (
        weekday_no BETWEEN 1 AND 7 AND start_period BETWEEN 1 AND 13
        AND end_period BETWEEN start_period AND 13
    )
) ENGINE=InnoDB;

CREATE TABLE classroom (
    room_id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    room_name VARCHAR(7) NOT NULL UNIQUE,
    CONSTRAINT ck_room_name CHECK (
        REGEXP_LIKE(room_name, '^([124]区[1-5]|3区[1-3])教[1-5](0[1-9]|1[0-9]|2[0-7])$', 'c')
    )
) ENGINE=InnoDB;

CREATE TABLE class_schedule (
    class_no VARCHAR(22) NOT NULL,
    time_id BIGINT UNSIGNED NOT NULL,
    room_id INT UNSIGNED NOT NULL,
    PRIMARY KEY (class_no, time_id),
    FOREIGN KEY (class_no) REFERENCES teaching_class(class_no),
    FOREIGN KEY (time_id) REFERENCES weekly_time(time_id),
    FOREIGN KEY (room_id) REFERENCES classroom(room_id),
    INDEX ix_schedule_room_time (room_id, time_id)
) ENGINE=InnoDB;

CREATE TABLE teaching_assignment (
    class_no VARCHAR(22) NOT NULL,
    teacher_no VARCHAR(24) NOT NULL,
    PRIMARY KEY (class_no, teacher_no),
    FOREIGN KEY (class_no) REFERENCES teaching_class(class_no),
    FOREIGN KEY (teacher_no) REFERENCES teacher(teacher_no),
    INDEX ix_assignment_teacher_class (teacher_no, class_no)
) ENGINE=InnoDB;

CREATE TABLE enrollment (
    enrollment_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    student_no VARCHAR(24) NOT NULL,
    class_no VARCHAR(22) NOT NULL,
    selected_at DATETIME(6) NOT NULL,
    withdrawn_at DATETIME(6),
    FOREIGN KEY (student_no) REFERENCES student(student_no),
    FOREIGN KEY (class_no) REFERENCES teaching_class(class_no),
    CONSTRAINT ck_enrollment_chronology CHECK (withdrawn_at IS NULL OR withdrawn_at >= selected_at),
    INDEX ix_enrollment_student_active (student_no, withdrawn_at, class_no),
    INDEX ix_enrollment_class_active (class_no, withdrawn_at)
) ENGINE=InnoDB;

CREATE TABLE grade (
    enrollment_id BIGINT UNSIGNED PRIMARY KEY,
    score TINYINT UNSIGNED NOT NULL,
    first_recorded_at DATETIME(6) NOT NULL,
    FOREIGN KEY (enrollment_id) REFERENCES enrollment(enrollment_id),
    CONSTRAINT ck_grade_score CHECK (score <= 100)
) ENGINE=InnoDB;

CREATE TABLE grade_revision (
    revision_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    enrollment_id BIGINT UNSIGNED NOT NULL,
    editor_no VARCHAR(24) NOT NULL,
    old_score TINYINT UNSIGNED NOT NULL,
    new_score TINYINT UNSIGNED NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    FOREIGN KEY (enrollment_id) REFERENCES grade(enrollment_id),
    FOREIGN KEY (editor_no) REFERENCES teacher(teacher_no),
    CONSTRAINT ck_revision_score CHECK (old_score <= 100 AND new_score <= 100),
    INDEX ix_revision_chain (enrollment_id, changed_at, revision_id)
) ENGINE=InnoDB;
