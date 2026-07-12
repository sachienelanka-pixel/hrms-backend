package com.hrms.leave;

import org.springframework.stereotype.Component;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Utility to calculate actual working days between two dates,
 * excluding weekends and Sri Lankan public holidays.
 */
@Component
public class WorkingDayCalculator {

    /** Sri Lankan public holidays for the current year (fixed dates; add more as needed) */
    private static final Set<String> SL_HOLIDAYS = Set.of(
        // 2025 & 2026 common public holidays (MM-DD format, year-agnostic)
        "01-01", // New Year's Day
        "01-14", // Tamil Thai Pongal Day
        "02-04", // National Day
        "04-13", // Sinhala & Tamil New Year Eve
        "04-14", // Sinhala & Tamil New Year
        "05-01", // May Day / Labour Day
        "05-22", // National Heroes Day
        "12-25"  // Christmas Day
        // Note: Buddhist/Islamic holidays are lunar-based and change yearly;
        // extend this list with year-specific dates as needed.
    );

    /**
     * Count working days (Mon–Fri, excluding Sri Lankan public holidays)
     * between startDate and endDate inclusive.
     */
    public double calculateWorkingDays(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) return 0;
        long workingDays = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (!isWeekend(current) && !isPublicHoliday(current)) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        return workingDays;
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private boolean isPublicHoliday(LocalDate date) {
        String mmdd = String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth());
        return SL_HOLIDAYS.contains(mmdd);
    }
}
