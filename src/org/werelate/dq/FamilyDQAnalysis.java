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

import nu.xom.Elements;
import nu.xom.Element;

/**
 * This class identifies data quality issues that involve multiple pages and extracts dates required for batch DQ analysis
 * User: DataAnalyst
 * Date: Dec 2022
 */
public class FamilyDQAnalysis {
   private Integer earliestMarriage = null, latestMarriage = null;
   private String[][] issues = new String[1000][4]; // [][0] = category, [][1] = description, [][3] = namesspace, [][2] = pagetitle

   int issueNum = 0;

   // Assumptions/thresholds for calculating years and identifying anomalies and errors
   public static final int usualLongestLife = 110, absLongestLife = 125;
   public static final int minMarriageAge = 12, maxMarriageAge = 80;
   public static final int usualYoungestFather = 15, usualYoungestMother = 12;
   public static final int absYoungestFather = 8, absYoungestMother = 4; 
   public static final int usualOldestFather = 70, usualOldestMother = 50;
   public static final int absOldestFather = 110, absOldestMother = 80;
   public static final int maxAfterParentMarriage = 35;

   // Issue categories and descriptions
   public static final String[] INVALID_DATE = {"Error", "Invalid date(s); edit the page to see message(s)"};
   public static final String[] MULT_SPOUSES = {"Error", "More than one <role> on a family page"};
   public static final String[] YOUNG_SPOUSE = {"Anomaly", "<role> younger than " + minMarriageAge + " at marriage"};
   public static final String[] ABS_OLD_SPOUSE = {"Error", "<role> older than " + absLongestLife + " at marriage"};
   public static final String[] OLD_SPOUSE = {"Anomaly", "<role> older than " + maxMarriageAge + " at marriage"};
   public static final String[] DEAD_SPOUSE = {"Error", "Married after death of <role>"};
   public static final String[] BEF_MARR = {"Anomaly", "Born before parents' marriage"};
   public static final String[] ABS_YOUNG_MOTHER = {"Error", "Born before mother was " + absYoungestMother};
   public static final String[] YOUNG_MOTHER = {"Anomaly", "Born before mother was " + usualYoungestMother};
   public static final String[] ABS_YOUNG_FATHER = {"Error", "Born before father was " + absYoungestFather};
   public static final String[] YOUNG_FATHER = {"Anomaly", "Born before father was " + usualYoungestFather};
   public static final String[] LONG_AFT_MARR = {"Anomaly", "Born over " + maxAfterParentMarriage + " years after parents' marriage"};
   public static final String[] ABS_OLD_MOTHER = {"Error", "Born after mother was " + absOldestMother};
   public static final String[] OLD_MOTHER = {"Anomaly", "Born after mother was " + usualOldestMother};
   public static final String[] ABS_OLD_FATHER = {"Error", "Born after father was " + absOldestFather};
   public static final String[] OLD_FATHER = {"Anomaly", "Born after father was " + usualOldestFather};
   public static final String[] DEAD_MOTHER = {"Error", "Born after mother died"};
   public static final String[] CHR_DEAD_MOTHER = {"Anomaly", "Christened/baptized after mother died"};
   public static final String[] DEAD_FATHER = {"Error", "Born more than 1 year after father died"};
   public static final String[] CHR_DEAD_FATHER = {"Anomaly", "Christened/baptized more than 1 year after father died"};

   /* Identify data quality issues for a Family page and derive other data required for batch DQ analysis */
   /**
    * @param root root element of the structured data for a family page
    * @param familyTitle string title of the family page, without namespace
    * @param childTitle scope of child pages to process - "none", "all", or a single title (without namespace)
    */
   public FamilyDQAnalysis(Element root, String familyTitle, String childTitle) {
      Elements elms;
      Element elm;

      Integer hEarliestBirth = null, hLatestBirth = null, hLatestDeath = null;
      Integer wEarliestBirth = null, wLatestBirth = null, wLatestDeath = null;
      Boolean invalidDateInd = false;
      EventDate eventDate;

      // Gather earliest and latest marriage years and determine if there are any invalid dates
      elms = root.getChildElements("event_fact");
      for (int i = 0; i < elms.size(); i++) {
         elm = elms.get(i);
         String eventType = elm.getAttributeValue("type");
         eventDate = new EventDate(elm.getAttributeValue("date"));

         if (!eventDate.getOriginalDate().equals("")) {
            // Track invalid dates. Optimize performance by only editing the date if no invalid dates already found.
            if (!invalidDateInd && !eventDate.editDate()) {
               invalidDateInd = true;
            }

            if (eventType.equals("Marriage")) {
               earliestMarriage = eventDate.getEarliestYear();
               latestMarriage = eventDate.getLatestYear();
            }
            else {
               if (eventType.startsWith("Marriage") || eventType.equals("Engagement")) {
                  if (eventDate.getEarliestYear() != null && (earliestMarriage == null || eventDate.getEarliestYear() > earliestMarriage)) {
                     earliestMarriage = eventDate.getEarliestYear();
                  }
               }
               else {
                  if (!eventType.equals("Alt Marriage")) {
                     if (eventDate.getLatestYear() != null && (latestMarriage == null || eventDate.getLatestYear() < latestMarriage)) {
                        latestMarriage = eventDate.getLatestYear();

                     }
                  }
               }
            }
         }
      }

      // If latest marriage year not yet set and there is an earliest marriage year, estimate latest marriage year based on
      // usual youngest age of a father (i.e., possible length of time between an engagement in infancy and a marriage in medieval times).
      // This is somewhat arbitrary, but helps to catch some errors not otherwise caught
      if (earliestMarriage!=null && latestMarriage==null) {
         latestMarriage = earliestMarriage + usualYoungestFather;
      }

      // If invalid date(s) found while getting marriage years, create an issue.
      if (invalidDateInd) {
         issues[issueNum][0] = INVALID_DATE[0];
         issues[issueNum][1] = INVALID_DATE[1];
         issues[issueNum][2] = "Family";
         issues[issueNum++][3] = familyTitle;
      }

      // Get spouse dates and determine issues related to each spouse.
      elms = root.getChildElements("husband");
      if (elms.size() > 0) {
         elm = elms.get(0);
         String date = elm.getAttributeValue("birthdate");
         if (date==null || date.equals("")) {
            date = elm.getAttributeValue("chrdate");
         }
         if (date!=null && !date.equals("")) {
            eventDate = new EventDate(date);
            hEarliestBirth = eventDate.getEarliestYear();
            hLatestBirth = eventDate.getLatestYear();
         }
         date = elm.getAttributeValue("deathdate");
         if (date==null || date.equals("")) {
            date = elm.getAttributeValue("burialdate");
         }
         if (date!=null && !date.equals("")) {
            eventDate = new EventDate(date);
            hLatestDeath = eventDate.getLatestYear();
         }
         identifyMarriageIssues("Husband", hEarliestBirth, hLatestBirth, hLatestDeath, earliestMarriage, latestMarriage, familyTitle);
         if (elms.size() > 1) {
            issues[issueNum][0] = MULT_SPOUSES[0];
            issues[issueNum][1] = MULT_SPOUSES[1].replace("<role>", "husband");
            issues[issueNum][2] = "Family";
            issues[issueNum++][3] = familyTitle;
         }
      }
      elms = root.getChildElements("wife");
      if (elms.size() > 0) {
         elm = elms.get(0);
         String date = elm.getAttributeValue("birthdate");
         if (date==null || date.equals("")) {
            date = elm.getAttributeValue("chrdate");
         }
         if (date!=null && !date.equals("")) {
            eventDate = new EventDate(date);
            wEarliestBirth = eventDate.getEarliestYear();
            wLatestBirth = eventDate.getLatestYear();
         }
         date = elm.getAttributeValue("deathdate");
         if (date==null || date.equals("")) {
            date = elm.getAttributeValue("burialdate");
         }
         if (date!=null && !date.equals("")) {
            eventDate = new EventDate(date);
            wLatestDeath = eventDate.getLatestYear();
         }
         identifyMarriageIssues("Wife", wEarliestBirth, wLatestBirth, wLatestDeath, earliestMarriage, latestMarriage, familyTitle);
         if (elms.size() > 1) {
            issues[issueNum][0] = MULT_SPOUSES[0];
            issues[issueNum][1] = MULT_SPOUSES[1].replace("<role>", "wife");
            issues[issueNum][2] = "Family";
            issues[issueNum++][3] = familyTitle;
         }
      }

      // For each child (or only for child identified by childTitle), get dates and determine inter-generational issues.
      if (!childTitle.equals("none")) {
         elms = root.getChildElements("child");
         for (int i = 0; i < elms.size(); i++) {
            elm = elms.get(i);
            String cTitle = elm.getAttributeValue("title");
            if (childTitle.equals("all") || childTitle.equals(SqlTitle(cTitle))) {
               Integer cEarliestBirth = null, cLatestBirth = null;
               short cProxyBirthInd = 0;
               String date = elm.getAttributeValue("birthdate");
               if (date==null || date.equals("")) {
                  date = elm.getAttributeValue("chrdate");
                  cProxyBirthInd = 1;
               }
               if (date!=null && !date.equals("")) {
                  eventDate = new EventDate(date);
                  cEarliestBirth = eventDate.getEarliestYear();
                  cLatestBirth = eventDate.getLatestYear();
               }
               identifyChildIssues(cEarliestBirth, cLatestBirth, cProxyBirthInd, 
                     wEarliestBirth, wLatestBirth, hEarliestBirth, hLatestBirth, wLatestDeath, hLatestDeath, 
                     earliestMarriage, latestMarriage, cTitle);
            }
         }
      } 
   }

   private void identifyMarriageIssues(String role, Integer earliestBirth, Integer latestBirth, Integer latestDeath, 
            Integer earliestMarriage, Integer latestMarriage, String title) {

      if (latestMarriage!=null) {
         if (earliestBirth!=null && latestMarriage < earliestBirth + minMarriageAge) {
            issues[issueNum][0] = YOUNG_SPOUSE[0];
            issues[issueNum][1] = YOUNG_SPOUSE[1].replace("<role>", role);
            issues[issueNum][2] = "Family";
            issues[issueNum++][3] = title;
         }
      }
      if (earliestMarriage!=null) {
         if (latestBirth!=null && earliestMarriage > latestBirth + absLongestLife) {
            issues[issueNum][0] = ABS_OLD_SPOUSE[0];
            issues[issueNum][1] = ABS_OLD_SPOUSE[1].replace("<role>", role);
            issues[issueNum][2] = "Family";
            issues[issueNum++][3] = title;
         }
         else {
            if (latestBirth!=null && earliestMarriage > latestBirth + maxMarriageAge) {
               issues[issueNum][0] = OLD_SPOUSE[0];
               issues[issueNum][1] = OLD_SPOUSE[1].replace("<role>", role);
               issues[issueNum][2] = "Family";
               issues[issueNum++][3] = title;
            }
         }
         if (latestDeath!=null && earliestMarriage > latestDeath) {
            issues[issueNum][0] = DEAD_SPOUSE[0];
            issues[issueNum][1] = DEAD_SPOUSE[1].replace("<role>", role.toLowerCase());
            issues[issueNum][2] = "Family";
            issues[issueNum++][3] = title;
         }
      }
   }

   private void identifyChildIssues(Integer cEarliestBirth, Integer cLatestBirth, short proxyBirthInd, 
            Integer mEarliestBirth, Integer mLatestBirth, Integer fEarliestBirth, Integer fLatestBirth,
            Integer mLatestDeath, Integer fLatestDeath, Integer parEarliestMarriage, Integer parLatestMarriage, String title) {

      if (cLatestBirth!=null) {
         if (parEarliestMarriage!=null && cLatestBirth < parEarliestMarriage) {
            issues[issueNum][0] = BEF_MARR[0];
            issues[issueNum][1] = BEF_MARR[1];
            issues[issueNum][2] = "Person";
            issues[issueNum++][3] = title;
         }
         if (mEarliestBirth!=null) {
            if (cLatestBirth < mEarliestBirth + absYoungestMother) {
               issues[issueNum][0] = ABS_YOUNG_MOTHER[0];
               issues[issueNum][1] = ABS_YOUNG_MOTHER[1];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               if (cLatestBirth < mEarliestBirth + usualYoungestMother) {
                  issues[issueNum][0] = YOUNG_MOTHER[0];
                  issues[issueNum][1] = YOUNG_MOTHER[1];
                  issues[issueNum][2] = "Person";
                  issues[issueNum++][3] = title;
               }
            }
         }
         if (fEarliestBirth!=null) {
            if (cLatestBirth < fEarliestBirth + absYoungestFather) {
               issues[issueNum][0] = ABS_YOUNG_FATHER[0];
               issues[issueNum][1] = ABS_YOUNG_FATHER[1];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               if (cLatestBirth < fEarliestBirth + usualYoungestFather) {
                  issues[issueNum][0] = YOUNG_FATHER[0];
                  issues[issueNum][1] = YOUNG_FATHER[1];
                  issues[issueNum][2] = "Person";
                  issues[issueNum++][3] = title;
                  }
            }
         }
      }

      if (cEarliestBirth!=null) {
         if (mLatestBirth==null && fLatestBirth==null && parLatestMarriage!=null 
               && cEarliestBirth > parLatestMarriage + maxAfterParentMarriage) {
            issues[issueNum][0] = LONG_AFT_MARR[0];
            issues[issueNum][1] = LONG_AFT_MARR[1];
            issues[issueNum][2] = "Person";
            issues[issueNum++][3] = title;
         }
         if (mLatestBirth!=null) {
            if ((cEarliestBirth > mLatestBirth + absOldestMother) && proxyBirthInd==0) {
               issues[issueNum][0] = ABS_OLD_MOTHER[0];
               issues[issueNum][1] = ABS_OLD_MOTHER[1];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               if (cEarliestBirth > mLatestBirth + usualOldestMother) {
                  issues[issueNum][0] = OLD_MOTHER[0];
                  issues[issueNum][1] = OLD_MOTHER[1];
                  issues[issueNum][2] = "Person";
                  issues[issueNum++][3] = title;
                  }
            }
         }
         if (fLatestBirth!=null) {
            if ((cEarliestBirth > fLatestBirth + absOldestFather) && proxyBirthInd==0) {
               issues[issueNum][0] = ABS_OLD_FATHER[0];
               issues[issueNum][1] = ABS_OLD_FATHER[1];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               if (cEarliestBirth > fLatestBirth + usualOldestFather) {
                  issues[issueNum][0] = OLD_FATHER[0];
                  issues[issueNum][1] = OLD_FATHER[1];
                  issues[issueNum][2] = "Person";
                  issues[issueNum++][3] = title;
                  }
            }
         }
         if (mLatestDeath!=null && cEarliestBirth > mLatestDeath) {
            if (proxyBirthInd==0) {
               issues[issueNum][0] = DEAD_MOTHER[0];
               issues[issueNum][1] = DEAD_MOTHER[1];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               issues[issueNum][0] = CHR_DEAD_MOTHER[0];
               issues[issueNum][1] = CHR_DEAD_MOTHER[1];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }   
         }
         if (fLatestDeath!=null && cEarliestBirth > fLatestDeath + 1) {
            if (proxyBirthInd==0) {
               issues[issueNum][0] = DEAD_FATHER[0];
               issues[issueNum][1] = DEAD_FATHER[1];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               issues[issueNum][0] = CHR_DEAD_FATHER[0];
               issues[issueNum][1] = CHR_DEAD_FATHER[1];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }   
         }
      }
   }

   // Utility copied from AnalyzeDataQuality.java   
   private static String SqlTitle(String title) {
      if (title != null) {
         title = title.replace(" ","_").replace("\"","\\\"").replace("'","\\\'");
      }
      return title;
   }
   
   // Methods to return issues and other data
   public String[][] getIssues() {
      return issues;
   }

   public Integer getEarliestMarriage() {
      return earliestMarriage;
   }

   public Integer getLatestMarriage() {
      return latestMarriage;
   }
}
