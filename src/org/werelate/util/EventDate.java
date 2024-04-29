package org.werelate.util;

import java.util.regex.*;
import java.util.Arrays;
import java.util.List;
import java.util.Calendar;
import java.io.File;  // Import the File class
import java.io.FileNotFoundException;  // Import this class to handle errors
import java.util.Scanner; // Import the Scanner class to read text files
import java.util.TimeZone;

// Written 2021 by Janet Bjorndahl as the Java equivalent of DateHandler.php
public class EventDate {
  public String originalDate;
  
  // Month abbreviations and full names (accented and unaccented) in English, Dutch, French, German, Spanish, Norwegian, and Danish
  private static String[] gedcomJan = {"Jan", "jan", "january", "januari", "janvier", "januar", "ene", "enero"};  
  private static String[] gedcomFeb = {"Feb", "feb", "february", "febr", "februari", "fév", "fev", "février", "fevrier", "februar", "febrero"};
  private static String[] gedcomMar = {"Mar", "mar", "march", "mrt", "maart", "mars", "mär", "märz", "marz", "marzo"};
  private static String[] gedcomApr = {"Apr", "apr", "april", "apl", "avr", "avril", "abr", "abril"};
  private static String[] gedcomMay = {"May", "may", "mei", "mai", "mayo", "maj"};
  private static String[] gedcomJun = {"Jun", "jun", "june", "juni", "juin", "junio"};
  private static String[] gedcomJul = {"Jul", "jul", "july", "juli", "juillet", "julio"};
  private static String[] gedcomAug = {"Aug", "aug", "august", "augustus", "aoû", "aou", "août", "aout", "ago", "agosto"};
  private static String[] gedcomSep = {"Sep", "sep", "september", "sept", "septembre", "septiembre"};
  private static String[] gedcomOct = {"Oct", "oct", "october", "okt", "oktober", "octobre", "octubre"};
  private static String[] gedcomNov = {"Nov", "nov", "november", "novembre", "noviembre"};
  private static String[] gedcomDec = {"Dec", "dec", "december", "déc", "décembre", "decembre", "dez", "dezember", "dic", "diciembre", "des", "desember"};
  
  private static String[][] gedcomMonths = { gedcomJan, gedcomFeb, gedcomMar, gedcomApr, gedcomMay, gedcomJun, gedcomJul, gedcomAug, gedcomSep, gedcomOct, gedcomNov, gedcomDec };
  private static int[] MONTH_OFFSETS = {0, 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365};

  // Modifier abbrebiations and full words in English (plus some scattered words in other languages)
  private static String[] gedcomAbt =  {"Abt", "abt", "about", "approx", "approximately", "vers", "omstreeks", "omstr", "omkring", "omk"};
  private static String[] gedcomCal =  {"Cal", "cal", "calculated", "calc", "calcd"};
  private static String[] gedcomEst =  {"Est", "est", "estimated", "estd", "c", "ca", "circa", "cir", "say", "ansl", "anslat"};
  private static String[] gedcomBef =  {"Bef", "bef", "before", "bfr", "by", "voor", "vóór", "før", "avant"};
  private static String[] gedcomAft =  {"Aft", "aft", "after", "na", "ett", "etter"};
  private static String[] gedcomFrom = {"From", "from", "frm", "van"};
  private static String[] gedcomTo  =  {"to", "to", "tot", "until"};
  private static String[] gedcomBet =  {"Bet", "bet", "between", "btw"};
  private static String[] gedcomAnd =  {"and", "and", "&"};
  private static String[] gedcomInt =  {"Int", "int", "interpreted"};

  private static String[][] gedcomMods = { gedcomAbt, gedcomCal, gedcomEst, gedcomBef, gedcomAft, gedcomFrom, gedcomTo, gedcomBet, gedcomAnd, gedcomInt };
  
  private static String[] ordinalSuffixes = {"st", "nd", "rd", "th"};    
  
  /* Note: Keep this list in sync with the list in ESINHandler.php */
  private static String[] discreteEventTypes = {"Birth", "Christening", "Death", "Burial", "Alt Birth", "Alt Christening", "Alt Death", "Alt Burial",
      "Adoption", "Baptism", "Bar Mitzvah", "Bat Mitzvah", "Blessing", "Confirmation", "Cremation", "Degree", "Emigration",
      "First Communion", "Funeral", "Graduation", "Immigration", "Naturalization", "Ordination", "Stillborn", "Will", "Estate Inventory",
      "Marriage", "Alt Marriage", "Marriage License", "Marriage Bond", "Marriage Contract", "Divorce Filing", "Divorce", "Annulment"};                            
  private static List<String> discreteEvents = Arrays.asList(discreteEventTypes);                            

  private String[] parsedYear = new String[2];
  private String[] parsedMonth = new String[2];
  private String[] parsedDay = new String[2];
  private String[] parsedModifier = new String[2];
  private String[] parsedSuffix = new String[2];
  private String[] parsedEffectiveYear = new String[2];
  private String   parsedText;
  private String   originalWithoutText;
  private String   formatedDate;
  private String   errorMessage;

  private boolean parseSuccessful = false;
  private boolean dateEdited = false;
  private boolean editSuccessful = false;
  private boolean discreteEvent = false;
  private boolean significantReformat = false;
 
  // Constructors store the original date and parse it for use by other methods. 
  public EventDate(String date) {
    if ( date != null) {
      originalDate = date;
      parseDate();
    }
    else {
      originalDate = "";
    }
    discreteEvent = false;
  }

  public EventDate(String date, String eventType) {
    if ( date != null) {
      originalDate = date;
      parseDate();
    }
    else {
      originalDate = "";
    }
    if (eventType != null) {
      discreteEvent = discreteEvents.contains(eventType);
    }
  }
    
  public String getOriginalDate() {
    return originalDate;
  }

  // Return the formated date (whether or not there were errors).
  public String getFormatedDate() {
    if (!dateEdited) {
      editDate();
    }
    return formatedDate;
  }
  
  public String getErrorMessage() {
    if (!dateEdited) {
      editDate();
    }
    return errorMessage;
  }

  public boolean getSignificantReformat() {
    if (!dateEdited) {
      editDate();
    }
    return significantReformat;
  }

  // Return the formated date if no errors, and the original date otherwise.
  public String formatDate() {
    if (!dateEdited) {
      editDate();
    }
    if ( editSuccessful ) {
      return formatedDate;
    }
    else {
      return originalDate;
    }
  }

  /* This method returns the year range of a date. If the date does not have a range, it returns the year (which could be null).
  */
  public String getYearRange() {
    if (!dateEdited) {
      editDate();
    }

    if ( parsedYear[0] != null ) {
      // If this is a date range and the start year and end year are the same, return the year (once) without modifiers.
      if ( parsedYear[1] != null && parsedYear[0].equals(parsedYear[1]) 
            && ((parsedSuffix[0] == null && parsedSuffix[1] == null) || 
                (parsedSuffix[0] != null && parsedSuffix[1] != null && parsedSuffix[0].equals(parsedSuffix[1]))) ) {
        return parsedYear[1] + (parsedSuffix[1] != null ? " " + parsedSuffix[1] : "");
      }
      else {
        return (parsedModifier[1] != null ? parsedModifier[1] + " " : "") + 
                (parsedYear[1] != null ? parsedYear[1] + " " : "") +
                (parsedSuffix[1] != null ? parsedSuffix[1] + " " : "") +
                (parsedModifier[0] != null ? (parsedModifier[0].equals("and") ? "&" : parsedModifier[0]) + " " : "") +
                parsedYear[0] +
                (parsedSuffix[0] != null ? " " + parsedSuffix[0] : "");

      }
    }
    else {
      return null;
    }
  }

  /* This method returns the year of a date (which could be null). If the date has a range, it returns only the last year of the range.
     This is different from getEffectiveYear in that it returns a split year as is.
  */
  public String getYearOnly() {
    if (parsedYear[0] != null) {
      return parsedYear[0] + (parsedSuffix[0] != null ? " " + parsedSuffix[0] : "") ;
    }
    else {
      return null;
    }
  }

  // Return the end year in the date (which could be null). 
  // If it is a split year, return the effective year (e.g., 1624 for 1623/24).
  public String getEffectiveYear() {
    return parsedEffectiveYear[0];
  }

  // Return the start year in the date (which could be null). 
  // If it is a split year, return the effective year (e.g., 1624 for 1623/24).
  public String getEffectiveStartYear() {
    if (parsedEffectiveYear[1]!=null) {
      return parsedEffectiveYear[1];
    } 
    else {
      return parsedEffectiveYear[0];
    }
  }

  /* Return (as an integer) the earliest year this date could represent. 
     If it is a split year, return the effective year (e.g., 1624 for 1623/24).
     If the earliest year is unknown (i.e. date starts with 'Bef' or 'To'), return null.
  */
  public Integer getEarliestYear() {
    /* This works on the parsed fields to attempt to get values even if the date doesn't pass the edit check. */
    for (int i=1; i>=0; i--) {
      if ( ! (parsedModifier[i]!=null && (parsedModifier[i].equals("Bef") || parsedModifier[i].toLowerCase().equals("to"))) ) {
        if (parsedEffectiveYear[i]!=null) {
          return Integer.valueOf(parsedEffectiveYear[i]);
        }
      }
    }
    return null;
  }

  /* Return (as an integer) the latest year this date could represent. 
     If it is a split year, return the effective year (e.g., 1624 for 1623/24).
     If the latest year is unknown (i.e. date starts with 'Aft' or starts with 'From' and there is no 'to'), return null.
  */
  public Integer getLatestYear() {
    /* This works on the parsed fields to attempt to get values even if the date doesn't pass the edit check. */
    if ( ! (parsedModifier[0]!=null && (parsedModifier[0].equals("Aft") || parsedModifier[0].equals("From"))) ) {
      if (parsedEffectiveYear[0]!=null) {
        return Integer.valueOf(parsedEffectiveYear[0]);
      }
    }
    return null;
  }

  /* This method returns the date as a yyyymmdd number for sorting purposes (0 if the date has no years).
   * The result has 00 placeholders for missing month/day data so that the numbers sort properly.
   * The result is adjusted based on bef/aft/bet/from/to modifiers.
   * BC dates are returned as negative numbers.
   * If the date is a date range, the start date is used.
   */
  public Integer getDateSortKey() {
    // Use start date if a date range (only date if not).
    int i;
    if (parsedEffectiveYear[1]!=null) {
      i = 1;
    } 
    else {
      if (parsedEffectiveYear[0]!=null) {
        i = 0;
      }
      else {
        return 0;
      }
    }

    // Create basic sort key. 00 placeholders for missing month and/or day. Negative number for BC dates.

    // If this is a BC date, multiply by -1 for the purpose of the remaining logic and then by -1 again before returning.
    // Multiplier is also used in the logic for adjusting the year based on the modifier.
    Integer absYear;
    int multiplier = 1;
    if ( Integer.parseInt(parsedEffectiveYear[i]) < 0 ) {
      multiplier = -1;
    }  
    absYear = Integer.parseInt(parsedEffectiveYear[i]) * multiplier;
    int monthNum = parsedMonth[i]==null ? 0 : getMonthNumber(parsedMonth[i]);

    Integer dateKey = absYear * 10000;

    dateKey += monthNum * 100;
    if (parsedDay[i]!=null) {
      dateKey += Integer.parseInt(parsedDay[i]);
    }

    // If no modifier, done - return the basic sort key.
    if (parsedModifier[i]==null) {
      return dateKey * multiplier;
    }

    // Adjust the date based on the modifier.

    // Handle situation where year, month and day are all present.
    // (Note that adjusting treats all years as leap years - good enough for sort dates.)
    if (parsedMonth[i]!=null && parsedDay[i]!=null) {
      if ( parsedModifier[i].equals("Bef") || parsedModifier[i].toLowerCase().equals("to")) {
        // Handle subtracting one from first day of month.
        if (parsedDay[i].equals("1")) {
          if (monthNum==1) {
            dateKey = (absYear-(1*multiplier)) * 10000 + 1231;  // Dec 31 of previous year
          }
          else {
            dateKey = absYear * 10000 + (monthNum-1) * 100 + 31; // 31st of previous month
            if (monthNum==3) {
              dateKey -= 2;                                       // If previous month=Feb, set to 29 instead
            }
            else {
              if (monthNum==5 || monthNum==7 || monthNum==10 || monthNum==12) {
                dateKey -= 1;                                     // If previous month=Apr, Jun, Sep or Nov, set to 30
              }
            }
          }
        }
        // All other dates, just subtract one from the previously constructed dateKey.
        else {
          dateKey -= 1;
        }
      }
      if ( parsedModifier[i].equals("Aft") || parsedModifier[i].equals("Bet") ||
           parsedModifier[i].equals("From") ) {
        // Handle adding one to last day of month.
        if (parsedDay[i].equals("31") ||
              (parsedDay[i].equals("29") && monthNum==2) ||
              (parsedDay[i].equals("30") && (monthNum==4 || monthNum==6 || monthNum==9 || monthNum==11))) {
          if (monthNum==12) {
            dateKey = (absYear+(1*multiplier)) * 10000 + 101;  // Jan 1 of next year
          }
          else {
            dateKey = absYear * 10000 + (monthNum+1) * 100 + 1; // first of next month
          }
        }
        // All other dates, just add one to the previously constructed dateKey.
        else {
          dateKey += 1;
        }
      }
      return dateKey * multiplier;
    }
      
    // Handle situation where only year and month are present.
    if (parsedMonth[i]!=null && parsedDay[i]==null) {
      if ( parsedModifier[i].equals("Bef") || parsedModifier[i].toLowerCase().equals("to") ) {
        if (monthNum==1) {
          dateKey = (absYear-(1*multiplier)) * 10000 + 1200;  // Dec of previous year
        }
        // All other months, subtract one month from the previously constructed dateKey.
        else {
          dateKey -= 100;
        }
      }
      if ( parsedModifier[i].equals("Aft") || parsedModifier[i].equals("Bet") ||
           parsedModifier[i].equals("From") ) {
        if (monthNum==12) {
          dateKey = (absYear+(1*multiplier)) * 10000 + 100;  // Jan of next year
        }
        // All other months, add one month to the previously constructed dateKey.
        else {
          dateKey += 100;
        }
      }
      return dateKey * multiplier;
    }
      
    // Handle situation where only year is present. 
    // Subtract or add one year from dateKey if applicable.
    if ( parsedModifier[i].equals("Bef") || parsedModifier[i].toLowerCase().equals("to") ) {
      dateKey = dateKey - (10000*multiplier);
    } 
    if ( parsedModifier[i].equals("Aft") || parsedModifier[i].equals("Bet") ||
         parsedModifier[i].equals("From") ) {
      dateKey = dateKey + (10000*multiplier);
    }
    return dateKey * multiplier;
  }
  
  /* This method returns the date as a yyyymmdd string for sorting purposes where the context requires a string.
   * The year, month, and day are all zero-padded on the left so that the strings sort properly. 
   * The result is NOT adjusted based on bef/aft/bet/from/to modifiers.
   * BC dates are returned as an empty string (not worth changing other code to handle them).
   * If the date is a date range, the start date is used.
   */
  public String getDateStringKey() {
    // Use start date if a date range (only date if not).
    int i;
    if (parsedEffectiveYear[1]!=null) {
      i = 1;
    } 
    else {
      if (parsedEffectiveYear[0]!=null) {
        i = 0;
      }
      else {
        return "";
      }
    }

    // If a BC date, return empty string.
    if (parsedSuffix[i] != null && parsedSuffix[i].equals("BC")) {
      return "";
    }

    return String.format("%04d",Integer.parseInt(parsedEffectiveYear[i])) + 
          ((parsedMonth[i] != null) ? String.format("%02d",getMonthNumber(parsedMonth[i])) : "") +
          ((parsedDay[i] != null) ? String.format("%02d", Integer.parseInt(parsedDay[i])) : "");
  }

  public String getIsoDate() {
    String $yyyymmdd = getDateStringKey();
    if ($yyyymmdd.equals("")) {
      return "";
    }
    return $yyyymmdd.substring(0,4) + 
          ($yyyymmdd.length() >= 6 ? "-" + $yyyymmdd.substring(4,6) : "") + 
          ($yyyymmdd.length() == 8 ? "-" + $yyyymmdd.substring(6,8) : "");
  }

  /* Calculate a minimum day number for an event date for the purpose of comparing 2 event dates.
   * If the date has a date range, use the start date.
   * If the date lacks precision (e.g., is only a year), use the beginning of the time period it describes.
   * If the date is "before" a date, use 10 years before the given date.
   * If the date is an approximated or estimated date, use 1 year, 91 days (3 months) or 10 days before the date
   * (depending on the precision).
   * 
   * Note that this is a "good enough" calculation for the purpose of comparing dates within a few years
   * of each other. As such, the calculation ignores leap years and doesn't account for the switch from the Julian
   * to the Gregorian calendar.
   */ 
  public Integer getMinDay() {
    // If the date has a date range, use the start date. Otherwise, use the only date.
    int i;
    if (parsedEffectiveYear[1]!=null) {
      i = 1;
    }
    else {
      if (parsedEffectiveYear[0]!=null) {
        i = 0;
      }
      else {
        return 0;
      }
    }

    Integer dayNumber = Integer.parseInt(parsedEffectiveYear[i]) * 365;
    if (parsedMonth[i]!=null) {
      dayNumber += MONTH_OFFSETS[getMonthNumber(parsedMonth[i])];      
      if (parsedDay[i]!=null) {
        dayNumber += Integer.parseInt(parsedDay[i]);
      }
      else {
        dayNumber += 1;  // Assume first of month when only the year and month are given
      }
    }
    else {
      dayNumber += 1;  // Assume 1 Jan when only the year is given
    }

    if (parsedModifier[i]!=null && parsedModifier[i].equals("Bef")) {
      return dayNumber - 3650;
    }

    if (parsedModifier[i]!=null && (parsedModifier[i].equals("Abt") || parsedModifier[i].equals("Est"))) {
      if (parsedDay[i]!=null) {
        return dayNumber - 10;
      }
      if (parsedMonth[i]!=null) {
        return dayNumber - 91;
      }
      return dayNumber - 365;
    }

    return dayNumber;
  }

  /* Calculate a maximum day number for an event date for the purpose of comparing 2 event dates.
   * If the date has a date range, use the end date.
   * If the date lacks precision (e.g., is only a year), use the end of the time period it describes.
   * If the date is "after" a date, use 10 years after the given date.
   * If the date is an approximated or estimated date, use 1 year, 91 days (3 months) or 10 days after the date
   * (depending on the precision).
   * 
   * Note that this is a "good enough" calculation for the purpose of comparing dates within a few years
   * of each other. As such, the calculation ignores leap years and doesn't account for the switch from the Julian
   * to the Gregorian calendar.
   */ 
  public Integer getMaxDay() {
    if (parsedEffectiveYear[0]==null) {
      return 0;
    }

    Integer dayNumber = Integer.parseInt(parsedEffectiveYear[0]) * 365;
    if (parsedMonth[0]!=null) {
      if (parsedDay[0]==null) {
        dayNumber += MONTH_OFFSETS[getMonthNumber(parsedMonth[0])+1];  // Assume end of month when day is missing 
      }
      else {
        dayNumber += MONTH_OFFSETS[getMonthNumber(parsedMonth[0])];    // End of previous month
        dayNumber += Integer.parseInt(parsedDay[0]);                   // plus this day
      }
    }
    else {
      dayNumber += 365;                                       // Assume end of year when only the year is given
    }

    if (parsedModifier[0]!=null && parsedModifier[0].equals("Aft")) {
      return dayNumber + 3650;
    }

    if (parsedModifier[0]!=null && (parsedModifier[0].equals("Abt") || parsedModifier[0].equals("Est"))) {
      if (parsedDay[0]!=null) {
        return dayNumber + 10;
      }
      if (parsedMonth[0]!=null) {
        return dayNumber + 91;
      }
      return dayNumber + 365;
    }

    return dayNumber;
  }

  // Return parsed fields
  // This is for PHP, which needs the parsed fields in order to construct a language-specific date based on the user's language preference.
  public List getParsedDate() {
    return Arrays.asList(parsedYear, parsedMonth, parsedDay, parsedModifier, parsedSuffix, parsedText);
  }  

  // Edit the date - this both formats the date for display and checks for errors not caught in parsing.     
  public boolean editDate() {
    int i;

    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Auckland"));      // New Zealand is unlikely to switch to the other side of the intl date line
    int thisYear = cal.get(Calendar.YEAR);
    int thisMonth = cal.get(Calendar.MONTH) + 1;
    int thisDay = cal.get(Calendar.DAY_OF_MONTH);
        
/*  This uses the more up-to-date time classes, which don't seem to be supported by WeRelate yet.  
    LocalDate today = LocalDate.now(ZoneId.of("Pacific/Auckland"));  // New Zealand (unlikely to move over intl date line)
    int thisYear = today.getYear();
    int thisMonth = today.getMonthValue();
    int thisDay = today.getDayOfMonth();
*/
            
    // Track if edited - edit only if and when needed.
    dateEdited = true;

    formatedDate = "";
    if (!parseSuccessful) {                               // if parsing (at instantiation) was unsuccessful, return
      return false;
    }

    // Check for some overall errors and do a few fixes before dealing with each part of the parsed date

    // Error if a pair of modifiers or a pair of dates does not use modifiers Bet/and or From/to
    if ( !(parsedModifier[1]==null) || !(parsedYear[1]==null) ) {
      if ( parsedModifier[1]==null ||
           ! ((parsedModifier[1].equals("Bet") && parsedModifier[0].equals("and")) || (parsedModifier[1].equals("From") && parsedModifier[0].equals("to"))) ) {
        errorMessage = "Invalid combination of modifiers";
        return false;  
      }
      if ( discreteEvent && parsedModifier[1].equals("From") ) {  // if this is a discrete event, change From/to (misused) to Bet/and
        parsedModifier[1] = "Bet";
        parsedModifier[0] = "and";
      }
 
    }
    else {
      if ( parsedModifier[0]!=null ) {
        if ( parsedModifier[0].equals("to") ) {                   // "to" can be used on its own - if so, capitalize
          parsedModifier[0] = "To";
        }
        if ( parsedModifier[0].equals("Bet") || parsedModifier[0].equals("and") ) { // error if "Bet" or "and" used on their own
          errorMessage = "Incorrect usage of bet/and";
          return false;
        }
      }
    }

    // Edit and format each part of the parsed date
    for (i=1; i>=0; i--) {
      if ( parsedModifier[i] != null || parsedYear[i] != null || parsedMonth[i] != null || parsedDay[i] != null || parsedSuffix[i] != null ) {

        // error if no year, or if day without month
        if ( parsedYear[i]==null || (parsedDay[i]!=null && parsedMonth[i]==null) ) {
          formatedDate = "";
          errorMessage = "Incomplete date";
          return false;
        }
        // error if day does not match month (leap day not checked)
        if ( parsedDay[i]!=null && ((Integer.parseInt(parsedDay[i]) > 29 && parsedMonth[i].equals("Feb")) || 
             (Integer.parseInt(parsedDay[i]) > 30 && (parsedMonth[i].equals("Apr") || parsedMonth[i].equals("Jun") ||
              parsedMonth[i].equals("Sep") || parsedMonth[i].equals("Nov")))) ) {
          formatedDate = "";
          errorMessage = "Invalid day for " + parsedMonth[i];
          return false;
        }
        // error if split year for a month after Mar
        if ( parsedYear[i].contains("/") && parsedMonth[i]!=null && 
             !parsedMonth[i].equals("Jan") && !parsedMonth[i].equals("Feb") && !parsedMonth[i].equals("Mar") ) {
          formatedDate = "";
          errorMessage = "Split year valid only for Jan-Mar";
          return false;
        }
         
        formatedDate += (!formatedDate.equals("") ? " " : "") + 
                        (parsedModifier[i] != null ? parsedModifier[i] + " " : "") + 
                        (parsedDay[i] != null ? parsedDay[i] + " " : "") + 
                        (parsedMonth[i] != null ? parsedMonth[i] + " " : "") + 
                        (parsedYear[i] != null ? parsedYear[i] : "") +
                        (parsedSuffix[i] != null ? " " + parsedSuffix[i] : "");
      }
    }
    
    // Check for invalid date range - done after checking for incomplete date above
    if ( parsedEffectiveYear[1]!=null ) {
      if ( Integer.parseInt(parsedEffectiveYear[1]) > Integer.parseInt(parsedEffectiveYear[0]) ||
           (Integer.parseInt(parsedEffectiveYear[1]) == Integer.parseInt(parsedEffectiveYear[0]) &&
               (parsedMonth[0]==null || parsedMonth[1]==null || getMonthNumber(parsedMonth[1]) > getMonthNumber(parsedMonth[0]))) ||
           (Integer.parseInt(parsedEffectiveYear[1]) == Integer.parseInt(parsedEffectiveYear[0]) && getMonthNumber(parsedMonth[1]) == getMonthNumber(parsedMonth[0]) &&
               (parsedDay[0]==null || parsedDay[1]==null || Integer.parseInt(parsedDay[1]) > Integer.parseInt(parsedDay[0]))) ) {
        formatedDate = "";
        errorMessage = "Invalid date range";
        return false;
      }  
    }
    
    // Future dates are not allowed
    for (i=0; i<=1; i++) {
      if ( (parsedEffectiveYear[i]!=null && Integer.parseInt(parsedEffectiveYear[i]) > thisYear) ||
           (parsedMonth[i]!=null && Integer.parseInt(parsedEffectiveYear[i]) == thisYear && 
              getMonthNumber(parsedMonth[i]) > thisMonth) || 
           (parsedDay[i]!=null && Integer.parseInt(parsedEffectiveYear[i]) == thisYear &&
              getMonthNumber(parsedMonth[i]) == thisMonth && Integer.parseInt(parsedDay[i]) > thisDay) ) {
        formatedDate = "";
        errorMessage = "Future date";
        return false;
      }
    }

    if ( parsedText != null ) {
      formatedDate += (formatedDate.equals("") ? parsedText : " " + parsedText);
    }
    editSuccessful = true;
    return editSuccessful;
  }

  /* Parse the event date into modifier, day, month, year and suffix (for each date if a date range) and parenthetical text portion.
   * Also calculates the effective year (for each date if a date range) and set an error message if appropriate.
   * If this is a date range (bet/and or from/to), [0] is the second date and [1] is the first date (because parsing
   * works from the end to the beginning of the event date).
   */
  private boolean parseDate() {
    boolean saveOriginal = false;
    boolean findSplitYear = false;
    boolean possibleSplitYear = false;
    int i, dateIndex, num;
    String date = "";                       // working date
    String year = "", firstNum = "", secondNum = "";
    String dateFirstChar, dateRest;
    String m, q;
    String[] splitDate;
    String[] embeddedDate = new String[2];
    String[] dateString = new String[2];
    String[] fields = new String[15];      // accommodate about twice as many fields as should exist in a date
    int[] dateStart = new int[2];
    significantReformat = false;
    
    // If the date ends with text in parentheses (GEDCOM standard), remove the parenthetical portion and save it separately
    if (originalDate.contains("(") && originalDate.trim().endsWith(")")) {
      splitDate = originalDate.split("\\(", 2);
      parsedText = "(" + splitDate[1].trim();
      originalWithoutText = splitDate[0].trim();
    }
    else {
      originalWithoutText = originalDate.trim();
    }

    // Prepare: lower case; remove leading and trailing whitespace, reduce internal strings of whitespace to one space each,
    // and replace long dashes with short dashes
    // Original date (minus any text portion removed above) is retained in case it needs to be returned in the text portion
    date = originalWithoutText.toLowerCase().trim().replaceAll("\\s+"," ");
    date = date.replaceAll("–","-");
    
    // Special cases
    switch ( date ) {
      case "":
        parseSuccessful = true;
        break;
      case "unknown":                    // unknown (or variation) will result in a blank date (or parenthetical portion if there is one)
        parseSuccessful = true;
        break;
      case "date unknown":
        parseSuccessful = true;
        break;
      case "unk":
        parseSuccessful = true;
        break;
      case "unknow":
        parseSuccessful = true;
        break;
      case "not known":
        parseSuccessful = true;
        break;
      case "unbekannt":
        parseSuccessful = true;
        break;
      case "unbek.":
        parseSuccessful = true;
        break;
      case "onbekend":
        parseSuccessful = true;
        break;
      case "inconnue":
        parseSuccessful = true;
        break;
      case "in infancy":
        parsedText = "(in infancy)" + ( parsedText!=null ? " " + parsedText : "" );
        parseSuccessful = true;
        break;
      case "died in infancy":
        parsedText = "(in infancy)" + ( parsedText!=null ? " " + parsedText : "" );
        parseSuccessful = true;
        break;
      case "infant":
        parsedText = "(in infancy)" + ( parsedText!=null ? " " + parsedText : "" );
        parseSuccessful = true;
        break;
      case "infancy":
        parsedText = "(in infancy)" + ( parsedText!=null ? " " + parsedText : "" );
        parseSuccessful = true;
        break;
      case "young":
        parsedText = "(young)" + ( parsedText!=null ? " " + parsedText : "" );
        parseSuccessful = true;
        break;
      case "died young":
        parsedText = "(young)" + ( parsedText!=null ? " " + parsedText : "" );
        parseSuccessful = true;
        break;
    }

    if (parseSuccessful) {
      return parseSuccessful;
    }

    // check for WFT EST - not a valid GEDCOM date
    if ( date.contains("wft est") ) {
      errorMessage = "WFT estimates not accepted";
      return false;
    }

    // Convert up to 2 valid yyyy-mm-dd dates to GEDCOM format (dd mmm yyyy) before continuing with parsing. 
    // This is somewhat inefficient because of having to reparse these dates, but it was the easiest way to add this code, and these dates are not common.
    Pattern regexFull = Pattern.compile("\\d{3,4}[\\-\\./]\\d{1,2}[\\-\\./]\\d{1,2}");
    Pattern regexPart = Pattern.compile("\\d+");
    Matcher yyyymmdd = regexFull.matcher(date);
    for ( i=0; yyyymmdd.find() && i<2; i++ ) {
      dateString[i] = yyyymmdd.group();
      dateStart[i] = yyyymmdd.start();
      Matcher part = regexPart.matcher(dateString[i]);
      if ( part.find() ) {
        year = part.group();
      }
      if ( part.find() ) {
        firstNum = part.group();
      }
      if ( part.find() ) {
        secondNum = part.group();
      }
      if ( isNumMonth(Integer.parseInt(firstNum)) ) {
        embeddedDate[i] = secondNum + " " + gedcomMonths[Integer.parseInt(firstNum)-1][0] + " " + year;
        significantReformat = true;
      }
      else {
        embeddedDate[i] = dateString[i];
      }
    }

    // Replace embedded dates already in yyyy-mm-dd format with the GEDCOM equivalent.
    // Start with the last embedded date, because any replacement of an earlier one can affect the offset (position of the embedded date within the string).
    for ( i--; i>=0; i--) {
      date = date.substring(0,dateStart[i]) + embeddedDate[i] + date.substring(dateStart[i]+dateString[i].length());
    }

    // Check for up to 2 embedded dates in mm-dd-yyyy or dd-mm-yyyy format. 
    // If a date is ambiguous, return an error message. Otherwise, if valid, convert to GEDCOM format (dd mmm yyyy) before continuing with parsing.
    Pattern regexAmb = Pattern.compile("\\d{1,2}[\\-\\./]\\d{1,2}[\\-\\./]\\d{3,4}");
    Matcher ambDate = regexAmb.matcher(date);
    for ( i=0; ambDate.find() && i<2; i++ ) {
      dateString[i] = ambDate.group();
      dateStart[i] = ambDate.start();
      Matcher ambPart = regexPart.matcher(dateString[i]);
      if ( ambPart.find() ) {
        firstNum = ambPart.group();
      }
      if ( ambPart.find() ) {
        secondNum = ambPart.group();
      }
      if ( ambPart.find() ) {
        year = ambPart.group();
      }
      if ( !isNumMonth(Integer.parseInt(firstNum)) && isNumMonth(Integer.parseInt(secondNum)) ) {
        embeddedDate[i] = firstNum + " " + gedcomMonths[Integer.parseInt(secondNum)-1][0] + " " + year;
        significantReformat = true;
      }
      else {
        if ( !isNumMonth(Integer.parseInt(secondNum)) && isNumMonth(Integer.parseInt(firstNum)) ) {
          embeddedDate[i] = secondNum + " " + gedcomMonths[Integer.parseInt(firstNum)-1][0] + " " + year;
          significantReformat = true;
        }
        else {
          if ( Integer.parseInt(firstNum) == Integer.parseInt(secondNum) && isNumMonth(Integer.parseInt(firstNum)) ) {
            embeddedDate[i] = secondNum + " " + gedcomMonths[Integer.parseInt(firstNum)-1][0] + " " + year;
            significantReformat = true;
          }
          else {
            parsedYear[0] = year;                          // Even if the date is ambiguous, the year can be returned for functions that require it
            parsedEffectiveYear[0] = year;                 // Not worried whether this is the first or second date since it is very rare to have a range with numeric dates 
            errorMessage = "Ambiguous date";
            return false;
          }
        }
      }
    }

    // Replace embedded ambiguous dates that could be resolved with the GEDCOM equivalent.
    // Start with the last embedded date, because any replacement of an earlier one can affect the offset (position of the embedded date within the string).
    for ( i--; i>=0; i-- ) {
      date = date.substring(0,dateStart[i]) + embeddedDate[i] + date.substring(dateStart[i]+dateString[i].length());
    }

    // If the date includes one or more split years expressed with uncertainty ([/d?] or [/dd?]), set a flag to save the original in the parenthetical text portion
    // and remove the ? so that it is not also added separately to the text portion in code below. 
    // The remaining code will ignore the brackets and format the split year correctly.
    Pattern regexUsy = Pattern.compile("\\[/\\d{1,2}\\?\\]");
    Matcher usyDate = regexUsy.matcher(date);
    for ( i=0; usyDate.find(); i++ ) {         // i is used to adjust starting place on second and subsequent patterns due to previous removal of "?"
      saveOriginal = true;
      significantReformat = true;
      date = date.substring(0,usyDate.start()-i) + usyDate.group().replace("?","") + date.substring(usyDate.start()-i+usyDate.group().length());
    }

    // If date includes a dash, replace with "to" or "and" depending on whether the string already has "from/est" or "bet".
    // If it has neither, treat as bet/and (applicable to all types of events).
    if ( date.contains("-")) {
      if ( date.contains("bet") || date.contains("btw") || date.contains("between"))  {
        date = date.replace("-", " and ");
      }
      else {
        date = date.replace("-", " to ");
        if ( !date.contains("from") && !date.contains("frm")) {
          date = "from " + date;
        }
      }
      significantReformat = true;
    }

    // replace b.c. with bc unless it is at the beginning of the string (when the user might have been intending "before circa")
    if ( date.contains("b.c.") ) {
      dateFirstChar = date.substring(0,1);
      dateRest = date.substring(1);
      date = dateFirstChar + dateRest.replace("b.c." , "bc");
      significantReformat = true;
    }
   
    // this should match 0-9+ or / or ? or & or alphabetic(including accented)+
    Pattern regexAll = Pattern.compile("(\\d+|[^0-9\\s`~!@#%\\^\\*\\(\\)_\\+\\-\\=\\{\\}\\|:'\\<\\>;,\"\\[\\]\\.\\\\]+)");
    Matcher dateField = regexAll.matcher(date);
    for ( i=0; dateField.find() && i<fields.length; i++ ) {
      fields[i] = dateField.group();
    }
    
    // start at the last field so that first numeric encountered is treated as the year
    dateIndex = 0;                                              // index=0 for the last date; index=1 for the first date if there are 2
    for (i--; i>=0 && dateIndex<2; i--) {
      if ( fields[i].equals("/") ) {
        if ( parsedYear[dateIndex] == null && !possibleSplitYear ) {   // error if second part of the split year not already captured
          errorMessage = "Incomplete split year";
          return false;
        }
        // error if split year, month or day already captured for this date (could indicate an either/or date with "/" meaning "or")
        if ( (!(parsedYear[dateIndex]==null) && parsedYear[dateIndex].contains("/")) || !(parsedMonth[dateIndex]==null) || !(parsedDay[dateIndex]==null) ) {
          errorMessage = "Invalid date format";
          return false;
        }
        else {
          findSplitYear = true;  
          if ( possibleSplitYear ) {                            // if digit(s) after / were 0, save them in the year now (as second part of the split year)
            parsedYear[dateIndex] = "0";
            possibleSplitYear = false;
          }
        }
      }
      else {
        if ( findSplitYear ) {                                  // if waiting for the first part of a split year, treat this field as the first part
          if ( !isNumeric(fields[i]) ) {                        // if field is not numeric, return error (added Feb 2021 by Janet Bjorndahl)
            errorMessage = "Incomplete split year";             // (year and effective year have already been captured - the value after the /)
            return false;
          }
          if (!editSplitYear(fields[i], dateIndex) ) {          // parsedYear[dateIndex] is updated if no error
            parsedYear[dateIndex] = fields[i];                  // save first part of split year even if not valid (closest we have to a year for functions that need it)
            parsedEffectiveYear[dateIndex] = fields[i];  
            return false;
          }
          parsedEffectiveYear[dateIndex] = Integer.toString(Integer.parseInt(fields[i]) + 1);       // capture the effective year (first part + 1)
          findSplitYear = false;
        }

        else {
          if ( fields[i].equals("bce") || fields[i].equals("bc") ) {
            parsedSuffix[dateIndex] = "BC";
          }
          else {
            // If the field is numeric, have to determine whether to treat is as the day or the year.
            if ( isNumeric(fields[i]) ) {
              num = Integer.parseInt(fields[i]);                 // convert to number - this strips leading zeros
              if ( num == 0 && parsedYear[dateIndex] == null ) {  
                possibleSplitYear = true;                        // a value of 0 is not valid for anything other than the second part of a split year: keep track of it
              }
              
              else {
                if ( isDay(num) ) {                              // if this field could be a day, it could also be a year. Need some logic to determine how to treat it.
                  // If month and/or year is already captured, treat it as the day.
                  if ( parsedMonth[dateIndex] != null || parsedYear[dateIndex] != null ) {             
                    if ( parsedDay[dateIndex] != null ) {               // error if day already has a value
                      errorMessage = "Too many numbers (days/years)";
                      return false;
                    }
                    else {
                      parsedDay[dateIndex] = Integer.toString(num);          
                    }
                  }
                  // If neither month nor year is captured, this would normally be treated as the year.
                  // However, in the case of bet/and or from/to, the first date can pick up the year and month from the second date (e.g.,
                  // ("Bet 10 and 15 Oct 1823" becomes "Bet 10 Oct 1823 and 15 Oct 1823") as long as this results in a valid date range.
                  // If this is applicable, treat this field as the day.
                  // UNLESS either of the following situations is true:
                  // * This field could be the second part of a split year - if the field before this one is "/", treat this as the year. 
                  // * This could be a year between 32 BC and 32 AD - if the year already captured is < 4 digits, treat this as the year.
                  else {
                    if ( !(i>0 && fields[i-1].equals("/")) &&           
                           dateIndex == 1 && parsedYear[1] == null && parsedYear[0] != null && parsedYear[0].length() > 3 ) {  
                      if ( parsedDay[0] != null && num < Integer.parseInt(parsedDay[0]) ) { 
                        parsedYear[1] = parsedYear[0];                        
                        parsedEffectiveYear[1] = parsedEffectiveYear[0];
                        parsedMonth[1] = parsedMonth[0];
                        parsedDay[1] = Integer.toString(num);
                        significantReformat = true;
                      }
                      else {                      
                        if ( !treatAsYearRange(num,Integer.parseInt(parsedEffectiveYear[0])) ) {  // if not valid to use this as the day, and not reasonable to use it as the year, error
                          errorMessage = "Missing month";
                          return false;
                        }
                      }
                    }
                    // If neither month nor year is captured, and it is not the special case above, treat it as the year (to be updated later if this is a split year)      
                    else {
                      parsedYear[dateIndex] = Integer.toString(num);             
                      parsedEffectiveYear[dateIndex] = parsedYear[dateIndex];           
                    }
                  }    
                }
                // Numeric field that is not a valid day is the year (or an error)
                else {
                  if ( parsedYear[dateIndex] != null ) {            // error if year already has a value
                    errorMessage = "Invalid day number";
                    return false;
                  }
                  else {
                    if ( isYear(num) ) {
                      parsedYear[dateIndex] = Integer.toString(num);             
                      parsedEffectiveYear[dateIndex] = parsedYear[dateIndex];           
                    }
                    else {                                               // error if a number is neither a valid day nor a valid year
                      errorMessage = "Invalid year number";  
                      return false;
                    }
                  }
                }
              }
            }
            // The field is not numeric.
            else {
              m = getMonthAbbrev(fields[i]);
              if ( !m.equals("not a month") ) {
                if ( parsedMonth[dateIndex] != null ) {                   // error if month already has a value
                  errorMessage = "Too many months";  
                  return false;
                }
                else {
                  parsedMonth[dateIndex] = m;
                }
              }
              else {
                q = getModifier(fields[i]);
                if ( !q.equals("not a modifier") ) {
                  parsedModifier[dateIndex++] = q;
                  if ( dateIndex > 1 && i > 0) {                          // If already processed 2 parts of the date and there are more fields, error
                    errorMessage = "Too many parts";
                    return false;
                  }
                }
                else { 
                  if ( fields[i].equals("?") ) {                              // if there is a question mark, capture it in a text field (too important to drop)
                    parsedText = "(?)" + ( parsedText != null ? " " + parsedText : "" );
                  }
                  else {
                    if ( Arrays.asList(ordinalSuffixes).contains(fields[i]) ) {     // ignore 'st', 'nd', 'rd', 'th'
                    }
                    else {
                      if ( fields[i].contains("wft") ) {                      // error if unrecognizable field
                        errorMessage = "WFT estimates not accepted";
                      }
                      else {  
                        errorMessage = "Unrecognized text";
                      }
                    return false;
                    }
                  }
                }
              }
            }
          }  
        }
      }
    }
    
    // If still waiting for first part of split year, message.
    if ( findSplitYear ) {
      errorMessage = "Incomplete split year";
      return false;
    }
    
    // If using bet/and or from/to and the first date [1] is missing the year (but has the month), pick it up from the second date [0]
    // as long as this results in a valid date range.
    // Note that picking up the month from the second date is handled in above code when dealing with the day.
    if ( dateIndex == 2 ) {                                            // dateIndex would have been incremented after the last modifier read
      if ( parsedYear[1]==null && parsedYear[0]!=null && parsedMonth[1]!=null && parsedMonth[0]!=null ) {
        if ( getMonthNumber(parsedMonth[1]) < getMonthNumber(parsedMonth[0]) ) { 
          parsedYear[1] = parsedYear[0];
          parsedEffectiveYear[1] = parsedEffectiveYear[0];
          significantReformat = true;
        }
      }
    }
    // If warranted, capture the original date in a parenthetical text portion (possibly in addition to an existing one)
    if ( saveOriginal ) {
      parsedText = "(" + originalWithoutText + ")" + ( parsedText!=null ? " " + parsedText : "" );
    }
  
    // If there is a pair of dates with "to" between them but no "From" before the first date, add the "From"
    if ( parsedYear[1]!=null && parsedModifier[1]==null && parsedModifier[0].equals("to") ) {
      parsedModifier[1] = "From";
      significantReformat = true;
    }
    // If (either) date is a BC year, make the effective year a negative number
    for (i=0; i<=dateIndex && i<2; i++) {
      if (parsedSuffix[i] != null && parsedSuffix[i].equals("BC") && parsedEffectiveYear[i] != null) {
        parsedEffectiveYear[i] = "-" + parsedEffectiveYear[i];
      }
    }
    parseSuccessful = true;
    return parseSuccessful;
  }
  
  private static boolean treatAsYearRange(int y1, int y2) {
    return ( (y2-y1) < 300 );
  }

  /* Note: Double-dating applies when the year started March 25 (not necessarily corresponding to when the Julian calendar was in use).
   * In England, the civil year started March 25 from the 12th century to 1752. From the 7th century to the 12th century, it started Dec 25.
   * We're allowing split year dates starting in 1000 because some other countries started the year in March before England did.
   * Most other countries started using Jan 1 as the beginning of the year around 1600 (Italy started about 1750).
   */
  private static boolean isDoubleDating(int y) { 
    return (y >= 1000 && y <= 1752);
  }
  
  private static String getMonthAbbrev(String m) {
    for ( int i=0 ; i<gedcomMonths.length; i++ ) {
      for ( int j=0; j<gedcomMonths[i].length; j++ ) {
        if ( m.equals(gedcomMonths[i][j]) ) {
          return gedcomMonths[i][0];
        }
      }
    }
    return "not a month";
  }
  
  private static int getMonthNumber(String m) {
    for ( int i=0 ; i<gedcomMonths.length; i++ ) {
      for ( int j=0; j<gedcomMonths[i].length; j++ ) {
        if ( m.equals(gedcomMonths[i][j]) ) {
          return i+1;
        }
      }
    }
    return 0;
  }
  
  private static String getModifier(String q) {
    for ( int i=0 ; i<gedcomMods.length; i++ ) {
      for ( int j=0; j<gedcomMods[i].length; j++ ) {
        if ( q.equals(gedcomMods[i][j]) ) {
          return gedcomMods[i][0];
        }
      }
    }
    return "not a modifier";
  }
 
  private boolean editSplitYear(String firstPart, int i) {  // firstPart is a numeric string and parsedYear[i] is a numeric string without leading zeros
    int num = Integer.parseInt(firstPart);
    if ( !isDoubleDating(num) ) {
      errorMessage = "Split year not valid for this year";
      return false;
    }
    String newYear = firstPart.substring(0,firstPart.length()-parsedYear[i].length()) + parsedYear[i];   
    if ( Integer.parseInt(newYear) - 1 == num ||
         (firstPart.substring(3).equals("9") && parsedYear[i].substring(parsedYear[i].length()-1).equals("0")) ) {
      parsedYear[i] = firstPart + "/" + (firstPart.substring(2).equals("99") ? "00" : String.format("%02d", Integer.parseInt(firstPart.substring(2))+1));          
      return true;
    }
    errorMessage = "Invalid split year";
    return false;
  }
  
  private static boolean isDay(int d) {
    return d >= 1 && d <= 31;
  }
  private static boolean isNumMonth(int m) {
    return m >= 1 && m <= 12;
  }
  private static boolean isYear(int y) {
    return y >= 1 && y <= 5000;
  }

  // This code is copied from wikidata/src/org/werelate/utils/Util.java
  private static final boolean isNumeric(final String s) {
    final char[] numbers = s.toCharArray();
    for (int x = 0; x < numbers.length; x++) {
      final char c = numbers[x];
      if ( c >= '0' && c <= '9' ) continue;
      return false;
    }
    return true;
  }

}
