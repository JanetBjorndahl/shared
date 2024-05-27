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
import org.werelate.util.SharedUtils;
import org.werelate.dq.FamilyDQAnalysis;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//import java.util.logging.Logger;

import java.util.Calendar;
import nu.xom.Elements;
import nu.xom.Element;

/**
 * This class analyzes a Person page to identify data quality issues and extract other data required for batch DQ analysis.
 * It is invoked from the wiki as well as for batch DQ analysis.
 * User: DataAnalyst
 * Date: Dec 2022
 */
public class PersonDQAnalysis {
   private Integer earliestBirth = null, latestBirth = null, earliestDeath = null, latestDeath = null;
   private short diedYoungInd = 0;
   private boolean isFamous = false;
   private String[][] issues = new String[1000][5]; // [][0] = category, [][1] = description, [][2] = namesspace, [][3] = pagetitle, [][4] = immediate fix required flag
   
   // Assumptions/thresholds for calculating years and identifying anomalies and errors
   private static final int ABS_LONGEST_LIFE = FamilyDQAnalysis.ABS_LONGEST_LIFE;
   private static final int USUAL_LONGEST_LIFE = FamilyDQAnalysis.USUAL_LONGEST_LIFE;

   private static int thisYear = Calendar.getInstance().get(Calendar.YEAR);

   // Issue categories, descriptions and whether they need to be fixed when editing the page
   private static final int EVENT_ORDER_THRESHOLD = 3;
   private static final String[] INVALID_DATE = FamilyDQAnalysis.INVALID_DATE;
//   private static final String[] EVENT_ORDER_ERROR = {"Error", "Events out of order by " + EVENT_ORDER_THRESHOLD + " or more years", "yes"};
//   private static final String[] EVENT_ORDER_UNDER_THRESHOLD = {"Anomaly", "Events out of order by less than " + EVENT_ORDER_THRESHOLD + " years", "no"};
   private static final String[] EVENT_ORDER_OVER_THRESHOLD = {"Error", "Events out of order", "yes"};
   private static final String[] EVENT_ORDER_UNDER_THRESHOLD = {"Error", "Events out of order", "no"};   // same message, different treatment in wiki edit mode
   private static final String[] LONG_LIFE = {"Error", "Event(s) more than " + ABS_LONGEST_LIFE + " years after birth", "yes"};
   private static final String[] MULT_PARENTS = {"Error", "Multiple sets of parents", "no"}; // doesn't need immediate fix - can save page and then merge parents
   private static final String[] MISSING_GENDER = {"Incomplete", "Missing gender", "yes"};
   private static final String[] PARENTS_SPOUSE_SAME = {"Error", "Child and spouse of the same family", "yes"};

   // For debugging in batch mode, such as with TestDQAnalysis. (Use logger.info rather than logger.debug when running AnalyzeDataQuality.)
   //private static final Logger logger = LogManager.getLogger("org.werelate.dq");
   // For debugging in interactive mode, as long as Search uses java.util.logging, the following is needed rather than log4j.
   //private static final Logger logger = Logger.getLogger("org.werelate.dq");

   /* Identify data quality issues for a Person page and derive other data required for batch DQ analysis */
   /**
    * @param root root element of the structured data for a person page
    * @param personTitle string title of the person page, without namespace (for a GEDCOM file, it is the id instead of the title)
    * @param isGedcom indicates whether this is being called from processing a GEDCOM file (which uses id's instead of titles)
    */
   public PersonDQAnalysis(Element root, String personTitle) {
      this(root, personTitle, false);
   } 

   public PersonDQAnalysis(Element root, String personTitle, boolean isGedcom) {
      Elements elms;
      Element elm;

      Integer latestPossBirth = null, firstNotBirth = null, firstPostDeath = null, lastLiving = null, eventOrderDiff = null;
      Boolean proxyBirthInd = false, invalidDateInd = false; 

      int issueNum = 0;

      // Gather dates required for analysis and determine if there are any invalid dates
      elms = root.getChildElements("event_fact");
      for (int i = 0; i < elms.size(); i++) {
         elm = elms.get(i);
         String eventType = elm.getAttributeValue("type");
         String eventDesc = elm.getAttributeValue("desc");
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

            // Note: The first code written for this (now decommissioned) was in ESINHandler in the wiki, and depended on sort order, which relies on 
            // the beginning date of a date range. Therefore these rules use earliestYear when available and latestYear otherwise. 
            // Since the ESINHandler function was decommissioned, it is possible to make these rules more sophisticated if the need arises.
             
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
               // These event types should never occur before death. If they do, events are out of order.
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
               // These event types are the only ones that should occur after death. If any other types occur after death, events are out of order. 
               // Note that Will is allowed after death because it is sometimes used for the date the will was presented to the court (before it was proved).
               // Property is allowed after death because it is sometimes used to note property disposition (or lack thereof) after death.
               // Religion is allowed after death because it is sometimes used to indicate when sainthood was conferred.
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
         // Determine whether the person is famous (and thus is exempt from the living persons restriction)
         if (eventType.equals("Death") && eventDesc != null && eventDesc.contains("{{FamousLivingPersonException")) {
            isFamous = true;
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
         latestBirth = earliestBirth + USUAL_LONGEST_LIFE;       // somewhat arbitrary
      }
      if (earliestBirth == null && earliestDeath != null) {
         earliestBirth = earliestDeath - USUAL_LONGEST_LIFE;
      }
      if (earliestBirth == null && lastLiving != null) {
         earliestBirth = lastLiving - USUAL_LONGEST_LIFE;
      }
      if (earliestBirth == null && latestBirth != null) {
         earliestBirth = latestBirth - USUAL_LONGEST_LIFE;       // somewhat arbitrary
      }
      if (latestBirth == null && firstNotBirth != null) {
         latestBirth = firstNotBirth + USUAL_LONGEST_LIFE;       // somewhat arbitrary
         if (earliestBirth == null) {
            earliestBirth = firstNotBirth - USUAL_LONGEST_LIFE;  // somewhat arbitrary
         }
      }

      // Determine whether any events are out of order, and if so, by how many years
      Integer compareBirth = (earliestBirth!=null) ? earliestBirth : latestBirth;
      Integer compareDeath = (earliestDeath!=null) ? earliestDeath : latestDeath;

      // Determine number of years the first non-birth event is before or after birth (only if birth year is based on birth event rather than a proxy)
      if (!proxyBirthInd && firstNotBirth != null && compareBirth != null) {
         eventOrderDiff = firstNotBirth - compareBirth;
      }
      // Determine number of years the first event that can only occur after death is before or after death
      if (firstPostDeath != null && compareDeath != null) {
         if (eventOrderDiff == null || (firstPostDeath - compareDeath) < eventOrderDiff) {
            eventOrderDiff = firstPostDeath - compareDeath;
         }
      }
      // Determine number of years the last event that can only occur while living is after or before death
      if (lastLiving != null && compareDeath != null) {
         if (eventOrderDiff == null || (compareDeath - lastLiving) < eventOrderDiff) {
            eventOrderDiff = compareDeath - lastLiving;
         }
      }

//logger.debug("title=" + personTitle + "; eventOrderError=" + eventOrderError);
      // Create issues

      // One or more invalid dates found
      if (invalidDateInd) {
         issues[issueNum][0] = INVALID_DATE[0];
         issues[issueNum][1] = INVALID_DATE[1];
         issues[issueNum++][4] = INVALID_DATE[2];
      }

      // Events out of order by a number of years at or over the threshold (has to be fixed when editing the page in the wiki)
      if (eventOrderDiff !=null && eventOrderDiff <= (0 - EVENT_ORDER_THRESHOLD)) {
         issues[issueNum][0] = EVENT_ORDER_OVER_THRESHOLD[0];
         issues[issueNum][1] = EVENT_ORDER_OVER_THRESHOLD[1];
         issues[issueNum++][4] = EVENT_ORDER_OVER_THRESHOLD[2];
      }

      // Events out of order by a number of years under the threshold (doesn't have to be fixed)
      if (eventOrderDiff !=null && eventOrderDiff < 0 && eventOrderDiff > (0 - EVENT_ORDER_THRESHOLD)) {
         issues[issueNum][0] = EVENT_ORDER_UNDER_THRESHOLD[0];
         issues[issueNum][1] = EVENT_ORDER_UNDER_THRESHOLD[1];
         issues[issueNum++][4] = EVENT_ORDER_UNDER_THRESHOLD[2];
      }

      // Living event or death more than absolutley longest life span after birth
      if ((lastLiving != null && latestBirth != null && lastLiving > latestBirth + FamilyDQAnalysis.ABS_LONGEST_LIFE) ||
            (earliestDeath != null && latestBirth != null && earliestDeath > latestBirth + ABS_LONGEST_LIFE)) {
         issues[issueNum][0] = LONG_LIFE[0];
         issues[issueNum][1] = LONG_LIFE[1];
         issues[issueNum++][4] = LONG_LIFE[2];
      }

      // Check for multiple parents and create issue if applicable
      Elements elmsParents = root.getChildElements("child_of_family");
      if (elmsParents.size() > 0) {
         if (elmsParents.size() > 1) {
            issues[issueNum][0] = MULT_PARENTS[0];
            issues[issueNum][1] = MULT_PARENTS[1];
            issues[issueNum++][4] = MULT_PARENTS[2];
         }

         // Check for circular relationship - child and spouse of same family
         Elements elmsSpouses = root.getChildElements("spouse_of_family");
         for (int i = 0; i < elmsSpouses.size(); i++) {
            Element elmSpouse = elmsSpouses.get(i);
            String sTitle = elmSpouse.getAttributeValue(isGedcom ? "id" : "title");
            for (int j = 0; j < elmsParents.size(); j++) {
               Element elmParent = elmsParents.get(j);
               if (sTitle.equals(elmParent.getAttributeValue(isGedcom ? "id" : "title"))) {
                  issues[issueNum][0] = PARENTS_SPOUSE_SAME[0];
                  issues[issueNum][1] = PARENTS_SPOUSE_SAME[1];
                  issues[issueNum++][4] = PARENTS_SPOUSE_SAME[2];
               }
            }
         }
      }

      // Check for missing gender and create issue if applicable
      elms = root.getChildElements("gender");
      String dq_gender = null;
      if (elms.size() > 0) {
         elm = elms.get(0);
         dq_gender = elm.getValue();
      }
      if (SharedUtils.isEmpty(dq_gender)) {
         issues[issueNum][0] = MISSING_GENDER[0];
         issues[issueNum][1] = MISSING_GENDER[1];
         issues[issueNum++][4] = MISSING_GENDER[2];
      }

      // Fill in remaining 2 columns of issues array (same for all issues for this page)
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

   /**
    * @return whether or not the person is dead or exempt from the rule that pages for living people can't be created
    */
   public int isDeadOrExempt() {
      if (latestDeath != null || diedYoungInd == 1 || isFamous) {
         return 1;
      }
      else {
         return 0;
      }
   }
   
}
