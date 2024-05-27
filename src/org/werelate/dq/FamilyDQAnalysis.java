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
   public static final int USUAL_LONGEST_LIFE = 110, ABS_LONGEST_LIFE = 125;
   public static final int MIN_MARRIAGE_AGE = 12, MAX_MARRIAGE_AGE = 80;
   public static final int USUAL_YOUNGEST_FATHER = 15, USUAL_YOUNGEST_MOTHER = 12;
   public static final int ABS_YOUNGEST_FATHER = 8, ABS_YOUNGEST_MOTHER = 4; 
   public static final int USUAL_OLDEST_FATHER = 70, USUAL_OLDEST_MOTHER = 50;
   public static final int ABS_OLDEST_FATHER = 110, ABS_OLDEST_MOTHER = 80;
   public static final int MAX_AFTER_PARENT_MARRIAGE = 35;
   public static final int MAX_SPOUSE_GAP = 25, MAX_SIBLING_GAP = 25;

   private static int thisYear = Calendar.getInstance().get(Calendar.YEAR);

   // Issue categories, descriptions and whether they need to be fixed when editing the page
   // Note that most don't need to be fixed when editing the page, as the error (incorrect date) might be on a different page.
   public static final String[] INVALID_DATE = {"Error", "Invalid date(s); edit the page to see message(s)", "yes"};
   public static final String[] SPOUSES_SAME = {"Error", "Same person is both husband and wife", "yes"};
   public static final String[] SPOUSE_CHILD_SAME = {"Error", "Same person is both <role> and child", "yes"};
   public static final String[] MULT_SPOUSES = {"Error", "More than one <role> on a family page", "no"}; // doesn't need immediate fix - can save page and then merge
   public static final String[] YOUNG_SPOUSE = {"Anomaly", "<role> younger than " + MIN_MARRIAGE_AGE + " at marriage", "no"};
   public static final String[] ABS_OLD_SPOUSE = {"Error", "<role> older than " + ABS_LONGEST_LIFE + " at marriage", "no"};
   public static final String[] OLD_SPOUSE = {"Anomaly", "<role> older than " + MAX_MARRIAGE_AGE + " at marriage", "no"};
   public static final String[] DEAD_SPOUSE = {"Error", "Married after death of <role>", "no"};
   public static final String[] BEF_MARR = {"Anomaly", "Born before parents' marriage", "no"};
   public static final String[] ABS_YOUNG_MOTHER = {"Error", "Born before mother was " + ABS_YOUNGEST_MOTHER, "no"};
   public static final String[] YOUNG_MOTHER = {"Anomaly", "Born before mother was " + USUAL_YOUNGEST_MOTHER, "no"};
   public static final String[] ABS_YOUNG_FATHER = {"Error", "Born before father was " + ABS_YOUNGEST_FATHER, "no"};
   public static final String[] YOUNG_FATHER = {"Anomaly", "Born before father was " + USUAL_YOUNGEST_FATHER, "no"};
   public static final String[] LONG_AFT_MARR = {"Anomaly", "Born over " + MAX_AFTER_PARENT_MARRIAGE + " years after parents' marriage", "no"};
   public static final String[] ABS_OLD_MOTHER = {"Error", "Born after mother was " + ABS_OLDEST_MOTHER, "no"};
   public static final String[] OLD_MOTHER = {"Anomaly", "Born after mother was " + USUAL_OLDEST_MOTHER, "no"};
   public static final String[] ABS_OLD_FATHER = {"Error", "Born after father was " + ABS_OLDEST_FATHER, "no"};
   public static final String[] OLD_FATHER = {"Anomaly", "Born after father was " + USUAL_OLDEST_FATHER, "no"};
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
    * @param isGedcom indicates whether this is being called from processing a GEDCOM file (which uses id's instead of titles)
    */
    public FamilyDQAnalysis(Element root, String familyTitle, String childTitle) {
      this(root, familyTitle, childTitle, false);
   } 

   public FamilyDQAnalysis(Element root, String familyTitle, String childTitle, boolean isGedcom) {
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
            else if (eventType.startsWith("Marriage") || eventType.equals("Engagement")) {
               earliestMarriage = SharedUtils.maxInteger(earliestMarriage, eventDate.getEarliestYear());
            }
            else if (!eventType.equals("Alt Marriage")) {
               latestMarriage = SharedUtils.minInteger(latestMarriage, eventDate.getLatestYear());
            }
         }
      }

      // If latest marriage year not yet set and there is an earliest marriage year, estimate latest marriage year based on
      // minimum marriage age (i.e., possible length of time between an engagement in infancy and a marriage in medieval times).
      // This is somewhat arbitrary, but helps to catch some errors not otherwise caught, without overly impacting the
      // ability to determine if someone might be living.
      if (earliestMarriage!=null && latestMarriage==null) {
         latestMarriage = earliestMarriage + MIN_MARRIAGE_AGE;
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
         if (SharedUtils.isEmpty(date)) {
            date = elm.getAttributeValue("chrdate");
         }
         if (!SharedUtils.isEmpty(date)) {
            eventDate = new EventDate(date);
            hEarliestBirth = eventDate.getEarliestYear();
            hLatestBirth = eventDate.getLatestYear();
         }
         date = elm.getAttributeValue("deathdate");
         if (SharedUtils.isEmpty(date)) {
            date = elm.getAttributeValue("burialdate");
         }
         if (!SharedUtils.isEmpty(date)) {
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
            identifyCircularRelationship(elms, elmsWife, SPOUSES_SAME, "", familyTitle, isGedcom);
              
            // The following check duplicates a check done on the child's Person page (where the data also exists) 
            // and is only executed here when this function is called from the context of editing the family page. 
            // In this context, the separate Person page edits are not run for any of the children, so duplicate
            // issues are not created.
            if (childTitle.equals("none")) {
               // Check for husband and child being the same person
               identifyCircularRelationship(elms, elmsChild, SPOUSE_CHILD_SAME, "husband", familyTitle, isGedcom);
            }     
         }
      }

      elms = root.getChildElements("wife");
      if (elms.size() > 0) {
         elm = elms.get(0);
         String date = elm.getAttributeValue("birthdate");
         if (SharedUtils.isEmpty(date)) {
            date = elm.getAttributeValue("chrdate");
         }
         if (!SharedUtils.isEmpty(date)) {
            eventDate = new EventDate(date);
            wEarliestBirth = eventDate.getEarliestYear();
            wLatestBirth = eventDate.getLatestYear();
         }
         date = elm.getAttributeValue("deathdate");
         if (SharedUtils.isEmpty(date)) {
            date = elm.getAttributeValue("burialdate");
         }
         if (!SharedUtils.isEmpty(date)) {
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
               identifyCircularRelationship(elms, elmsChild, SPOUSE_CHILD_SAME, "wife", familyTitle, isGedcom);
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
            String cTitle = elm.getAttributeValue(isGedcom ? "id" : "title");
            if (childTitle.equals("all") || childTitle.equals(cTitle)) {
               cEarliestBirth = null;
               cLatestBirth = null;
               short cProxyBirthInd = 0;
               String date = elm.getAttributeValue("birthdate");
               if (SharedUtils.isEmpty(date)) {
                  date = elm.getAttributeValue("chrdate");
                  cProxyBirthInd = 1;
               }
               if (!SharedUtils.isEmpty(date)) {
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
      latestMarriage = SharedUtils.minInteger(latestMarriage, hLatestDeath);
      latestMarriage = SharedUtils.minInteger(latestMarriage, wLatestDeath);
   }

   private void identifyMarriageIssues(String role, Integer earliestBirth, Integer latestBirth, Integer latestDeath, 
            Integer earliestMarriage, Integer latestMarriage, String title) {

      if (latestMarriage!=null) {
         if (earliestBirth!=null && latestMarriage < earliestBirth + MIN_MARRIAGE_AGE) {
            issues[issueNum][0] = YOUNG_SPOUSE[0];
            issues[issueNum][1] = YOUNG_SPOUSE[1].replace("<role>", role);
            issues[issueNum][4] = YOUNG_SPOUSE[2];
            issues[issueNum][2] = "Family";
            issues[issueNum++][3] = title;
         }
      }
      if (earliestMarriage!=null) {
         if (latestBirth!=null && earliestMarriage > latestBirth + ABS_LONGEST_LIFE) {
            issues[issueNum][0] = ABS_OLD_SPOUSE[0];
            issues[issueNum][1] = ABS_OLD_SPOUSE[1].replace("<role>", role);
            issues[issueNum][4] = ABS_OLD_SPOUSE[2];
            issues[issueNum][2] = "Family";
            issues[issueNum++][3] = title;
         }
         else {
            if (latestBirth!=null && earliestMarriage > latestBirth + MAX_MARRIAGE_AGE) {
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
            if (cLatestBirth < mEarliestBirth + ABS_YOUNGEST_MOTHER) {
               issues[issueNum][0] = ABS_YOUNG_MOTHER[0];
               issues[issueNum][1] = ABS_YOUNG_MOTHER[1];
               issues[issueNum][4] = ABS_YOUNG_MOTHER[2];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               if (cLatestBirth < mEarliestBirth + USUAL_YOUNGEST_MOTHER) {
                  issues[issueNum][0] = YOUNG_MOTHER[0];
                  issues[issueNum][1] = YOUNG_MOTHER[1];
                  issues[issueNum][4] = YOUNG_MOTHER[2];
                  issues[issueNum][2] = "Person";
                  issues[issueNum++][3] = title;
               }
            }
         }
         if (fEarliestBirth!=null) {
            if (cLatestBirth < fEarliestBirth + ABS_YOUNGEST_FATHER) {
               issues[issueNum][0] = ABS_YOUNG_FATHER[0];
               issues[issueNum][1] = ABS_YOUNG_FATHER[1];
               issues[issueNum][4] = ABS_YOUNG_FATHER[2];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               if (cLatestBirth < fEarliestBirth + USUAL_YOUNGEST_FATHER) {
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
               && cEarliestBirth > parLatestMarriage + MAX_AFTER_PARENT_MARRIAGE) {
            issues[issueNum][0] = LONG_AFT_MARR[0];
            issues[issueNum][1] = LONG_AFT_MARR[1];
            issues[issueNum][4] = LONG_AFT_MARR[2];
            issues[issueNum][2] = "Person";
            issues[issueNum++][3] = title;
         }
         if (mLatestBirth!=null) {
            if ((cEarliestBirth > mLatestBirth + ABS_OLDEST_MOTHER) && proxyBirthInd==0) {
               issues[issueNum][0] = ABS_OLD_MOTHER[0];
               issues[issueNum][1] = ABS_OLD_MOTHER[1];
               issues[issueNum][4] = ABS_OLD_MOTHER[2];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               if (cEarliestBirth > mLatestBirth + USUAL_OLDEST_MOTHER) {
                  issues[issueNum][0] = OLD_MOTHER[0];
                  issues[issueNum][1] = OLD_MOTHER[1];
                  issues[issueNum][4] = OLD_MOTHER[2];
                  issues[issueNum][2] = "Person";
                  issues[issueNum++][3] = title;
                  }
            }
         }
         if (fLatestBirth!=null) {
            if ((cEarliestBirth > fLatestBirth + ABS_OLDEST_FATHER) && proxyBirthInd==0) {
               issues[issueNum][0] = ABS_OLD_FATHER[0];
               issues[issueNum][1] = ABS_OLD_FATHER[1];
               issues[issueNum][4] = ABS_OLD_FATHER[2];
               issues[issueNum][2] = "Person";
               issues[issueNum++][3] = title;
            }
            else {
               if (cEarliestBirth > fLatestBirth + USUAL_OLDEST_FATHER) {
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
   private void identifyCircularRelationship(Elements basePerson, Elements comparePeople, String issueConstants[], 
            String role, String title, boolean isGedcom) {
      for (int i = 0; i < comparePeople.size(); i++) {
         Element compare = comparePeople.get(i);
         String compareTitle = compare.getAttributeValue(isGedcom ? "id" : "title");
         for (int j = 0; j < basePerson.size(); j++) {
            Element base = basePerson.get(j);
            if (compareTitle.equals(base.getAttributeValue(isGedcom ? "id" : "title"))) {
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
    *
    * Note that these refinement methods (refine___BirthYear) are not invoked by the constructor method because 
    * AnalyzeDataQuality uses a multi-pass strategy instead and tracks all steps of refining the birth year.
    */
   public void refineChildBirthYear() {
      if (cEarliestBirth == null || cLatestBirth == null || (cLatestBirth > thisYear - USUAL_LONGEST_LIFE && cEarliestBirth < cLatestBirth)) {
         // Refine based on dates of parents and their marriage.
         if (hEarliestBirth != null) {
            cEarliestBirth = SharedUtils.maxInteger(cEarliestBirth, hEarliestBirth + USUAL_YOUNGEST_FATHER);
         }
         if (hLatestBirth != null) {
            cLatestBirth = SharedUtils.minInteger(cLatestBirth, hLatestBirth + USUAL_OLDEST_FATHER);
         }
         if (hLatestDeath != null) {
            cLatestBirth = SharedUtils.minInteger(cLatestBirth, hLatestDeath + 1);
         }
         if (wEarliestBirth != null) {
            cEarliestBirth = SharedUtils.maxInteger(cEarliestBirth, wEarliestBirth + USUAL_YOUNGEST_MOTHER);
         }
         if (wLatestBirth != null) {
            cLatestBirth = SharedUtils.minInteger(cLatestBirth, wLatestBirth + USUAL_OLDEST_MOTHER);
         }
         cLatestBirth = SharedUtils.minInteger(cLatestBirth, wLatestDeath);
         cEarliestBirth = SharedUtils.maxInteger(cEarliestBirth, earliestMarriage);
         if (latestMarriage != null) {
            cLatestBirth = SharedUtils.minInteger(cLatestBirth, latestMarriage + MAX_AFTER_PARENT_MARRIAGE);
         }
         // Refine based on birth/christening dates of siblings.
         for (int i = 0; i < elmsChild.size(); i++) {
            Element elm = elmsChild.get(i);
            String date = elm.getAttributeValue("birthdate");
            if (date==null || date.equals("")) {
               date = elm.getAttributeValue("chrdate");
            }
            if (!SharedUtils.isEmpty(date)) {
               EventDate eventDate = new EventDate(date);
               if (eventDate.getEarliestYear() != null) {
                  cEarliestBirth = SharedUtils.maxInteger(cEarliestBirth, eventDate.getEarliestYear() - MAX_SIBLING_GAP);
               }
               if (eventDate.getLatestYear() != null) {
                  cLatestBirth = SharedUtils.minInteger(cLatestBirth, eventDate.getLatestYear() + MAX_SIBLING_GAP);
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
      if (hEarliestBirth == null || hLatestBirth == null || (hLatestBirth > thisYear - USUAL_LONGEST_LIFE && hEarliestBirth < hLatestBirth)) {
         // Refine based on birth/christening date of spouse and date of marriage
         if (wEarliestBirth != null) {
            hEarliestBirth = SharedUtils.maxInteger(hEarliestBirth, wEarliestBirth - MAX_SPOUSE_GAP);
         }
         if (wLatestBirth != null) {
            hLatestBirth = SharedUtils.minInteger(hLatestBirth, wLatestBirth + MAX_SPOUSE_GAP);
         }
         if (earliestMarriage != null) {
            hEarliestBirth = SharedUtils.maxInteger(hEarliestBirth, earliestMarriage - MAX_MARRIAGE_AGE);
         }
         if (latestMarriage != null) {
            hLatestBirth = SharedUtils.minInteger(hLatestBirth, latestMarriage - MIN_MARRIAGE_AGE);
         }
         // Refine based on birth/christening dates of children.
         for (int i = 0; i < elmsChild.size(); i++) {
            Element elm = elmsChild.get(i);
            String date = elm.getAttributeValue("birthdate");
            if (SharedUtils.isEmpty(date)) {
               date = elm.getAttributeValue("chrdate");
            }
            if (!SharedUtils.isEmpty(date)) {
               EventDate eventDate = new EventDate(date);
               if (eventDate.getEarliestYear() != null) {
                  hEarliestBirth = SharedUtils.maxInteger(hEarliestBirth, eventDate.getEarliestYear() - USUAL_OLDEST_FATHER);
               }
               if (eventDate.getLatestYear() != null) {
                  hLatestBirth = SharedUtils.minInteger(hLatestBirth, eventDate.getLatestYear() - USUAL_YOUNGEST_FATHER);
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
      if (wEarliestBirth == null || wLatestBirth == null || (wLatestBirth > thisYear - USUAL_LONGEST_LIFE && wEarliestBirth < wLatestBirth)) {
         // Refine based on birth/christening date of spouse and date of marriage
         if (hEarliestBirth != null) {
            wEarliestBirth = SharedUtils.maxInteger(wEarliestBirth, hEarliestBirth - MAX_SPOUSE_GAP);
         }
         if (hLatestBirth != null) {
            wLatestBirth = SharedUtils.minInteger(wLatestBirth, hLatestBirth + MAX_SPOUSE_GAP);
         }
         if (earliestMarriage != null) {
            wEarliestBirth = SharedUtils.maxInteger(wEarliestBirth, earliestMarriage - MAX_MARRIAGE_AGE);
         }
         if (latestMarriage != null) {
            wLatestBirth = SharedUtils.minInteger(wLatestBirth, latestMarriage - MIN_MARRIAGE_AGE);
         }
         // Refine based on birth/christening dates of children.
         for (int i = 0; i < elmsChild.size(); i++) {
            Element elm = elmsChild.get(i);
            String date = elm.getAttributeValue("birthdate");
            if (SharedUtils.isEmpty(date)) {
               date = elm.getAttributeValue("chrdate");
            }
            if (!SharedUtils.isEmpty(date)) {
               EventDate eventDate = new EventDate(date);
               if (eventDate.getEarliestYear() != null) {
                  wEarliestBirth = SharedUtils.maxInteger(wEarliestBirth, eventDate.getEarliestYear() - USUAL_OLDEST_MOTHER);
               }
               if (eventDate.getLatestYear() != null) {
                  wLatestBirth = SharedUtils.minInteger(wLatestBirth, eventDate.getLatestYear() - USUAL_YOUNGEST_MOTHER);
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
