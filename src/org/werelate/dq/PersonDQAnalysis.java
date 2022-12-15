/*
 * Copyright 2012 Foundation for On-Line Genealogy, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.werelate.dq;

import org.werelate.util.EventDate;
import org.werelate.dq.FamilyDQAnalysis;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Calendar;
import nu.xom.Elements;
import nu.xom.Element;

/**
 * This class analyzes a Person page to identify data quality issues and extract other data required for batch DQ analysis
 * User: DataAnalyst
 * Date: Dec 2022
 */
public class PersonDQAnalysis {
   private Integer earliestBirth = null, latestBirth = null, earliestDeath = null, latestDeath = null;
   private short diedYoungInd = 0;
   private String[][] issues = new String[1000][4]; // [][0] = category, [][1] = description, [][3] = namesspace, [][2] = pagetitle
   
   // Assumptions/thresholds for calculating years and identifying anomalies and errors
   private static final int absLongestLife = FamilyDQAnalysis.absLongestLife;
   private static final int usualLongestLife = FamilyDQAnalysis.usualLongestLife;

   private static int thisYear = Calendar.getInstance().get(Calendar.YEAR);

   // Issue categories and descriptions
   private static final String[] INVALID_DATE = FamilyDQAnalysis.INVALID_DATE;
   private static final String[] EVENT_ORDER = {"Error", "Events out of order"};
   private static final String[] LONG_LIFE = {"Error", "Event(s) more than " + absLongestLife + " years after birth"};
   private static final String[] MULT_PARENTS = {"Error", "Multiple sets of parents"};
   private static final String[] MISSING_GENDER = {"Incomplete", "Missing gender"};

   // For debugging in batch mode, using TestDQAnalysis.
   //private static final Logger logger = LogManager.getLogger("org.werelate.dq");

   /* Identify data quality issues for a Person page and derive other data required for batch DQ analysis */
   /**
    * @param root root element of the structured data for a person page
    * @param personTitle string title of the person page, without namespace
    */
   public PersonDQAnalysis(Element root, String personTitle) {
      Elements elms;
      Element elm;

      Integer latestPossBirth = null, firstNotBirth = null, firstPostDeath = null, lastLiving = null;
      Boolean proxyBirthInd = false, invalidDateInd = false, eventOrderError = false; 

      int issueNum = 0;

      // Gather dates required for analysis and determine if there are any invalid dates
      elms = root.getChildElements("event_fact");
      for (int i = 0; i < elms.size(); i++) {
         elm = elms.get(i);
         String eventType = elm.getAttributeValue("type");
         EventDate eventDate = new EventDate(elm.getAttributeValue("date"));
         String date = eventDate.formatDate();
         if (!date.equals("")) {
            // Track invalid dates. Optimize performance by only editing the date if no invalid dates already found.
            if (!invalidDateInd && !eventDate.editDate()) {
               invalidDateInd = true;
            }

            // Determine earliest and latest birth and death years

            // For birth year, consider birth, christening and baptism dates, in that precedence.
            if (eventType.equals("Birth") || 
                  (eventType.equals("Christening") && ((earliestBirth == null && latestBirth == null) || proxyBirthInd)) ||
                  (eventType.equals("Baptism") && earliestBirth == null && latestBirth == null)) {
               if (eventType.equals("Birth")) {
                  proxyBirthInd = false;
               }
               else {
                  proxyBirthInd = true;
               }
               earliestBirth = eventDate.getEarliestYear();
               latestBirth = eventDate.getLatestYear();
            }
            else {
            // if neither Birth nor Alt event, keep track of latest possible birth year
            if (!eventType.startsWith("Alt")) {    
                  if (eventDate.getLatestYear() != null && (latestPossBirth == null || eventDate.getLatestYear() < latestPossBirth)) {
                     latestPossBirth = eventDate.getLatestYear();
                  }
               }
            }

            // For death year, consider death and burial dates, in that precedence.
            // Ignore future and estimated death dates - these signify an unknown death date.
            if (eventType.equals("Death") || (eventType.equals("Burial") && latestDeath == null)) {
               if (eventDate.getEarliestYear() != null && eventDate.getEarliestYear() <= thisYear && !date.contains("Est")) {
                  earliestDeath = eventDate.getEarliestYear();
               }
               if (eventDate.getLatestYear() != null && eventDate.getLatestYear() <= thisYear && !date.contains("Est")) {
                  latestDeath = eventDate.getLatestYear();
               }
            }

            // Determine dates for doing "events out of order" checks. These dates are captured first and the "out of order"
            // checks done later to ensure the code works regardless of the order in which events are encountered.

            // Keep rules in sync with similar rules in ESINHandler.php (more explanation exists there).
            // Note: Rules in ESINHandler depend on sort order, which relies on the beginning date of a date range - 
            // therefore these rules use earliestYear when available and latestYear otherwise. Rules may not evaluate
            // exactly the same here as in the wiki, due to how the wiki sorts inexact dates, but these rules are close enough.

            if (!eventType.startsWith("Alt") && (eventDate.getEarliestYear() != null || eventDate.getLatestYear() != null)) {    // ignore Alt events
               if (!eventType.equals("Birth")) {
                  if (eventDate.getEarliestYear() != null && (firstNotBirth == null || eventDate.getEarliestYear() < firstNotBirth)) {
                     firstNotBirth = eventDate.getEarliestYear();
                  }
                  else {
                     if (eventDate.getLatestYear() != null && (firstNotBirth == null || eventDate.getLatestYear() < firstNotBirth)) {
                        firstNotBirth = eventDate.getLatestYear();
                     }
                  }
               }
               if (eventType.equals("Burial") || eventType.equals("Obituary") || 
                     eventType.equals("Funeral") || eventType.equals("Cremation") || 
                     eventType.equals("Cause of Death") || eventType.equals("Estate Inventory") || 
                     eventType.equals("Probate") || eventType.equals("Estate Settlement")) {
                  if (eventDate.getEarliestYear() != null && (firstPostDeath == null || eventDate.getEarliestYear() < firstPostDeath)) {
                     firstPostDeath = eventDate.getEarliestYear();
                  }
                  else {
                     if (eventDate.getLatestYear() != null && (firstPostDeath == null || eventDate.getLatestYear() < firstPostDeath)) {
                        firstPostDeath = eventDate.getLatestYear();
                     }
                  }
               }
               if (!eventType.equals("Death") && !eventType.equals("Burial") && !eventType.equals("Obituary") &&
                     !eventType.equals("Funeral") && !eventType.equals("Cremation") &&
                     !eventType.equals("Cause of Death") && !eventType.equals("Estate Inventory") && 
                     !eventType.equals("Probate") && !eventType.equals("Estate Settlement") &&
                     !eventType.equals("DNA") && !eventType.equals("Other") && !eventType.equals("Will") && 
                     !eventType.equals("Property") && !eventType.equals("Religion")) {
                  if (eventDate.getEarliestYear() != null) {
                     if (lastLiving == null || eventDate.getEarliestYear() > lastLiving) {
                        lastLiving = eventDate.getEarliestYear();
                     }
                  }
                  else {
                     if (eventDate.getLatestYear() != null && (lastLiving == null || eventDate.getLatestYear() > lastLiving)) {
                        lastLiving = eventDate.getLatestYear();
                     }
                  }
               }
            }
         }
         
         // Set indicator for early death
         if (eventType.equals("Death") && (date.startsWith("(in infancy") || date.startsWith("(young"))) {
            diedYoungInd = 1;
         }
         if (eventType.equals("Stillborn"))  {
            diedYoungInd = 1;
         }
      }

      // If latest and/or earliest birth years not yet set and there were events with dates, set them now.
      // Usual longest life span is used to set missing dates. For some dates this is an obvious choice; for others it is somewhat arbitrary.
      // Note that these statements are in order according to precedence of how to set the dates. It is important
      // that the statement to set latest birth date based on earliest birth date comes before earliest birth date is set here, 
      // and that the last statement that could set both dates is after the other statements.
      if (latestBirth == null && latestPossBirth != null && (earliestBirth == null || latestPossBirth >= earliestBirth)) {
         latestBirth = latestPossBirth;
      }
      if (latestBirth == null && earliestBirth != null) {
         latestBirth = earliestBirth + usualLongestLife;       // somewhat arbitrary
      }
      if (earliestBirth == null && earliestDeath != null) {
         earliestBirth = earliestDeath - usualLongestLife;
      }
      if (earliestBirth == null && lastLiving != null) {
         earliestBirth = lastLiving - usualLongestLife;
      }
      if (earliestBirth == null && latestBirth != null) {
         earliestBirth = latestBirth - usualLongestLife;       // somewhat arbitrary
      }
      if (latestBirth == null && firstNotBirth != null) {
         latestBirth = firstNotBirth + usualLongestLife;       // somewhat arbitrary
         if (earliestBirth == null) {
            earliestBirth = firstNotBirth - usualLongestLife;  // somewhat arbitrary
         }
      }

      // Determine whether any events are out of order

      // Check for event before birth (only if birth year is based on birth event rather than a proxy)
      if (!proxyBirthInd && firstNotBirth != null) {
         if ((earliestBirth != null && firstNotBirth < earliestBirth) || 
               (earliestBirth == null && latestBirth != null && firstNotBirth < latestBirth)) {
            eventOrderError = true;
         }
      }
      // Check for event that can only occur after death occurring before death
      if (firstPostDeath != null) {
         if ((earliestDeath != null && firstPostDeath < earliestDeath) || 
               (earliestDeath == null && latestDeath != null && firstPostDeath < latestDeath)) {
            eventOrderError = true;
         }
      }
      // Check for event that can only occur while living occurring after death
      if (lastLiving != null) {
         if ((earliestDeath != null && lastLiving > earliestDeath) || 
               (earliestDeath == null && latestDeath != null && lastLiving > latestDeath)) {
            eventOrderError = true;
         }
      }

//logger.debug("title=" + personTitle + "; eventOrderError=" + eventOrderError);
      // Create issues

      // One or more invalid dates found
      if (invalidDateInd) {
         issues[issueNum][0] = INVALID_DATE[0];
         issues[issueNum++][1] = INVALID_DATE[1];
      }

      // Events out of order
      if (eventOrderError) {
         issues[issueNum][0] = EVENT_ORDER[0];
         issues[issueNum++][1] = EVENT_ORDER[1];
      }

      // Living event or death more than absolutley longest life span after birth
      if ((lastLiving != null && latestBirth != null && lastLiving > latestBirth + FamilyDQAnalysis.absLongestLife) ||
            (earliestDeath != null && latestBirth != null && earliestDeath > latestBirth + absLongestLife)) {
         issues[issueNum][0] = LONG_LIFE[0];
         issues[issueNum++][1] = LONG_LIFE[1];
      }

      // Check for multiple parents and create issue if applicable
      elms = root.getChildElements("child_of_family");
      if (elms.size() > 0) {
         elm = elms.get(0);
         if (elms.size() > 1) {
            issues[issueNum][0] = MULT_PARENTS[0];
            issues[issueNum++][1] = MULT_PARENTS[1];
         }
      }

      // Check for missing gender and create issue if applicable
      elms = root.getChildElements("gender");
      String dq_gender = null;
      if (elms.size() > 0) {
         elm = elms.get(0);
         dq_gender = elm.getValue();
      }
      if (dq_gender == null || dq_gender == "") {
         issues[issueNum][0] = MISSING_GENDER[0];
         issues[issueNum++][1] = MISSING_GENDER[1];
      }

      // Fill in last 2 columns of issues array (same for all issues for this page)
      for (int i=0; issues[i][0]!=null; i++) {
         issues[i][2] = "Person";
         issues[i][3] = personTitle;
      }
   }

   // Methods to return issues and other data
   public String[][] getIssues() {
      return issues;
   }

   public Integer getEarliestBirth() {
      return earliestBirth;
   }

   public Integer getLatestBirth() {
      return latestBirth;
   }

   public Integer getEarliestDeath() {
      return earliestDeath;
   }

   public Integer getLatestDeath() {
      return latestDeath;
   }

   public short getDiedYoungInd() {
      return diedYoungInd;
   }
}
