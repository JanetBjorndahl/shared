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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Calendar;
import nu.xom.Elements;
import nu.xom.Element;

/**
 * This class identifies data quality issues that involve multiple pages and extracts dates required for batch DQ analysis
 * User: DataAnalyst
 * Date: Dec 2022
 */
public class FamilyDQAnalysis {
   private Integer earliestMarriage = null, latestMarriage = null;
   private Integer hEarliestBirth = null, hLatestBirth = null, wEarliestBirth = null, wLatestBirth = null;
   private Integer hLatestDeath = null, wLatestDeath = null;
   private Integer cEarliestBirth = null, cLatestBirth = null;
   private Elements elmsChild;
   private String[][] issues = new String[1000][5]; // [][0] = category, [][1] = description, [][2] = namesspace, [][3] = pagetitle, [][4] = immediate fix required flag

   int issueNum = 0;

   // Assumptions/thresholds for calculating years and identifying anomalies and errors
   public static final int usualLongestLife = 110, absLongestLife = 125;
   public static final int minMarriageAge = 12, maxMarriageAge = 80;
   public static final int usualYoungestFather = 15, usualYoungestMother = 12;
   public static final int absYoungestFather = 8, absYoungestMother = 4; 
   public static final int usualOldestFather = 70, usualOldestMother = 50;
   public static final int absOldestFather = 110, absOldestMother = 80;
   public static final int maxAfterParentMarriage = 35;
   public static final int maxSpouseGap = 25, maxSiblingGap = 25;

   private static int thisYear = Calendar.getInstance().get(Calendar.YEAR);

   // Issue categories, descriptions and whether they need to be fixed when editing the page
   // Note that most don't need to be fixed when editing the page, as the error (incorrect date) might be on a different page.
   public static final String[] INVALID_DATE = {"Error", "Invalid date(s); edit the page to see message(s)", "yes"};
   public static final String[] SPOUSES_SAME = {"Error", "Same person is both husband and wife", "yes"};
   public static final String[] SPOUSE_CHILD_SAME = {"Error", "Same person is both <role> and child", "yes"};
   public static final String[] MULT_SPOUSES = {"Error", "More than one <role> on a family page", "no"}; // doesn't need immediate fix - can save page and then merge
   public static final String[] YOUNG_SPOUSE = {"Anomaly", "<role> younger than " + minMarriageAge + " at marriage", "no"};
   public static final String[] ABS_OLD_SPOUSE = {"Error", "<role> older than " + absLongestLife + " at marriage", "no"};
   public static final String[] OLD_SPOUSE = {"Anomaly", "<role> older than " + maxMarriageAge + " at marriage", "no"};
   public static final String[] DEAD_SPOUSE = {"Error", "Married after death of <role>", "no"};
   public static final String[] BEF_MARR = {"Anomaly", "Born before parents' marriage", "no"};
   public static final String[] ABS_YOUNG_MOTHER = {"Error", "Born before mother was " + absYoungestMother, "no"};
   public static final String[] YOUNG_MOTHER = {"Anomaly", "Born before mother was " + usualYoungestMother, "no"};
   public static final String[] ABS_YOUNG_FATHER = {"Error", "Born before father was " + absYoungestFather, "no"};
   public static final String[] YOUNG_FATHER = {"Anomaly", "Born before father was " + usualYoungestFather, "no"};
   public static final String[] LONG_AFT_MARR = {"Anomaly", "Born over " + maxAfterParentMarriage + " years after parents' marriage", "no"};
   public static final String[] ABS_OLD_MOTHER = {"Error", "Born after mother was " + absOldestMother, "no"};
   public static final String[] OLD_MOTHER = {"Anomaly", "Born after mother was " + usualOldestMother, "no"};
   public static final String[] ABS_OLD_FATHER = {"Error", "Born after father was " + absOldestFather, "no"};
   public static final String[] OLD_FATHER = {"Anomaly", "Born after father was " + usualOldestFather, "no"};
   public static final String[] DEAD_MOTHER = {"Error", "Born after mother died", "no"};
   public static final String[] CHR_DEAD_MOTHER = {"Anomaly", "Christened/baptized after mother died", "no"};
   public static final String[] DEAD_FATHER = {"Error", "Born more than 1 year after father died", "no"};
   public static final String[] CHR_DEAD_FATHER = {"Anomaly", "Christened/baptized more than 1 year after father died", "no"};
    
   // Logger is for debugging in batch mode, using TestDQAnalysis.
   // Note: Logging has to be commented out for interactive use, due to use of a different logging package in the search project.
//private static final Logger logger = LogManager.getLogger("org.werelate.dq");

   /* Identify data quality issues for a Family page and derive other data required for batch DQ analysis 
    * This analysis can be requested to identify issues for:
    *    A family page and all its children
    *    A family page but none of its children (i.e., for performance reasons)
    *    A specific child on the family page - in that case, issues for the family page aren't returned, to minimize processing in the calling program
   */
   /**
    * @param root root element of the structured data for a family page
    * @param familyTitle string title of the family page, without namespace
    * @param childTitle scope of child pages to process - "none", "all", or a single title (without namespace)
    */
   public FamilyDQAnalysis(Element root, String familyTitle, String childTitle) {
      Elements elms;
      Element elm;

      earliestMarriage = null;
      latestMarriage = null;
      hEarliestBirth = null; 
      hLatestBirth = null;
      hLatestDeath = null;
      wEarliestBirth = null; 
      wLatestBirth = null;
      wLatestDeath = null;
      Boolean invalidDateInd = false;
      EventDate eventDate;

      // Gather earliest and latest marriage years and determine if there are any invalid dates
      elms = root.getChildElements("event_fact");
      for (int i = 0; i < elms.size(); i++) {
         elm = elms.get(i);
         String eventType = elm.getAttributeValue("type");
         eventDate = new EventDate(elm.getAttributeValue("date"));

         if (!eventDate.getOriginalDate().equals("")) {
            // Unless this analysis was requested for a specific child, track invalid dates on the family page. 
            // Optimize performance by only editing the date if no invalid dates already found.
            if ((childTitle.equals("all") || childTitle.equals("none")) && !invalidDateInd && !eventDate.editDate()) {
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
         issues[issueNum][4] = INVALID_DATE[2];
         issues[issueNum][2] = "Family";
         issues[issueNum++][3] = familyTitle;
      }

      // Get list of children for error checking below and for refining dates.
      elmsChild = root.getChildElements("child");

      // Get spouse dates and (if getting issues for the family page) determine issues related to each spouse.
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
         if (childTitle.equals("all") || childTitle.equals("none")) {
            identifyMarriageIssues("Husband", hEarliestBirth, hLatestBirth, hLatestDeath, earliestMarriage, latestMarriage, familyTitle);
            if (elms.size() > 1) {
               issues[issueNum][0] = MULT_SPOUSES[0];
               issues[issueNum][1] = MULT_SPOUSES[1].replace("<role>", "husband");
               issues[issueNum][4] = MULT_SPOUSES[2];
               issues[issueNum][2] = "Family";
               issues[issueNum++][3] = familyTitle;
            }

            // Check for husband and wife being the same person
            Elements elmsWife = root.getChildElements("wife");
            identifyCircularRelationship(elms, elmsWife, SPOUSES_SAME, "", familyTitle);
              
            // The following check duplicates a check done on the child's Person page (where the data also exists) 
            // and is only executed here when this function is called from the context of editing the family page. 
            // In this context, the separate Person page edits are not run for any of the children, so duplicate
            // issues are not created.
            if (childTitle.equals("none")) {
               // Check for husband and child being the same person
               identifyCircularRelationship(elms, elmsChild, SPOUSE_CHILD_SAME, "husband", familyTitle);
            }     
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
         if (childTitle.equals("all") || childTitle.equals("none")) {
            identifyMarriageIssues("Wife", wEarliestBirth, wLatestBirth, wLatestDeath, earliestMarriage, latestMarriage, familyTitle);
            if (elms.size() > 1) {
               issues[issueNum][0] = MULT_SPOUSES[0];
               issues[issueNum][1] = MULT_SPOUSES[1].replace("<role>", "wife");
               issues[issueNum][4] = MULT_SPOUSES[2];
               issues[issueNum][2] = "Family";
               issues[issueNum++][3] = familyTitle;
            }
              
            // The following check duplicates a check done on the child's Person page (where the data also exists) 
            // and is only executed here when this function is called from the context of editing the family page. 
            // In this context, the separate Person page edits are not run for any of the children, so duplicate
            // issues are not created.
            if (childTitle.equals("none")) {
               // Check for wife and child being the same person
               identifyCircularRelationship(elms, elmsChild, SPOUSE_CHILD_SAME, "wife", familyTitle);
            }     
         }
      }

      // For each child (or only for child identified by childTitle), get dates and determine inter-generational issues.
      if (!childTitle.equals("none")) {
         // If the original title included one or more quotation marks, the title received from PHP via SolrParams will have an
         // additional set of quotation marks around it and each original quotation mark will have been doubled. 
         // If so, reverse both of these.
         if (childTitle.startsWith("\"") && childTitle.endsWith("\"")) {
            childTitle = childTitle.substring(1,childTitle.length()-1).replace("\"\"", "\"");
         }
         for (int i = 0; i < elmsChild.size(); i++) {
            elm = elmsChild.get(i);
            String cTitle = elm.getAttributeValue("title");
            if (childTitle.equals("all") || childTitle.equals(cTitle)) {
               cEarliestBirth = null;
               cLatestBirth = null;
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

      // The following adjustments to latest marriage year are for setting/adjusting latest birth year of spouses in subsequent rounds.  
      // If latest marriage year is null or is after latest death year of a spouse, set it to the latest death year.
      if (hLatestDeath!=null && (latestMarriage==null || latestMarriage > hLatestDeath)) {
         latestMarriage = hLatestDeath;
      }
      if (wLatestDeath!=null && (latestMarriage==null || latestMarriage > wLatestDeath)) {
         latestMarriage = wLatestDeath;
      }
   }

   private void identifyMarriageIssues(String role, Integer earliestBirth, Integer latestBirth, Integer latestDeath, 
            Integer earliestMarriage, Integer latestMarriage, String title) {

      if (latestMarriage!=null) {
         if (earliestBirth!=null && latestMarriage < earliestBirth + minMarriageAge) {
            issues[issueNum][0] = YOUNG_SPOUSE[0];
            issues[issueNum][1] = YOUNG_SPOUSE[1].replace("<role>", role);
            issues[issueNum][4] = YOUNG_SPOUSE[2];
            issues[issueNum][2] = "Family";
            issues[issueNum++][3] = title;
         }
      }
      if (earliestMarriage!=null) {
         if (latestBirth!=null && earliestMarriage > latestBirth + absLongestLife) {
            issues[issueNum][0] = ABS_OLD_SPOUSE[0];
            issues[issueNum][1] = ABS_OLD_SPOUSE[1].replace("<role>", role);
            issues[issueNum][4] = ABS_OLD_SPOUSE[2];
            issues[issueNum][2] = "Family";
            issues[issueNum++][3] = title;
         }
         else {
            if (latestBirth!=null && earliestMarriage > latestBirth + maxMarriageAge) {
               issues[issueNum][0] = OLD_SPOUSE[0];
               issues[issueNum][1] = OLD_SPOUSE[1].replace("<role>", role);
               issues[issueNum][4] = OLD_SPOUSE[2];
               issues[issueNum][2] = "Family";
               issues[issueNum++][3] = title;
            }
         }
         if (latestDeath!=null && earliestMarriage > latestDeath) {
            issues[issueNum][0] = DEAD_SPOUSE[0];
            issues[issueNum][1] = DEAD_SPOUSE[1].replace("<role>", role.toLowerCase());
            issues[issueNum][4] = DEAD_SPOUSE[2];
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
            issues[issueNum][4] = BEF_MARR[2];
            issues[issueNum][2] = "Person";
            issues[issueNum++][3] = title;
         }
         if (mEarliestBirth!=null) {
            if (cLatestBirth < mEarliestBirth + absYoungestMother) {
               issues[issueNum][0] = ABS_YOUNG_MOTHER[0];
               issues[issueNum][1] = ABS_YOUNG_MOTHER[1];
               issues[issueNum][4] = ABS_YOUNG_MOTHER[2];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               if (cLatestBirth < mEarliestBirth + usualYoungestMother) {
                  issues[issueNum][0] = YOUNG_MOTHER[0];
                  issues[issueNum][1] = YOUNG_MOTHER[1];
                  issues[issueNum][4] = YOUNG_MOTHER[2];
                  issues[issueNum][2] = "Person";
                  issues[issueNum++][3] = title;
               }
            }
         }
         if (fEarliestBirth!=null) {
            if (cLatestBirth < fEarliestBirth + absYoungestFather) {
               issues[issueNum][0] = ABS_YOUNG_FATHER[0];
               issues[issueNum][1] = ABS_YOUNG_FATHER[1];
               issues[issueNum][4] = ABS_YOUNG_FATHER[2];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               if (cLatestBirth < fEarliestBirth + usualYoungestFather) {
                  issues[issueNum][0] = YOUNG_FATHER[0];
                  issues[issueNum][1] = YOUNG_FATHER[1];
                  issues[issueNum][4] = YOUNG_FATHER[2];
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
            issues[issueNum][4] = LONG_AFT_MARR[2];
            issues[issueNum][2] = "Person";
            issues[issueNum++][3] = title;
         }
         if (mLatestBirth!=null) {
            if ((cEarliestBirth > mLatestBirth + absOldestMother) && proxyBirthInd==0) {
               issues[issueNum][0] = ABS_OLD_MOTHER[0];
               issues[issueNum][1] = ABS_OLD_MOTHER[1];
               issues[issueNum][4] = ABS_OLD_MOTHER[2];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               if (cEarliestBirth > mLatestBirth + usualOldestMother) {
                  issues[issueNum][0] = OLD_MOTHER[0];
                  issues[issueNum][1] = OLD_MOTHER[1];
                  issues[issueNum][4] = OLD_MOTHER[2];
                  issues[issueNum][2] = "Person";
                  issues[issueNum++][3] = title;
                  }
            }
         }
         if (fLatestBirth!=null) {
            if ((cEarliestBirth > fLatestBirth + absOldestFather) && proxyBirthInd==0) {
               issues[issueNum][0] = ABS_OLD_FATHER[0];
               issues[issueNum][1] = ABS_OLD_FATHER[1];
               issues[issueNum][4] = ABS_OLD_FATHER[2];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               if (cEarliestBirth > fLatestBirth + usualOldestFather) {
                  issues[issueNum][0] = OLD_FATHER[0];
                  issues[issueNum][1] = OLD_FATHER[1];
                  issues[issueNum][4] = OLD_FATHER[2];
                  issues[issueNum][2] = "Person";
                  issues[issueNum++][3] = title;
                  }
            }
         }
         if (mLatestDeath!=null && cEarliestBirth > mLatestDeath) {
            if (proxyBirthInd==0) {
               issues[issueNum][0] = DEAD_MOTHER[0];
               issues[issueNum][1] = DEAD_MOTHER[1];
               issues[issueNum][4] = DEAD_MOTHER[2];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               issues[issueNum][0] = CHR_DEAD_MOTHER[0];
               issues[issueNum][1] = CHR_DEAD_MOTHER[1];
               issues[issueNum][4] = CHR_DEAD_MOTHER[2];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }   
         }
         if (fLatestDeath!=null && cEarliestBirth > fLatestDeath + 1) {
            if (proxyBirthInd==0) {
               issues[issueNum][0] = DEAD_FATHER[0];
               issues[issueNum][1] = DEAD_FATHER[1];
               issues[issueNum][4] = DEAD_FATHER[2];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               issues[issueNum][0] = CHR_DEAD_FATHER[0];
               issues[issueNum][1] = CHR_DEAD_FATHER[1];
               issues[issueNum][4] = CHR_DEAD_FATHER[2];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }   
         }
      }
   }

   // Check for two members of the same family being the same person
   private void identifyCircularRelationship(Elements basePerson, Elements comparePeople, String issueConstants[], String role, String title) {
      for (int i = 0; i < comparePeople.size(); i++) {
         Element compare = comparePeople.get(i);
         String compareTitle = compare.getAttributeValue("title");
         for (int j = 0; j < basePerson.size(); j++) {
            Element base = basePerson.get(j);
            if (compareTitle.equals(base.getAttributeValue("title"))) {
               issues[issueNum][0] = issueConstants[0];
               issues[issueNum][1] = issueConstants[1].replace("<role>", role);
               issues[issueNum][4] = issueConstants[2];
               issues[issueNum][2] = "Family";
               issues[issueNum++][3] = title;
            }
         }
      } 
   }    

   /**
    * Refine the earliest and latest birth years of a child for the purpose of determining whether or not the child
    * might be living. No need to do this if the the child is assumed to be deceased based on the latest birth year or if
    * the earliest and latest birth years are the same (the birth/christening date was given without a range).
    */
   public void refineChildBirthYear() {
      if (cEarliestBirth == null || cLatestBirth == null || (cLatestBirth > thisYear - usualLongestLife && cEarliestBirth < cLatestBirth)) {
         if (hEarliestBirth != null && (cEarliestBirth == null || (hEarliestBirth + usualYoungestFather > cEarliestBirth))) {
            cEarliestBirth = hEarliestBirth + usualYoungestFather;
         }
         if (hLatestBirth != null && (cLatestBirth == null || (hLatestBirth + usualOldestFather < cLatestBirth))) {
            cLatestBirth = hLatestBirth + usualOldestFather;
         }
         if (hLatestDeath != null && (cLatestBirth == null || (hLatestDeath + 1 < cLatestBirth))) {
            cLatestBirth = hLatestDeath + 1;
         }
         if (wEarliestBirth != null && (cEarliestBirth == null || (wEarliestBirth + usualYoungestMother > cEarliestBirth))) {
            cEarliestBirth = wEarliestBirth + usualYoungestMother;
         }
         if (wLatestBirth != null && (cLatestBirth == null || (wLatestBirth + usualOldestMother < cLatestBirth))) {
            cLatestBirth = wLatestBirth + usualOldestMother;
         }
         if (wLatestDeath != null && (cLatestBirth == null || (wLatestDeath < cLatestBirth))) {
            cLatestBirth = wLatestDeath;
         }
         if (earliestMarriage != null && (cEarliestBirth == null || (earliestMarriage > cEarliestBirth))) {
            cEarliestBirth = earliestMarriage;
         }
         if (latestMarriage != null && (cLatestBirth == null || (latestMarriage + maxAfterParentMarriage < cLatestBirth))) {
            cLatestBirth = latestMarriage + maxAfterParentMarriage;
         }
         // Refine based on birth/christening dates of siblings.
         for (int i = 0; i < elmsChild.size(); i++) {
            Element elm = elmsChild.get(i);
            String date = elm.getAttributeValue("birthdate");
            if (date==null || date.equals("")) {
               date = elm.getAttributeValue("chrdate");
            }
            if (date!=null && !date.equals("")) {
               EventDate eventDate = new EventDate(date);
               if (eventDate.getEarliestYear() != null && (cEarliestBirth == null || (eventDate.getEarliestYear() - maxSiblingGap > cEarliestBirth))) {
                  cEarliestBirth = eventDate.getEarliestYear() - maxSiblingGap;
               }
               if (eventDate.getLatestYear() != null && (cLatestBirth == null || (eventDate.getLatestYear() + maxSiblingGap < cLatestBirth))) {
                  cLatestBirth = eventDate.getLatestYear() + maxSiblingGap;
               }
            }
         }
      }
   }

   /**
    * Refine the earliest and latest birth years of the husband for the purpose of determining whether or not the husband
    * might be living. No need to do this if the the husband is assumed to be deceased based on the latest birth year or if
    * the earliest and latest birth years are the same (the birth/christening date was given without a range).
    */
    public void refineHusbandBirthYear() {
      if (hEarliestBirth == null || hLatestBirth == null || (hLatestBirth > thisYear - usualLongestLife && hEarliestBirth < hLatestBirth)) {
         if (wEarliestBirth != null && (hEarliestBirth == null || (wEarliestBirth - maxSpouseGap > hEarliestBirth))) {
            hEarliestBirth = wEarliestBirth - maxSpouseGap;
         }
         if (wLatestBirth != null && (hLatestBirth == null || (wLatestBirth + maxSpouseGap < hLatestBirth))) {
            hLatestBirth = wLatestBirth + maxSpouseGap;
         }
         if (earliestMarriage != null && (hEarliestBirth == null || (earliestMarriage - maxMarriageAge > hEarliestBirth))) {
            hEarliestBirth = earliestMarriage - maxMarriageAge;
         }
         if (latestMarriage != null && (hLatestBirth == null || (latestMarriage - minMarriageAge < hLatestBirth))) {
            hLatestBirth = latestMarriage - minMarriageAge;
         }
         // Refine based on birth/christening dates of children.
         for (int i = 0; i < elmsChild.size(); i++) {
            Element elm = elmsChild.get(i);
            String date = elm.getAttributeValue("birthdate");
            if (date==null || date.equals("")) {
               date = elm.getAttributeValue("chrdate");
            }
            if (date!=null && !date.equals("")) {
               EventDate eventDate = new EventDate(date);
               if (eventDate.getEarliestYear() != null && (hEarliestBirth == null || (eventDate.getEarliestYear() - usualOldestFather > hEarliestBirth))) {
                  hEarliestBirth = eventDate.getEarliestYear() - usualOldestFather;
               }
               if (eventDate.getLatestYear() != null && (hLatestBirth == null || (eventDate.getLatestYear() - usualYoungestFather < hLatestBirth))) {
                  hLatestBirth = eventDate.getLatestYear() - usualYoungestFather;
               }
            }
         }
      }
   }

   /**
    * Refine the earliest and latest birth years of the wife for the purpose of determining whether or not the wife
    * might be living. No need to do this if the the wife is assumed to be deceased based on the latest birth year or if
    * the earliest and latest birth years are the same (the birth/christening date was given without a range).
    */
    public void refineWifeBirthYear() {
      if (wEarliestBirth == null || wLatestBirth == null || (wLatestBirth > thisYear - usualLongestLife && wEarliestBirth < wLatestBirth)) {
         if (hEarliestBirth != null && (wEarliestBirth == null || (hEarliestBirth - maxSpouseGap > wEarliestBirth))) {
            wEarliestBirth = hEarliestBirth - maxSpouseGap;
         }
         if (hLatestBirth != null && (wLatestBirth == null || (hLatestBirth + maxSpouseGap < wLatestBirth))) {
            wLatestBirth = hLatestBirth + maxSpouseGap;
         }
         if (earliestMarriage != null && (wEarliestBirth == null || (earliestMarriage - maxMarriageAge > wEarliestBirth))) {
            wEarliestBirth = earliestMarriage - maxMarriageAge;
         }
         if (latestMarriage != null && (wLatestBirth == null || (latestMarriage - minMarriageAge < wLatestBirth))) {
            wLatestBirth = latestMarriage - minMarriageAge;
         }
         // Refine based on birth/christening dates of children.
         for (int i = 0; i < elmsChild.size(); i++) {
            Element elm = elmsChild.get(i);
            String date = elm.getAttributeValue("birthdate");
            if (date==null || date.equals("")) {
               date = elm.getAttributeValue("chrdate");
            }
            if (date!=null && !date.equals("")) {
               EventDate eventDate = new EventDate(date);
               if (eventDate.getEarliestYear() != null && (wEarliestBirth == null || (eventDate.getEarliestYear() - usualOldestMother > wEarliestBirth))) {
                  wEarliestBirth = eventDate.getEarliestYear() - usualOldestMother;
               }
               if (eventDate.getLatestYear() != null && (wLatestBirth == null || (eventDate.getLatestYear() - usualYoungestMother < wLatestBirth))) {
                  wLatestBirth = eventDate.getLatestYear() - usualYoungestMother;
               }
            }
         }
      }
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

   // The following are needed to support determination of whether a Person page without dates might be for a living person.
   public Integer getHEarliestBirth() {
      return hEarliestBirth;
   }

   public Integer getHLatestBirth() {
      return hLatestBirth;
   }

   public Integer getWEarliestBirth() {
      return wEarliestBirth;
   }

   public Integer getWLatestBirth() {
      return wLatestBirth;
   }
   public Integer getCEarliestBirth() {
      return cEarliestBirth;
   }

   public Integer getCLatestBirth() {
      return cLatestBirth;
   }
}
