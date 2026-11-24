package edu.coursework;

import edu.coursework.util.*;
import edu.coursework.dao.*;
import edu.coursework.service.*;
import java.time.*;
public final class RulesTest {
    static void check(boolean value) { if(!value) throw new AssertionError(); }
    public static void main(String[] args) {
        LocalDate start=LocalDate.of(2026,7,5);
        Instant begin=start.atStartOfDay(Rules.BEIJING).toInstant();
        check(!Rules.regularWindow(start,3,begin.minusSeconds(1)));
        check(Rules.regularWindow(start,3,begin));
        check(Rules.regularWindow(start,3,begin.plusSeconds(3*86400-1)));
        check(!Rules.regularWindow(start,3,begin.plusSeconds(3*86400)));
        check(!Rules.midtermWindow(start,3,begin.plusSeconds(9*7*86400)));
        check(Rules.midtermWindow(start,2,begin.plusSeconds(9*7*86400)));
        check(!Rules.midtermWindow(start,2,begin.plusSeconds((9*7+4)*86400)));
        check(Rules.canRevise(begin,begin.plusSeconds(168*3600)));
        check(!Rules.canRevise(begin,begin.plusSeconds(168*3600+1)));
        int[] scores={0,59,60,69,70,79,80,89,90,100};
        int[] expected={0,0,1,1,2,2,3,3,4,4};
        for(int i=0;i<scores.length;i++) check(Rules.gpa(scores[i])==expected[i]);
        try { Rules.gpa(101); throw new AssertionError(); } catch (IllegalArgumentException expectedException) { }
        System.out.println("RulesTest PASS: 选退课时间、成绩修改期限和绩点边界");
    }
}
