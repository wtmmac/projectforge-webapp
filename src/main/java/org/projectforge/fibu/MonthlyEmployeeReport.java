/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2011 Kai Reinhard (k.reinhard@me.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.fibu;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.Validate;
import org.projectforge.calendar.DayHolder;
import org.projectforge.calendar.MonthHolder;
import org.projectforge.calendar.WeekHolder;
import org.projectforge.common.DateHolder;
import org.projectforge.common.NumberHelper;
import org.projectforge.common.StringHelper;
import org.projectforge.fibu.kost.Kost2DO;
import org.projectforge.task.TaskDO;
import org.projectforge.timesheet.TimesheetDO;
import org.projectforge.user.PFUserDO;
import org.projectforge.web.common.OutputType;
import org.projectforge.web.task.TaskFormatter;

/**
 * Repräsentiert einen Monatsbericht eines Mitarbeiters.
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
public class MonthlyEmployeeReport implements Serializable
{
  private static final long serialVersionUID = -4636357379552246075L;

  public class Kost2Row implements Serializable
  {
    private static final long serialVersionUID = -5379735557333691194L;

    public Kost2Row(Kost2DO kost2)
    {
      this.kost2 = kost2;
    }

    /**
     * XML-escaped or null if not exists.
     */
    public String getProjektname()
    {
      if (kost2 == null || kost2.getProjekt() == null) {
        return null;
      }
      return StringEscapeUtils.escapeXml(kost2.getProjekt().getName());
    }

    /**
     * XML-escaped or null if not exists.
     */
    public String getKundename()
    {
      if (kost2 == null || kost2.getProjekt() == null || kost2.getProjekt().getKunde() == null) {
        return null;
      }
      return StringEscapeUtils.escapeXml(kost2.getProjekt().getKunde().getName());
    }

    /**
     * XML-escaped or null if not exists.
     */
    public String getKost2ArtName()
    {
      if (kost2 == null || kost2.getKost2Art() == null) {
        return null;
      }
      return StringEscapeUtils.escapeXml(kost2.getKost2Art().getName());
    }

    /**
     * XML-escaped or null if not exists.
     */
    public String getKost2Description()
    {
      if (kost2 == null) {
        return null;
      }
      return StringEscapeUtils.escapeXml(kost2.getDescription());
    }

    public Kost2DO getKost2()
    {
      return kost2;
    }

    private Kost2DO kost2;
  }

  private int year;

  private int month;

  private Date fromDate;

  private Date toDate;

  private BigDecimal numberOfWorkingDays;

  /** Employee can be null, if not found. As fall back, store user. */
  private PFUserDO user;

  private EmployeeDO employee;

  private long totalDuration;

  private Integer kost1Id;

  private List<MonthlyEmployeeReportWeek> weeks;

  /** Days with time sheets. */
  private Set<Integer> bookedDays = new HashSet<Integer>();

  private List<Integer> unbookedDays = new ArrayList<Integer>();

  /**
   * Key is kost2.id.
   */
  private Map<Integer, MonthlyEmployeeReportEntry> kost2Durations;

  /**
   * Key is task.id.
   */
  private Map<Integer, MonthlyEmployeeReportEntry> taskDurations;

  /** String is formatted Kost2-String for sorting. */
  private Map<String, Kost2Row> kost2Rows;

  /** String is formatted Task path string for sorting. */
  private Map<String, TaskDO> taskEntries;

  public static final String getFormattedDuration(long duration)
  {
    if (duration == 0) {
      return "";
    }
    BigDecimal hours = new BigDecimal(duration).divide(new BigDecimal(1000 * 60 * 60), 2, BigDecimal.ROUND_HALF_UP);
    return NumberHelper.formatFraction2(hours);
  }

  /**
   * Dont't forget to initialize: setFormatter and setUser or setEmployee.
   * @param year
   * @param month
   */
  public MonthlyEmployeeReport(int year, int month)
  {
    this.year = year;
    this.month = month;
  }

  /**
   * Use only as fallback, if employee is not available.
   * @param user
   */
  public void setUser(PFUserDO user)
  {
    this.user = user;
  }

  /**
   * User will be set automatically from given employee.
   * @param employee
   */
  public void setEmployee(EmployeeDO employee)
  {
    this.employee = employee;
    if (employee != null) {
      this.user = employee.getUser();
      this.kost1Id = employee.getKost1Id();
    }
  }

  public void init()
  {
    // Create the weeks:
    this.weeks = new ArrayList<MonthlyEmployeeReportWeek>();
    DateHolder dh = new DateHolder();
    dh.setDate(year, month, 1, 0, 0, 0);
    fromDate = dh.getDate();
    DateHolder dh2 = new DateHolder(dh.getDate());
    dh2.setEndOfMonth();
    toDate = dh2.getDate();
    int i = 0;
    do {
      MonthlyEmployeeReportWeek week = new MonthlyEmployeeReportWeek(dh.getDate());
      weeks.add(week);
      dh.setEndOfWeek();
      dh.add(Calendar.DAY_OF_WEEK, +1);
      dh.setBeginOfWeek();
      if (i++ > 5) {
        throw new RuntimeException("Endlos loop protection: Please contact developer!");
      }
    } while (dh.getDate().before(toDate));
  }

  public void addTimesheet(TimesheetDO sheet)
  {
    final DayHolder day = new DayHolder(sheet.getStartTime());
    bookedDays.add(day.getDayOfMonth());
    for (MonthlyEmployeeReportWeek week : weeks) {
      if (week.matchWeek(sheet) == true) {
        week.addEntry(sheet);
        return;
      }
    }
    throw new RuntimeException("Oups, given time sheet is not inside the month represented by this month object.");
  }

  public void calculate()
  {
    Validate.notEmpty(weeks);
    kost2Rows = new TreeMap<String, Kost2Row>();
    taskEntries = new TreeMap<String, TaskDO>();
    kost2Durations = new HashMap<Integer, MonthlyEmployeeReportEntry>();
    taskDurations = new HashMap<Integer, MonthlyEmployeeReportEntry>();
    for (MonthlyEmployeeReportWeek week : weeks) {
      if (MapUtils.isNotEmpty(week.getKost2Entries()) == true) {
        for (MonthlyEmployeeReportEntry entry : week.getKost2Entries().values()) {
          Validate.notNull(entry.getKost2());
          kost2Rows.put(entry.getKost2().getShortDisplayName(), new Kost2Row(entry.getKost2()));
          MonthlyEmployeeReportEntry kost2Total = kost2Durations.get(entry.getKost2().getId());
          if (kost2Total == null) {
            kost2Total = new MonthlyEmployeeReportEntry(entry.getKost2());
            kost2Total.addMillis(entry.getWorkFractionMillis());
            kost2Durations.put(entry.getKost2().getId(), kost2Total);
          } else {
            kost2Total.addMillis(entry.getWorkFractionMillis());
          }
          // Reisezeiten: Kost2Art um Faktor erweitern
          totalDuration += entry.getWorkFractionMillis();
        }
      }
      if (MapUtils.isNotEmpty(week.getTaskEntries()) == true) {
        for (MonthlyEmployeeReportEntry entry : week.getTaskEntries().values()) {
          Validate.notNull(entry.getTask());
          taskEntries.put(TaskFormatter.instance().getTaskPath(entry.getTask().getId(), true, OutputType.XML), entry.getTask());
          MonthlyEmployeeReportEntry taskTotal = taskDurations.get(entry.getTask().getId());
          if (taskTotal == null) {
            taskTotal = new MonthlyEmployeeReportEntry(entry.getTask());
            taskTotal.addMillis(entry.getMillis());
            taskDurations.put(entry.getTask().getId(), taskTotal);
          } else {
            taskTotal.addMillis(entry.getMillis());
          }
          totalDuration += entry.getMillis();
        }
      }
    }
    final MonthHolder monthHolder = new MonthHolder(this.fromDate);
    this.numberOfWorkingDays = monthHolder.getNumberOfWorkingDays();
    for (final WeekHolder week : monthHolder.getWeeks()) {
      for (final DayHolder day : week.getDays()) {
        if (day.getMonth() == this.month && day.isWorkingDay() == true && bookedDays.contains(day.getDayOfMonth()) == false) {
          unbookedDays.add(day.getDayOfMonth());
        }
      }
    }
  }

  /**
   * Gets the list of unbooked working days. These are working days without time sheets of the actual user.
   */
  public List<Integer> getUnbookedDays()
  {
    return unbookedDays;
  }

  /**
   * @return Days of month without time sheets: 03.11., 08.11., ... or null if no entries exists.
   */
  public String getFormattedUnbookedDays()
  {
    final StringBuffer buf = new StringBuffer();
    boolean first = true;
    for (Integer dayOfMonth : unbookedDays) {
      if (first == true) {
        first = false;
      } else {
        buf.append(", ");
      }
      buf.append(StringHelper.format2DigitNumber(dayOfMonth)).append(".").append(StringHelper.format2DigitNumber(month + 1)).append(".");
    }
    if (first == true) {
      return null;
    }
    return buf.toString();

  }

  /**
   * Key is the shortDisplayName of Kost2DO. The Map is a TreeMap sorted by the keys.
   */
  public Map<String, Kost2Row> getKost2Rows()
  {
    return kost2Rows;
  }

  /**
   * Key is the kost2 id.
   */
  public Map<Integer, MonthlyEmployeeReportEntry> getKost2Durations()
  {
    return kost2Durations;
  }

  /**
   * Key is the task path string of TaskDO. The Map is a TreeMap sorted by the keys.
   */
  public Map<String, TaskDO> getTaskEntries()
  {
    return taskEntries;
  }

  /**
   * Key is the task id.
   */
  public Map<Integer, MonthlyEmployeeReportEntry> getTaskDurations()
  {
    return taskDurations;
  }

  public int getYear()
  {
    return year;
  }

  public int getMonth()
  {
    return month;
  }

  public List<MonthlyEmployeeReportWeek> getWeeks()
  {
    return weeks;
  }

  public String getFormmattedMonth()
  {
    return StringHelper.format2DigitNumber(month + 1);
  }

  public Date getFromDate()
  {
    return fromDate;
  }

  public Date getToDate()
  {
    return toDate;
  }

  /**
   * Can be null, if not set (not available).
   */
  public EmployeeDO getEmployee()
  {
    return employee;
  }

  public PFUserDO getUser()
  {
    return user;
  }

  /**
   * @return Total duration in ms.
   */
  public long getTotalDuration()
  {
    return totalDuration;
  }

  public String getFormattedTotalDuration()
  {
    return MonthlyEmployeeReport.getFormattedDuration(totalDuration);
  }

  public Integer getKost1Id()
  {
    return kost1Id;
  }

  public BigDecimal getNumberOfWorkingDays()
  {
    return numberOfWorkingDays;
  }
}
